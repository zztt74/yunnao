// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import { backendDoctorFixture } from './helpers/fixtures'

import { getCurrentDoctor, getDoctorProfile, getDoctorSchedules, getDoctorTodaySchedules, updateDoctorProfile } from '@/api/doctor'
import { useAuthStore } from '@/stores/auth'

const mock = getApiClientMock()

function setupAuth(userId: number) {
  const auth = useAuthStore()
  auth.establishSession('token', {
    userId,
    username: 'doc',
    roles: ['DOCTOR'],
    mustChangePassword: false,
  })
}

describe('doctor API', () => {
  beforeEach(() => {
    resetApiClientMock()
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  describe('getCurrentDoctor', () => {
    it('throws when user is not logged in', async () => {
      await expect(getCurrentDoctor()).rejects.toThrow('当前医生未登录')
    })

    it('throws when no doctor matches the user id', async () => {
      setupAuth(99)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ userId: 22 })]))
      await expect(getCurrentDoctor()).rejects.toThrow('当前登录账号没有关联医生档案')
    })

    it('returns the doctor matching the auth userId', async () => {
      setupAuth(22)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ userId: 22 })]))
      const doctor = await getCurrentDoctor()
      expect(doctor.userId).toBe(22)
    })
  })

  describe('getDoctorProfile', () => {
    it('maps title to Chinese label and status to ACTIVE', async () => {
      setupAuth(22)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ title: 'CHIEF', userId: 22 })]))
      const profile = await getDoctorProfile()
      expect(profile.title).toBe('主任医师')
      expect(profile.status).toBe('ACTIVE')
    })

    it('maps status DISABLED', async () => {
      setupAuth(22)
      mock.get.mockResolvedValueOnce(
        pageEnvelope([backendDoctorFixture({ status: 'DISABLED', userId: 22 })]),
      )
      const profile = await getDoctorProfile()
      expect(profile.status).toBe('DISABLED')
    })

    it('keeps title unchanged for unknown code', async () => {
      setupAuth(22)
      mock.get.mockResolvedValueOnce(
        pageEnvelope([backendDoctorFixture({ title: 'CUSTOM', userId: 22 })]),
      )
      const profile = await getDoctorProfile()
      expect(profile.title).toBe('CUSTOM')
    })
  })

  describe('updateDoctorProfile', () => {
    it('puts specialty/introduction only', async () => {
      setupAuth(22)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ userId: 22 })]))
      mock.put.mockResolvedValueOnce(successEnvelope(backendDoctorFixture({ userId: 22 })))
      await updateDoctorProfile({ specialty: '心脏', introduction: '简介' })
      expect(mock.put).toHaveBeenCalledWith('/doctors/me/profile', {
        specialty: '心脏',
        introduction: '简介',
      })
    })
  })

  describe('getDoctorSchedules', () => {
    it('fetches schedules for the current doctor', async () => {
      setupAuth(22)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ userId: 22 })]))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          {
            id: 100,
            doctorId: 11,
            doctorName: '李医生',
            departmentId: 2,
            departmentName: '内科',
            scheduleDate: '2026-07-01',
            startTime: '2026-07-01T09:00:00',
            endTime: '2026-07-01T12:00:00',
            maxAppointments: 20,
            bookedCount: 5,
            remainingCount: 15,
            status: 'AVAILABLE',
            createdAt: '2026-06-28T10:00:00',
            updatedAt: '2026-06-28T10:00:00',
          },
        ]),
      )
      const schedules = await getDoctorSchedules()
      expect(schedules).toHaveLength(1)
      expect(schedules[0].id).toBe(100)
    })
  })

  describe('getDoctorTodaySchedules', () => {
    it('filters by today scheduleDate', async () => {
      setupAuth(22)
      const today = new Date().toISOString().slice(0, 10)
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDoctorFixture({ userId: 22 })]))
      mock.get.mockResolvedValueOnce(
        pageEnvelope([
          {
            id: 100,
            doctorId: 11,
            doctorName: '李医生',
            departmentId: 2,
            departmentName: '内科',
            scheduleDate: today,
            startTime: `${today}T09:00:00`,
            endTime: `${today}T12:00:00`,
            maxAppointments: 20,
            bookedCount: 5,
            remainingCount: 15,
            status: 'AVAILABLE',
            createdAt: '2026-06-28T10:00:00',
            updatedAt: '2026-06-28T10:00:00',
          },
          {
            id: 101,
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
            createdAt: '2025-01-01T10:00:00',
            updatedAt: '2025-01-01T10:00:00',
          },
        ]),
      )
      const schedules = await getDoctorTodaySchedules()
      expect(schedules).toHaveLength(1)
      expect(schedules[0].scheduleDate).toBe(today)
    })
  })
})
