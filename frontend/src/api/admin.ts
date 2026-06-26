// 管理端 API
// 设计来源：product/11_功能需求.md §2/§3/§4/§5/§6/§7/§13/§15/§16
//          product/12_业务流程与状态机.md §2/§3/§11/§13
// 后端接口未就绪，使用本地 MOCK 实现（与 doctor.ts、encounter.ts 模式一致）
// 后端就绪后请用真实调用替换，并删除 admin-mock 引用

import type {
  DepartmentResponse,
  DepartmentCreateRequest,
  DepartmentUpdateRequest,
  DepartmentStatus,
  UserManageResponse,
  UserCreateRequest,
  UserUpdateRequest,
  UserStatusChangeRequest,
  ResetPasswordRequest,
  UserStatus,
  DoctorManageResponse,
  DoctorCreateRequest,
  DoctorUpdateRequest,
  DoctorManageStatus,
  ScheduleCreateRequest,
  ScheduleCancelRequest,
  ScheduleUpdateRequest,
  StatisticsSummary,
  StatisticsTrendItem,
  DepartmentStatItem,
  DoctorRankingItem,
  DeviceUsageStatItem,
  AiCallStatItem,
  StatisticsQuery,
  LoginLog,
  OperationLog,
  AiInvocationLog,
  PageResult,
} from '@/types/admin'
import type {
  ScheduleResponse,
  AppointmentResponse,
  AppointmentStatus,
} from '@/types/appointment'
import type {
  DeviceResponse,
  DeviceStatus,
  DeviceStatusHistory,
} from '@/types/device'
import type { PatientDetailResponse } from '@/types/patient'
import type { AdminTriageRecord } from '@/types/triage'
import type { UserRole } from '@/types/auth'
import {
  getMockDepartments,
  getMockDepartmentById,
  createMockDepartment,
  updateMockDepartment,
  changeMockDepartmentStatus,
  getMockUsers,
  createMockUser,
  updateMockUser,
  changeMockUserStatus,
  resetMockUserPassword,
  getMockDoctors,
  getMockDoctorById,
  createMockDoctor,
  updateMockDoctor,
  changeMockDoctorStatus,
  getMockSchedules,
  createMockSchedule,
  cancelMockSchedule,
  updateMockSchedule,
  getMockAppointments,
  getMockPatients,
  getMockDevices,
  createMockDevice,
  updateMockDevice,
  changeMockDeviceStatus,
  getMockDeviceStatusHistory,
  getMockTriages,
  getMockStatisticsSummary,
  getMockStatisticsTrend,
  getMockDepartmentStats,
  getMockDoctorRanking,
  getMockDeviceUsageStats,
  getMockAiCallStats,
  getMockLoginLogs,
  getMockOperationLogs,
  getMockAiInvocationLogs,
} from '@/api/mock/admin-mock'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// ============================================================
// §4.3 科室管理
// ============================================================

/** 查询全部科室（§4.3） */
export async function getDepartments(): Promise<DepartmentResponse[]> {
  console.warn('[MOCK] /api/admin/departments 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDepartments()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/departments')
  // return parseApiResponse(res.data)
}

/** 按 ID 查询科室（§4.3） */
export async function getDepartmentById(id: number): Promise<DepartmentResponse> {
  console.warn('[MOCK] /api/admin/departments/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDepartmentById(id)!

  // 后端就绪后替换为：
  // const res = await apiClient.get(`/admin/departments/${id}`)
  // return parseApiResponse(res.data)
}

/** 新建科室（§4.3） */
export async function createDepartment(
  payload: DepartmentCreateRequest,
): Promise<DepartmentResponse> {
  console.warn('[MOCK] /api/admin/departments POST 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return createMockDepartment(payload)

  // 后端就绪后替换为：
  // const res = await apiClient.post('/admin/departments', payload)
  // return parseApiResponse(res.data)
}

/** 更新科室信息（§4.3） */
export async function updateDepartment(
  id: number,
  payload: DepartmentUpdateRequest,
): Promise<DepartmentResponse> {
  console.warn('[MOCK] /api/admin/departments/{id} PUT 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return updateMockDepartment(id, payload)!

  // 后端就绪后替换为：
  // const res = await apiClient.put(`/admin/departments/${id}`, payload)
  // return parseApiResponse(res.data)
}

/** 启用/停用科室（§4.3） */
export async function setDepartmentStatus(
  id: number,
  status: DepartmentStatus,
): Promise<DepartmentResponse> {
  console.warn('[MOCK] /api/admin/departments/{id}/status 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return changeMockDepartmentStatus(id, status)!

  // 后端就绪后替换为：
  // const res = await apiClient.patch(`/admin/departments/${id}/status`, { status })
  // return parseApiResponse(res.data)
}

// ============================================================
// §2.3 用户管理
// ============================================================

/** 查询用户列表（§2.3：支持按状态/角色/关键字筛选） */
export async function getUsers(query?: {
  status?: UserStatus
  role?: UserRole
  keyword?: string
}): Promise<UserManageResponse[]> {
  console.warn('[MOCK] /api/admin/users 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockUsers(query)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/users', { params: query })
  // return parseApiResponse(res.data)
}

/** 创建用户（§2.3） */
export async function createUser(
  payload: UserCreateRequest,
): Promise<UserManageResponse> {
  console.warn('[MOCK] /api/admin/users POST 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return createMockUser(payload)

  // 后端就绪后替换为：
  // const res = await apiClient.post('/admin/users', payload)
  // return parseApiResponse(res.data)
}

/** 更新用户信息（§2.3） */
export async function updateUser(
  id: number,
  payload: UserUpdateRequest,
): Promise<UserManageResponse> {
  console.warn('[MOCK] /api/admin/users/{id} PUT 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return updateMockUser(id, payload)!

  // 后端就绪后替换为：
  // const res = await apiClient.put(`/admin/users/${id}`, payload)
  // return parseApiResponse(res.data)
}

/** 变更用户状态（§2.3：启用/停用/锁定） */
export async function changeUserStatus(
  id: number,
  payload: UserStatusChangeRequest,
): Promise<UserManageResponse> {
  console.warn('[MOCK] /api/admin/users/{id}/status 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return changeMockUserStatus(id, payload)!!

  // 后端就绪后替换为：
  // const res = await apiClient.patch(`/admin/users/${id}/status`, payload)
  // return parseApiResponse(res.data)
}

/** 重置用户密码（§2.3） */
export async function resetUserPassword(
  id: number,
  payload: ResetPasswordRequest,
): Promise<void> {
  console.warn('[MOCK] /api/admin/users/{id}/reset-password 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  resetMockUserPassword(id, payload)

  // 后端就绪后替换为：
  // await apiClient.post(`/admin/users/${id}/reset-password`, payload)
}

// ============================================================
// §4.3 医生管理
// ============================================================

/** 查询医生列表（§4.3：支持按科室/状态/关键字筛选） */
export async function getDoctors(query?: {
  departmentId?: number
  status?: DoctorManageStatus
  keyword?: string
}): Promise<DoctorManageResponse[]> {
  console.warn('[MOCK] /api/admin/doctors 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDoctors(query)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/doctors', { params: query })
  // return parseApiResponse(res.data)
}

/** 按 ID 查询医生（§4.3） */
export async function getDoctorById(id: number): Promise<DoctorManageResponse> {
  console.warn('[MOCK] /api/admin/doctors/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDoctorById(id)!!

  // 后端就绪后替换为：
  // const res = await apiClient.get(`/admin/doctors/${id}`)
  // return parseApiResponse(res.data)
}

/** 创建医生（§4.3：同步创建关联用户账号） */
export async function createDoctor(
  payload: DoctorCreateRequest,
): Promise<DoctorManageResponse> {
  console.warn('[MOCK] /api/admin/doctors POST 后端未就绪，使用本地虚构演示数据')
  await delay(500)
  return createMockDoctor(payload)

  // 后端就绪后替换为：
  // const res = await apiClient.post('/admin/doctors', payload)
  // return parseApiResponse(res.data)
}

/** 更新医生信息（§4.3） */
export async function updateDoctor(
  id: number,
  payload: DoctorUpdateRequest,
): Promise<DoctorManageResponse> {
  console.warn('[MOCK] /api/admin/doctors/{id} PUT 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return updateMockDoctor(id, payload)!

  // 后端就绪后替换为：
  // const res = await apiClient.put(`/admin/doctors/${id}`, payload)
  // return parseApiResponse(res.data)
}

/** 启用/停用医生（§4.3） */
export async function setDoctorStatus(
  id: number,
  status: DoctorManageStatus,
): Promise<DoctorManageResponse> {
  console.warn('[MOCK] /api/admin/doctors/{id}/status 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return changeMockDoctorStatus(id, status)!!

  // 后端就绪后替换为：
  // const res = await apiClient.patch(`/admin/doctors/${id}/status`, { status })
  // return parseApiResponse(res.data)
}

// ============================================================
// §5 排班管理
// ============================================================

/** 查询排班列表（§5：支持按医生/科室/日期筛选） */
export async function getAdminSchedules(query?: {
  doctorId?: number
  departmentId?: number
  date?: string
}): Promise<ScheduleResponse[]> {
  console.warn('[MOCK] /api/admin/schedules 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockSchedules(query)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/schedules', { params: query })
  // return parseApiResponse(res.data)
}

/** 创建排班（§5） */
export async function createSchedule(
  payload: ScheduleCreateRequest,
): Promise<ScheduleResponse> {
  console.warn('[MOCK] /api/admin/schedules POST 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return createMockSchedule(payload)

  // 后端就绪后替换为：
  // const res = await apiClient.post('/admin/schedules', payload)
  // return parseApiResponse(res.data)
}

/** 取消排班（§5） */
export async function cancelSchedule(
  id: number,
  payload: ScheduleCancelRequest,
): Promise<ScheduleResponse> {
  console.warn('[MOCK] /api/admin/schedules/{id}/cancel 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return cancelMockSchedule(id, payload)!

  // 后端就绪后替换为：
  // const res = await apiClient.post(`/admin/schedules/${id}/cancel`, payload)
  // return parseApiResponse(res.data)
}

/** 修改未开始排班（§5.3：仅 AVAILABLE 可改，maxAppointments 不得低于已约数） */
export async function updateSchedule(
  id: number,
  payload: ScheduleUpdateRequest,
): Promise<ScheduleResponse> {
  console.warn('[MOCK] /api/admin/schedules/{id} PUT 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return updateMockSchedule(id, payload)!

  // 后端就绪后替换为：
  // const res = await apiClient.put(`/admin/schedules/${id}`, payload)
  // return parseApiResponse(res.data)
}

// ============================================================
// §7 挂号管理
// ============================================================

/** 查询挂号列表（§7：支持按状态/患者/医生/日期筛选） */
export async function getAdminAppointments(query?: {
  status?: AppointmentStatus
  patientId?: number
  doctorId?: number
  date?: string
}): Promise<AppointmentResponse[]> {
  console.warn('[MOCK] /api/admin/appointments 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockAppointments(query)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/appointments', { params: query })
  // return parseApiResponse(res.data)
}

// ============================================================
// §3.3 患者查询
// ============================================================

/** 分页查询患者（§3.3） */
export async function getAdminPatients(query?: {
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<PatientDetailResponse>> {
  console.warn('[MOCK] /api/admin/patients 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockPatients(query)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/patients', { params: query })
  // return parseApiResponse(res.data)
}

// ============================================================
// §13 设备管理
// ============================================================

/** 查询全部设备（§13） */
export async function getAdminDevices(): Promise<DeviceResponse[]> {
  console.warn('[MOCK] /api/admin/devices 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDevices()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/devices')
  // return parseApiResponse(res.data)
}

/** 创建设备（§13） */
export async function createDevice(
  payload: Partial<DeviceResponse>,
): Promise<DeviceResponse> {
  console.warn('[MOCK] /api/admin/devices POST 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return createMockDevice(payload)

  // 后端就绪后替换为：
  // const res = await apiClient.post('/admin/devices', payload)
  // return parseApiResponse(res.data)
}

/** 更新设备（§13） */
export async function updateDevice(
  id: number,
  payload: Partial<DeviceResponse>,
): Promise<DeviceResponse> {
  console.warn('[MOCK] /api/admin/devices/{id} PUT 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return updateMockDevice(id, payload)!!

  // 后端就绪后替换为：
  // const res = await apiClient.put(`/admin/devices/${id}`, payload)
  // return parseApiResponse(res.data)
}

/** 变更设备状态（§13.6：必须记录来源/目标/操作人/原因） */
export async function setDeviceStatus(
  id: number,
  status: DeviceStatus,
  reason: string,
): Promise<DeviceResponse> {
  console.warn('[MOCK] /api/admin/devices/{id}/status 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return changeMockDeviceStatus(id, status, reason)!!

  // 后端就绪后替换为：
  // const res = await apiClient.patch(`/admin/devices/${id}/status`, { status, reason })
  // return parseApiResponse(res.data)
}

/** 查询设备状态历史（§13.6） */
export async function getDeviceStatusHistory(
  id: number,
): Promise<DeviceStatusHistory[]> {
  console.warn('[MOCK] /api/admin/devices/{id}/status-history 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDeviceStatusHistory(id)

  // 后端就绪后替换为：
  // const res = await apiClient.get(`/admin/devices/${id}/status-history`)
  // return parseApiResponse(res.data)
}

// ============================================================
// §6 分诊记录
// ============================================================

/** 查询分诊记录（§6） */
export async function getTriageRecords(): Promise<AdminTriageRecord[]> {
  console.warn('[MOCK] /api/admin/triage-records 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockTriages()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/triage-records')
  // return parseApiResponse(res.data)
}

// ============================================================
// §15 统计驾驶舱
// ============================================================

/** 统计概览（§15） */
export async function getStatisticsSummary(): Promise<StatisticsSummary> {
  console.warn('[MOCK] /api/admin/statistics/summary 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockStatisticsSummary()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/statistics/summary')
  // return parseApiResponse(res.data)
}

/** 统计趋势（§15：最近 N 天） */
export async function getStatisticsTrend(
  days: number,
): Promise<StatisticsTrendItem[]> {
  console.warn('[MOCK] /api/admin/statistics/trend 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockStatisticsTrend(days)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/statistics/trend', { params: { days } })
  // return parseApiResponse(res.data)
}

/** 科室统计（§15） */
export async function getDepartmentStats(
  query?: StatisticsQuery,
): Promise<DepartmentStatItem[]> {
  console.warn('[MOCK] /api/admin/statistics/departments 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDepartmentStats(query)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/statistics/departments', { params: query })
  // return parseApiResponse(res.data)
}

/** 医生接诊排名（§15） */
export async function getDoctorRanking(
  query?: StatisticsQuery,
): Promise<DoctorRankingItem[]> {
  console.warn('[MOCK] /api/admin/statistics/doctor-ranking 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDoctorRanking(query)

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/statistics/doctor-ranking', { params: query })
  // return parseApiResponse(res.data)
}

/** 设备使用统计（§15） */
export async function getDeviceUsageStats(): Promise<DeviceUsageStatItem[]> {
  console.warn('[MOCK] /api/admin/statistics/devices 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDeviceUsageStats()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/statistics/devices')
  // return parseApiResponse(res.data)
}

/** AI 调用统计（§15） */
export async function getAiCallStats(): Promise<AiCallStatItem> {
  console.warn('[MOCK] /api/admin/statistics/ai-calls 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockAiCallStats()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/statistics/ai-calls')
  // return parseApiResponse(res.data)
}

// ============================================================
// §16 日志查询
// ============================================================

/** 登录日志（§16） */
export async function getLoginLogs(): Promise<LoginLog[]> {
  console.warn('[MOCK] /api/admin/logs/login 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockLoginLogs()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/logs/login')
  // return parseApiResponse(res.data)
}

/** 操作日志（§16） */
export async function getOperationLogs(): Promise<OperationLog[]> {
  console.warn('[MOCK] /api/admin/logs/operation 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockOperationLogs()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/logs/operation')
  // return parseApiResponse(res.data)
}

/** AI 调用日志（§16） */
export async function getAiInvocationLogs(): Promise<AiInvocationLog[]> {
  console.warn('[MOCK] /api/admin/logs/ai-invocation 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockAiInvocationLogs()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/admin/logs/ai-invocation')
  // return parseApiResponse(res.data)
}
