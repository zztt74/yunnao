// 处方状态（与状态机 §9 对齐）
export type PrescriptionStatus = 'DRAFT' | 'CONFIRMED' | 'VOIDED'

// AI 审核状态
export type PrescriptionAiReviewStatus =
  | 'NOT_REQUESTED'
  | 'PENDING'
  | 'REVIEWED'
  | 'FAILED'

// AI 风险等级
export type PrescriptionRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

// 处方药品明细
export interface PrescriptionItem {
  id: number
  drugId: number
  drugCode: string
  drugName: string
  strength: string // 规格
  unit: string
  dosage: string // 剂量
  frequency: string // 频次
  usage: string // 用法
  duration: string // 疗程
  remark?: string
}

// AI 审核提示
export interface PrescriptionAiReview {
  riskLevel: PrescriptionRiskLevel
  warnings: string[]
  advice?: string
  reviewedAt: string
}

// 处方（与设计文档 §12 对齐）
export interface PrescriptionResponse {
  id: number
  encounterId: number
  patientId: number
  patientName: string
  doctorId: number
  doctorName: string
  departmentName: string
  diagnosis: string
  items: PrescriptionItem[]
  status: PrescriptionStatus
  // 作废原因（VOIDED 时填写）
  voidedReason?: string | null
  voidedAt?: string | null
  // 备注
  remark?: string
  // AI 审核结果（仅医生确认后展示，且去除提示词原文）
  aiReview?: PrescriptionAiReview | null
  // AI 审核状态
  aiReviewStatus: PrescriptionAiReviewStatus
  confirmedAt?: string | null
  createdAt: string
  updatedAt: string
}

// ===== 医生端：处方开立与审核 =====

// 处方药品明细录入项
export interface PrescriptionItemRequest {
  drugId: number
  drugCode: string
  drugName: string
  strength: string
  unit: string
  dosage: string
  frequency: string
  usage: string
  duration: string
  remark?: string
}

// 医生创建/更新处方草稿请求
export interface PrescriptionSaveRequest {
  encounterId: number
  diagnosis: string
  items: PrescriptionItemRequest[]
  remark?: string
}
