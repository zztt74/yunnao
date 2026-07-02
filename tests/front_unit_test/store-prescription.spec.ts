// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { usePrescriptionStore } from '@/stores/prescription'
import type { PrescriptionResponse } from '@/types/prescription'

function prescriptionFixture(overrides: Record<string, unknown> = {}): PrescriptionResponse {
  return {
    id: 1,
    encounterId: 10,
    patientId: 42,
    patientName: '张三',
    doctorId: 5,
    doctorName: '王医生',
    departmentName: '内科',
    diagnosis: '上呼吸道感染',
    items: [],
    status: 'DRAFT',
    aiReviewStatus: 'NOT_REQUESTED',
    createdAt: '2026-07-01T10:00:00',
    updatedAt: '2026-07-01T10:00:00',
    ...overrides,
  } as PrescriptionResponse
}

describe('prescription store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('initial state', () => {
    it('starts empty', () => {
      const store = usePrescriptionStore()
      expect(store.currentPrescription).toBeNull()
      expect(store.historyPrescriptions).toEqual([])
      expect(store.historyLoaded).toBe(false)
    })
  })

  describe('setCurrentPrescription', () => {
    it('stores prescription and context', () => {
      const store = usePrescriptionStore()
      const pres = prescriptionFixture()
      store.setCurrentPrescription(pres, { encounterId: 10, patientId: 42 })
      expect(store.currentPrescription).toEqual(pres)
      expect(store.currentEncounterId).toBe(10)
      expect(store.currentPatientId).toBe(42)
    })

    it('clears only when null is passed', () => {
      const store = usePrescriptionStore()
      store.setCurrentPrescription(prescriptionFixture())
      store.setCurrentPrescription(null)
      expect(store.currentPrescription).toBeNull()
    })
  })

  describe('history', () => {
    it('setHistory records patient id and marks loaded', () => {
      const store = usePrescriptionStore()
      const list = [prescriptionFixture({ id: 1 }), prescriptionFixture({ id: 2 })]
      store.setHistory(42, list)
      expect(store.historyPrescriptions).toEqual(list)
      expect(store.historyPatientId).toBe(42)
      expect(store.historyLoaded).toBe(true)
    })

    it('clearHistory resets only history fields', () => {
      const store = usePrescriptionStore()
      store.setHistory(42, [prescriptionFixture()])
      store.setCurrentPrescription(prescriptionFixture(), {
        encounterId: 10,
        patientId: 42,
      })
      store.clearHistory()
      expect(store.historyPrescriptions).toEqual([])
      expect(store.currentPrescription).not.toBeNull()
    })
  })

  describe('clear', () => {
    it('clears everything (cross-patient isolation)', () => {
      const store = usePrescriptionStore()
      store.setCurrentPrescription(prescriptionFixture(), {
        encounterId: 10,
        patientId: 42,
      })
      store.setHistory(42, [prescriptionFixture()])
      store.clear()
      expect(store.currentPrescription).toBeNull()
      expect(store.currentEncounterId).toBeNull()
      expect(store.currentPatientId).toBeNull()
      expect(store.historyPrescriptions).toEqual([])
      expect(store.historyPatientId).toBeNull()
    })
  })
})
