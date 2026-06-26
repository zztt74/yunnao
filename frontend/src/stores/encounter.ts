import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { EncounterResponse } from '@/types/encounter'

/**
 * 接诊工作台状态
 * - activeEncounter：当前正在处理的就诊（工作台各子页面共享）
 * - consultationNotes：问诊记录（主诉/现病史/既往史/体格检查），用于 AI 诊断与病历生成
 */
export const useEncounterStore = defineStore('encounter', () => {
  const activeEncounter = ref<EncounterResponse | null>(null)
  const consultationNotes = ref({
    chiefComplaint: '',
    presentIllness: '',
    pastHistory: '',
    physicalExam: '',
  })

  function setActiveEncounter(enc: EncounterResponse | null) {
    activeEncounter.value = enc
  }

  function setConsultationNotes(notes: {
    chiefComplaint?: string
    presentIllness?: string
    pastHistory?: string
    physicalExam?: string
  }) {
    consultationNotes.value = { ...consultationNotes.value, ...notes }
  }

  function reset() {
    activeEncounter.value = null
    consultationNotes.value = {
      chiefComplaint: '',
      presentIllness: '',
      pastHistory: '',
      physicalExam: '',
    }
  }

  return {
    activeEncounter,
    consultationNotes,
    setActiveEncounter,
    setConsultationNotes,
    reset,
  }
})
