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
