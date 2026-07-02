// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'

import {
  cancelSchedule,
  changeUserStatus,
  createDepartment,
  createDevice,
  createDoctor,
  createSchedule,
  createUser,
  getAdminDevices,
  getAiCallStats,
  getAiInvocationAttempts,
  getAiInvocationLogs,
  getAdminAppointments,
  getAdminPatients,
  getAdminSchedules,
  getDepartmentById,
  getDepartmentStats,
  getDeviceStatusHistory,
  getDeviceUsageStats,
  getDoctorById,
  getDoctorRanking,
  getDoctors,
  getLoginLogs,
  getOperationLogs,
  getStatisticsSummary,
  getStatisticsTrend,
  getTriageRecords,
  getUsers,
  resetUserPassword,
  setDepartmentStatus,
  setDeviceStatus,
  setDoctorStatus,
  updateDepartment,
  updateDevice,
  updateDoctor,
  updateSchedule,
  updateUser,
} from '@/api/admin'

const mock = getApiClientMock()
const TIMESTAMP = '2026-07-01T10:00:00'

beforeEach(() => {
  resetApiClientMock()
})

describe('admin API', () => {
  describe('getTriageRecords', () => {
    it('returns mapped records with pagination metadata', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 1,
              patientId: 42,
              symptoms: '胸痛',
              aiPriority: 'EMERGENCY',
              aiReason: '急诊风险',
              aiSafetyNotice: '请立即就诊',
              aiEmergencySuggested: true,
              mappedDepartmentId: 7,
              aiStatus: 'SUCCESS',
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          { total: 1, page: 1, pageSize: 20 },
        ),
      )
      const result = await getTriageRecords({ page: 1, pageSize: 20 })
      expect(result.list).toHaveLength(1)
      expect(result.list[0].priority).toBe('EMERGENCY')
      expect(result.list[0].emergencyAdvice).toBe('请立即就诊')
      expect(result.total).toBe(1)
      expect(mock.get).toHaveBeenCalledWith('/triage', {
        params: { page: 1, size: 20 },
      })
    })

    it('falls back to empty fields when backend returns nulls', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 2,
              patientId: 99,
              symptoms: '咳嗽',
              aiPriority: null,
              aiReason: null,
              aiFailureReason: null,
              aiSafetyNotice: null,
              aiEmergencySuggested: null,
              mappedDepartmentId: null,
              aiStatus: 'FAILED',
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          { total: 1 },
        ),
      )
      const result = await getTriageRecords()
      expect(result.list[0].priority).toBe('LOW')
      expect(result.list[0].recommendedDepartmentId).toBe(0)
      expect(result.list[0].emergencyAdvice).toBeUndefined()
      expect(result.list[0].reason).toBe('')
    })
  })

  describe('getAiInvocationLogs', () => {
    it('forwards page, capability, success, businessType, startDate, endDate', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 100,
              capability: 'TRIAGE',
              businessType: 'TRIAGE_ANALYZE',
              businessId: 1,
              status: 'SUCCESS',
              errorType: null,
              errorMessage: null,
              durationMs: 1234,
              attemptCount: 1,
              operatorId: 3,
              startedAt: TIMESTAMP,
              finishedAt: TIMESTAMP,
            },
          ],
          { total: 1 },
        ),
      )
      const result = await getAiInvocationLogs({
        page: 1,
        pageSize: 20,
        capability: 'TRIAGE',
        businessType: 'TRIAGE_ANALYZE',
        success: true,
        startDate: '2026-07-01',
        endDate: '2026-07-01',
      })
      expect(result.list[0].success).toBe(true)
      expect(mock.get).toHaveBeenCalledWith('/audit/ai/invocations', {
        params: {
          page: 1,
          size: 20,
          capability: 'TRIAGE',
          businessType: 'TRIAGE_ANALYZE',
          success: true,
          startDate: '2026-07-01',
          endDate: '2026-07-01',
        },
      })
    })

    it('maps failed invocation log to success=false', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 101,
              capability: 'EXAM_INTERPRET',
              businessType: 'EXAM_REVIEW',
              businessId: 7,
              status: 'FAILED',
              errorType: 'TIMEOUT',
              errorMessage: 'AI timeout',
              durationMs: 30000,
              attemptCount: 2,
              operatorId: null,
              startedAt: TIMESTAMP,
              finishedAt: TIMESTAMP,
            },
          ],
          { total: 1 },
        ),
      )
      const result = await getAiInvocationLogs()
      expect(result.list[0].success).toBe(false)
      expect(result.list[0].errorType).toBe('TIMEOUT')
    })
  })

  describe('getAiInvocationAttempts', () => {
    it('returns attempts for a given invocation', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          {
            id: 200,
            invocationId: 100,
            provider: 'deepseek',
            model: 'deepseek-chat',
            promptVersion: 'v1',
            status: 'SUCCESS',
            httpStatus: 200,
            errorType: null,
            errorMessage: null,
            requestSummary: 'req',
            responseSummary: 'resp',
            durationMs: 1234,
            attemptIndex: 1,
            startedAt: TIMESTAMP,
            finishedAt: TIMESTAMP,
          },
        ]),
      )
      const result = await getAiInvocationAttempts(100)
      expect(result).toHaveLength(1)
      expect(result[0].provider).toBe('deepseek')
      expect(mock.get).toHaveBeenCalledWith('/audit/ai/invocations/100/attempts')
    })
  })

  describe('getStatisticsSummary', () => {
    it('aggregates dashboard + doctors + departments + devices', async () => {
      // 1. /statistics/dashboard
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          todayAppointmentCount: 10,
          todayCompletedEncounterCount: 5,
          currentOnDutyDoctorCount: 3,
          currentAvailableDeviceCount: 7,
          highPriorityTriageCount: 1,
          totalPatientCount: 100,
        }),
      )
      // 2. /departments (called from getDepartments)
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { id: 1, code: 'D1', name: '内科', status: 'ENABLED', createdAt: TIMESTAMP, updatedAt: TIMESTAMP },
          { id: 2, code: 'D2', name: '外科', status: 'ENABLED', createdAt: TIMESTAMP, updatedAt: TIMESTAMP },
        ]),
      )
      // 3. /doctors (called from getAllDoctorsRaw inside getDepartments)
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [
            {
              id: 1,
              userId: 1,
              departmentId: 1,
              departmentName: '内科',
              name: '李医生',
              title: 'ATTENDING',
              specialty: null,
              status: 'ENABLED',
              introduction: null,
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          page: 1,
          pageSize: 100,
          total: 1,
          totalPages: 1,
        }),
      )
      // 4. /doctors (called from getAllDoctorsRaw inside getDoctors)
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [
            {
              id: 1,
              userId: 1,
              departmentId: 1,
              departmentName: '内科',
              name: '李医生',
              title: 'ATTENDING',
              specialty: null,
              status: 'ENABLED',
              introduction: null,
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          page: 1,
          pageSize: 100,
          total: 1,
          totalPages: 1,
        }),
      )
      // 5. /schedules/available (called from getAdminSchedules inside getDoctors)
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      // 6. /devices (called from getAllDevices inside getAdminDevices)
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [
            {
              id: 1,
              code: 'DEV-1',
              name: 'ECG',
              type: 'EXAMINATION',
              status: 'AVAILABLE',
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          page: 1,
          pageSize: 100,
          total: 1,
          totalPages: 1,
        }),
      )

      const result = await getStatisticsSummary()
      expect(result.todayAppointments).toBe(10)
      expect(result.totalDepartments).toBe(2)
      expect(result.totalDoctors).toBe(1)
      expect(result.totalDevices).toBe(1)
    })
  })

  describe('getStatisticsTrend', () => {
    it('combines completed and cancelled into appointments', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { date: '2026-07-01', completedCount: 5, cancelledCount: 1 },
          { date: '2026-06-30', completedCount: 3, cancelledCount: 0 },
        ]),
      )
      const result = await getStatisticsTrend(7)
      expect(result).toEqual([
        { date: '2026-07-01', appointments: 6, completedEncounters: 5 },
        { date: '2026-06-30', appointments: 3, completedEncounters: 3 },
      ])
      expect(mock.get).toHaveBeenCalledWith('/statistics/outpatient/daily', {
        params: { days: 7 },
      })
    })
  })

  describe('getDepartmentStats', () => {
    it('maps encounterCount to both appointment and encounter fields', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { departmentId: 1, departmentName: '内科', encounterCount: 12 },
          { departmentId: 2, departmentName: '外科', encounterCount: 8 },
        ]),
      )
      const result = await getDepartmentStats()
      expect(result[0]).toEqual({
        departmentId: 1,
        departmentName: '内科',
        appointmentCount: 12,
        encounterCount: 12,
      })
    })

    it('filters by departmentId when provided', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { departmentId: 1, departmentName: '内科', encounterCount: 12 },
          { departmentId: 2, departmentName: '外科', encounterCount: 8 },
        ]),
      )
      const result = await getDepartmentStats({ departmentId: 1 })
      expect(result).toHaveLength(1)
      expect(result[0].departmentId).toBe(1)
    })
  })

  describe('getDoctorRanking', () => {
    it('returns mapped doctor encounter counts', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { doctorId: 1, doctorName: '李医生', departmentName: '内科', encounterCount: 20 },
        ]),
      )
      const result = await getDoctorRanking()
      expect(result[0]).toEqual({
        doctorId: 1,
        doctorName: '李医生',
        departmentName: '内科',
        encounterCount: 20,
      })
    })
  })

  describe('getDeviceUsageStats', () => {
    it('converts usage seconds to minutes and maps device fields', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          {
            deviceId: 1,
            deviceName: 'ECG',
            deviceType: 'EXAMINATION',
            usageCount: 10,
            totalUsageSeconds: 1800,
            usageRate: 0.5,
          },
        ]),
      )
      const result = await getDeviceUsageStats()
      expect(result[0].totalUsageDuration).toBe(30)
      expect(result[0].deviceCode).toBe('EXAMINATION')
      expect(result[0].utilizationRate).toBe(0.5)
    })
  })

  describe('getAiCallStats', () => {
    it('combines summary and by-capability stats', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          totalInvocations: 100,
          successCount: 90,
          failedCount: 10,
          avgDurationMs: 1234,
        }),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { capability: 'TRIAGE', totalInvocations: 50, successCount: 45, avgDurationMs: 1000 },
          { capability: 'EXAM', totalInvocations: 50, successCount: 45, avgDurationMs: 1500 },
        ]),
      )
      const result = await getAiCallStats()
      expect(result.totalCalls).toBe(100)
      expect(result.byType).toHaveLength(2)
      expect(result.byType[0].type).toBe('TRIAGE')
    })
  })

  describe('getLoginLogs', () => {
    it('requests AUTH_LOGIN action and maps fields', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 1,
              operatorId: 5,
              operatorType: 'ADMIN',
              operatorName: 'admin',
              action: 'AUTH_LOGIN',
              targetType: 'USER',
              targetId: 5,
              details: '登录成功',
              result: 'SUCCESS',
              errorMessage: null,
              ipAddress: '127.0.0.1',
              createdAt: TIMESTAMP,
            },
          ],
          { total: 1 },
        ),
      )
      const result = await getLoginLogs()
      expect(result[0].success).toBe(true)
      expect(result[0].ip).toBe('127.0.0.1')
      expect(mock.get).toHaveBeenCalledWith('/audit/logs', {
        params: { action: 'AUTH_LOGIN', page: 1, size: 100 },
      })
    })

    it('marks failed logins with success=false and failReason', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 2,
              operatorId: null,
              operatorType: null,
              operatorName: null,
              action: 'AUTH_LOGIN',
              targetType: 'USER',
              targetId: null,
              details: null,
              result: 'FAILED',
              errorMessage: '密码错误',
              ipAddress: '127.0.0.1',
              createdAt: TIMESTAMP,
            },
          ],
          { total: 1 },
        ),
      )
      const result = await getLoginLogs()
      expect(result[0].success).toBe(false)
      expect(result[0].username).toBe('SYSTEM')
      expect(result[0].failReason).toBe('密码错误')
    })
  })

  describe('getOperationLogs', () => {
    it('maps audit log fields to operation log fields', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 3,
              operatorId: 7,
              operatorType: 'DOCTOR',
              operatorName: 'doc',
              action: 'CREATE_PRESCRIPTION',
              targetType: 'PRESCRIPTION',
              targetId: 100,
              details: '开立处方',
              result: 'SUCCESS',
              errorMessage: null,
              ipAddress: '127.0.0.1',
              createdAt: TIMESTAMP,
            },
          ],
          { total: 1 },
        ),
      )
      const result = await getOperationLogs()
      expect(result[0].operatorId).toBe(7)
      expect(result[0].operatorName).toBe('doc')
      expect(result[0].detail).toBe('开立处方')
      expect(result[0].targetId).toBe(100)
    })
  })

  describe('getAdminAppointments', () => {
    it('applies status/patient/doctor/date filters client-side', async () => {
      const today = new Date().toISOString().slice(0, 10)
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 1,
              patientId: 42,
              scheduleId: 3,
              doctorId: 11,
              doctorName: '李医生',
              departmentId: 2,
              departmentName: '内科',
              appointmentNumber: 'A000001',
              status: 'BOOKED',
              bookedAt: `${today}T09:00:00`,
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
            {
              id: 2,
              patientId: 43,
              scheduleId: 3,
              doctorId: 11,
              doctorName: '李医生',
              departmentId: 2,
              departmentName: '内科',
              appointmentNumber: 'A000002',
              status: 'COMPLETED',
              bookedAt: '2025-01-01T09:00:00',
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          { total: 2 },
        ),
      )
      const result = await getAdminAppointments({
        status: 'BOOKED',
        patientId: 42,
        doctorId: 11,
        date: today,
      })
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(1)
    })
  })

  describe('getAdminSchedules', () => {
    it('queries /schedules/available when no doctor/department', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          {
            id: 1,
            doctorId: 11,
            doctorName: '李医生',
            departmentId: 2,
            departmentName: '内科',
            scheduleDate: '2026-07-01',
            startTime: '2026-07-01T09:00:00',
            endTime: '2026-07-01T12:00:00',
            maxAppointments: 20,
            bookedCount: 0,
            remainingCount: 20,
            status: 'AVAILABLE',
            createdAt: TIMESTAMP,
            updatedAt: TIMESTAMP,
          },
        ]),
      )
      const result = await getAdminSchedules()
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenCalledWith('/schedules/available')
    })

    it('uses doctor-scoped endpoint and filters by date when doctorId is provided', async () => {
      mock.get.mockResolvedValueOnce(
        pageEnvelope(
          [
            {
              id: 2,
              doctorId: 11,
              doctorName: '李医生',
              departmentId: 2,
              departmentName: '内科',
              scheduleDate: '2026-07-01',
              startTime: '2026-07-01T09:00:00',
              endTime: '2026-07-01T12:00:00',
              maxAppointments: 20,
              bookedCount: 0,
              remainingCount: 20,
              status: 'AVAILABLE',
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
            {
              id: 3,
              doctorId: 11,
              doctorName: '李医生',
              departmentId: 2,
              departmentName: '内科',
              scheduleDate: '2025-01-01',
              startTime: '2025-01-01T09:00:00',
              endTime: '2025-01-01T12:00:00',
              maxAppointments: 20,
              bookedCount: 0,
              remainingCount: 20,
              status: 'AVAILABLE',
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          { total: 2 },
        ),
      )
      const result = await getAdminSchedules({ doctorId: 11, date: '2026-07-01' })
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenCalledWith('/schedules/doctor/11', {
        params: { page: 1, size: 100 },
      })
    })
  })

  describe('getAdminPatients', () => {
    it('returns empty page when keyword is empty', async () => {
      const result = await getAdminPatients({ keyword: '   ' })
      expect(result.total).toBe(0)
      expect(result.list).toEqual([])
      expect(mock.get).not.toHaveBeenCalled()
    })

    it('returns full page list when results fit on a single page', async () => {
      // 1. /patients/search
      mock.get.mockResolvedValueOnce(
        successEnvelope([{ id: 1, userId: 7, name: '张三', gender: 'MALE', birthDate: '1990-01-01', phone: '13800000000', status: 'ACTIVE', createdAt: TIMESTAMP, updatedAt: TIMESTAMP }]),
      )
      // 2. /patients/1 (from getPatientDetail)
      mock.get.mockResolvedValueOnce(
        successEnvelope({ id: 1, userId: 7, name: '张三', gender: 'MALE', birthDate: '1990-01-01', phone: '13800000000', status: 'ACTIVE', createdAt: TIMESTAMP, updatedAt: TIMESTAMP }),
      )
      // 3. /patients/1/profile (from getPatientDetail)
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          id: 9,
          patientId: 1,
          address: null,
          emergencyContact: null,
          emergencyPhone: null,
          allergies: null,
          medicalHistory: null,
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      const result = await getAdminPatients({ keyword: '张', page: 1, pageSize: 10 })
      expect(result.total).toBe(1)
      expect(result.list).toHaveLength(1)
    })
  })

  describe('device management', () => {
    it('createDevice posts payload to /devices', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope({
          id: 50,
          code: 'DEV-50',
          name: 'B超',
          type: 'EXAMINATION',
          status: 'AVAILABLE',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      const result = await createDevice({ code: 'DEV-50', name: 'B超', category: 'EXAMINATION' })
      expect(result.id).toBe(50)
      expect(mock.post).toHaveBeenCalledWith('/devices', expect.objectContaining({
        code: 'DEV-50',
        name: 'B超',
        type: 'EXAMINATION',
      }))
    })

    it('updateDevice puts payload to /devices/:id', async () => {
      mock.put.mockResolvedValueOnce(
        successEnvelope({
          id: 50,
          code: 'DEV-50',
          name: 'B超',
          type: 'EXAMINATION',
          status: 'AVAILABLE',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      await updateDevice(50, { name: 'B超 v2' })
      expect(mock.put).toHaveBeenCalledWith('/devices/50', expect.objectContaining({
        name: 'B超 v2',
        type: 'EXAMINATION',
      }))
    })

    it('setDeviceStatus delegates to device API', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope({
          id: 50,
          code: 'DEV-50',
          name: 'B超',
          type: 'EXAMINATION',
          status: 'MAINTENANCE',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      await setDeviceStatus(50, 'MAINTENANCE', '保养')
      expect(mock.post).toHaveBeenCalledWith('/devices/50/status', expect.objectContaining({
        targetStatus: 'MAINTENANCE',
        reason: '保养',
      }))
    })

    it('getDeviceStatusHistory delegates to device API', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          {
            id: 1,
            deviceId: 50,
            fromStatus: 'AVAILABLE',
            toStatus: 'MAINTENANCE',
            reason: '保养',
            operatorId: 1,
            operatedAt: TIMESTAMP,
          },
        ]),
      )
      const result = await getDeviceStatusHistory(50)
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenCalledWith('/devices/50/history')
    })

    it('getAdminDevices delegates to device API', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [
            {
              id: 50,
              code: 'DEV-50',
              name: 'B超',
              type: 'EXAMINATION',
              status: 'AVAILABLE',
              createdAt: TIMESTAMP,
              updatedAt: TIMESTAMP,
            },
          ],
          page: 1,
          pageSize: 100,
          total: 1,
          totalPages: 1,
        }),
      )
      const result = await getAdminDevices()
      expect(result).toHaveLength(1)
      expect(result[0].category).toBe('EXAMINATION')
    })
  })

  describe('schedule management', () => {
    it('createSchedule posts payload with combined date+time fields', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope({
          id: 1,
          doctorId: 11,
          doctorName: '李医生',
          departmentId: 2,
          departmentName: '内科',
          scheduleDate: '2026-07-01',
          startTime: '2026-07-01T09:00:00',
          endTime: '2026-07-01T12:00:00',
          maxAppointments: 20,
          bookedCount: 0,
          remainingCount: 20,
          status: 'AVAILABLE',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      await createSchedule({
        doctorId: 11,
        departmentId: 2,
        scheduleDate: '2026-07-01',
        startTime: '09:00',
        endTime: '12:00',
        maxAppointments: 20,
      })
      expect(mock.post).toHaveBeenCalledWith('/schedules', expect.objectContaining({
        startTime: '2026-07-01T09:00:00',
        endTime: '2026-07-01T12:00:00',
      }))
    })

    it('updateSchedule fetches current schedule then merges payload', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          id: 1,
          doctorId: 11,
          doctorName: '李医生',
          departmentId: 2,
          departmentName: '内科',
          scheduleDate: '2026-07-01',
          startTime: '2026-07-01T09:00:00',
          endTime: '2026-07-01T12:00:00',
          maxAppointments: 20,
          bookedCount: 0,
          remainingCount: 20,
          status: 'AVAILABLE',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      mock.put.mockResolvedValueOnce(
        successEnvelope({
          id: 1,
          doctorId: 11,
          doctorName: '李医生',
          departmentId: 2,
          departmentName: '内科',
          scheduleDate: '2026-07-01',
          startTime: '2026-07-01T09:00:00',
          endTime: '2026-07-01T13:00:00',
          maxAppointments: 20,
          bookedCount: 0,
          remainingCount: 20,
          status: 'AVAILABLE',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      await updateSchedule(1, { endTime: '13:00' })
      expect(mock.get).toHaveBeenCalledWith('/schedules/1')
      expect(mock.put).toHaveBeenCalledWith('/schedules/1', expect.objectContaining({
        endTime: '2026-07-01T13:00:00',
      }))
    })

    it('cancelSchedule posts payload to /schedules/:id/cancel', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope({
          id: 1,
          doctorId: 11,
          scheduleDate: '2026-07-01',
          startTime: '2026-07-01T09:00:00',
          endTime: '2026-07-01T12:00:00',
          maxAppointments: 20,
          status: 'CANCELLED',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      await cancelSchedule(1, { reason: '医生请假' })
      expect(mock.post).toHaveBeenCalledWith('/schedules/1/cancel', { reason: '医生请假' })
    })
  })

  describe('getAdminSchedules department branch', () => {
    it('uses department-scoped endpoint when departmentId is provided', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          {
            id: 1,
            doctorId: 11,
            scheduleDate: '2026-07-01',
            startTime: '2026-07-01T09:00:00',
            endTime: '2026-07-01T12:00:00',
            maxAppointments: 20,
            status: 'AVAILABLE',
            createdAt: TIMESTAMP,
            updatedAt: TIMESTAMP,
          },
        ]),
      )
      const result = await getAdminSchedules({ departmentId: 2 })
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenCalledWith('/schedules/department/2')
    })
  })

  describe('getAdminPatients fallback', () => {
    it('falls back to mapPatientBasic when getPatientDetail throws', async () => {
      // 1. /patients/search
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { id: 1, userId: 7, name: '张三', gender: 'MALE', birthDate: '1990-01-01', phone: '13800000000', status: 'ACTIVE', createdAt: TIMESTAMP, updatedAt: TIMESTAMP },
        ]),
      )
      // 2. /patients/1 (getPatientDetail fails)
      mock.get.mockRejectedValueOnce(new Error('profile missing'))
      // Should NOT call /patients/1/profile
      const result = await getAdminPatients({ keyword: '张' })
      expect(result.total).toBe(1)
      expect(result.list[0].name).toBe('张三')
      expect(result.list[0].allergies).toBe('')
    })

    it('slices the result list when page size is exceeded', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          { id: 1, userId: 7, name: 'A', gender: 'MALE', birthDate: '1990-01-01', phone: '1', status: 'ACTIVE', createdAt: TIMESTAMP, updatedAt: TIMESTAMP },
          { id: 2, userId: 8, name: 'B', gender: 'MALE', birthDate: '1990-01-01', phone: '2', status: 'ACTIVE', createdAt: TIMESTAMP, updatedAt: TIMESTAMP },
          { id: 3, userId: 9, name: 'C', gender: 'MALE', birthDate: '1990-01-01', phone: '3', status: 'ACTIVE', createdAt: TIMESTAMP, updatedAt: TIMESTAMP },
        ]),
      )
      mock.get.mockRejectedValue(new Error('profile missing'))
      const result = await getAdminPatients({ keyword: 'x', page: 2, pageSize: 2 })
      expect(result.total).toBe(3)
      expect(result.list).toHaveLength(1)
      expect(result.list[0].id).toBe(3)
    })
  })

  describe('department management', () => {
    it('getDepartmentById maps status ENABLED to ACTIVE and counts doctors', async () => {
      // 1. /departments/:id
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          id: 2,
          code: 'DEPT_2',
          name: '外科',
          status: 'ENABLED',
          description: '外科系统',
          createdAt: TIMESTAMP,
          updatedAt: TIMESTAMP,
        }),
      )
      // 2. /doctors (from getAllDoctorsRaw)
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [
            { id: 11, userId: 22, departmentId: 2, departmentName: '外科', name: '李医生', title: 'ATTENDING', status: 'ENABLED', createdAt: TIMESTAMP, updatedAt: TIMESTAMP },
          ],
          page: 1,
          pageSize: 100,
          total: 1,
          totalPages: 1,
        }),
      )
      const result = await getDepartmentById(2)
      expect(result.status).toBe('ACTIVE')
      expect(result.doctorCount).toBe(1)
    })

    it('getDepartmentById maps status DISABLED to INACTIVE', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          id: 3, code: 'D3', name: '骨科', status: 'DISABLED', description: '', createdAt: TIMESTAMP, updatedAt: TIMESTAMP,
        }),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope({ items: [], page: 1, pageSize: 100, total: 0, totalPages: 0 }),
      )
      const result = await getDepartmentById(3)
      expect(result.status).toBe('INACTIVE')
    })

    it('createDepartment posts payload to /departments', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope({
          id: 99, code: 'D99', name: '新科室', status: 'ENABLED', description: '', createdAt: TIMESTAMP, updatedAt: TIMESTAMP,
        }),
      )
      mock.get.mockResolvedValueOnce(
        successEnvelope({ items: [], page: 1, pageSize: 100, total: 0, totalPages: 0 }),
      )
      const result = await createDepartment({ code: 'D99', name: '新科室' })
      expect(result.id).toBe(99)
      expect(mock.post).toHaveBeenCalledWith('/departments', {
        code: 'D99',
        name: '新科室',
        description: undefined,
      })
    })

    it('updateDepartment fetches current then puts merged payload', async () => {
      // 1. /departments/2
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          id: 2, code: 'D2', name: '外科', status: 'ENABLED', description: 'old', createdAt: TIMESTAMP, updatedAt: TIMESTAMP,
        }),
      )
      // 2. /doctors
      mock.get.mockResolvedValueOnce(
        successEnvelope({ items: [], page: 1, pageSize: 100, total: 0, totalPages: 0 }),
      )
      // 3. PUT /departments/2
      mock.put.mockResolvedValueOnce(
        successEnvelope({
          id: 2, code: 'D2', name: '外科新', status: 'ENABLED', description: 'new', createdAt: TIMESTAMP, updatedAt: TIMESTAMP,
        }),
      )
      const result = await updateDepartment(2, { name: '外科新', description: 'new' })
      expect(result.name).toBe('外科新')
      expect(mock.put).toHaveBeenCalledWith('/departments/2', {
        name: '外科新',
        status: 'ENABLED',
        description: 'new',
      })
    })

    it('setDepartmentStatus calls backendDepartmentStatus for ACTIVE', async () => {
      // 1. /departments/2
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          id: 2, code: 'D2', name: '外科', status: 'ENABLED', description: '', createdAt: TIMESTAMP, updatedAt: TIMESTAMP,
        }),
      )
      // 2. /doctors
      mock.get.mockResolvedValueOnce(
        successEnvelope({ items: [], page: 1, pageSize: 100, total: 0, totalPages: 0 }),
      )
      // 3. PUT /departments/2
      mock.put.mockResolvedValueOnce(
        successEnvelope({
          id: 2, code: 'D2', name: '外科', status: 'DISABLED', description: '', createdAt: TIMESTAMP, updatedAt: TIMESTAMP,
        }),
      )
      await setDepartmentStatus(2, 'INACTIVE')
      expect(mock.put).toHaveBeenCalledWith('/departments/2', expect.objectContaining({
        status: 'DISABLED',
      }))
    })
  })

  describe('user management', () => {
    const backendUser = {
      id: 1,
      username: 'admin',
      realName: '管理员',
      phone: '13800000000',
      email: 'admin@example.com',
      enabled: true,
      accountNonLocked: true,
      roles: ['ADMIN'],
      createdAt: TIMESTAMP,
      updatedAt: TIMESTAMP,
    }

    it('getUsers returns mapped users', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [backendUser],
          page: 1,
          pageSize: 100,
          total: 1,
          totalPages: 1,
        }),
      )
      const result = await getUsers()
      expect(result).toHaveLength(1)
      expect(result[0].status).toBe('ENABLED')
      expect(mock.get).toHaveBeenCalledWith('/admin/users', {
        params: { enabled: undefined, role: undefined, keyword: undefined, page: 1, size: 100 },
      })
    })

    it('getUsers filters by status ENABLED/DISABLED/LOCKED', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [backendUser],
          page: 1,
          pageSize: 100,
          total: 1,
          totalPages: 1,
        }),
      )
      await getUsers({ status: 'ENABLED', role: 'ADMIN', keyword: 'adm' })
      expect(mock.get).toHaveBeenCalledWith('/admin/users', {
        params: { enabled: true, role: 'ADMIN', keyword: 'adm', page: 1, size: 100 },
      })
    })

    it('getUsers returns LOCKED users after filtering', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [
            { ...backendUser, id: 1, accountNonLocked: false },
            { ...backendUser, id: 2, enabled: false },
            { ...backendUser, id: 3, accountNonLocked: true, enabled: true },
          ],
          page: 1,
          pageSize: 100,
          total: 3,
          totalPages: 1,
        }),
      )
      const result = await getUsers({ status: 'LOCKED' })
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(1)
    })

    it('createUser posts ADMIN payload', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendUser))
      await createUser({
        username: 'admin',
        password: 'secret',
        roles: ['ADMIN'],
        realName: '管理员',
        phone: '13800000000',
      })
      expect(mock.post).toHaveBeenCalledWith('/admin/users', expect.objectContaining({
        username: 'admin',
        role: 'ADMIN',
      }))
    })

    it('createUser posts DOCTOR payload with doctor-specific fields', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope({ ...backendUser, id: 2, roles: ['DOCTOR'] }),
      )
      await createUser({
        username: 'doc',
        password: 'p',
        roles: ['DOCTOR'],
        realName: '张医',
        phone: '1',
        email: 'd@x.com',
        departmentId: 5,
        doctorName: '张医',
        doctorTitle: 'ATTENDING',
        specialty: '心内',
        education: '本科',
        experienceYears: 5,
        introduction: '...',
      })
      expect(mock.post).toHaveBeenCalledWith('/admin/users', expect.objectContaining({
        role: 'DOCTOR',
        departmentId: 5,
        specialty: '心内',
      }))
    })

    it('createUser throws for PATIENT role', async () => {
      await expect(
        createUser({ username: 'p', password: 'p', roles: ['PATIENT'] } as never),
      ).rejects.toThrow('管理端')
    })

    it('updateUser puts payload with role mapping', async () => {
      mock.put.mockResolvedValueOnce(successEnvelope(backendUser))
      await updateUser(1, {
        roles: ['DOCTOR'],
        realName: '改名',
        phone: '111',
        email: 'a@x.com',
      })
      expect(mock.put).toHaveBeenCalledWith('/admin/users/1', {
        role: 'DOCTOR',
        realName: '改名',
        phone: '111',
        email: 'a@x.com',
      })
    })

    it('changeUserStatus maps statuses to backend actions', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendUser))
      await changeUserStatus(1, { status: 'DISABLED', reason: '违规' })
      expect(mock.post).toHaveBeenCalledWith('/admin/users/1/status', { action: 'DISABLE' })
    })

    it('changeUserStatus maps LOCKED to LOCK action', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendUser))
      await changeUserStatus(1, { status: 'LOCKED', reason: '尝试暴力破解' })
      expect(mock.post).toHaveBeenCalledWith('/admin/users/1/status', { action: 'LOCK' })
    })

    it('resetUserPassword posts to /admin/users/:id/reset-password', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(null))
      await resetUserPassword(1, { newPassword: 'new-pwd' })
      expect(mock.post).toHaveBeenCalledWith('/admin/users/1/reset-password', { newPassword: 'new-pwd' })
    })
  })

  describe('doctor management', () => {
    const backendDoctor = {
      id: 11,
      userId: 22,
      departmentId: 2,
      departmentName: '内科',
      name: '李医生',
      title: 'CHIEF',
      specialty: '心血管',
      status: 'ENABLED',
      introduction: '...',
      createdAt: TIMESTAMP,
      updatedAt: TIMESTAMP,
    }

    it('getDoctors filters by departmentId, status, keyword', async () => {
      // 1. /doctors (from getAllDoctorsRaw)
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [backendDoctor, { ...backendDoctor, id: 12, departmentId: 5, name: '王医生' }],
          page: 1,
          pageSize: 100,
          total: 2,
          totalPages: 1,
        }),
      )
      // 2. /schedules/available (from getAdminSchedules)
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      const result = await getDoctors({ departmentId: 2, status: 'ACTIVE' })
      expect(result).toHaveLength(1)
      expect(result[0].id).toBe(11)
      expect(result[0].title).toBe('主任医师')
    })

    it('getDoctors filters by keyword against name/title/department', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope({
          items: [backendDoctor, { ...backendDoctor, id: 12, name: '王医生', departmentName: '外科' }],
          page: 1,
          pageSize: 100,
          total: 2,
          totalPages: 1,
        }),
      )
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      const result = await getDoctors({ keyword: '王' })
      expect(result).toHaveLength(1)
      expect(result[0].name).toBe('王医生')
    })

    it('getDoctorById uses doctor-scoped schedule endpoint', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendDoctor))
      // /schedules/doctor/11 (page envelope)
      mock.get.mockResolvedValueOnce(
        successEnvelope({ items: [], page: 1, pageSize: 100, total: 0, totalPages: 0 }),
      )
      const result = await getDoctorById(11)
      expect(result.id).toBe(11)
      expect(mock.get).toHaveBeenCalledWith('/schedules/doctor/11', {
        params: { page: 1, size: 100 },
      })
    })

    it('createDoctor posts payload to /doctors', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(backendDoctor))
      await createDoctor({
        username: 'doc',
        password: 'p',
        departmentId: 2,
        name: '李医生',
        title: '主任医师',
        gender: 'MALE',
        phone: '13800000000',
        specialty: '心血管',
        introduction: '...',
      })
      expect(mock.post).toHaveBeenCalledWith('/doctors', expect.objectContaining({
        title: 'CHIEF',
        departmentId: 2,
      }))
    })

    it('updateDoctor fetches current then puts merged payload', async () => {
      // 1. /doctors/11
      mock.get.mockResolvedValueOnce(successEnvelope(backendDoctor))
      // 2. /schedules/doctor/11
      mock.get.mockResolvedValueOnce(
        successEnvelope({ items: [], page: 1, pageSize: 100, total: 0, totalPages: 0 }),
      )
      // 3. PUT /doctors/11
      mock.put.mockResolvedValueOnce(successEnvelope({ ...backendDoctor, name: '李医生 v2' }))
      const result = await updateDoctor(11, { name: '李医生 v2' })
      expect(result.name).toBe('李医生 v2')
      expect(mock.put).toHaveBeenCalledWith('/doctors/11', expect.objectContaining({
        name: '李医生 v2',
        status: 'ENABLED',
      }))
    })

    it('setDoctorStatus calls backendDoctorStatus for DISABLED', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope(backendDoctor))
      mock.get.mockResolvedValueOnce(
        successEnvelope({ items: [], page: 1, pageSize: 100, total: 0, totalPages: 0 }),
      )
      mock.put.mockResolvedValueOnce(successEnvelope({ ...backendDoctor, status: 'DISABLED' }))
      await setDoctorStatus(11, 'DISABLED')
      expect(mock.put).toHaveBeenCalledWith('/doctors/11', expect.objectContaining({
        status: 'DISABLED',
      }))
    })
  })
})
