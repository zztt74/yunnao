// 病历状态
export type MedicalRecordStatus =
  | 'DRAFT'
  | 'AI_GENERATED'
  | 'CONFIRMED'
  | 'AMENDED'

// 诊断类型
export type DiagnosisType = 'PRELIMINARY' | 'FINAL'

// 诊断来源
export type DiagnosisSource = 'DOCTOR' | 'AI_SUGGESTION'

// 诊断
export interface Diagnosis {
  id: number
  type: DiagnosisType
  source: DiagnosisSource
  diagnosisCode?: string
  diagnosisName: string
  description?: string
  createdAt: string
}

// 病历（与设计文档 §11、§14 对齐）
export interface MedicalRecord {
  id: number
  encounterId: number
  patientId: number
  doctorId: number
  doctorName: string
  departmentName: string
  // 主诉
  chiefComplaint: string
  // 现病史
  presentIllness: string
  // 既往史
  pastHistory?: string
  // 体格检查
  physicalExam?: string
  // 初步诊断
  preliminaryDiagnosis?: string
  // 治疗建议
  treatmentAdvice?: string
  // 状态
  status: MedicalRecordStatus
  // 诊断列表
  diagnoses: Diagnosis[]
  // 关联就诊时间
  encounterDate: string
  // 确认时间
  confirmedAt?: string | null
  // 备注：医生内部备注、修订原因等，患者端不展示 internalNote
  createdAt: string
  updatedAt: string
}

// ===== 医生端：病历生成与编辑 =====

// AI 生成病历请求（医生提交问诊上下文）
export interface MedicalRecordAiRequest {
  encounterId: number
  chiefComplaint: string
  presentIllness?: string
  pastHistory?: string
  physicalExam?: string
  // 医患问诊对话原文，用于 AI 生成结构化病历
  consultationTranscript?: string
  // 已有的医生最终诊断（用于回填初步诊断）
  diagnoses?: string[]
}

// AI 生成病历响应（草稿，状态 AI_GENERATED，医生必须确认）
export interface MedicalRecordAiResponse {
  encounterId: number
  chiefComplaint: string
  presentIllness: string
  pastHistory: string
  physicalExam: string
  preliminaryDiagnosis: string
  treatmentAdvice: string
  aiStatus: 'SUCCESS' | 'FAILED'
  aiFailureReason?: string
}

// 医生保存病历草稿/确认请求
export interface MedicalRecordSaveRequest {
  encounterId: number
  chiefComplaint: string
  presentIllness: string
  pastHistory?: string
  physicalExam?: string
  preliminaryDiagnosis?: string
  treatmentAdvice?: string
  status: MedicalRecordStatus
}
