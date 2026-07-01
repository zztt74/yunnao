import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import {
  backendEncounterFixture,
  backendExaminationFixture,
  backendExaminationResultFixture,
  backendPatientFixture,
} from './helpers/fixtures'

import {
  createExamination,
  getEncounterExaminations,
  getExaminationById,
  getMyExaminations,
  reviewExamination,
  simulateEnterResult,
} from '@/api/examination'

const mock = getApiClientMock()

const encounterFixture = backendEncounterFixture({ id: 9, departmentId: 2, departmentName: '内科' })

describe('examination API', () => {
  beforeEach(() => resetApiClientMock())

  describe('getMyExaminations', () => {
    it('returns all statuses for patient (UF-02 fix)', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendExaminationFixture({ id: 1, status: 'ORDERED' }),
          backendExaminationFixture({ id: 2, status: 'IN_PROGRESS' }),
          backendExaminationFixture({ id: 3, status: 'RESULT_ENTERED' }),
          backendExaminationFixture({ id: 4, status: 'REVIEWED' }),
          backendExaminationFixture({ id: 5, status: 'CANCELLED' }),
        ]),
      )
      const result = await getMyExaminations()
      expect(result).toHaveLength(5)
      // 验证所有 5 个状态都被返回，不依赖固定顺序（实现按 orderedAt desc 排序）
      expect(new Set(result.map((r) => r.status))).toEqual(
        new Set(['ORDERED', 'IN_PROGRESS', 'RESULT_ENTERED', 'REVIEWED', 'CANCELLED']),
      )
    })

    it('filters by type on client side', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendExaminationFixture({ id: 1, orderType: 'LABORATORY' }),
          backendExaminationFixture({ id: 2, orderType: 'EXAMINATION' }),
        ]),
      )
      const result = await getMyExaminations({ type: 'LABORATORY' })
      expect(result).toHaveLength(1)
      expect(result[0].type).toBe('LABORATORY')
    })

    it('filters by fromDate on client side', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendExaminationFixture({ id: 1, orderedAt: '2026-06-15T00:00:00' }),
          backendExaminationFixture({ id: 2, orderedAt: '2026-07-15T00:00:00' }),
        ]),
      )
      const result = await getMyExaminations({ fromDate: '2026-07-01' })
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(2)
    })

    it('filters by toDate inclusive of T23:59:59', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendExaminationFixture({ id: 1, orderedAt: '2026-06-15T00:00:00' }),
          backendExaminationFixture({ id: 2, orderedAt: '2026-06-30T23:30:00' }),
          backendExaminationFixture({ id: 3, orderedAt: '2026-07-01T00:00:00' }),
        ]),
      )
      const result = await getMyExaminations({ toDate: '2026-06-30' })
      // id=2 (06-30) 命中；id=1 (06-15) 命中；id=3 (07-01) 超出
      expect(result.map((r) => r.id).sort()).toEqual([1, 2])
    })

    it('sorts desc by orderedAt', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendExaminationFixture({ id: 1, orderedAt: '2026-06-01T00:00:00' }),
          backendExaminationFixture({ id: 2, orderedAt: '2026-06-30T00:00:00' }),
        ]),
      )
      const result = await getMyExaminations()
      expect(result[0].id).toBe(2)
    })
  })

  describe('getExaminationById', () => {
    it('fetches result for RESULT_ENTERED status', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'RESULT_ENTERED' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendExaminationResultFixture()))
      const result = await getExaminationById(100)
      expect(result.status).toBe('RESULT_ENTERED')
      expect(result.labItems).toHaveLength(1)
      expect(result.findings).toBe('白细胞 6.5')
      expect(result.impression).toBe('正常')
    })

    it('fetches result for REVIEWED status', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'REVIEWED' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendExaminationResultFixture()))
      const result = await getExaminationById(100)
      expect(result.labItems).toHaveLength(1)
    })

    it('does not fetch result for ORDERED status', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'ORDERED' })),
      )
      const result = await getExaminationById(100)
      expect(result.status).toBe('ORDERED')
      expect(result.labItems).toEqual([])
      expect(mock.get).toHaveBeenCalledTimes(1)
    })

    it('falls back gracefully when result fetch fails', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'RESULT_ENTERED' })),
      )
      mock.get.mockRejectedValueOnce(new Error('result not ready'))
      const result = await getExaminationById(100)
      expect(result.status).toBe('RESULT_ENTERED')
      expect(result.labItems).toEqual([])
    })
  })

  describe('getEncounterExaminations', () => {
    it('fetches result for each RESULT_ENTERED order', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          backendExaminationFixture({ id: 1, status: 'ORDERED' }),
          backendExaminationFixture({ id: 2, status: 'RESULT_ENTERED' }),
        ]),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendExaminationResultFixture()))
      const result = await getEncounterExaminations(9)
      expect(result).toHaveLength(2)
      expect(result[0].status).toBe('ORDERED')
      expect(result[1].labItems).toHaveLength(1)
    })
  })

  describe('createExamination', () => {
    it('posts to /examinations with orderType/itemName', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendExaminationFixture({ id: 999 })))
      const result = await createExamination(encounterFixture, {
        encounterId: encounterFixture.id,
        type: 'EXAMINATION',
        itemName: '胸部 X 光',
      })
      expect(result.id).toBe(999)
      expect(mock.post).toHaveBeenCalledWith('/examinations', {
        encounterId: 9,
        orderType: 'EXAMINATION',
        itemCode: '胸部 X 光',
        itemName: '胸部 X 光',
      })
    })
  })

  describe('simulateEnterResult', () => {
    it('starts before result entry when status is ORDERED', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'ORDERED' })),
      )
      mock.post.mockResolvedValueOnce(successEnvelope({}))
      mock.post.mockResolvedValueOnce(successEnvelope(backendExaminationResultFixture()))
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'RESULT_ENTERED' })),
      )
      await simulateEnterResult(100)
      expect(mock.post).toHaveBeenCalledTimes(2)
      expect(mock.post).toHaveBeenNthCalledWith(1, '/examinations/100/start')
      expect(mock.post).toHaveBeenNthCalledWith(2, '/examinations/100/result', {
        resultText: '已完成检查检验，结果录入来自联调模拟人员。',
        normalRange: '参考范围见原始报告',
        conclusion: '未见明显异常',
        abnormalFlag: 'NORMAL',
      })
    })

    it('skips start when status is not ORDERED', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'IN_PROGRESS' })),
      )
      mock.post.mockResolvedValueOnce(successEnvelope(backendExaminationResultFixture()))
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'RESULT_ENTERED' })),
      )
      await simulateEnterResult(100)
      // 只调 result + 最终 get，没有 /start
      expect(mock.post).toHaveBeenCalledTimes(1)
      expect(mock.post).toHaveBeenCalledWith('/examinations/100/result', expect.any(Object))
    })
  })

  describe('reviewExamination', () => {
    it('posts to /examinations/:id/review', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope({}))
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'REVIEWED' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendExaminationResultFixture()))
      await reviewExamination(100)
      expect(mock.post).toHaveBeenCalledWith('/examinations/100/review')
    })
  })

  describe('mapping details', () => {
    it('EXAMINATION type for non-LABORATORY orderType', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'ORDERED', orderType: 'EXAMINATION' })),
      )
      const result = await getExaminationById(100)
      expect(result.type).toBe('EXAMINATION')
      expect(result.labItems).toEqual([])
    })

    it('abnormalFlag HIGH maps to HIGH', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'REVIEWED' })),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationResultFixture({ abnormalFlag: 'HIGH' })),
      )
      const result = await getExaminationById(100)
      expect(result.labItems?.[0].abnormalFlag).toBe('HIGH')
    })

    it('abnormalFlag unknown maps to NORMAL', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'REVIEWED' })),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationResultFixture({ abnormalFlag: 'WEIRD' })),
      )
      const result = await getExaminationById(100)
      expect(result.labItems?.[0].abnormalFlag).toBe('NORMAL')
    })

    it('reporterName uses user-{id} format', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'REVIEWED' })),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationResultFixture({ enteredBy: 7 })),
      )
      const result = await getExaminationById(100)
      expect(result.reporterName).toBe('user-7')
    })

    it('reporterName null when enteredBy missing', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationFixture({ status: 'REVIEWED' })),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendExaminationResultFixture({ enteredBy: null })),
      )
      const result = await getExaminationById(100)
      expect(result.reporterName).toBeNull()
    })
  })
})
