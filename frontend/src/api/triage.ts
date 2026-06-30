import { apiClient } from '@/api/client'
import { getPatientInfo } from '@/api/patient'
import { parseApiResponse } from '@/api/response'
import type { PageResponse } from '@/types/api'
import type {
  TriageConsultRequest,
  TriagePriority,
  TriageResultResponse,
} from '@/types/triage'

interface TriageAnalyzeResponse {
  triageRecordId: number
  patientId: number
  symptoms: string
  duration?: string | null
  supplement?: string | null
  aiDepartmentCode?: string | null
  aiPriority?: TriagePriority | null
  aiReason?: string | null
  aiSafetyNotice?: string | null
  aiEmergencySuggested?: boolean | null
  aiStatus?: string | null
  aiFailureReason?: string | null
  mappedDepartmentId?: number | null
  mappedDepartmentName?: string | null
  mappingStatus?: string | null
  createdAt: string
}

interface TriageRecordApiResponse {
  id: number
  patientId: number
  symptoms: string
  duration?: string | null
  supplement?: string | null
  aiDepartmentCode?: string | null
  aiPriority?: TriagePriority | null
  aiReason?: string | null
  aiSafetyNotice?: string | null
  aiEmergencySuggested?: boolean | null
  mappedDepartmentId?: number | null
  mappingStatus?: string | null
  aiStatus?: string | null
  aiFailureReason?: string | null
  createdAt: string
}

/**
 * 多轮追问的请求载荷。
 * 后端当前冻结接口只接收本轮症状、持续时间和补充信息，history 保留给页面状态使用。
 */
export interface TriageConsultWithHistoryRequest extends TriageConsultRequest {
  history?: TriageTurn[]
}

export interface TriageTurn {
  role: 'user' | 'ai'
  text: string
  meta?: {
    followUpQuestion?: string
    reason?: string
  }
}

async function getCurrentPatientId(defaultPatientId: number): Promise<number> {
  try {
    return (await getPatientInfo()).id
  } catch {
    return defaultPatientId
  }
}

function mapPriority(priority?: TriagePriority | null): TriagePriority {
  return priority ?? 'LOW'
}

function mapAnalyzeResponse(response: TriageAnalyzeResponse): TriageResultResponse {
  return {
    id: response.triageRecordId,
    patientId: response.patientId,
    recommendedDepartmentId: response.mappedDepartmentId ?? 0,
    recommendedDepartmentName: response.mappedDepartmentName ?? '待人工选择',
    priority: mapPriority(response.aiPriority),
    reason: response.aiFailureReason ?? response.aiReason ?? 'AI 分诊未返回推荐理由',
    safetyAdvice: response.aiSafetyNotice ?? 'AI 分诊仅供辅助参考，请以医生判断为准。',
    emergencyAdvice: response.aiEmergencySuggested ? '症状存在急诊风险，请优先急诊处理。' : undefined,
    followUpQuestion: response.mappingStatus === 'MANUAL' ? '请选择合适科室继续挂号。' : undefined,
    createdAt: response.createdAt,
  }
}

function mapRecordResponse(response: TriageRecordApiResponse): TriageResultResponse {
  return {
    id: response.id,
    patientId: response.patientId,
    recommendedDepartmentId: response.mappedDepartmentId ?? 0,
    recommendedDepartmentName: response.aiDepartmentCode ?? '待人工选择',
    priority: mapPriority(response.aiPriority),
    reason: response.aiFailureReason ?? response.aiReason ?? response.symptoms,
    safetyAdvice: response.aiSafetyNotice ?? 'AI 分诊仅供辅助参考，请以医生判断为准。',
    emergencyAdvice: response.aiEmergencySuggested ? '症状存在急诊风险，请优先急诊处理。' : undefined,
    createdAt: response.createdAt,
  }
}

export async function consultTriage(
  payload: TriageConsultWithHistoryRequest,
  patientId = 1,
): Promise<TriageResultResponse> {
  const res = await apiClient.post('/triage/analyze', {
    patientId: await getCurrentPatientId(patientId),
    symptoms: payload.chiefComplaint,
    duration: payload.duration,
    supplement: payload.additionalInfo,
  })
  return mapAnalyzeResponse(parseApiResponse<TriageAnalyzeResponse>(res.data))
}

export async function getMyTriageRecords(
  patientId = 1,
): Promise<TriageResultResponse[]> {
  const res = await apiClient.get(`/triage/patient/${await getCurrentPatientId(patientId)}`)
  const page = parseApiResponse<PageResponse<TriageRecordApiResponse>>(res.data)
  return page.items.map(mapRecordResponse)
}

export async function getTriageRecordById(
  id: number,
): Promise<TriageResultResponse> {
  const res = await apiClient.get(`/triage/${id}`)
  return mapRecordResponse(parseApiResponse<TriageRecordApiResponse>(res.data))
}
