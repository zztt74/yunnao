// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useDoctorStore } from '@/stores/doctor'

const getAvailableSchedulesMock = vi.fn()

vi.mock('@/api/appointment', () => ({
  getAvailableSchedules: (...args: unknown[]) => getAvailableSchedulesMock(...args),
}))

function schedule(overrides: Record<string, unknown> = {}) {
  return {
    id: 1001,
    doctorId: 1,
    doctorName: '王医生',
    departmentId: 7,
    departmentName: '内科',
    scheduleDate: '2026-07-02',
    startTime: '2026-07-02T09:00:00',
    endTime: '2026-07-02T10:00:00',
    maxAppointments: 20,
    bookedCount: 5,
    remainingCount: 15,
    status: 'AVAILABLE' as const,
    ...overrides,
  }
}

describe('doctor store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getAvailableSchedulesMock.mockReset()
    // 固定当前日期，避免依赖系统时间
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-02T08:00:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('loadDoctorsByDepartment', () => {
    it('aggregates unique doctors from 7-day schedules', async () => {
      getAvailableSchedulesMock.mockImplementation(async ({ date }) => {
        if (date === '2026-07-02') {
          return [
            schedule({ id: 1, doctorId: 1, doctorName: '王医生', scheduleDate: '2026-07-02' }),
            schedule({ id: 2, doctorId: 2, doctorName: '李医生', scheduleDate: '2026-07-02' }),
          ]
        }
        if (date === '2026-07-03') {
          return [
            schedule({ id: 3, doctorId: 1, doctorName: '王医生', scheduleDate: '2026-07-03' }),
          ]
        }
        return []
      })

      const store = useDoctorStore()
      const list = await store.loadDoctorsByDepartment(7)
      expect(list).toHaveLength(2)
      // 王医生有 2 个日期，应排在前面
      expect(list[0].doctorName).toBe('王医生')
      expect(list[0].availableDates).toEqual(['2026-07-02', '2026-07-03'])
      expect(list[0].remainingTotal).toBe(30)
      expect(list[0].nextScheduleId).toBe(1)
      expect(list[1].doctorName).toBe('李医生')
    })

    it('skips schedules that are not AVAILABLE or full', async () => {
      getAvailableSchedulesMock.mockImplementation(async ({ date }) => {
        if (date === '2026-07-02') {
          return [
            schedule({ id: 1, status: 'AVAILABLE', remainingCount: 10 }),
            schedule({ id: 2, status: 'AVAILABLE', remainingCount: 0 }),
            schedule({ id: 3, status: 'CANCELLED', remainingCount: 10 }),
            schedule({ id: 4, status: 'FULL', remainingCount: 0 }),
          ]
        }
        return []
      })
      const store = useDoctorStore()
      const list = await store.loadDoctorsByDepartment(7)
      expect(list).toHaveLength(1)
      expect(list[0].remainingTotal).toBe(10)
    })

    it('returns cached data within TTL', async () => {
      getAvailableSchedulesMock.mockResolvedValue([
        schedule({ id: 1, doctorName: '王医生' }),
      ])
      const store = useDoctorStore()
      const a = await store.loadDoctorsByDepartment(7)
      expect(a).toHaveLength(1)
      // 第二次调用应走缓存，不再请求（用 deep equality 避开 Vue reactive proxy 包装）
      const b = await store.loadDoctorsByDepartment(7)
      expect(b).toEqual(a)
      expect(getAvailableSchedulesMock).toHaveBeenCalledTimes(7) // 第一次 7 天
    })

    it('force=true bypasses cache', async () => {
      getAvailableSchedulesMock.mockResolvedValue([schedule({ id: 1, doctorName: '王医生' })])
      const store = useDoctorStore()
      await store.loadDoctorsByDepartment(7)
      await store.loadDoctorsByDepartment(7, { force: true })
      expect(getAvailableSchedulesMock).toHaveBeenCalledTimes(14)
    })

    it('keeps loading forward when a single day fails', async () => {
      getAvailableSchedulesMock.mockImplementation(async ({ date }) => {
        if (date === '2026-07-02') {
          throw new Error('network error')
        }
        if (date === '2026-07-03') {
          return [schedule({ id: 1, doctorName: '王医生' })]
        }
        return []
      })
      const store = useDoctorStore()
      const list = await store.loadDoctorsByDepartment(7)
      expect(list).toHaveLength(1)
      expect(list[0].doctorName).toBe('王医生')
    })

    it('returns empty list when all days fail', async () => {
      getAvailableSchedulesMock.mockRejectedValue(new Error('boom'))
      const store = useDoctorStore()
      const list = await store.loadDoctorsByDepartment(7)
      expect(list).toEqual([])
    })
  })

  describe('clear', () => {
    it('drops all cached departments', async () => {
      getAvailableSchedulesMock.mockResolvedValue([schedule()])
      const store = useDoctorStore()
      await store.loadDoctorsByDepartment(7)
      expect(store.getDoctorsByDepartment(7)).toHaveLength(1)
      store.clear()
      expect(store.getDoctorsByDepartment(7)).toEqual([])
    })
  })
})
