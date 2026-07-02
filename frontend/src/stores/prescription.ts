// 处方状态：
// - currentPrescription：当前就诊的处方（DRAFT/CONFIRMED/VOIDED 全生命周期）
// - historyPrescriptions：当前患者的历史处方（CONFIRMED + VOIDED）
// - 切换患者时必须 clear()，避免跨患者串扰
import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { PrescriptionResponse } from '@/types/prescription'

export const usePrescriptionStore = defineStore('prescription', () => {
  const currentPrescription = ref<PrescriptionResponse | null>(null)
  const currentEncounterId = ref<number | null>(null)
  const currentPatientId = ref<number | null>(null)

  const historyPrescriptions = ref<PrescriptionResponse[]>([])
  const historyLoading = ref(false)
  const historyLoaded = ref(false)
  const historyPatientId = ref<number | null>(null)

  function setCurrentPrescription(
    pres: PrescriptionResponse | null,
    context?: { encounterId?: number; patientId?: number },
  ) {
    currentPrescription.value = pres
    if (context?.encounterId !== undefined) currentEncounterId.value = context.encounterId
    if (context?.patientId !== undefined) currentPatientId.value = context.patientId
  }

  function setHistory(patientId: number, list: PrescriptionResponse[]) {
    historyPatientId.value = patientId
    historyPrescriptions.value = list
    historyLoaded.value = true
  }

  function setHistoryLoading(loading: boolean) {
    historyLoading.value = loading
  }

  function clearHistory() {
    historyPrescriptions.value = []
    historyLoading.value = false
    historyLoaded.value = false
    historyPatientId.value = null
  }

  function clear() {
    currentPrescription.value = null
    currentEncounterId.value = null
    currentPatientId.value = null
    clearHistory()
  }

  return {
    currentPrescription,
    currentEncounterId,
    currentPatientId,
    historyPrescriptions,
    historyLoading,
    historyLoaded,
    historyPatientId,
    setCurrentPrescription,
    setHistory,
    setHistoryLoading,
    clearHistory,
    clear,
  }
})
