import { describe, expect, it } from 'vitest'

import { ApiResponseError, parseApiResponse } from '@/api/response'
import type { ApiResponse } from '@/types/api'

describe('parseApiResponse', () => {
  it('returns data field for successful envelope', () => {
    const response: ApiResponse<{ id: number }> = {
      code: 'SUCCESS',
      message: '操作成功',
      data: { id: 1 },
      traceId: 't-1',
    }
    expect(parseApiResponse(response)).toEqual({ id: 1 })
  })

  it('returns data as-is for primitive payloads', () => {
    const response: ApiResponse<number> = {
      code: 'SUCCESS',
      message: '',
      data: 42,
      traceId: 't-2',
    }
    expect(parseApiResponse(response)).toBe(42)
  })

  it('returns null data when backend returns null on success', () => {
    const response: ApiResponse<null> = {
      code: 'SUCCESS',
      message: '',
      data: null,
      traceId: 't-3',
    }
    expect(parseApiResponse(response)).toBeNull()
  })

  it('throws ApiResponseError with formatted message including traceId', () => {
    const response: ApiResponse<null> = {
      code: 'VALIDATION_FAILED',
      message: '请求参数错误',
      data: null,
      traceId: 'trace-x',
    }
    expect(() => parseApiResponse(response)).toThrow(
      '请求参数错误（traceId: trace-x）',
    )
  })

  it('exposes code and traceId on thrown ApiResponseError', () => {
    const response: ApiResponse<null> = {
      code: 'AUTH_FAILED',
      message: '未登录',
      data: null,
      traceId: 'trace-y',
    }
    try {
      parseApiResponse(response)
      expect.fail('expected to throw')
    } catch (e) {
      expect(e).toBeInstanceOf(ApiResponseError)
      const err = e as ApiResponseError
      expect(err.code).toBe('AUTH_FAILED')
      expect(err.traceId).toBe('trace-y')
      expect(err.name).toBe('ApiResponseError')
    }
  })

  it('throws for every non-SUCCESS code', () => {
    for (const code of ['UNAUTHORIZED', 'FORBIDDEN', 'NOT_FOUND', 'INTERNAL_ERROR']) {
      const response: ApiResponse<null> = {
        code,
        message: `错误：${code}`,
        data: null,
        traceId: 't',
      }
      expect(() => parseApiResponse(response)).toThrow(ApiResponseError)
    }
  })
})
