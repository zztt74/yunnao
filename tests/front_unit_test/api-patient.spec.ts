import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import { backendPatientFixture, backendProfileFixture } from './helpers/fixtures'

import {
  getPatientDetail,
  getPatientInfo,
  getPatientProfile,
  getPatientTimeline,
  registerPatient,
  updatePatientInfo,
  updatePatientProfile,
} from '@/api/patient'

const mock = getApiClientMock()

describe('patient API', () => {
  beforeEach(() => resetApiClientMock())

  describe('getPatientInfo', () => {
    it('returns parsed patient data', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      const result = await getPatientInfo()
      expect(result).toEqual(backendPatientFixture())
      expect(mock.get).toHaveBeenCalledWith('/patients/me')
    })
  })

  describe('getPatientProfile', () => {
    it('uses patient id resolved from getPatientInfo', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.get.mockResolvedValueOnce(successEnvelope(backendProfileFixture()))
      const profile = await getPatientProfile()
      expect(profile.allergies).toBe('青霉素')
      expect(mock.get).toHaveBeenNthCalledWith(1, '/patients/me')
      expect(mock.get).toHaveBeenNthCalledWith(2, '/patients/42/profile')
    })
  })

  describe('updatePatientProfile', () => {
    it('puts the payload to the profile endpoint', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.put.mockResolvedValueOnce(successEnvelope(backendProfileFixture()))
      const result = await updatePatientProfile({ allergies: '青霉素' })
      expect(result).toEqual(backendProfileFixture())
      expect(mock.put).toHaveBeenCalledWith('/patients/42/profile', {
        allergies: '青霉素',
      })
    })
  })

  describe('updatePatientInfo', () => {
    it('puts the payload to the patient endpoint', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendPatientFixture()))
      mock.put.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ phone: '13900000000' })),
      )
      const result = await updatePatientInfo({
        name: '张三',
        gender: 'MALE',
        birthDate: '1990-01-01',
        phone: '13900000000',
      })
      expect(result.phone).toBe('13900000000')
      expect(mock.put).toHaveBeenCalledWith('/patients/42', {
        name: '张三',
        gender: 'MALE',
        birthDate: '1990-01-01',
        phone: '13900000000',
      })
    })
  })

  describe('registerPatient', () => {
    it('posts to the register endpoint', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ id: 99 })),
      )
      const result = await registerPatient({
        username: 'zhang',
        password: 'secret',
        name: '张三',
        gender: 'MALE',
        birthDate: '1990-01-01',
        phone: '13800000000',
      })
      expect(result.id).toBe(99)
      expect(mock.post).toHaveBeenCalledWith('/patients/register', {
        username: 'zhang',
        password: 'secret',
        name: '张三',
        gender: 'MALE',
        birthDate: '1990-01-01',
        phone: '13800000000',
      })
    })
  })

  describe('getPatientDetail', () => {
    it('merges patient and profile, falls back defaults', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ birthDate: '2020-01-01' })),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendProfileFixture({ allergies: null, medicalHistory: null })),
      )
      const detail = await getPatientDetail(42)
      expect(detail.id).toBe(42)
      expect(detail.allergies).toBe('无')
      expect(detail.medicalHistory).toBe('无')
      expect(detail.age).toBeGreaterThanOrEqual(0)
      expect(mock.get).toHaveBeenCalledTimes(2)
    })

    it('returns 0 age for invalid birth date', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ birthDate: 'invalid-date' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendProfileFixture()))
      const detail = await getPatientDetail(42)
      expect(detail.age).toBe(0)
    })

    it('computes age with month boundary deduction', async () => {
      // 冻结到 2026-07-01，出生日期 2000-09-15 跨过 7 月
      // 2026-07-01 仍不足月，应该减 1 → 2026 - 2000 - 1 = 25
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ birthDate: '2000-09-15' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendProfileFixture()))
      const detail = await getPatientDetail(42)
      expect(detail.age).toBe(25)
    })

    it('computes age for a person born after today in same year', async () => {
      // 冻结到 2026-07-01，出生日期 2000-12-15 同年但未来日，应直接减 1 → 25
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ birthDate: '2000-12-15' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendProfileFixture()))
      const detail = await getPatientDetail(42)
      expect(detail.age).toBe(25)
    })

    it('computes age for a person born exactly today', async () => {
      // 冻结到 2026-07-01，出生 2000-07-01 → 26（恰好到生日）
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ birthDate: '2000-07-01' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendProfileFixture()))
      const detail = await getPatientDetail(42)
      expect(detail.age).toBe(26)
    })

    it('computes age for a person born in earlier month same year', async () => {
      // 冻结到 2026-07-01，出生 2000-03-15 → 26（已过生日）
      mock.get.mockResolvedValueOnce(
        successEnvelope(backendPatientFixture({ birthDate: '2000-03-15' })),
      )
      mock.get.mockResolvedValueOnce(successEnvelope(backendProfileFixture()))
      const detail = await getPatientDetail(42)
      expect(detail.age).toBe(26)
    })
  })

  describe('getPatientTimeline', () => {
    it('returns merged appointments/exams/records/prescriptions sorted desc', async () => {
      const appointment = {
        id: 1,
        patientId: 42,
        scheduleId: 3,
        doctorId: 11,
        doctorName: '李医生',
        departmentId: 2,
        departmentName: '内科',
        appointmentNumber: 'A000001',
        status: 'BOOKED',
        bookedAt: '2026-06-29T09:00:00',
        createdAt: '2026-06-29T09:00:00',
        updatedAt: '2026-06-29T09:00:00',
      }
      const exam = {
        id: 2,
        encounterId: 9,
        patientId: 42,
        doctorId: 3,
        doctorName: '',
        departmentName: '',
        type: 'EXAMINATION' as const,
        itemName: '胸部 X 光',
        purpose: '',
        orderedAt: '2026-06-30T10:00:00',
        reportedAt: null,
        reviewedAt: null,
        reporterName: null,
        status: 'ORDERED' as const,
        createdAt: '2026-06-30T10:00:00',
        updatedAt: '2026-06-30T10:00:00',
      }
      const record = {
        id: 3,
        encounterId: 9,
        patientId: 42,
        doctorId: 3,
        doctorName: '李医生',
        departmentName: '内科',
        chiefComplaint: '咳嗽',
        presentIllness: '',
        pastHistory: '',
        physicalExam: '',
        preliminaryDiagnosis: '',
        treatmentAdvice: '',
        status: 'CONFIRMED' as const,
        diagnoses: [],
        encounterDate: '2026-06-28T10:00:00',
        confirmedAt: '2026-06-28T10:00:00',
        createdAt: '2026-06-28T10:00:00',
        updatedAt: '2026-06-28T10:00:00',
      }
      const prescription = {
        id: 4,
        encounterId: 9,
        patientId: 42,
        patientName: '张三',
        doctorId: 3,
        doctorName: '李医生',
        departmentName: '内科',
        diagnosis: '感冒',
        items: [
          {
            id: 5,
            drugId: 5,
            drugCode: 'D-001',
            drugName: '阿莫西林',
            strength: '',
            unit: '',
            dosage: '500mg',
            frequency: 'TID',
            usage: '',
            duration: '7',
          },
        ],
        status: 'CONFIRMED' as const,
        voidedReason: null,
        voidedAt: null,
        remark: undefined,
        aiReview: null,
        aiReviewStatus: 'NOT_REQUESTED' as const,
        confirmedAt: '2026-06-28T11:00:00',
        createdAt: '2026-06-28T10:00:00',
        updatedAt: '2026-06-28T11:00:00',
      }
      mock.get.mockResolvedValueOnce(pageEnvelope([appointment]))
      mock.get.mockResolvedValueOnce(pageEnvelope([exam]))
      mock.get.mockResolvedValueOnce(pageEnvelope([record]))
      mock.get.mockResolvedValueOnce(pageEnvelope([prescription]))

      const timeline = await getPatientTimeline(42)
      expect(timeline).toHaveLength(4)
      const occurredAts = timeline.map((t) => t.occurredAt)
      const sorted = [...occurredAts].sort((a, b) => new Date(b).getTime() - new Date(a).getTime())
      expect(occurredAts).toEqual(sorted)
      expect(timeline[0].type).toBe('EXAMINATION')
      expect(timeline.some((t) => t.type === 'PRESCRIPTION')).toBe(true)
    })

    it('accepts array-shaped appointments response (non-page)', async () => {
      const appointment = {
        id: 1,
        patientId: 42,
        scheduleId: 3,
        doctorId: 11,
        doctorName: '李医生',
        departmentId: 2,
        departmentName: '内科',
        appointmentNumber: 'A000001',
        status: 'BOOKED',
        bookedAt: '2026-06-29T09:00:00',
        createdAt: '2026-06-29T09:00:00',
        updatedAt: '2026-06-29T09:00:00',
      }
      mock.get.mockResolvedValueOnce(successEnvelope([appointment]))
      mock.get.mockResolvedValueOnce(pageEnvelope([]))
      mock.get.mockResolvedValueOnce(pageEnvelope([]))
      mock.get.mockResolvedValueOnce(pageEnvelope([]))
      const timeline = await getPatientTimeline(42)
      expect(timeline).toHaveLength(1)
      expect(timeline[0].type).toBe('APPOINTMENT')
    })
  })
})
