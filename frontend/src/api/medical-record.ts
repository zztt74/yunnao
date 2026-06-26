import type {
  MedicalRecord,
  MedicalRecordAiRequest,
  MedicalRecordAiResponse,
  MedicalRecordSaveRequest,
} from '@/types/medical-record'
import type { EncounterResponse } from '@/types/encounter'
import { mockMedicalRecords } from '@/api/mock/medical-mock'
import {
  getEncounterMedicalRecord as mockGetEncMr,
  generateMedicalRecordDraft as mockGenDraft,
  saveMedicalRecord as mockSaveMr,
  shouldSimulateAiFailure,
} from '@/api/mock/doctor-mock'

// MOCK：后端 /api/medical-records 接口未就绪，使用本地演示数据
// 后端就绪后请删除本文件并替换为真实调用

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 查询当前患者的病历列表（按时间倒序） */
export async function getMyMedicalRecords(params?: {
  fromDate?: string
  toDate?: string
}): Promise<MedicalRecord[]> {
  console.warn('[MOCK] /api/medical-records 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  let list = [...mockMedicalRecords]
  // 仅展示已 CONFIRMED 的（按设计文档 §14.4「只展示医生确认或审核后的正式结果」）
  list = list.filter((r) => r.status === 'CONFIRMED')
  if (params?.fromDate) {
    list = list.filter((r) => r.encounterDate >= params.fromDate!)
  }
  if (params?.toDate) {
    list = list.filter((r) => r.encounterDate <= params.toDate!)
  }
  return list.sort(
    (a, b) => new Date(b.encounterDate).getTime() - new Date(a.encounterDate).getTime(),
  )
}

/** 获取病历详情 */
export async function getMedicalRecordById(id: number): Promise<MedicalRecord> {
  console.warn('[MOCK] /api/medical-records/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  const found = mockMedicalRecords.find((r) => r.id === id)
  if (!found) {
    throw new Error('病历不存在')
  }
  return found
}

// ===== 医生端：病历生成与编辑 =====

/** 查询某就诊的病历（医生端，含草稿） */
export async function getEncounterMedicalRecord(encounterId: number): Promise<MedicalRecord | null> {
  console.warn('[MOCK] /api/medical-records/encounter/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetEncMr(encounterId) ?? null
}

/**
 * AI 生成病历草稿（§11.4）
 * - AI 只能生成草稿，不写入正式病历（§11.6）
 * - 返回结构化草稿，前端回填可编辑表单（§11.3）
 * - AI 失败时返回降级标记，医生可手工填写（§11.5、§14）
 */
export async function generateMedicalRecordDraft(
  payload: MedicalRecordAiRequest,
): Promise<MedicalRecordAiResponse> {
  console.warn('[MOCK] /api/medical-records/ai-generate 后端未就绪，使用本地虚构演示数据')
  await delay(1500)
  if (shouldSimulateAiFailure()) {
    sessionStorage.removeItem('cloud-brain.mock-ai-fail')
    return {
      encounterId: payload.encounterId,
      chiefComplaint: payload.chiefComplaint,
      presentIllness: '',
      pastHistory: '',
      physicalExam: '',
      preliminaryDiagnosis: '',
      treatmentAdvice: '',
      aiStatus: 'FAILED',
      aiFailureReason: 'AI_GENERATION_FAILED：AI 病历生成失败，请手工填写病历。',
    }
  }
  return mockGenDraft(payload.encounterId, {
    chiefComplaint: payload.chiefComplaint,
    presentIllness: payload.presentIllness,
    pastHistory: payload.pastHistory,
    physicalExam: payload.physicalExam,
  })
}

/** 医生保存病历草稿（DRAFT/AI_GENERATED，§11.3） */
export async function saveMedicalRecord(
  encounter: EncounterResponse,
  payload: MedicalRecordSaveRequest,
): Promise<MedicalRecord> {
  console.warn('[MOCK] /api/medical-records POST（草稿）后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockSaveMr(encounter.id, encounter, {
    chiefComplaint: payload.chiefComplaint,
    presentIllness: payload.presentIllness,
    pastHistory: payload.pastHistory,
    physicalExam: payload.physicalExam,
    preliminaryDiagnosis: payload.preliminaryDiagnosis,
    treatmentAdvice: payload.treatmentAdvice,
    status: payload.status,
  })
}

/** 医生确认病历（DRAFT/AI_GENERATED → CONFIRMED，§11.6：正式病历必须医生确认） */
export async function confirmMedicalRecord(
  encounter: EncounterResponse,
  payload: MedicalRecordSaveRequest,
): Promise<MedicalRecord> {
  console.warn('[MOCK] /api/medical-records/{id}/confirm 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockSaveMr(encounter.id, encounter, {
    chiefComplaint: payload.chiefComplaint,
    presentIllness: payload.presentIllness,
    pastHistory: payload.pastHistory,
    physicalExam: payload.physicalExam,
    preliminaryDiagnosis: payload.preliminaryDiagnosis,
    treatmentAdvice: payload.treatmentAdvice,
    status: 'CONFIRMED',
  })
}
