export type Gender = 'MALE' | 'FEMALE'

export type PatientStatus = 'ACTIVE' | 'INACTIVE'

export interface PatientRegisterRequest {
  username: string
  password: string
  name: string
  gender: Gender
  birthDate: string
  phone: string
}

export interface PatientUpdateRequest {
  name: string
  gender: Gender
  birthDate: string
  phone: string
}

export interface PatientProfileUpdateRequest {
  address?: string
  emergencyContact?: string
  emergencyPhone?: string
  allergies?: string
  medicalHistory?: string
}

export interface PatientResponse {
  id: number
  userId: number
  name: string
  gender: Gender
  birthDate: string
  phone: string
  status: PatientStatus
  createdAt: string
  updatedAt: string
}

export interface PatientProfileResponse {
  id: number
  patientId: number
  address: string
  emergencyContact: string
  emergencyPhone: string
  allergies: string
  medicalHistory: string
  createdAt: string
  updatedAt: string
}

// ===== 医生端：患者详情与诊疗时间线（§3.3、§3.4）=====

// 诊疗时间线条目类型（§3.4 诊疗时间线）
export type TimelineEntryType =
  | 'TRIAGE' // 分诊记录
  | 'APPOINTMENT' // 挂号记录
  | 'ENCOUNTER' // 就诊记录
  | 'EXAMINATION' // 检查检验
  | 'MEDICAL_RECORD' // 电子病历
  | 'PRESCRIPTION' // 处方

// 诊疗时间线条目
export interface PatientTimelineEntry {
  id: number
  type: TimelineEntryType
  title: string
  description: string
  occurredAt: string
  // 关联就诊 ID（医生端可跳转工作台）
  encounterId?: number
  // 关联资源 ID（病历/处方/检查检验的主键）
  resourceId?: number
  // 状态摘要
  statusLabel?: string
}

// 患者详情（医生端，§3.3：接诊关系成立后医生可见必要信息）
export interface PatientDetailResponse {
  id: number
  name: string
  gender: Gender
  birthDate: string
  age: number
  phone: string
  // 扩展档案（§3.3 过敏史、既往史等）
  allergies: string
  medicalHistory: string
  address: string
  emergencyContact: string
  emergencyPhone: string
  createdAt: string
}
