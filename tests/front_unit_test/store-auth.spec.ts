// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { loginMock, logoutMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  logoutMock: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  login: loginMock,
  logout: logoutMock,
}))

import { useAuthStore } from '@/stores/auth'
import type { UserInfo } from '@/types/auth'

const user: UserInfo = {
  userId: 1,
  username: 'alice',
  roles: ['PATIENT'],
  mustChangePassword: false,
}

const doctorUser: UserInfo = {
  userId: 2,
  username: 'doc',
  roles: ['DOCTOR'],
  mustChangePassword: false,
}

const adminUser: UserInfo = {
  userId: 3,
  username: 'admin',
  roles: ['ADMIN'],
  mustChangePassword: true,
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    loginMock.mockReset()
    logoutMock.mockReset()
  })

  describe('initial state', () => {
    it('starts unauthenticated when storage empty', () => {
      const auth = useAuthStore()
      expect(auth.accessToken).toBeNull()
      expect(auth.userInfo).toBeNull()
      expect(auth.isAuthenticated).toBe(false)
    })

    it('reads token from storage on init', () => {
      sessionStorage.setItem('cloud-brain.access-token', 'stored-token')
      sessionStorage.setItem('cloud-brain.user', JSON.stringify(user))
      const auth = useAuthStore()
      expect(auth.accessToken).toBe('stored-token')
      expect(auth.userInfo?.userId).toBe(1)
      expect(auth.isAuthenticated).toBe(true)
    })

    it('falls back to null user when storage has invalid JSON', () => {
      sessionStorage.setItem('cloud-brain.user', '{not valid json')
      const auth = useAuthStore()
      expect(auth.userInfo).toBeNull()
    })
  })

  describe('role helpers', () => {
    it('isPatient / isDoctor / isAdmin reflect roles', () => {
      const auth = useAuthStore()
      auth.establishSession('t', patientUser())
      expect(auth.isPatient).toBe(true)
      expect(auth.isDoctor).toBe(false)
      expect(auth.isAdmin).toBe(false)
    })

    it('returns false for role helpers when no user', () => {
      const auth = useAuthStore()
      expect(auth.isPatient).toBe(false)
      expect(auth.isDoctor).toBe(false)
      expect(auth.isAdmin).toBe(false)
    })

    it('primaryRole prioritizes ADMIN over DOCTOR over PATIENT', () => {
      const auth = useAuthStore()
      auth.establishSession('t', { ...user, roles: ['PATIENT', 'DOCTOR'] })
      expect(auth.primaryRole).toBe('DOCTOR')

      auth.establishSession('t', { ...user, roles: ['PATIENT', 'DOCTOR', 'ADMIN'] })
      expect(auth.primaryRole).toBe('ADMIN')

      auth.establishSession('t', { ...user, roles: ['PATIENT'] })
      expect(auth.primaryRole).toBe('PATIENT')
    })

    it('primaryRole returns null when no roles', () => {
      const auth = useAuthStore()
      auth.establishSession('t', { ...user, roles: [] })
      expect(auth.primaryRole).toBeNull()
    })

    it('hasRole matches by role name', () => {
      const auth = useAuthStore()
      auth.establishSession('t', doctorUser)
      expect(auth.hasRole('DOCTOR')).toBe(true)
      expect(auth.hasRole('ADMIN')).toBe(false)
    })

    it('hasRole returns false when no user', () => {
      const auth = useAuthStore()
      expect(auth.hasRole('DOCTOR')).toBe(false)
    })

    it('mustChangePassword reflects user flag', () => {
      const auth = useAuthStore()
      auth.establishSession('t', user)
      expect(auth.mustChangePassword).toBe(false)
      auth.establishSession('t', adminUser)
      expect(auth.mustChangePassword).toBe(true)
    })
  })

  describe('establishSession / clearSession', () => {
    it('establishSession writes to storage', () => {
      const auth = useAuthStore()
      auth.establishSession('token-1', user)
      expect(sessionStorage.getItem('cloud-brain.access-token')).toBe('token-1')
      expect(sessionStorage.getItem('cloud-brain.user')).toBe(JSON.stringify(user))
    })

    it('clearSession removes from storage', () => {
      const auth = useAuthStore()
      auth.establishSession('token-1', user)
      auth.clearSession()
      expect(auth.accessToken).toBeNull()
      expect(auth.userInfo).toBeNull()
      expect(sessionStorage.getItem('cloud-brain.access-token')).toBeNull()
    })
  })

  describe('login', () => {
    it('calls apiLogin and establishes session', async () => {
      const auth = useAuthStore()
      loginMock.mockResolvedValueOnce({
        accessToken: 'new-token',
        tokenType: 'Bearer',
        userId: 5,
        username: 'bob',
        roles: ['DOCTOR'],
        mustChangePassword: false,
        expiresIn: 3600,
      })
      await auth.login({ username: 'bob', password: 'pw' })
      expect(loginMock).toHaveBeenCalledWith({ username: 'bob', password: 'pw' })
      expect(auth.accessToken).toBe('new-token')
      expect(auth.userInfo?.userId).toBe(5)
      expect(auth.userInfo?.roles).toEqual(['DOCTOR'])
    })

    it('leaves session untouched and propagates error when apiLogin throws', async () => {
      const auth = useAuthStore()
      loginMock.mockRejectedValueOnce(new Error('INVALID_CREDENTIALS'))
      await expect(auth.login({ username: 'bob', password: 'wrong' })).rejects.toThrow('INVALID_CREDENTIALS')
      expect(auth.accessToken).toBeNull()
      expect(auth.userInfo).toBeNull()
      expect(auth.isAuthenticated).toBe(false)
      expect(sessionStorage.getItem('cloud-brain.access-token')).toBeNull()
      expect(sessionStorage.getItem('cloud-brain.user')).toBeNull()
    })

    it('does not overwrite an existing session when apiLogin throws', async () => {
      const auth = useAuthStore()
      // 已有会话
      auth.establishSession('existing-token', user)
      loginMock.mockRejectedValueOnce(new Error('network down'))
      await expect(auth.login({ username: 'bob', password: 'pw' })).rejects.toThrow('network down')
      // 旧会话应保留
      expect(auth.accessToken).toBe('existing-token')
      expect(auth.userInfo?.userId).toBe(1)
    })
  })

  describe('logout', () => {
    it('always clears session even if api logout throws', async () => {
      const auth = useAuthStore()
      auth.establishSession('token', user)
      logoutMock.mockRejectedValueOnce(new Error('network down'))
      await expect(auth.logout()).resolves.toBeUndefined()
      expect(auth.accessToken).toBeNull()
      expect(auth.userInfo).toBeNull()
    })

    it('clears session on success', async () => {
      const auth = useAuthStore()
      auth.establishSession('token', user)
      logoutMock.mockResolvedValueOnce(undefined)
      await auth.logout()
      expect(auth.accessToken).toBeNull()
    })
  })
})

function patientUser(): UserInfo {
  return { userId: 1, username: 'alice', roles: ['PATIENT'], mustChangePassword: false }
}
