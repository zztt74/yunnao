// 管理端类型定义
// 设计来源：product/11_功能需求.md §2/§4/§5/§13/§15/§16
//          product/12_业务流程与状态机.md §2/§3/§11/§13

import type { UserRole } from '@/types/auth'
import type { Gender } from '@/types/patient'

// ============================================================
// §2 用户认证与权限管理
// ============================================================

export type UserStatus = 'ENABLED' | 'DISABLED' | 'LOCKED'

export interface UserManageResponse {
  id: number
  username: string
  realName: string
  roles: UserRole[]
  status: UserStatus
  phone: string
  email: string
  // 关联实体 ID（按角色）
  patientId?: number
  doctorId?: number
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface UserCreateRequest {
  username: string
  password: string
  realName: string
  roles: UserRole[]
  phone: string
  email?: string
  departmentId?: number
  doctorName?: string
  doctorTitle?: string
  specialty?: string
  education?: string
  experienceYears?: number
  introduction?: string
}

export interface UserUpdateRequest {
  realName?: string
  roles?: UserRole[]
  phone?: string
  email?: string
}

export interface UserStatusChangeRequest {
  status: UserStatus
  reason: string
}

export interface ResetPasswordRequest {
  newPassword: string
}

// ============================================================
// §4 科室管理
// ============================================================

export type DepartmentStatus = 'ACTIVE' | 'INACTIVE'

export interface DepartmentResponse {
  id: number
  code: string
  name: string
  description: string
  status: DepartmentStatus
  doctorCount: number
  createdAt: string
  updatedAt: string
}

export interface DepartmentCreateRequest {
  code: string
  name: string
  description?: string
}

export interface DepartmentUpdateRequest {
  name?: string
  description?: string
}

// ============================================================
// §4 医生管理
// ============================================================

export type DoctorManageStatus = 'ACTIVE' | 'DISABLED'

export interface DoctorManageResponse {
  id: number
  userId: number
  username: string
  name: string
  title: string
  departmentId: number
  departmentName: string
  gender: Gender
  phone: string
  email: string
  specialty: string
  introduction: string
  status: DoctorManageStatus
  scheduleCount: number
  encounterCount: number
  createdAt: string
  updatedAt: string
}

export interface DoctorCreateRequest {
  username: string
  password: string
  name: string
  title: string
  departmentId: number
  gender: Gender
  phone: string
  email?: string
  specialty?: string
  introduction?: string
}

export interface DoctorUpdateRequest {
  name?: string
  title?: string
  departmentId?: number
  phone?: string
  email?: string
  specialty?: string
  introduction?: string
}

// ============================================================
// §5 排班管理
// ============================================================

export interface ScheduleCreateRequest {
  doctorId: number
  departmentId: number
  scheduleDate: string
  startTime: string // HH:mm
  endTime: string // HH:mm
  maxAppointments: number
}

export interface ScheduleCancelRequest {
  reason: string
}

// §5.3 修改未开始排班（仅 AVAILABLE 状态可改；maxAppointments 不得低于已约数）
export interface ScheduleUpdateRequest {
  scheduleDate?: string
  startTime?: string // HH:mm
  endTime?: string // HH:mm
  maxAppointments?: number
}

// ============================================================
// §15 统计驾驶舱
// ============================================================

export interface StatisticsSummary {
  todayAppointments: number
  todayCompletedEncounters: number
  todayActiveDoctors: number
  todayAvailableDevices: number
  todayHighPriorityTriages: number
  totalPatients: number
  totalDoctors: number
  totalDepartments: number
  totalDevices: number
}

export interface StatisticsTrendItem {
  date: string
  appointments: number
  completedEncounters: number
}

export interface DepartmentStatItem {
  departmentId: number
  departmentName: string
  appointmentCount: number
  encounterCount: number
}

export interface DoctorRankingItem {
  doctorId: number
  doctorName: string
  departmentName: string
  encounterCount: number
}

export interface DeviceUsageStatItem {
  deviceId: number
  deviceName: string
  deviceCode: string
  totalUsageCount: number
  totalUsageDuration: number // 分钟
  utilizationRate: number // 百分比
}

export interface AiCallStatItem {
  totalCalls: number
  successCount: number
  failureCount: number
  averageDuration: number // 毫秒
  byType: { type: string; count: number; successCount: number }[]
}

export interface StatisticsQuery {
  startDate?: string
  endDate?: string
  departmentId?: number
}

// ============================================================
// §16 日志查询
// ============================================================

export interface LoginLog {
  id: number
  userId: number
  username: string
  role: UserRole
  loginTime: string
  ip: string
  success: boolean
  failReason: string | null
}

export interface OperationLog {
  id: number
  operatorId: number
  operatorName: string
  action: string
  targetType: string
  targetId: number | null
  detail: string
  operatedAt: string
}

export interface AiInvocationLog {
  id: number
  callType: string
  provider: string
  model: string
  businessType: string
  businessId: number | null
  success: boolean
  duration: number // 毫秒
  errorType: string | null // 错误类型（timeout/auth_failure/quota_exceeded/model_error 等）
  errorMessage: string | null
  attemptCount: number
  operatorId: number | null
  calledAt: string
}

export interface AiInvocationAttempt {
  id: number
  invocationId: number
  provider: string
  model: string
  promptVersion: string | null
  status: string
  httpStatus: number | null
  errorType: string | null
  errorMessage: string | null
  requestSummary: string | null
  responseSummary: string | null
  duration: number | null
  attemptIndex: number
  startedAt: string
  finishedAt: string | null
}

// ============================================================
// 通用查询参数
// ============================================================

export interface PageQuery {
  page: number
  pageSize: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}
