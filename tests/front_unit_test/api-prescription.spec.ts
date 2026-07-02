import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import { backendEncounterFixture, backendPatientFixture, backendPrescriptionFixture } from './helpers/fixtures'

import {
  aiReviewPrescription,
  confirmPrescription,
  getEncounterPrescription,
  getMyPrescriptions,
  getPatientPrescriptions,
  getPrescriptionById,
  savePrescriptionDraft,
  voidPrescription,
} from '@/api/prescription'

const mock = getApiClientMock()

const encounterFixture = backendEncounterFixture({ id: 9, departmentId: 2, departmentName: '内科' })

describe('prescription API', () => {
  beforeEach(() => resetApiClientMock())

  describe('getMyPrescriptions', () => {
    it('filters out DRAFT prescriptions', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendPrescriptionFixture({ id: 1, status: 'CONFIRMED' }),
          backendPrescriptionFixture({ id: 2, status: 'DRAFT' }),
          backendPrescriptionFixture({ id: 3, status: 'VOIDED' }),
        ]),
      )
      const result = await getMyPrescriptions()
      expect(result.map((p) => p.id)).toEqual([1, 3])
    })

    it('sorts desc by confirmedAt or createdAt', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendPrescriptionFixture({
            id: 1,
            status: 'CONFIRMED',
            confirmedAt: '2026-06-01T10:00:00',
            createdAt: '2026-05-30T10:00:00',
          }),
          backendPrescriptionFixture({
            id: 2,
            status: 'CONFIRMED',
            confirmedAt: '2026-06-15T10:00:00',
            createdAt: '2026-06-10T10:00:00',
          }),
        ]),
      )
      const result = await getMyPrescriptions()
      expect(result[0].id).toBe(2)
    })

    it('filters by fromDate/toDate using confirmedAt or createdAt', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendPrescriptionFixture({ id: 1, confirmedAt: '2026-06-01T10:00:00' }),
          backendPrescriptionFixture({ id: 2, confirmedAt: '2026-07-15T10:00:00' }),
        ]),
      )
      const result = await getMyPrescriptions({ fromDate: '2026-07-01', toDate: '2026-07-31' })
      expect(result.map((p) => p.id)).toEqual([2])
    })
  })

  describe('getPatientPrescriptions (F2)', () => {
    it('filters out DRAFT by default and calls correct endpoint', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendPrescriptionFixture({ id: 1, status: 'CONFIRMED' }),
          backendPrescriptionFixture({ id: 2, status: 'DRAFT' }),
          backendPrescriptionFixture({ id: 3, status: 'VOIDED' }),
        ]),
      )
      const result = await getPatientPrescriptions(42)
      expect(mock.get).toHaveBeenCalledWith('/prescriptions/patient/42')
      expect(result.map((p) => p.id)).toEqual([1, 3])
    })

    it('includes DRAFT when includeDraft=true', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendPrescriptionFixture({ id: 1, status: 'CONFIRMED' }),
          backendPrescriptionFixture({ id: 2, status: 'DRAFT' }),
        ]),
      )
      const result = await getPatientPrescriptions(42, { includeDraft: true })
      expect(result.map((p) => p.id)).toEqual([1, 2])
    })

    it('sorts desc by confirmedAt or createdAt', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendPrescriptionFixture({
            id: 1,
            status: 'CONFIRMED',
            confirmedAt: '2026-06-01T10:00:00',
          }),
          backendPrescriptionFixture({
            id: 2,
            status: 'CONFIRMED',
            confirmedAt: '2026-06-15T10:00:00',
          }),
        ]),
      )
      const result = await getPatientPrescriptions(42, { includeDraft: true })
      expect(result[0].id).toBe(2)
    })

    it('filters by fromDate/toDate using confirmedAt or createdAt', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          backendPrescriptionFixture({ id: 1, confirmedAt: '2026-06-01T10:00:00' }),
          backendPrescriptionFixture({ id: 2, confirmedAt: '2026-07-15T10:00:00' }),
        ]),
      )
      const result = await getPatientPrescriptions(42, {
        fromDate: '2026-07-01',
        toDate: '2026-07-31',
      })
      expect(result.map((p) => p.id)).toEqual([2])
    })
  })

  describe('getPrescriptionById', () => {
    it('returns mapped prescription', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture()))
      const result = await getPrescriptionById(300)
      expect(result.id).toBe(300)
      expect(result.aiReview?.riskLevel).toBe('LOW')
    })

    it('CONTRAINDICATED risk maps to HIGH', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(
          backendPrescriptionFixture({
            review: {
              ...backendPrescriptionFixture().review!,
              riskLevel: 'CONTRAINDICATED' as never,
            },
          }),
        ),
      )
      const result = await getPrescriptionById(300)
      expect(result.aiReview?.riskLevel).toBe('HIGH')
    })

    it('aiReview null when no review', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture({ review: null })))
      const result = await getPrescriptionById(300)
      expect(result.aiReview).toBeNull()
    })
  })

  describe('getEncounterPrescription', () => {
    it('returns null when empty', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      const result = await getEncounterPrescription(9)
      expect(result).toBeNull()
    })

    it('returns first mapped prescription', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([backendPrescriptionFixture()]))
      const result = await getEncounterPrescription(9)
      expect(result?.id).toBe(300)
    })
  })

  describe('savePrescriptionDraft', () => {
    it('posts items with mapped fields', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture({ status: 'DRAFT' })))
      await savePrescriptionDraft(encounterFixture, {
        encounterId: encounterFixture.id,
        diagnosis: '感冒',
        items: [
          {
            drugId: 1,
            drugCode: 'D-001',
            drugName: '阿莫西林',
            strength: '500mg',
            unit: '片',
            dosage: '500mg',
            frequency: 'TID',
            usage: '饭后',
            duration: '7',
            remark: '注',
          },
        ],
      })
      expect(mock.post).toHaveBeenCalledWith('/prescriptions', {
        encounterId: 9,
        items: [
          {
            drugCode: 'D-001',
            drugName: '阿莫西林',
            dosage: '500mg',
            dosageValue: 500,
            frequency: 'TID',
            duration: 7,
            quantity: 7,
            instructions: '饭后；注',
          },
        ],
      })
    })

    it('falls back dosageValue=1 and duration=1 for invalid input', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture()))
      await savePrescriptionDraft(encounterFixture, {
        encounterId: encounterFixture.id,
        diagnosis: '感冒',
        items: [
          {
            drugId: 1,
            drugCode: 'D-001',
            drugName: 'X',
            strength: '',
            unit: '',
            dosage: 'abc',
            frequency: 'BID',
            usage: '',
            duration: '0',
          },
        ],
      })
      expect(mock.post).toHaveBeenCalledWith('/prescriptions', {
        encounterId: 9,
        items: [
          expect.objectContaining({
            dosageValue: 1,
            duration: 1,
            quantity: 1,
          }),
        ],
      })
    })

    it('uses default fallback diagnosis when not provided', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture({ status: 'DRAFT' })))
      const saved = await savePrescriptionDraft(encounterFixture, {
        encounterId: encounterFixture.id,
        diagnosis: '',
        items: [],
      })
      expect(saved.diagnosis).toContain('后端处方契约暂未包含诊断字段')
    })
  })

  describe('aiReviewPrescription', () => {
    it('returns the same prescription (placeholder)', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture()))
      const result = await aiReviewPrescription(300, '青霉素')
      expect(result.id).toBe(300)
    })
  })

  describe('confirmPrescription/voidPrescription', () => {
    it('confirmPrescription posts to /prescriptions/:id/confirm', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture({ status: 'CONFIRMED' })))
      await confirmPrescription(300)
      expect(mock.post).toHaveBeenCalledWith('/prescriptions/300/confirm')
    })

    it('voidPrescription posts reason payload', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendPrescriptionFixture({ status: 'VOIDED' })))
      await voidPrescription(300, 're-issue')
      expect(mock.post).toHaveBeenCalledWith('/prescriptions/300/void', { reason: 're-issue' })
    })
  })
})
