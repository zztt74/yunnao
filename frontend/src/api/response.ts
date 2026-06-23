import type { ApiResponse } from '@/types/api'

export class ApiResponseError extends Error {
  readonly code: string
  readonly traceId: string

  constructor(code: string, message: string, traceId: string) {
    super(`${message}（traceId: ${traceId}）`)
    this.name = 'ApiResponseError'
    this.code = code
    this.traceId = traceId
  }
}

export function parseApiResponse<T>(response: ApiResponse<T>): T {
  if (response.code !== 'SUCCESS') {
    throw new ApiResponseError(response.code, response.message, response.traceId)
  }

  return response.data
}
