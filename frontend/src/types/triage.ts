// AI 分诊优先级
export type TriagePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'EMERGENCY'

// AI 智能问诊请求
export interface TriageConsultRequest {
  chiefComplaint: string // 主诉症状描述（必填）
  duration?: string // 持续时间
  additionalInfo?: string // 补充信息
}

// AI 智能问诊响应
export interface TriageResultResponse {
  id: number
  patientId: number
  recommendedDepartmentId: number
  recommendedDepartmentName: string
  priority: TriagePriority
  reason: string // 推荐理由
  safetyAdvice: string // 安全提示
  emergencyAdvice?: string // 急诊建议（HIGH/EMERGENCY 时）
  followUpQuestion?: string // AI 追问（可选）
  createdAt: string // ISO 8601 +08:00
}

// 管理端分诊记录扩展视图（补充患者姓名、症状、AI 摘要）
export interface AdminTriageRecord extends TriageResultResponse {
  patientName: string
  symptoms: string
  aiSummary: string
}

// 对齐后端 B4 GET /api/triage 查询参数：
// patientId/priority/departmentId/startDate/endDate/page/pageSize
export interface AdminTriageQuery {
  patientId?: number
  priority?: TriagePriority
  departmentId?: number
  startDate?: string // ISO yyyy-MM-dd
  endDate?: string
  page?: number
  pageSize?: number
}
