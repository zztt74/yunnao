// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useAppointmentStore } from '@/stores/appointment'
import type { AppointmentResponse } from '@/types/appointment'

function apptFixture(overrides: Record<string, unknown> = {}): AppointmentResponse {
  return {
    id: 1,
    patientId: 42,
    patientName: '张三',
    scheduleId: 100,
    doctorId: 5,
    doctorName: '王医生',
    departmentId: 7,
    departmentName: '内科',
    appointmentNumber: 'A202607010001',
    status: 'BOOKED',
    bookedAt: '2026-07-01T09:00:00',
    ...overrides,
  } as AppointmentResponse
}

describe('appointment store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('initial state', () => {
    it('starts empty', () => {
      const store = useAppointmentStore()
      expect(store.myAppointments).toEqual([])
      expect(store.myAppointmentsLoaded).toBe(false)
      expect(store.preSelection).toBeNull()
    })
  })

  describe('setMyAppointments', () => {
    it('stores list and marks loaded', async () => {
      const store = useAppointmentStore()
      const list = [apptFixture({ id: 1 }), apptFixture({ id: 2 })]
      await store.setMyAppointments(list)
      expect(store.myAppointments).toEqual(list)
      expect(store.myAppointmentsLoaded).toBe(true)
    })
  })

  describe('preSelection', () => {
    it('set / clear lifecycle', () => {
      const store = useAppointmentStore()
      store.setPreSelection({
        departmentId: 7,
        departmentName: '内科',
        doctorId: 5,
        doctorName: '王医生',
        scheduleId: 100,
      })
      expect(store.preSelection?.departmentId).toBe(7)
      expect(store.preSelection?.doctorId).toBe(5)
      expect(store.preSelection?.setAt).toBeGreaterThan(0)
      store.clearPreSelection()
      expect(store.preSelection).toBeNull()
    })

    it('builds preSelection from query with fallback', () => {
      const store = useAppointmentStore()
      const sel = store.buildPreSelectionFromQuery({
        departmentId: 7,
        departmentName: '内科',
        doctorId: 5,
      })
      expect(sel?.departmentId).toBe(7)
      expect(sel?.doctorId).toBe(5)
      expect(sel?.scheduleId).toBeUndefined()
    })

    it('returns null when query has no departmentId', () => {
      const store = useAppointmentStore()
      const sel = store.buildPreSelectionFromQuery({})
      expect(sel).toBeNull()
    })
  })

  describe('clear', () => {
    it('resets all state', async () => {
      const store = useAppointmentStore()
      await store.setMyAppointments([apptFixture()])
      store.setPreSelection({ departmentId: 7 })
      store.clear()
      expect(store.myAppointments).toEqual([])
      expect(store.myAppointmentsLoaded).toBe(false)
      expect(store.preSelection).toBeNull()
    })
  })
})
