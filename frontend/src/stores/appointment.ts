// 挂号（Appointment）状态：
// 1. 我的挂号列表缓存
// 2. 分诊跳转带来的预选参数（departmentId / doctorId / scheduleId）
// 设计原则：仅缓存跨页面复用的状态，不做全量持久化。
import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { AppointmentResponse } from '@/types/appointment'

export interface TriagePreSelection {
  departmentId: number
  departmentName?: string
  doctorId?: number
  doctorName?: string
  scheduleId?: number
  /** 进入预选的时间戳，便于上层做"已消费"清理 */
  setAt: number
}

/**
 * 分诊 → 挂号 的预选参数通过 store 流转：
 * - 进入 PatientAppointmentsView 时读取 store；若不存在再回退到 query
 * - 跳转的目标页确认预约或用户取消预选后，应调用 clearPreSelection()
 * - 避免"再次从分诊跳过来时仍带着上一次的预选"
 */
export const useAppointmentStore = defineStore('appointment', () => {
  const myAppointments = ref<AppointmentResponse[]>([])
  const myAppointmentsLoaded = ref(false)
  const myAppointmentsLoading = ref(false)
  const preSelection = ref<TriagePreSelection | null>(null)

  async function setMyAppointments(list: AppointmentResponse[]) {
    myAppointments.value = list
    myAppointmentsLoaded.value = true
  }

  function setMyAppointmentsLoading(loading: boolean) {
    myAppointmentsLoading.value = loading
  }

  function setPreSelection(sel: Omit<TriagePreSelection, 'setAt'>) {
    preSelection.value = { ...sel, setAt: Date.now() }
  }

  function clearPreSelection() {
    preSelection.value = null
  }

  /** 从 query 参数推断 preSelection（兜底，主要路径走 store） */
  function buildPreSelectionFromQuery(query: Record<string, unknown>): TriagePreSelection | null {
    const deptId = Number(query.departmentId)
    if (!deptId) return null
    return {
      departmentId: deptId,
      departmentName: query.departmentName ? String(query.departmentName) : undefined,
      doctorId: query.doctorId ? Number(query.doctorId) : undefined,
      doctorName: query.doctorName ? String(query.doctorName) : undefined,
      scheduleId: query.scheduleId ? Number(query.scheduleId) : undefined,
      setAt: Date.now(),
    }
  }

  function clear() {
    myAppointments.value = []
    myAppointmentsLoaded.value = false
    preSelection.value = null
  }

  return {
    myAppointments,
    myAppointmentsLoaded,
    myAppointmentsLoading,
    preSelection,
    setMyAppointments,
    setMyAppointmentsLoading,
    setPreSelection,
    clearPreSelection,
    buildPreSelectionFromQuery,
    clear,
  }
})
