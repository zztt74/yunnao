import { describe, expect, it } from 'vitest'

import { parseApiResponse } from '@/api/response'

describe('parseApiResponse', () => {
  it('returns data for a successful frozen response envelope', () => {
    expect(
      parseApiResponse({
        code: 'SUCCESS',
        message: '操作成功',
        data: { id: 7 },
        traceId: 'trace-001',
      }),
    ).toEqual({ id: 7 })
  })

  it('throws an error containing the trace id for a failed response', () => {
    expect(() =>
      parseApiResponse({
        code: 'VALIDATION_FAILED',
        message: '请求参数错误',
        data: null,
        traceId: 'trace-002',
      }),
    ).toThrow('请求参数错误（traceId: trace-002）')
  })
})
