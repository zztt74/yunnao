// 医生缓存：按科室医生列表 + 医生详情缓存
// 主要用于：分诊结果页推荐医生、挂号页按科室选医生
// 后端暂未提供"按科室列出医生"接口，因此用 /schedules/available 反向聚合
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getAvailableSchedules } from '@/api/appointment'
import type { ScheduleResponse } from '@/types/appointment'

export interface DoctorSummary {
  doctorId: number
  doctorName: string
  departmentId: number
  departmentName: string
  title: string
  availableDates: string[]
  remainingTotal: number
  /** 下一个可选 scheduleId，若无则为 null */
  nextScheduleId: number | null
}

interface CacheEntry {
  loadedAt: number
  doctors: DoctorSummary[]
  /** 聚合时使用的所有 schedule（按 scheduleDate 升序） */
  schedules: ScheduleResponse[]
}

const CACHE_TTL_MS = 60_000 // 60s 软过期，过期后下次访问会重新拉取

/**
 * 最小化：仅缓存"按科室的医生摘要列表"，不缓存详情。
 * 缓存键：departmentId
 * TTL：60s；超期后下次调用会重新拉取一次以保持新鲜。
 */
export const useDoctorStore = defineStore('doctor', () => {
  const byDepartment = ref<Map<number, CacheEntry>>(new Map())
  const loadingByDept = ref<Set<number>>(new Set())

  function isFresh(entry: CacheEntry | undefined): boolean {
    return !!entry && Date.now() - entry.loadedAt < CACHE_TTL_MS
  }

  /**
   * 按科室聚合可预约排班，提取医生摘要。
   * - 仅聚合 status === 'AVAILABLE' 且 remainingCount > 0 的排班
   * - 同一医生只输出一条记录，汇总其所有可预约日期与总余号
   */
  async function loadDoctorsByDepartment(
    departmentId: number,
    options: { daysAhead?: number; force?: boolean } = {},
  ): Promise<DoctorSummary[]> {
    const cached = byDepartment.value.get(departmentId)
    if (!options.force && isFresh(cached)) {
      return cached!.doctors
    }
    if (loadingByDept.value.has(departmentId)) {
      // 并发请求合并：等当前请求完成
      await new Promise<void>((resolve) => {
        const timer = setInterval(() => {
          if (!loadingByDept.value.has(departmentId)) {
            clearInterval(timer)
            resolve()
          }
        }, 50)
      })
      return byDepartment.value.get(departmentId)?.doctors ?? []
    }

    loadingByDept.value.add(departmentId)
    try {
      const days = options.daysAhead ?? 7
      // 并行拉取未来 N 天的排班
      const today = new Date()
      const dayStrs: string[] = []
      for (let i = 0; i < days; i++) {
        const d = new Date(today)
        d.setDate(d.getDate() + i)
        dayStrs.push(d.toISOString().slice(0, 10))
      }
      const allSchedules: ScheduleResponse[] = []
      for (const date of dayStrs) {
        try {
          const list = await getAvailableSchedules({ departmentId, date })
          allSchedules.push(...list)
        } catch (e) {
          // 单日失败不阻塞其他日期
          console.warn(`[doctorStore] ${date} 排班加载失败：`, e)
        }
      }

      const doctorMap = new Map<number, DoctorSummary>()
      for (const s of allSchedules) {
        if (s.status !== 'AVAILABLE' || s.remainingCount <= 0) continue
        const existing = doctorMap.get(s.doctorId)
        if (existing) {
          if (!existing.availableDates.includes(s.scheduleDate)) {
            existing.availableDates.push(s.scheduleDate)
          }
          existing.remainingTotal += s.remainingCount
          // 取日期最早且 ID 较小的 schedule 作为 nextScheduleId（与排序结果保持一致）
          if (existing.nextScheduleId === null || s.id < existing.nextScheduleId) {
            existing.nextScheduleId = s.id
          }
        } else {
          doctorMap.set(s.doctorId, {
            doctorId: s.doctorId,
            doctorName: s.doctorName,
            departmentId: s.departmentId,
            departmentName: s.departmentName,
            title: '医师', // 排班接口不返回职称；按需可扩展专用接口
            availableDates: [s.scheduleDate],
            remainingTotal: s.remainingCount,
            nextScheduleId: s.id,
          })
        }
      }
      // 排序：可预约日期数多者优先，再按姓名
      const doctors = Array.from(doctorMap.values()).sort((a, b) => {
        if (b.availableDates.length !== a.availableDates.length) {
          return b.availableDates.length - a.availableDates.length
        }
        return a.doctorName.localeCompare(b.doctorName, 'zh-Hans-CN')
      })
      byDepartment.value.set(departmentId, {
        loadedAt: Date.now(),
        doctors,
        schedules: allSchedules,
      })
      return doctors
    } finally {
      loadingByDept.value.delete(departmentId)
    }
  }

  function getDoctorsByDepartment(departmentId: number): DoctorSummary[] {
    return byDepartment.value.get(departmentId)?.doctors ?? []
  }

  function isLoading(departmentId: number): boolean {
    return loadingByDept.value.has(departmentId)
  }

  function clear() {
    byDepartment.value.clear()
    loadingByDept.value.clear()
  }

  const hasCache = computed(() => byDepartment.value.size > 0)

  return {
    byDepartment,
    loadingByDept,
    hasCache,
    loadDoctorsByDepartment,
    getDoctorsByDepartment,
    isLoading,
    clear,
  }
})
