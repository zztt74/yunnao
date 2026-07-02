// 当前患者资料（跨页面复用：医生端病历/处方需展示患者过敏史等）
// 仅缓存当前登录患者（患者端）/ 当前接诊患者（医生端）
import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getPatientDetail } from '@/api/patient'
import { getPatientInfo } from '@/api/patient'
import type { PatientDetailResponse, PatientResponse } from '@/types/patient'

/**
 * 使用场景：
 * - 患者端：进入"我的"或缴费页时缓存 getPatientInfo() 结果，避免多个组件重复请求 /patients/me
 * - 医生端：进入病历/处方/检查页时缓存 getPatientDetail(patientId)，用于显示姓名/过敏/紧急联系
 *
 * 不应变为全量缓存：仅保留"当前关注患者"。切换患者时必须 clear()，避免跨患者串扰。
 */
export const usePatientStore = defineStore('patient', () => {
  const current = ref<PatientDetailResponse | null>(null)
  const currentBasic = ref<PatientResponse | null>(null)
  const loading = ref(false)

  async function loadCurrentDetail(patientId?: number): Promise<PatientDetailResponse | null> {
    loading.value = true
    try {
      const id = patientId ?? current.value?.id ?? (await getPatientInfo()).id
      const detail = await getPatientDetail(id)
      current.value = detail
      return detail
    } finally {
      loading.value = false
    }
  }

  async function loadCurrentBasic(): Promise<PatientResponse | null> {
    if (currentBasic.value) return currentBasic.value
    loading.value = true
    try {
      const basic = await getPatientInfo()
      currentBasic.value = basic
      return basic
    } finally {
      loading.value = false
    }
  }

  function setCurrent(detail: PatientDetailResponse | null) {
    current.value = detail
  }

  function clear() {
    current.value = null
    currentBasic.value = null
  }

  return {
    current,
    currentBasic,
    loading,
    loadCurrentDetail,
    loadCurrentBasic,
    setCurrent,
    clear,
  }
})
