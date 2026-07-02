// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { usePatientStore } from '@/stores/patient'

const getPatientDetailMock = vi.fn()
const getPatientInfoMock = vi.fn()

vi.mock('@/api/patient', () => ({
  getPatientDetail: (...args: unknown[]) => getPatientDetailMock(...args),
  getPatientInfo: (...args: unknown[]) => getPatientInfoMock(...args),
}))

const PATIENT_DETAIL = {
  id: 42,
  name: '张三',
  gender: 'MALE' as const,
  birthDate: '1990-01-01',
  age: 34,
  phone: '13800000000',
  allergies: '青霉素',
  medicalHistory: '无',
  address: '',
  emergencyContact: '',
  emergencyPhone: '',
  createdAt: '2026-07-01T10:00:00',
}

const PATIENT_BASIC = {
  id: 42,
  userId: 7,
  name: '张三',
  gender: 'MALE' as const,
  birthDate: '1990-01-01',
  phone: '13800000000',
  status: 'ACTIVE' as const,
  createdAt: '2026-07-01T10:00:00',
  updatedAt: '2026-07-01T10:00:00',
}

describe('patient store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getPatientDetailMock.mockReset()
    getPatientInfoMock.mockReset()
  })

  describe('initial state', () => {
    it('starts with no current patient', () => {
      const store = usePatientStore()
      expect(store.current).toBeNull()
      expect(store.currentBasic).toBeNull()
      expect(store.loading).toBe(false)
    })
  })

  describe('loadCurrentDetail', () => {
    it('fetches and stores patient detail', async () => {
      getPatientDetailMock.mockResolvedValueOnce(PATIENT_DETAIL)
      const store = usePatientStore()
      const result = await store.loadCurrentDetail(42)
      expect(result).toEqual(PATIENT_DETAIL)
      expect(store.current).toEqual(PATIENT_DETAIL)
      expect(getPatientDetailMock).toHaveBeenCalledWith(42)
    })

    it('falls back to getPatientInfo id when no id provided and not cached', async () => {
      getPatientInfoMock.mockResolvedValueOnce(PATIENT_BASIC)
      getPatientDetailMock.mockResolvedValueOnce(PATIENT_DETAIL)
      const store = usePatientStore()
      await store.loadCurrentDetail()
      expect(getPatientInfoMock).toHaveBeenCalled()
      expect(getPatientDetailMock).toHaveBeenCalledWith(42)
    })

    it('reuses cached id when current is set', async () => {
      getPatientDetailMock.mockResolvedValueOnce(PATIENT_DETAIL)
      const store = usePatientStore()
      await store.loadCurrentDetail(42)
      getPatientDetailMock.mockResolvedValueOnce({ ...PATIENT_DETAIL, name: '李四' })
      await store.loadCurrentDetail()
      expect(getPatientDetailMock).toHaveBeenLastCalledWith(42)
      expect(store.current?.name).toBe('李四')
    })
  })

  describe('loadCurrentBasic', () => {
    it('caches basic info on first call', async () => {
      getPatientInfoMock.mockResolvedValueOnce(PATIENT_BASIC)
      const store = usePatientStore()
      const r1 = await store.loadCurrentBasic()
      const r2 = await store.loadCurrentBasic()
      expect(r1).toEqual(PATIENT_BASIC)
      expect(r2).toEqual(PATIENT_BASIC)
      expect(getPatientInfoMock).toHaveBeenCalledTimes(1)
    })
  })

  describe('setCurrent / clear', () => {
    it('manually sets the current patient', () => {
      const store = usePatientStore()
      store.setCurrent(PATIENT_DETAIL)
      expect(store.current).toEqual(PATIENT_DETAIL)
    })

    it('clears both detail and basic caches without touching other state', () => {
      const store = usePatientStore()
      store.setCurrent(PATIENT_DETAIL)
      store.currentBasic = PATIENT_BASIC
      store.clear()
      expect(store.current).toBeNull()
      expect(store.currentBasic).toBeNull()
    })
  })
})
