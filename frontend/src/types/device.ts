// 医疗设备类型定义
// 设计来源：product/11_功能需求.md §13 医疗设备管理、product/12_业务流程与状态机.md
// §13.6：每次设备状态变化必须记录来源状态、目标状态、操作人、时间和原因

// 设备状态（§13.6）
// 基础版本：AVAILABLE / IN_USE / ABNORMAL / DISABLED
// 扩展版本：MAINTENANCE（维修）
export type DeviceStatus = 'AVAILABLE' | 'IN_USE' | 'ABNORMAL' | 'MAINTENANCE' | 'DISABLED'

// 设备分类
export type DeviceCategory =
  | 'EXAMINATION' // 检查设备（X 光、B 超等）
  | 'LABORATORY' // 检验设备
  | 'MONITOR' // 监护设备
  | 'OTHER'

// 设备使用记录状态
export type DeviceUsageStatus = 'IN_USE' | 'COMPLETED' | 'ABNORMAL'

// 设备状态历史（§13.6：每次状态变化必须记录）
export interface DeviceStatusHistory {
  id: number
  deviceId: number
  // 来源状态（创建时第一条为 null）
  fromStatus: DeviceStatus | null
  toStatus: DeviceStatus
  operatorId: number
  operatorName: string
  reason?: string
  changedAt: string
}

// 设备（§13.3）
// 字段对齐后端 B2 DeviceCreateRequest/DeviceUpdateRequest/BackendDeviceResponse：
// code/name/type/departmentId/location/manufacturer/model/serialNumber/notes/purchaseDate/warrantyUntil/status。
// category 由后端 type 派生；applicableItems 保留兼容旧 UI 但不再用于提交载荷。
export interface DeviceResponse {
  id: number
  code: string
  name: string
  category: DeviceCategory
  status: DeviceStatus
  location: string
  enabled: boolean
  // 适配的检查检验项目（旧字段，保留用于列表展示；后端无独立字段，由 type/notes 派生）
  applicableItems: string[]
  // 后端 B2 真实字段
  type: string
  departmentId: number | null
  departmentName?: string
  manufacturer: string
  model: string
  serialNumber: string
  notes: string
  purchaseDate: string | null
  warrantyUntil: string | null
  createdAt: string
  updatedAt: string
}

// 设备使用记录（§13.3）
export interface DeviceUsageResponse {
  id: number
  deviceId: number
  deviceName: string
  deviceCode: string
  // 关联就诊（§13.6：使用记录关联 Encounter 或检查申请）
  encounterId?: number
  examinationId?: number
  patientId?: number
  patientName?: string
  doctorId: number
  doctorName: string
  purpose?: string
  startedAt: string
  endedAt?: string | null
  result?: string
  // 设备结束后状态（§13.4：恢复空闲或转为故障）
  endDeviceStatus?: DeviceStatus
  status: DeviceUsageStatus
  createdAt: string
  updatedAt: string
}

// 创建设备使用记录请求
export interface DeviceUsageCreateRequest {
  deviceId: number
  encounterId?: number
  examinationId?: number
  patientId?: number
  patientName?: string
  purpose?: string
}

// 结束使用请求
export interface DeviceUsageEndRequest {
  result?: string
  // 设备结束后的状态：恢复正常或转为故障（§13.4）
  deviceEndStatus?: 'AVAILABLE' | 'MAINTENANCE'
}
