// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const mocks = vi.hoisted(() => ({
  pushMock: vi.fn(),
  currentRouteRef: { value: { path: '/dashboard' } },
  clearSessionMock: vi.fn(),
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: mocks.currentRouteRef,
    push: mocks.pushMock,
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ clearSession: mocks.clearSessionMock }),
}))

import { apiClient } from '@/api/client'

beforeEach(() => {
  sessionStorage.clear()
  mocks.pushMock.mockReset()
  mocks.clearSessionMock.mockReset()
  mocks.currentRouteRef.value = { path: '/dashboard' }
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.restoreAllMocks()
})

function getRequestHandler() {
  const handlers = apiClient.interceptors.request.handlers as Array<{
    fulfilled: (config: unknown) => unknown
    rejected?: (err: unknown) => unknown
  }>
  return handlers[0]?.fulfilled
}

function getResponseErrorHandler() {
  const handlers = apiClient.interceptors.response.handlers as Array<{
    fulfilled: (config: unknown) => unknown
    rejected: (err: unknown) => unknown
  }>
  return handlers[0]?.rejected
}

describe('apiClient request interceptor', () => {
  it('attaches Bearer token when sessionStorage has a token', () => {
    sessionStorage.setItem('cloud-brain.access-token', 'token-123')
    const handler = getRequestHandler()
    const config = { headers: {} as Record<string, string> }
    const result = handler(config) as { headers: Record<string, string> }
    expect(result.headers.Authorization).toBe('Bearer token-123')
  })

  it('does not set Authorization header when no token is stored', () => {
    const handler = getRequestHandler()
    const config = { headers: {} as Record<string, string> }
    const result = handler(config) as { headers: Record<string, string> }
    expect(result.headers.Authorization).toBeUndefined()
  })
})

describe('apiClient response interceptor', () => {
  it('clears the session and routes to / on 401 when not on landing page', async () => {
    mocks.currentRouteRef.value = { path: '/patients' }
    const handler = getResponseErrorHandler()
    const error = { response: { status: 401 } }
    await expect(handler(error)).rejects.toBe(error)
    expect(mocks.clearSessionMock).toHaveBeenCalledTimes(1)
    expect(mocks.pushMock).toHaveBeenCalledWith('/')
  })

  it('clears the session but skips navigation when already on landing page', async () => {
    mocks.currentRouteRef.value = { path: '/' }
    const handler = getResponseErrorHandler()
    const error = { response: { status: 401 } }
    await expect(handler(error)).rejects.toBe(error)
    expect(mocks.clearSessionMock).toHaveBeenCalledTimes(1)
    expect(mocks.pushMock).not.toHaveBeenCalled()
  })

  it('routes to /forbidden on 403 and does not clear the session', async () => {
    const handler = getResponseErrorHandler()
    const error = { response: { status: 403 } }
    await expect(handler(error)).rejects.toBe(error)
    expect(mocks.clearSessionMock).not.toHaveBeenCalled()
    expect(mocks.pushMock).toHaveBeenCalledWith('/forbidden')
  })

  it('rejects with the original error for non-401/403 status codes', async () => {
    const handler = getResponseErrorHandler()
    const error = { response: { status: 500 } }
    await expect(handler(error)).rejects.toBe(error)
    expect(mocks.clearSessionMock).not.toHaveBeenCalled()
    expect(mocks.pushMock).not.toHaveBeenCalled()
  })

  it('rejects with the original error when no response is present', async () => {
    const handler = getResponseErrorHandler()
    const error = new Error('network down')
    await expect(handler(error)).rejects.toBe(error)
  })
})
