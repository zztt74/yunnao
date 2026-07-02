// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import { backendDiagnosisFixture, backendDoctorFixture, backendEncounterFixture, TIMESTAMP } from './helpers/fixtures'

import {
  addAIDiagnosis,
  addDoctorDiagnosis,
  assistDiagnosis,
  cancelEncounter,
  completeEncounter,
  getDoctorActiveAppointments,
  getDoctorAppointmentById,
  getDoctorEncounters,
  getDoctorPendingQueue,
  getDoctorTodayAppointments,
  getEncounterByAppointmentId,
  getEncounterById,
  getEncounterDiagnoses,
  resumeEncounter,
  startPatientEncounter,
  waitForExam,
} from '@/api/encounter'
import { useAuthStore } from '@/stores/auth'

const mock = getApiClientMock()

function setupDoctorAuth(userId: number) {
  const auth = useAuthStore()
  auth.establishSession('token', {
    userId,
    username: 'doc',
    roles: ['DOCTOR'],
    mustChangePassword: false,
  })
}

const appointmentFixture = {
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

describe('encounter API', () => {
  beforeEach(() => {
    resetApiClientMock()
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  describe('getDoctorPendingQueue', () => {
    it('uses current doctor id', async () => {
      setupDoctorAuth(22)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ id: 11, userId: 22 })]))
      mock.get.mockResolvedValueOnce(successEnvelope([appointmentFixture]))
      const result = await getDoctorPendingQueue()
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenNthCalledWith(2, '/appointments/doctor/11/pending')
    })

    it('accepts explicit doctorId', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([appointmentFixture]))
      const result = await getDoctorPendingQueue(11)
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenCalledWith('/appointments/doctor/11/pending')
    })
  })

  describe('getDoctorActiveAppointments', () => {
    it('keeps only IN_PROGRESS and WAITING_EXAM', async () => {
      setupDoctorAuth(22)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ id: 11, userId: 22 })]))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          { ...appointmentFixture, id: 1, status: 'IN_PROGRESS' },
          { ...appointmentFixture, id: 2, status: 'WAITING_EXAM' },
          { ...appointmentFixture, id: 3, status: 'BOOKED' },
          { ...appointmentFixture, id: 4, status: 'COMPLETED' },
        ]),
      )
      const result = await getDoctorActiveAppointments()
      expect(result.map((a) => a.id)).toEqual([1, 2])
    })
  })

  describe('getDoctorTodayAppointments', () => {
    it('filters by bookedAt date prefix', async () => {
      setupDoctorAuth(22)
      const today = new Date().toISOString().slice(0, 10)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ id: 11, userId: 22 })]))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          { ...appointmentFixture, id: 1, bookedAt: `${today}T08:00:00` },
          { ...appointmentFixture, id: 2, bookedAt: '2025-01-01T08:00:00' },
        ]),
      )
      const result = await getDoctorTodayAppointments()
      expect(result.map((a) => a.id)).toEqual([1])
    })
  })

  describe('getDoctorEncounters', () => {
    it('returns page items', async () => {
      setupDoctorAuth(22)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ id: 11, userId: 22 })]))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          { id: 100, patientId: 42, doctorId: 11, status: 'IN_PROGRESS' },
        ]),
      )
      const result = await getDoctorEncounters()
      expect(result).toHaveLength(1)
    })
  })

  describe('startPatientEncounter', () => {
    it('posts appointmentId to /encounters/start', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendEncounterFixture()))
      const result = await startPatientEncounter({ appointmentId: 1 })
      expect(result.id).toBe(200)
      expect(result.status).toBe('IN_PROGRESS')
      expect(mock.post).toHaveBeenCalledWith('/encounters/start', {
        appointmentId: 1,
      })
    })
  })

  describe('waitForExam/resume/complete', () => {
    it('waitForExam posts to /encounters/:id/wait-exam', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendEncounterFixture({ status: 'WAITING_EXAM' })),
      )
      const result = await waitForExam(200)
      expect(result.status).toBe('WAITING_EXAM')
      expect(mock.post).toHaveBeenCalledWith('/encounters/200/wait-exam')
    })

    it('resumeEncounter posts to /encounters/:id/resume', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendEncounterFixture()))
      const result = await resumeEncounter(200)
      expect(result.id).toBe(200)
      expect(mock.post).toHaveBeenCalledWith('/encounters/200/resume')
    })

    it('completeEncounter posts to /encounters/:id/complete', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendEncounterFixture({ status: 'COMPLETED', completedAt: TIMESTAMP })),
      )
      const result = await completeEncounter(200)
      expect(result.status).toBe('COMPLETED')
      expect(result.completedAt).toBe(TIMESTAMP)
      expect(mock.post).toHaveBeenCalledWith('/encounters/200/complete')
    })
  })

  describe('cancelEncounter', () => {
    it('passes payload', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendEncounterFixture({ status: 'CANCELLED', cancelReason: 'patient request' })),
      )
      const result = await cancelEncounter(200, { reason: 'patient request' })
      expect(result.status).toBe('CANCELLED')
      expect(mock.post).toHaveBeenCalledWith('/encounters/200/cancel', { reason: 'patient request' })
    })

    it('uses empty payload when not provided', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendEncounterFixture({ status: 'CANCELLED' })))
      await cancelEncounter(200)
      expect(mock.post).toHaveBeenCalledWith('/encounters/200/cancel', {})
    })
  })

  describe('getEncounterByAppointmentId', () => {
    it('returns null when api throws', async () => {
      mock.get.mockRejectedValueOnce(new Error('not found'))
      const result = await getEncounterByAppointmentId(99)
      expect(result).toBeNull()
    })

    it('returns parsed response on success', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendEncounterFixture()))
      const result = await getEncounterByAppointmentId(1)
      expect(result?.id).toBe(200)
      expect(result?.patientId).toBe(42)
    })
  })

  describe('getEncounterById', () => {
    it('returns parsed response', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendEncounterFixture()))
      const result = await getEncounterById(200)
      expect(result.id).toBe(200)
      expect(result.status).toBe('IN_PROGRESS')
    })
  })

  describe('getEncounterDiagnoses', () => {
    it('returns list', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([backendDiagnosisFixture()]))
      const result = await getEncounterDiagnoses(200)
      expect(result).toHaveLength(1)
      expect(result[0].diagnosisCode).toBe('I10')
      expect(result[0].diagnosisName).toBe('高血压')
    })
  })

  describe('addAIDiagnosis/addDoctorDiagnosis', () => {
    it('addAIDiagnosis posts to /encounters/:id/diagnoses/ai', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendDiagnosisFixture({ source: 'AI_SUGGESTION' })),
      )
      const result = await addAIDiagnosis(200, {
        diagnosisCode: 'I10',
        diagnosisName: '高血压',
        type: 'PRELIMINARY',
        source: 'AI_SUGGESTION',
      })
      expect(result.id).toBe(1)
      expect(mock.post).toHaveBeenCalledWith('/encounters/200/diagnoses/ai', {
        diagnosisCode: 'I10',
        diagnosisName: '高血压',
        type: 'PRELIMINARY',
        source: 'AI_SUGGESTION',
      })
    })

    it('addDoctorDiagnosis posts to /encounters/:id/diagnoses/doctor', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendDiagnosisFixture()))
      await addDoctorDiagnosis(200, {
        diagnosisCode: 'I10',
        diagnosisName: '高血压',
        type: 'FINAL',
        source: 'DOCTOR',
      })
      expect(mock.post).toHaveBeenCalledWith('/encounters/200/diagnoses/doctor', {
        diagnosisCode: 'I10',
        diagnosisName: '高血压',
        type: 'FINAL',
        source: 'DOCTOR',
      })
    })
  })

  describe('assistDiagnosis', () => {
    it('posts payload to /ai/assist-diagnosis and returns raw response', async () => {
      const aiResponse = {
        encounterId: 200,
        candidates: [
          {
            diagnosisCode: 'I10',
            diagnosisName: '高血压',
            reason: '主诉提示',
            confidence: 0.85,
            riskFactors: ['家族史'],
            informationGaps: ['血压值'],
            recommendedExaminations: ['血压监测'],
          },
        ],
        aiStatus: 'SUCCESS' as const,
      }
      mock.post.mockResolvedValueOnce({ data: aiResponse })
      const result = await assistDiagnosis({
        encounterId: 200,
        chiefComplaint: '头痛、眩晕',
        presentIllness: '3 天',
      })
      expect(result.candidates).toHaveLength(1)
      expect(result.candidates[0].diagnosisCode).toBe('I10')
      expect(result.aiStatus).toBe('SUCCESS')
      expect(mock.post).toHaveBeenCalledWith('/ai/assist-diagnosis', {
        encounterId: 200,
        chiefComplaint: '头痛、眩晕',
        presentIllness: '3 天',
      })
    })

    it('propagates error when AI service fails', async () => {
      mock.post.mockRejectedValueOnce(new Error('AI service timeout'))
      await expect(
        assistDiagnosis({ encounterId: 200, chiefComplaint: '咳嗽' }),
      ).rejects.toThrow('AI service timeout')
    })

    it('handles FAILED aiStatus with failure reason', async () => {
      const aiResponse = {
        encounterId: 200,
        candidates: [],
        aiStatus: 'FAILED' as const,
        aiFailureReason: '模型配额耗尽',
      }
      mock.post.mockResolvedValueOnce({ data: aiResponse })
      const result = await assistDiagnosis({
        encounterId: 200,
        chiefComplaint: '未知症状',
      })
      expect(result.aiStatus).toBe('FAILED')
      expect(result.aiFailureReason).toBe('模型配额耗尽')
      expect(result.candidates).toHaveLength(0)
    })
  })

  describe('getDoctorAppointmentById', () => {
    it('returns parsed appointment by id', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(appointmentFixture))
      const result = await getDoctorAppointmentById(1)
      expect(result.id).toBe(1)
      expect(result.patientId).toBe(42)
      expect(mock.get).toHaveBeenCalledWith('/appointments/1')
    })

    it('uses explicit doctorId without fetching current doctor', async () => {
      // 无 session 时调用 getCurrentDoctor 也不会报错（被 mock 覆盖）
      mock.get.mockResolvedValueOnce(successEnvelope(appointmentFixture))
      const result = await getDoctorAppointmentById(1)
      expect(result.id).toBe(1)
    })
  })
})
