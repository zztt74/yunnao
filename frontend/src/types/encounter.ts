// 就诊模块类型
// 契约来源：contracts/openapi.yaml（就诊接口）+ contracts/schemas/triage_encounter.yaml
// 业务基线：product/12_业务流程与状态机.md §6 就诊状态、§7 诊断类型与来源

// 就诊状态（与状态机 §6 对齐）
export type EncounterStatus =
  | 'CREATED'
  | 'IN_PROGRESS'
  | 'WAITING_EXAM'
  | 'COMPLETED'
  | 'CANCELLED'

// 诊断类型
export type DiagnosisType = 'PRELIMINARY' | 'FINAL'

// 诊断来源
export type DiagnosisSource = 'DOCTOR' | 'AI_SUGGESTION'

// 开始接诊请求（对应 OpenAPI EncounterStartRequest）
export interface EncounterStartRequest {
  appointmentId: number
}

// 取消就诊请求（对应 OpenAPI EncounterCancelRequest）
export interface EncounterCancelRequest {
  reason?: string
}

// 诊断请求（对应 OpenAPI EncounterDiagnosisRequest）
export interface EncounterDiagnosisRequest {
  diagnosisCode: string
  diagnosisName: string
  type: DiagnosisType
  source: DiagnosisSource
  notes?: string
}

// 诊断响应（对应 OpenAPI EncounterDiagnosisResponse）
export interface EncounterDiagnosisResponse {
  id: number
  encounterId: number
  diagnosisCode: string
  diagnosisName: string
  type: DiagnosisType
  source: DiagnosisSource
  aiInvocationId?: number | null
  doctorId?: number | null
  confirmedAt?: string | null
  notes?: string | null
  createdAt: string
  updatedAt: string
}

// 就诊响应（对应 OpenAPI EncounterResponse）
export interface EncounterResponse {
  id: number
  appointmentId: number
  patientId: number
  patientName: string
  doctorId: number
  doctorName: string
  departmentId: number
  departmentName: string
  status: EncounterStatus
  startedAt?: string | null
  waitingExamAt?: string | null
  completedAt?: string | null
  cancelledAt?: string | null
  cancelReason?: string | null
  createdAt: string
  updatedAt: string
}

// AI 辅助诊断请求（医生触发，提交问诊上下文，返回候选诊断）
export interface AiDiagnosisRequest {
  encounterId: number
  chiefComplaint: string
  presentIllness?: string
  pastHistory?: string
  physicalExam?: string
}

// AI 候选诊断条目（仅 source=AI_SUGGESTION，type=PRELIMINARY）
export interface AiCandidateDiagnosis {
  diagnosisCode: string
  diagnosisName: string
  reason: string
  confidence: number // 0-1
  riskFactors: string[]
  informationGaps: string[]
  recommendedExaminations: string[]
}

// AI 辅助诊断响应
export interface AiDiagnosisResponse {
  encounterId: number
  candidates: AiCandidateDiagnosis[]
  aiStatus: 'SUCCESS' | 'FAILED'
  aiFailureReason?: string
}
