// 后端响应封装工具：successEnvelope / failureEnvelope / pageEnvelope
// Mock 注册与状态共享见 ./mock-setup.ts

export type { ApiClientMock } from './mock-setup'

export function successEnvelope<T>(data: T) {
  return {
    data: {
      code: 'SUCCESS',
      message: '操作成功',
      data,
      traceId: 'trace-test',
    },
  }
}

export function failureEnvelope(code: string, message: string, data: unknown = null) {
  return {
    data: {
      code,
      message,
      data,
      traceId: 'trace-test',
    },
  }
}

export function pageEnvelope<T>(items: T[], overrides: Partial<{ page: number; pageSize: number; total: number; totalPages: number }> = {}) {
  const page = overrides.page ?? 1
  const pageSize = overrides.pageSize ?? 20
  const total = overrides.total ?? items.length
  return successEnvelope({
    items,
    page,
    pageSize,
    total,
    totalPages: overrides.totalPages ?? Math.max(1, Math.ceil(total / pageSize)),
  })
}
