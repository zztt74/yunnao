import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import {
  backendEncounterFixture,
  backendMedicalRecordFixture,
  backendPatientFixture,
} from './helpers/fixtures'

import {
  confirmMedicalRecord,
  generateMedicalRecordDraft,
  getEncounterMedicalRecord,
  getMedicalRecordById,
  getMyMedicalRecords,
  saveMedicalRecord,
} from '@/api/medical-record'

const mock = getApiClientMock()

const encounterFixture = backendEncounterFixture({ id: 9, departmentId: 2, departmentName: '内科' })

const savePayload = {
  encounterId: 9,
  chiefComplaint: '咳嗽',
  presentIllness: '3 天',
  pastHistory: '无',
  physicalExam: '双肺呼吸音清',
  preliminaryDiagnosis: '急性支气管炎',
  treatmentAdvice: '对症治疗',
  status: 'DRAFT' as const,
}

describe('medical-record API', () => {
  beforeEach(() => resetApiClientMock())

  describe('getMyMedicalRecords', () => {
    it('returns only CONFIRMED records sorted desc', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendMedicalRecordFixture({ id: 1, status: 'CONFIRMED' }),
          backendMedicalRecordFixture({ id: 2, status: 'DRAFT' }),
        ]),
      )
      const result = await getMyMedicalRecords()
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(1)
    })

    it('filters by fromDate/toDate on client side', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendMedicalRecordFixture({ id: 1, createdAt: '2026-06-15T00:00:00' }),
          backendMedicalRecordFixture({ id: 2, createdAt: '2026-06-30T00:00:00' }),
          backendMedicalRecordFixture({ id: 3, createdAt: '2026-07-15T00:00:00' }),
        ]),
      )
      const result = await getMyMedicalRecords({
        fromDate: '2026-07-01',
        toDate: '2026-07-31',
      })
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(3)
    })
  })

  describe('getMedicalRecordById', () => {
    it('parses JSON content and falls back to plain text', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(
          backendMedicalRecordFixture({
            content: 'plain text complaint',
            status: 'CONFIRMED',
          }),
        ),
      )
      const result = await getMedicalRecordById(500)
      expect(result.chiefComplaint).toBe('plain text complaint')
    })

    it('parses structured content from JSON', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendMedicalRecordFixture()))
      const result = await getMedicalRecordById(500)
      expect(result.chiefComplaint).toBe('咳嗽')
      expect(result.preliminaryDiagnosis).toBe('急性支气管炎')
    })
  })

  describe('getEncounterMedicalRecord', () => {
    it('returns null for empty list', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      const result = await getEncounterMedicalRecord(9)
      expect(result).toBeNull()
    })

    it('returns first record', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([backendMedicalRecordFixture()]))
      const result = await getEncounterMedicalRecord(9)
      expect(result?.id).toBe(500)
    })
  })

  describe('generateMedicalRecordDraft', () => {
    it('posts to /medical-records/ai-generate and merges content', async () => {
      const response = backendMedicalRecordFixture({
        content: JSON.stringify({
          chiefComplaint: 'AI 主诉',
          presentIllness: 'AI 现病史',
          pastHistory: 'AI 既往史',
          physicalExam: 'AI 体格检查',
          preliminaryDiagnosis: 'AI 初诊',
          treatmentAdvice: 'AI 治疗建议',
        }),
      })
      mock.post.mockResolvedValueOnce(successEnvelope(response))
      const result = await generateMedicalRecordDraft({
        encounterId: 9,
        chiefComplaint: 'orig',
        presentIllness: 'orig present',
        pastHistory: 'orig past',
        physicalExam: 'orig phys',
        diagnoses: ['高血压'],
      })
      expect(result.chiefComplaint).toBe('AI 主诉')
      expect(result.aiStatus).toBe('SUCCESS')
      expect(mock.post).toHaveBeenCalledWith('/medical-records/ai-generate', {
        encounterId: 9,
        chiefComplaint: 'orig',
        presentIllness: 'orig present',
        pastHistory: 'orig past',
        physicalExamination: 'orig phys',
        preliminaryDiagnoses: ['高血压'],
      })
    })

    it('falls back to input fields when AI omits fields', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope(
          backendMedicalRecordFixture({
            content: JSON.stringify({}),
          }),
        ),
      )
      const result = await generateMedicalRecordDraft({
        encounterId: 9,
        chiefComplaint: 'orig',
        presentIllness: 'orig present',
        pastHistory: 'orig past',
        physicalExam: 'orig phys',
        diagnoses: ['高血压', '糖尿病'],
      })
      expect(result.chiefComplaint).toBe('orig')
      expect(result.preliminaryDiagnosis).toBe('高血压、糖尿病')
    })
  })

  describe('saveMedicalRecord', () => {
    it('updates existing record via PUT', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([backendMedicalRecordFixture({ id: 500 })]))
      mock.put.mockResolvedValueOnce(successEnvelope(backendMedicalRecordFixture({ id: 500 })))
      await saveMedicalRecord(encounterFixture, savePayload)
      expect(mock.put).toHaveBeenCalledWith('/medical-records/500', {
        content: JSON.stringify({
          chiefComplaint: '咳嗽',
          presentIllness: '3 天',
          pastHistory: '无',
          physicalExam: '双肺呼吸音清',
          preliminaryDiagnosis: '急性支气管炎',
          treatmentAdvice: '对症治疗',
        }),
      })
    })

    it('creates new record via POST when none exists', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      mock.post.mockResolvedValueOnce(successEnvelope(backendMedicalRecordFixture({ id: 501 })))
      await saveMedicalRecord(encounterFixture, savePayload)
      expect(mock.post).toHaveBeenCalledWith('/medical-records', {
        encounterId: 9,
        content: expect.any(String),
      })
    })
  })

  describe('confirmMedicalRecord', () => {
    it('saves then confirms', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      mock.post.mockResolvedValueOnce(successEnvelope(backendMedicalRecordFixture({ id: 501 })))
      mock.post.mockResolvedValueOnce(successEnvelope(backendMedicalRecordFixture({ id: 501, status: 'CONFIRMED' })))
      await confirmMedicalRecord(encounterFixture, savePayload)
      expect(mock.post).toHaveBeenNthCalledWith(1, '/medical-records', expect.any(Object))
      expect(mock.post).toHaveBeenNthCalledWith(2, '/medical-records/501/confirm')
    })
  })
})
