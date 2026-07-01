import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'

import {
  cancelAppointment,
  createAppointment,
  getAvailableSchedules,
  getMyAppointments,
} from '@/api/appointment'

const mock = getApiClientMock()

const scheduleFixture = {
  id: 3,
  doctorId: 11,
  doctorName: '李医生',
  departmentId: 2,
  departmentName: '心血管内科',
  scheduleDate: '2026-06-29',
  startTime: '2026-06-29T09:00:00',
  endTime: '2026-06-29T09:30:00',
  maxAppointments: 20,
  bookedCount: 1,
  remainingCount: 19,
  status: 'AVAILABLE',
  createdAt: '2026-06-28T10:00:00',
  updatedAt: '2026-06-28T10:00:00',
}

const appointmentFixture = {
  id: 5,
  patientId: 42,
  scheduleId: 3,
  doctorId: 11,
  doctorName: '李医生',
  departmentId: 2,
  departmentName: '心血管内科',
  appointmentNumber: 'A000001',
  status: 'BOOKED',
  bookedAt: '2026-06-28T10:00:00',
  createdAt: '2026-06-28T10:00:00',
  updatedAt: '2026-06-28T10:00:00',
}

describe('appointment API', () => {
  beforeEach(() => resetApiClientMock())

  describe('getAvailableSchedules', () => {
    it('appends T00:00:00 to date param', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([scheduleFixture]))
      const result = await getAvailableSchedules({ departmentId: 2, date: '2026-06-29' })
      expect(result).toEqual([scheduleFixture])
      expect(mock.get).toHaveBeenCalledWith('/schedules/available', {
        params: { departmentId: 2, date: '2026-06-29T00:00:00' },
      })
    })

    it('omits date param when not provided', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([scheduleFixture]))
      await getAvailableSchedules({ departmentId: 2 })
      expect(mock.get).toHaveBeenCalledWith('/schedules/available', {
        params: { departmentId: 2, date: undefined },
      })
    })

    it('accepts empty params', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      await getAvailableSchedules({})
      expect(mock.get).toHaveBeenCalledWith('/schedules/available', {
        params: { date: undefined },
      })
    })
  })

  describe('createAppointment', () => {
    it('posts payload to /appointments', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(appointmentFixture))
      const result = await createAppointment({ scheduleId: 3, symptoms: '胸痛' })
      expect(result).toEqual(appointmentFixture)
      expect(mock.post).toHaveBeenCalledWith('/appointments', { scheduleId: 3, symptoms: '胸痛' })
    })
  })

  describe('cancelAppointment', () => {
    it('posts to /appointments/:id/cancel', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope({ ...appointmentFixture, status: 'CANCELLED' }),
      )
      const result = await cancelAppointment(5, { reason: '改期' })
      expect(result.status).toBe('CANCELLED')
      expect(mock.post).toHaveBeenCalledWith('/appointments/5/cancel', { reason: '改期' })
    })
  })

  describe('getMyAppointments', () => {
    it('resolves patient id and returns page items', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope({ id: 42 }))
      mock.get.mockResolvedValueOnce(pageEnvelope([appointmentFixture]))
      const result = await getMyAppointments()
      expect(result).toEqual([appointmentFixture])
      expect(mock.get).toHaveBeenNthCalledWith(1, '/patients/me')
      expect(mock.get).toHaveBeenNthCalledWith(2, '/appointments/patient/42')
    })
  })
})
