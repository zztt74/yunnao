// 共享 vi.hoisted mock 工厂：必须在工厂内部用 vi.fn()，不能引用外部 import
// 用法：在测试文件 import 任何 "@/api/..." 之前，import './helpers/mock-setup'
//       这样会自动 vi.mock('@/api/client')，并通过 getApiClientMock() 获取共享 mock
//       在 beforeEach 中调用 resetApiClientMock() 重置状态
import { vi } from 'vitest'

export type ApiClientMock = {
  get: ReturnType<typeof vi.fn>
  post: ReturnType<typeof vi.fn>
  put: ReturnType<typeof vi.fn>
  delete: ReturnType<typeof vi.fn>
}

const state = vi.hoisted(() => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  } as ApiClientMock,
}))

vi.mock('@/api/client', () => ({ apiClient: state.apiClient }))

export function getApiClientMock(): ApiClientMock {
  return state.apiClient
}

export function resetApiClientMock() {
  state.apiClient.get.mockReset()
  state.apiClient.post.mockReset()
  state.apiClient.put.mockReset()
  state.apiClient.delete.mockReset()
}
