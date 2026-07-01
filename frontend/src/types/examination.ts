// 检查/检验状态（与状态机 §10 对齐）
export type ExaminationStatus =
  | 'ORDERED'
  | 'IN_PROGRESS'
  | 'RESULT_ENTERED'
  | 'REVIEWED'
  | 'CANCELLED'

// 检查/检验类型
export type ExaminationType = 'EXAMINATION' | 'LABORATORY'

// 检验指标
export interface LabItem {
  id: number
  itemName: string
  resultValue: string
  unit: string
  referenceRange: string
  abnormalFlag: 'NORMAL' | 'HIGH' | 'LOW'
}

// 检查/检验申请（与设计文档 §10 对齐）
export interface ExaminationResponse {
  id: number
  encounterId: number
  patientId: number
  doctorId: number
  doctorName: string
  departmentName: string
  type: ExaminationType
  // 项目名称（如 血常规 / 胸部 X 光）
  itemName: string
  // 申请目的/备注
  purpose: string
  // 申请时间
  orderedAt: string
  // 报告时间
  reportedAt?: string | null
  // 审核时间
  reviewedAt?: string | null
  // 报告医生
  reporterName?: string | null
  // 面向患者的流程引导
  departmentLocation?: string | null
  nextAction?: string | null
  deviceName?: string | null
  deviceLocation?: string | null
  cancelledAt?: string | null
  cancelReason?: string | null
  // 状态
  status: ExaminationStatus
  // 检验指标（仅检验）
  labItems?: LabItem[]
  // 检查所见/影像描述（仅检查）
  findings?: string
  // 印象/结论
  impression?: string
  // AI 解读（仅 REVIEWED 后展示）
  aiInterpretation?: string
  createdAt: string
  updatedAt: string
}

// ===== 医生端：检查检验开立 =====

// 医生开立检查检验申请
export interface ExaminationCreateRequest {
  encounterId: number
  type: ExaminationType
  itemName: string
  purpose?: string
}
