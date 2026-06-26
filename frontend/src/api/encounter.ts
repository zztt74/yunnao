// 就诊模块 API（医生端）
// 契约来源：contracts/openapi.yaml 就诊接口（/api/encounters/*）
// 当前后端未就绪，使用本地 MOCK 实现（与患者端 mock 模式一致）
// 后端就绪后请用真实调用替换，并删除 doctor-mock 引用

import type {
  EncounterResponse,
  EncounterStartRequest,
  EncounterCancelRequest,
  EncounterDiagnosisRequest,
  EncounterDiagnosisResponse,
  AiDiagnosisRequest,
  AiDiagnosisResponse,
} from '@/types/encounter'
import {
  startEncounter as mockStart,
  waitForExam as mockWait,
  resumeEncounter as mockResume,
  completeEncounter as mockComplete,
  cancelEncounter as mockCancel,
  getEncounterById as mockGetById,
  getEncounterByAppointmentId as mockGetByAppt,
  getDoctorEncounters as mockGetByDoctor,
  getDoctorPendingAppointments as mockPending,
  getDoctorInprogressAppointments as mockInprogress,
  getDoctorTodayAppointments as mockToday,
  getEncounterDiagnoses as mockGetDiags,
  addAIDiagnosis as mockAddAiDiag,
  addDoctorDiagnosis as mockAddDoctorDiag,
  generateAiDiagnosis as mockGenAiDiag,
  getCurrentDoctorId,
  getAppointmentById,
  shouldSimulateAiFailure,
} from '@/api/mock/doctor-mock'
import type { AppointmentResponse } from '@/types/appointment'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 查询医生待诊队列（BOOKED 挂号） */
export async function getDoctorPendingQueue(doctorId?: number): Promise<AppointmentResponse[]> {
  console.warn('[MOCK] /api/appointments/doctor/{id}/pending 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockPending(doctorId ?? getCurrentDoctorId())
}

/** 查询医生进行中/等待检查的挂号（继续接诊入口） */
export async function getDoctorActiveAppointments(doctorId?: number): Promise<AppointmentResponse[]> {
  console.warn('[MOCK] /api/appointments/doctor/{id}（进行中）后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockInprogress(doctorId ?? getCurrentDoctorId())
}

/** 查询医生今日挂号 */
export async function getDoctorTodayAppointments(doctorId?: number): Promise<AppointmentResponse[]> {
  await delay(300)
  return mockToday(doctorId ?? getCurrentDoctorId())
}

/** 查询医生就诊列表 */
export async function getDoctorEncounters(doctorId?: number): Promise<EncounterResponse[]> {
  console.warn('[MOCK] /api/encounters/doctor/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockGetByDoctor(doctorId ?? getCurrentDoctorId())
}

/** 开始接诊（CREATED → IN_PROGRESS，同步挂号） */
export async function startPatientEncounter(
  payload: EncounterStartRequest,
): Promise<EncounterResponse> {
  console.warn('[MOCK] /api/encounters/start 后端未就绪，使用本地虚构演示数据')
  await delay(500)
  return mockStart(payload.appointmentId, getCurrentDoctorId())

  // 后端就绪后替换为：
  // const res = await apiClient.post('/encounters/start', payload)
  // return parseApiResponse(res.data)
}

/** 等待检查（IN_PROGRESS → WAITING_EXAM） */
export async function waitForExam(encounterId: number): Promise<EncounterResponse> {
  console.warn('[MOCK] /api/encounters/{id}/wait-exam 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockWait(encounterId)
}

/** 继续诊疗（WAITING_EXAM → IN_PROGRESS） */
export async function resumeEncounter(encounterId: number): Promise<EncounterResponse> {
  console.warn('[MOCK] /api/encounters/{id}/resume 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockResume(encounterId)
}

/** 完成就诊（IN_PROGRESS → COMPLETED，前置条件校验） */
export async function completeEncounter(encounterId: number): Promise<EncounterResponse> {
  console.warn('[MOCK] /api/encounters/{id}/complete 后端未就绪，使用本地虚构演示数据')
  await delay(500)
  return mockComplete(encounterId)
}

/** 取消就诊（仅 CREATED → CANCELLED） */
export async function cancelEncounter(
  encounterId: number,
  payload?: EncounterCancelRequest,
): Promise<EncounterResponse> {
  console.warn('[MOCK] /api/encounters/{id}/cancel 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockCancel(encounterId, payload?.reason)
}

/** 获取就诊详情 */
export async function getEncounterById(id: number): Promise<EncounterResponse> {
  console.warn('[MOCK] /api/encounters/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  const enc = mockGetById(id)
  if (!enc) throw new Error('就诊不存在')
  return enc
}

/** 按挂号 ID 查询就诊 */
export async function getEncounterByAppointmentId(appointmentId: number): Promise<EncounterResponse | null> {
  console.warn('[MOCK] /api/encounters/appointment/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetByAppt(appointmentId) ?? null
}

/** 查询就诊诊断列表 */
export async function getEncounterDiagnoses(encounterId: number): Promise<EncounterDiagnosisResponse[]> {
  console.warn('[MOCK] /api/encounters/{id}/diagnoses 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetDiags(encounterId)
}

/** 添加 AI 候选诊断（PRELIMINARY + AI_SUGGESTION） */
export async function addAIDiagnosis(
  encounterId: number,
  payload: EncounterDiagnosisRequest,
): Promise<EncounterDiagnosisResponse> {
  console.warn('[MOCK] /api/encounters/{id}/diagnoses/ai 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockAddAiDiag(encounterId, {
    diagnosisCode: payload.diagnosisCode,
    diagnosisName: payload.diagnosisName,
    notes: payload.notes,
  })
}

/** 添加医生最终诊断（FINAL + DOCTOR） */
export async function addDoctorDiagnosis(
  encounterId: number,
  payload: EncounterDiagnosisRequest,
): Promise<EncounterDiagnosisResponse> {
  console.warn('[MOCK] /api/encounters/{id}/diagnoses/doctor 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockAddDoctorDiag(encounterId, getCurrentDoctorId(), {
    diagnosisCode: payload.diagnosisCode,
    diagnosisName: payload.diagnosisName,
    notes: payload.notes,
  })
}

/**
 * AI 辅助诊断（§9）：根据问诊上下文生成候选诊断
 * - AI 输出仅为候选建议，不写入正式诊断
 * - 医生可采纳（持久化为 AI_SUGGESTION）并补充医生最终诊断
 * - AI 失败时返回降级标记，医生可手工诊断（§9.5、§14）
 */
export async function assistDiagnosis(payload: AiDiagnosisRequest): Promise<AiDiagnosisResponse> {
  console.warn('[MOCK] /api/ai/assist-diagnosis 后端未就绪，使用本地虚构演示数据')
  await delay(1500)
  if (shouldSimulateAiFailure()) {
    sessionStorage.removeItem('cloud-brain.mock-ai-fail')
    return {
      encounterId: payload.encounterId,
      candidates: [],
      aiStatus: 'FAILED',
      aiFailureReason: 'AI_PROVIDER_UNAVAILABLE：AI 服务暂不可用，请进行手工诊断。',
    }
  }
  const result = mockGenAiDiag({
    encounterId: payload.encounterId,
    chiefComplaint: payload.chiefComplaint,
    presentIllness: payload.presentIllness,
    pastHistory: payload.pastHistory,
    physicalExam: payload.physicalExam,
  })
  return {
    encounterId: payload.encounterId,
    candidates: result.candidates,
    aiStatus: result.aiStatus,
  }
}

/** 获取挂号详情（医生端：接诊前查看） */
export async function getDoctorAppointmentById(id: number): Promise<AppointmentResponse> {
  await delay(200)
  const appt = getAppointmentById(id)
  if (!appt) throw new Error('挂号不存在')
  return appt
}
