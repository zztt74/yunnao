import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import { backendPatientFixture, backendTriageAnalyzeFixture } from './helpers/fixtures'

import { consultTriage, getMyTriageRecords, getTriageRecordById } from '@/api/triage'

const mock = getApiClientMock()

describe('triage API', () => {
  beforeEach(() => resetApiClientMock())

  describe('consultTriage', () => {
    it('maps full response with emergency and mapped department', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(successEnvelope(backendTriageAnalyzeFixture()))
      const result = await consultTriage({ chiefComplaint: '胸痛', duration: '30分钟' })
      expect(result.priority).toBe('EMERGENCY')
      expect(result.recommendedDepartmentName).toBe('急诊科')
      expect(result.reason).toBe('存在急诊风险')
      expect(result.emergencyAdvice).toBe('症状存在急诊风险，请优先急诊处理。')
      expect(result.followUpQuestion).toBeUndefined()
      expect(mock.post).toHaveBeenCalledWith('/triage/consult', {
        patientId: 42,
        symptoms: '胸痛',
        duration: '30分钟',
        supplement: undefined,
      })
    })

    it('uses default reason when AI returns nothing', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(
          backendTriageAnalyzeFixture({
            aiReason: null,
            aiFailureReason: null,
          }),
        ),
      )
      const result = await consultTriage({ chiefComplaint: '头痛' })
      expect(result.reason).toBe('AI 分诊未返回推荐理由')
    })

    it('prefers failure reason over reason when both present', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(
          backendTriageAnalyzeFixture({
            aiFailureReason: 'AI 调用超时',
            aiReason: '实际有理由',
          }),
        ),
      )
      const result = await consultTriage({ chiefComplaint: '头痛' })
      expect(result.reason).toBe('AI 调用超时')
    })

    it('uses default department name when not mapped', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(
          backendTriageAnalyzeFixture({
            mappedDepartmentName: null,
            mappingStatus: 'MANUAL',
          }),
        ),
      )
      const result = await consultTriage({ chiefComplaint: '头痛' })
      expect(result.recommendedDepartmentName).toBe('待人工选择')
      expect(result.followUpQuestion).toBe('请选择合适科室继续挂号。')
    })

    it('maps null priority to LOW', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendTriageAnalyzeFixture({ aiPriority: null })),
      )
      const result = await consultTriage({ chiefComplaint: '头痛' })
      expect(result.priority).toBe('LOW')
    })

    it('omits emergencyAdvice when aiEmergencySuggested false', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(
          backendTriageAnalyzeFixture({ aiEmergencySuggested: false }),
        ),
      )
      const result = await consultTriage({ chiefComplaint: '头痛' })
      expect(result.emergencyAdvice).toBeUndefined()
    })

    it('omits emergencyAdvice when null', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(
          backendTriageAnalyzeFixture({ aiEmergencySuggested: null }),
        ),
      )
      const result = await consultTriage({ chiefComplaint: '头痛' })
      expect(result.emergencyAdvice).toBeUndefined()
    })

    it('passes additionalInfo as supplement', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(successEnvelope(backendTriageAnalyzeFixture()))
      await consultTriage({ chiefComplaint: '胸痛', additionalInfo: '疼痛向左肩放射' })
      expect(mock.post).toHaveBeenCalledWith('/triage/consult', {
        patientId: 42,
        symptoms: '胸痛',
        duration: undefined,
        supplement: '疼痛向左肩放射',
      })
    })

    it('uses default patient id when getPatientInfo throws', async () => {
      mock.get.mockRejectedValueOnce(new Error('not found'))
      mock.post.mockResolvedValueOnce(successEnvelope(backendTriageAnalyzeFixture()))
      const result = await consultTriage({ chiefComplaint: '胸痛' }, 1)
      expect(result.id).toBe(8)
      expect(mock.post).toHaveBeenCalledWith('/triage/consult', {
        patientId: 1,
        symptoms: '胸痛',
        duration: undefined,
        supplement: undefined,
      })
    })

    it('forwards conversationId, round and history on follow-up call (F-HW-07)', async () => {
      // 第一轮
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendTriageAnalyzeFixture({ conversationId: 'conv-1', round: 1 })),
      )
      await consultTriage({
        chiefComplaint: '头痛',
        conversationId: 'conv-1',
        round: 1,
        history: [
          { role: 'user', text: '头痛' },
          { role: 'ai', text: '请补充是否伴有恶心' },
        ],
      })
      // 第二轮：前端必须沿用同一 conversationId 并携带累积 history 和递增的 round
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendTriageAnalyzeFixture({ conversationId: 'conv-1', round: 2 })),
      )
      await consultTriage({
        chiefComplaint: '是，有恶心感',
        conversationId: 'conv-1',
        round: 2,
        history: [
          { role: 'user', text: '头痛' },
          { role: 'ai', text: '请补充是否伴有恶心' },
          { role: 'user', text: '是，有恶心感' },
        ],
      })
      expect(mock.post).toHaveBeenNthCalledWith(2, '/triage/consult', {
        patientId: 42,
        symptoms: '是，有恶心感',
        duration: undefined,
        supplement: undefined,
        conversationId: 'conv-1',
        history: [
          { role: 'USER', content: '头痛' },
          { role: 'ASSISTANT', content: '请补充是否伴有恶心' },
          { role: 'USER', content: '是，有恶心感' },
        ],
        round: 2,
      })
    })
  })

  describe('getMyTriageRecords', () => {
    it('returns mapped records from patient page', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([backendTriageAnalyzeFixture({ id: 8 })]),
      )
      const result = await getMyTriageRecords()
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(8)
    })

    it('record mapping uses symptoms as reason fallback', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendTriageAnalyzeFixture({
            id: 9,
            symptoms: '咳嗽',
            aiReason: null,
            aiFailureReason: null,
          }),
        ]),
      )
      const result = await getMyTriageRecords()
      expect(result[0].reason).toBe('咳嗽')
    })

    it('record mapping uses default department when not mapped', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendTriageAnalyzeFixture({
            id: 10,
            aiDepartmentCode: null,
            mappedDepartmentId: null,
          }),
        ]),
      )
      const result = await getMyTriageRecords()
      expect(result[0].recommendedDepartmentName).toBe('待人工选择')
      expect(result[0].recommendedDepartmentId).toBe(0)
    })
  })

  describe('getTriageRecordById', () => {
    it('returns mapped record', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendTriageAnalyzeFixture({ id: 8 })))
      const result = await getTriageRecordById(8)
      expect(result.id).toBe(8)
      expect(mock.get).toHaveBeenCalledWith('/triage/8')
    })
  })
})
