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
  AiInvocationAttempt,
  PageResult,
} from '@/types/admin'
import type {
  AppointmentResponse,
  AppointmentStatus,
  ScheduleResponse,
} from '@/types/appointment'
import type {
  DeviceResponse,
  DeviceStatus,
  DeviceStatusHistory,
} from '@/types/device'
import type { PageResponse } from '@/types/api'
import type { PatientDetailResponse, PatientResponse } from '@/types/patient'
import type { AdminTriageRecord } from '@/types/triage'
import type { UserRole } from '@/types/auth'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import {
  changeDeviceStatus as changeRealDeviceStatus,
  getAllDevices as getRealDevices,
  getDeviceStatusHistory as getRealDeviceStatusHistory,
  mapBackendDevice,
} from '@/api/device'
import { getPatientDetail } from '@/api/patient'

interface BackendDepartmentResponse {
  id: number
  code: string
  name: string
  status: 'ENABLED' | 'DISABLED'
  description?: string | null
  createdAt: string
  updatedAt: string
}

interface BackendDoctorResponse {
  id: number
  userId: number
  departmentId: number
  departmentName: string
  name: string
  title: string
  specialty?: string | null
  status: 'ENABLED' | 'DISABLED'
  introduction?: string | null
  createdAt: string
  updatedAt: string
}

interface DashboardSummaryResponse {
  todayAppointmentCount: number
  todayCompletedEncounterCount: number
  currentOnDutyDoctorCount: number
  currentAvailableDeviceCount: number
  highPriorityTriageCount: number
  totalPatientCount: number
}

interface DailyOutpatientStatisticsResponse {
  date: string
  completedCount: number
  cancelledCount: number
}

interface DepartmentOutpatientStatisticsResponse {
  departmentId: number
  departmentName: string
  encounterCount: number
}

interface DoctorEncounterStatisticsResponse {
  doctorId: number
  doctorName: string
  departmentName: string
  encounterCount: number
}

interface DeviceUsageStatisticsResponse {
  deviceId: number
  deviceName: string
  deviceType: string
  usageCount: number
  totalUsageSeconds: number
  usageRate: number
}

interface AIStatisticsResponse {
  totalInvocations: number
  successCount: number
  failedCount: number
  avgDurationMs: number
}

interface AICapabilityStatisticsResponse {
  capability: string
  totalInvocations: number
  successCount: number
  avgDurationMs: number
}

interface AuditLogResponse {
  id: number
  operatorId: number | null
  operatorType: string | null
  operatorName: string | null
  action: string
  targetType: string
  targetId: number | null
  details: string | null
  result: string
  errorMessage: string | null
  ipAddress: string | null
  createdAt: string
}

interface BackendDeviceResponse {
  id: number
  code: string
  name: string
  type: string
  departmentId?: number | null
  status: DeviceStatus
  purchaseDate?: string | null
  warrantyUntil?: string | null
  lastMaintenance?: string | null
  location?: string | null
  manufacturer?: string | null
  model?: string | null
  serialNumber?: string | null
  notes?: string | null
  createdAt: string
  updatedAt: string
}

interface BackendAdminUserResponse {
  id: number
  username: string
  realName?: string | null
  // 后端可在列表 DTO 中下发展示名（displayName）；未下发时回退到 realName/username。
  displayName?: string | null
  phone?: string | null
  email?: string | null
  enabled: boolean
  accountNonLocked: boolean
  roles: string[]
  // 后端可在列表 DTO 中下发最后登录时间（lastLoginAt）；
  // 未下发（undefined）与显式空（null）保留差异：undefined → '--'；null → 从未登录。
  lastLoginAt?: string | null
  createdAt: string
  updatedAt: string
}

interface BackendTriageRecordResponse {
  id: number
  patientId: number
  symptoms: string
  duration?: string | null
  supplement?: string | null
  aiDepartmentCode?: string | null
  aiPriority?: string | null
  aiReason?: string | null
  aiSafetyNotice?: string | null
  aiEmergencySuggested?: boolean | null
  aiSymptomKeywords?: string | null
  mappedDepartmentId?: number | null
  mappingStatus?: string | null
  aiStatus?: string | null
  aiFailureReason?: string | null
  createdAt: string
  updatedAt: string
}

interface AIInvocationResponse {
  id: number
  capability: string
  businessType: string
  businessId: number | null
  status: string
  errorType: string | null
  errorMessage: string | null
  durationMs: number | null
  attemptCount: number | null
  operatorId: number | null
  startedAt: string
  finishedAt: string | null
}

interface AIInvocationAttemptResponse {
  id: number
  invocationId: number
  provider: string
  model: string | null
  promptVersion: string | null
  status: string
  httpStatus: number | null
  errorType: string | null
  errorMessage: string | null
  requestSummary: string | null
  responseSummary: string | null
  durationMs: number | null
  attemptIndex: number
  startedAt: string
  finishedAt: string | null
}

function emptyPage<T>(query?: { page?: number; pageSize?: number }): PageResult<T> {
  return {
    list: [],
    total: 0,
    page: query?.page ?? 1,
    pageSize: query?.pageSize ?? 10,
  }
}

function defaultDateRange(query?: StatisticsQuery): Required<Pick<StatisticsQuery, 'startDate' | 'endDate'>> {
  const end = query?.endDate ? new Date(`${query.endDate}T00:00:00`) : new Date()
  const start = query?.startDate
    ? new Date(`${query.startDate}T00:00:00`)
    : new Date(end.getTime() - 29 * 24 * 60 * 60 * 1000)
  return {
    startDate: start.toISOString().slice(0, 10),
    endDate: end.toISOString().slice(0, 10),
  }
}

function titleText(title: string): string {
  const map: Record<string, string> = {
    CHIEF: '主任医师',
    DEPUTY_CHIEF: '副主任医师',
    ATTENDING: '主治医师',
    RESIDENT: '住院医师',
  }
  return map[title] ?? title
}

function backendTitle(title: string): string {
  const map: Record<string, string> = {
    主任医师: 'CHIEF',
    副主任医师: 'DEPUTY_CHIEF',
    主治医师: 'ATTENDING',
    住院医师: 'RESIDENT',
  }
  return map[title] ?? title
}

function backendDepartmentStatus(status: DepartmentStatus): 'ENABLED' | 'DISABLED' {
  return status === 'ACTIVE' ? 'ENABLED' : 'DISABLED'
}

function backendDoctorStatus(status: DoctorManageStatus): 'ENABLED' | 'DISABLED' {
  return status === 'ACTIVE' ? 'ENABLED' : 'DISABLED'
}

function mapDepartment(
  department: BackendDepartmentResponse,
  doctors: BackendDoctorResponse[] = [],
): DepartmentResponse {
  return {
    id: department.id,
    code: department.code,
    name: department.name,
    description: department.description ?? '',
    status: department.status === 'ENABLED' ? 'ACTIVE' : 'INACTIVE',
    doctorCount: doctors.filter((doctor) => doctor.departmentId === department.id).length,
    createdAt: department.createdAt,
    updatedAt: department.updatedAt,
  }
}

function mapDoctor(
  doctor: BackendDoctorResponse,
  schedules: ScheduleResponse[] = [],
): DoctorManageResponse {
  return {
    id: doctor.id,
    userId: doctor.userId,
    username: `user-${doctor.userId}`,
    name: doctor.name,
    title: titleText(doctor.title),
    departmentId: doctor.departmentId,
    departmentName: doctor.departmentName,
    gender: 'MALE',
    phone: '',
    email: '',
    specialty: doctor.specialty ?? '',
    introduction: doctor.introduction ?? '',
    status: doctor.status === 'ENABLED' ? 'ACTIVE' : 'DISABLED',
    scheduleCount: schedules.filter((schedule) => schedule.doctorId === doctor.id).length,
    encounterCount: 0,
    createdAt: doctor.createdAt,
    updatedAt: doctor.updatedAt,
  }
}

function mapPatientBasic(patient: PatientResponse): PatientDetailResponse {
  return {
    id: patient.id,
    name: patient.name,
    gender: patient.gender,
    birthDate: patient.birthDate,
    age: Math.max(0, new Date().getFullYear() - new Date(patient.birthDate).getFullYear()),
    phone: patient.phone,
    allergies: '',
    medicalHistory: '',
    address: '',
    emergencyContact: '',
    emergencyPhone: '',
    createdAt: patient.createdAt,
  }
}

function adminSchedulePayload(payload: ScheduleCreateRequest | ScheduleUpdateRequest) {
  return {
    ...payload,
    startTime: payload.scheduleDate && payload.startTime && payload.startTime.length <= 5
      ? `${payload.scheduleDate}T${payload.startTime}:00`
      : payload.startTime,
    endTime: payload.scheduleDate && payload.endTime && payload.endTime.length <= 5
      ? `${payload.scheduleDate}T${payload.endTime}:00`
      : payload.endTime,
  }
}

function mapAuditLog(log: AuditLogResponse): OperationLog {
  // F-HW-10：操作人不再用 'SYSTEM' 占位，空值时显示「系统」/「未识别」；
  // 目标类型与目标名称均做空值降级，便于 UI 做中文翻译。
  const rawName = log.operatorName?.trim()
  const operatorName = rawName && rawName.toUpperCase() !== 'SYSTEM' ? rawName : '系统'
  return {
    id: log.id,
    operatorId: log.operatorId ?? 0,
    operatorName,
    action: log.action,
    targetType: log.targetType ?? '',
    targetId: log.targetId,
    targetName: null,
    detail: log.details ?? log.errorMessage ?? '',
    operatedAt: log.createdAt,
  }
}

function backendUserStatus(user: Pick<BackendAdminUserResponse, 'enabled' | 'accountNonLocked'>): UserStatus {
  if (!user.enabled) return 'DISABLED'
  if (!user.accountNonLocked) return 'LOCKED'
  return 'ENABLED'
}

function backendUserRole(role: UserRole): 'ADMIN' | 'DOCTOR' {
  if (role === 'ADMIN' || role === 'DOCTOR') return role
  throw new Error('管理端创建/更新患者账号不走 /api/admin/users，请使用患者自助注册接口')
}

function mapBackendUser(user: BackendAdminUserResponse): UserManageResponse {
  const roles = user.roles.filter((role): role is UserRole =>
    role === 'ADMIN' || role === 'DOCTOR' || role === 'PATIENT',
  )
  // 兜底：先 displayName（业务展示名）→ realName → username，
  // 确保手机/资料缺失时前端不展示空字符串，也不会因 null 触发 charAt(0) 等渲染异常。
  const safeName = (user.displayName?.trim() || user.realName?.trim() || user.username || '').trim()
  return {
    id: user.id,
    username: user.username,
    realName: safeName,
    displayName: user.displayName ?? undefined,
    roles,
    status: backendUserStatus(user),
    phone: user.phone ?? '',
    email: user.email ?? '',
    // lastLoginAt:
    //   - string: 正常时间
    //   - null  : 后端明确标记为「从未登录」
    //   - undefined: 后端未下发该字段（旧版本契约），前端用 '--' 提示，避免误显示为「从未登录」
    lastLoginAt: user.lastLoginAt === undefined ? undefined : user.lastLoginAt,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
  }
}

// 后端 /api/devices 的 type 字段与前端 category 是同一字符串枚举；
// 值为空时默认 EXAMINATION，行为与原 backendDeviceType 一致。
function backendDeviceType(category: string | undefined): string {
  return category ?? 'EXAMINATION'
}

function backendCreateDevicePayload(payload: Partial<DeviceResponse>) {
  return {
    code: payload.code,
    name: payload.name,
    type: backendDeviceType(payload.category),
    location: payload.location,
    notes: payload.applicableItems?.join(', '),
  }
}

function backendUpdateDevicePayload(payload: Partial<DeviceResponse>) {
  return {
    name: payload.name,
    type: backendDeviceType(payload.category),
    location: payload.location,
    notes: payload.applicableItems?.join(', '),
  }
}

function mapTriageRecord(record: BackendTriageRecordResponse): AdminTriageRecord {
  return {
    id: record.id,
    patientId: record.patientId,
    patientName: `patient-${record.patientId}`,
    symptoms: record.symptoms,
    recommendedDepartmentId: record.mappedDepartmentId ?? 0,
    recommendedDepartmentName: record.aiDepartmentCode ?? record.mappingStatus ?? '',
    priority: (record.aiPriority ?? 'LOW') as AdminTriageRecord['priority'],
    reason: record.aiReason ?? record.aiFailureReason ?? '',
    safetyAdvice: record.aiSafetyNotice ?? '',
    emergencyAdvice: record.aiEmergencySuggested ? record.aiSafetyNotice ?? '' : undefined,
    aiSummary: record.aiReason ?? record.aiFailureReason ?? '',
    round: 1,
    isFinal: true,
    createdAt: record.createdAt,
  }
}

function mapAiInvocation(log: AIInvocationResponse): AiInvocationLog {
  return {
    id: log.id,
    callType: log.capability,
    // 后端 DTO 当前不下发 provider/model（只统计在 attempts 里），前端用占位符并降级显示 '--'
    provider: '',
    model: '',
    businessType: log.businessType,
    businessId: log.businessId,
    success: log.status === 'SUCCESS',
    duration: log.durationMs ?? 0,
    errorType: log.errorType,
    errorMessage: log.errorMessage,
    attemptCount: log.attemptCount ?? 0,
    operatorId: log.operatorId,
    calledAt: log.startedAt,
  }
}

function mapAiInvocationAttempt(
  attempt: AIInvocationAttemptResponse,
): AiInvocationAttempt {
  return {
    id: attempt.id,
    invocationId: attempt.invocationId,
    provider: attempt.provider,
    model: attempt.model ?? '',
    promptVersion: attempt.promptVersion,
    status: attempt.status,
    httpStatus: attempt.httpStatus,
    errorType: attempt.errorType,
    errorMessage: attempt.errorMessage,
    requestSummary: attempt.requestSummary,
    responseSummary: attempt.responseSummary,
    duration: attempt.durationMs,
    attemptIndex: attempt.attemptIndex,
    startedAt: attempt.startedAt,
    finishedAt: attempt.finishedAt,
  }
}

async function getAllDoctorsRaw(name?: string): Promise<BackendDoctorResponse[]> {
  const res = await apiClient.get('/doctors', {
    params: { page: 1, pageSize: 100, name: name || undefined },
  })
  return parseApiResponse<PageResponse<BackendDoctorResponse>>(res.data).items
}

export async function getDepartments(): Promise<DepartmentResponse[]> {
  const [departmentsRes, doctors] = await Promise.all([
    apiClient.get('/departments'),
    getAllDoctorsRaw(),
  ])
  const departments = parseApiResponse<BackendDepartmentResponse[]>(departmentsRes.data)
  return departments.map((department) => mapDepartment(department, doctors))
}

export async function getDepartmentById(id: number): Promise<DepartmentResponse> {
  const [departmentRes, doctors] = await Promise.all([
    apiClient.get(`/departments/${id}`),
    getAllDoctorsRaw(),
  ])
  return mapDepartment(parseApiResponse<BackendDepartmentResponse>(departmentRes.data), doctors)
}

export async function createDepartment(
  payload: DepartmentCreateRequest,
): Promise<DepartmentResponse> {
  const res = await apiClient.post('/departments', {
    code: payload.code,
    name: payload.name,
    description: payload.description,
  })
  return mapDepartment(parseApiResponse<BackendDepartmentResponse>(res.data))
}

export async function updateDepartment(
  id: number,
  payload: DepartmentUpdateRequest,
): Promise<DepartmentResponse> {
  const current = await getDepartmentById(id)
  const res = await apiClient.put(`/departments/${id}`, {
    name: payload.name ?? current.name,
    status: backendDepartmentStatus(current.status),
    description: payload.description ?? current.description,
  })
  return mapDepartment(parseApiResponse<BackendDepartmentResponse>(res.data))
}

export async function setDepartmentStatus(
  id: number,
  status: DepartmentStatus,
): Promise<DepartmentResponse> {
  const current = await getDepartmentById(id)
  const res = await apiClient.put(`/departments/${id}`, {
    name: current.name,
    status: backendDepartmentStatus(status),
    description: current.description,
  })
  return mapDepartment(parseApiResponse<BackendDepartmentResponse>(res.data))
}

export async function getUsers(query?: {
  status?: UserStatus
  role?: UserRole
  keyword?: string
}): Promise<UserManageResponse[]> {
  const res = await apiClient.get('/admin/users', {
    params: {
      enabled: query?.status === 'ENABLED'
        ? true
        : query?.status === 'DISABLED'
          ? false
          : undefined,
      role: query?.role,
      keyword: query?.keyword,
      page: 1,
      size: 100,
    },
  })
  let users = parseApiResponse<PageResponse<BackendAdminUserResponse>>(res.data).items.map(mapBackendUser)
  if (query?.status === 'LOCKED') {
    users = users.filter((user) => user.status === 'LOCKED')
  }
  return users
}

export async function createUser(payload: UserCreateRequest): Promise<UserManageResponse> {
  const role = backendUserRole(payload.roles[0])
  const res = await apiClient.post('/admin/users', {
    username: payload.username,
    password: payload.password,
    role,
    realName: payload.realName,
    phone: payload.phone,
    email: payload.email,
    departmentId: role === 'DOCTOR' ? payload.departmentId : undefined,
    doctorName: role === 'DOCTOR' ? payload.doctorName ?? payload.realName : undefined,
    doctorTitle: role === 'DOCTOR' ? payload.doctorTitle : undefined,
    specialty: role === 'DOCTOR' ? payload.specialty : undefined,
    education: role === 'DOCTOR' ? payload.education : undefined,
    experienceYears: role === 'DOCTOR' ? payload.experienceYears : undefined,
    introduction: role === 'DOCTOR' ? payload.introduction : undefined,
  })
  return mapBackendUser(parseApiResponse<BackendAdminUserResponse>(res.data))
}

export async function updateUser(
  id: number,
  payload: UserUpdateRequest,
): Promise<UserManageResponse> {
  const res = await apiClient.put(`/admin/users/${id}`, {
    role: payload.roles?.[0] ? backendUserRole(payload.roles[0]) : undefined,
    realName: payload.realName,
    phone: payload.phone,
    email: payload.email,
  })
  return mapBackendUser(parseApiResponse<BackendAdminUserResponse>(res.data))
}

export async function changeUserStatus(
  id: number,
  payload: UserStatusChangeRequest,
): Promise<UserManageResponse> {
  const actionMap: Record<UserStatus, 'ENABLE' | 'DISABLE' | 'LOCK'> = {
    ENABLED: 'ENABLE',
    DISABLED: 'DISABLE',
    LOCKED: 'LOCK',
  }
  const res = await apiClient.post(`/admin/users/${id}/status`, {
    action: actionMap[payload.status],
  })
  return mapBackendUser(parseApiResponse<BackendAdminUserResponse>(res.data))
}

export async function resetUserPassword(
  id: number,
  payload: ResetPasswordRequest,
): Promise<void> {
  await apiClient.post(`/admin/users/${id}/reset-password`, payload)
}

export async function getDoctors(query?: {
  departmentId?: number
  status?: DoctorManageStatus
  keyword?: string
}): Promise<DoctorManageResponse[]> {
  const [doctors, schedules] = await Promise.all([
    getAllDoctorsRaw(query?.keyword),
    getAdminSchedules(),
  ])
  return doctors
    .map((doctor) => mapDoctor(doctor, schedules))
    .filter((doctor) => !query?.departmentId || doctor.departmentId === query.departmentId)
    .filter((doctor) => !query?.status || doctor.status === query.status)
    .filter((doctor) => {
      if (!query?.keyword) return true
      const keyword = query.keyword.toLowerCase()
      return `${doctor.name} ${doctor.title} ${doctor.departmentName}`.toLowerCase().includes(keyword)
    })
}

export async function getDoctorById(id: number): Promise<DoctorManageResponse> {
  const [doctorRes, schedules] = await Promise.all([
    apiClient.get(`/doctors/${id}`),
    getAdminSchedules({ doctorId: id }),
  ])
  return mapDoctor(parseApiResponse<BackendDoctorResponse>(doctorRes.data), schedules)
}

export async function createDoctor(
  payload: DoctorCreateRequest,
): Promise<DoctorManageResponse> {
  const res = await apiClient.post('/doctors', {
    username: payload.username,
    password: payload.password,
    departmentId: payload.departmentId,
    name: payload.name,
    title: backendTitle(payload.title),
    specialty: payload.specialty,
    introduction: payload.introduction,
  })
  return mapDoctor(parseApiResponse<BackendDoctorResponse>(res.data))
}

export async function updateDoctor(
  id: number,
  payload: DoctorUpdateRequest,
): Promise<DoctorManageResponse> {
  const current = await getDoctorById(id)
  const res = await apiClient.put(`/doctors/${id}`, {
    departmentId: payload.departmentId ?? current.departmentId,
    name: payload.name ?? current.name,
    title: backendTitle(payload.title ?? current.title),
    specialty: payload.specialty ?? current.specialty,
    status: backendDoctorStatus(current.status),
    introduction: payload.introduction ?? current.introduction,
  })
  return mapDoctor(parseApiResponse<BackendDoctorResponse>(res.data))
}

export async function setDoctorStatus(
  id: number,
  status: DoctorManageStatus,
): Promise<DoctorManageResponse> {
  const current = await getDoctorById(id)
  const res = await apiClient.put(`/doctors/${id}`, {
    departmentId: current.departmentId,
    name: current.name,
    title: backendTitle(current.title),
    specialty: current.specialty,
    status: backendDoctorStatus(status),
    introduction: current.introduction,
  })
  return mapDoctor(parseApiResponse<BackendDoctorResponse>(res.data))
}

export async function getAdminSchedules(query?: {
  doctorId?: number
  departmentId?: number
  date?: string
}): Promise<ScheduleResponse[]> {
  let schedules: ScheduleResponse[]
  if (query?.doctorId) {
    const res = await apiClient.get(`/schedules/doctor/${query.doctorId}`, {
      params: { page: 1, size: 100 },
    })
    schedules = parseApiResponse<PageResponse<ScheduleResponse>>(res.data).items
  } else if (query?.departmentId) {
    const res = await apiClient.get(`/schedules/department/${query.departmentId}`)
    schedules = parseApiResponse<ScheduleResponse[]>(res.data)
  } else {
    const res = await apiClient.get('/schedules/available')
    schedules = parseApiResponse<ScheduleResponse[]>(res.data)
  }
  return query?.date
    ? schedules.filter((schedule) => schedule.scheduleDate === query.date)
    : schedules
}

export async function createSchedule(
  payload: ScheduleCreateRequest,
): Promise<ScheduleResponse> {
  const res = await apiClient.post('/schedules', adminSchedulePayload(payload))
  return parseApiResponse<ScheduleResponse>(res.data)
}

export async function cancelSchedule(
  id: number,
  payload: ScheduleCancelRequest,
): Promise<ScheduleResponse> {
  const res = await apiClient.post(`/schedules/${id}/cancel`, payload)
  return parseApiResponse<ScheduleResponse>(res.data)
}

export async function updateSchedule(
  id: number,
  payload: ScheduleUpdateRequest,
): Promise<ScheduleResponse> {
  const current = await apiClient.get(`/schedules/${id}`)
  const schedule = parseApiResponse<ScheduleResponse>(current.data)
  const res = await apiClient.put(`/schedules/${id}`, adminSchedulePayload({
    scheduleDate: payload.scheduleDate ?? schedule.scheduleDate,
    startTime: payload.startTime ?? schedule.startTime,
    endTime: payload.endTime ?? schedule.endTime,
    maxAppointments: payload.maxAppointments ?? schedule.maxAppointments,
  }))
  return parseApiResponse<ScheduleResponse>(res.data)
}

export async function getAdminAppointments(query?: {
  status?: AppointmentStatus
  patientId?: number
  doctorId?: number
  date?: string
}): Promise<AppointmentResponse[]> {
  const res = await apiClient.get('/appointments', {
    params: { page: 1, size: 100 },
  })
  let appointments = parseApiResponse<PageResponse<AppointmentResponse>>(res.data).items
  if (query?.status) {
    appointments = appointments.filter((appointment) => appointment.status === query.status)
  }
  if (query?.patientId) {
    appointments = appointments.filter((appointment) => appointment.patientId === query.patientId)
  }
  if (query?.doctorId) {
    appointments = appointments.filter((appointment) => appointment.doctorId === query.doctorId)
  }
  if (query?.date) {
    appointments = appointments.filter((appointment) => appointment.bookedAt?.slice(0, 10) === query.date)
  }
  return appointments
}

export async function getAdminPatients(query?: {
  keyword?: string
  status?: 'ACTIVE' | 'INACTIVE' | ''
  page?: number
  pageSize?: number
}): Promise<PageResult<PatientDetailResponse>> {
  const keyword = query?.keyword?.trim()
  const page = query?.page ?? 1
  const pageSize = query?.pageSize ?? 10
  // 后端 /api/patients 支持 status/name 筛选；F-HW-06 改为透传 status，未传则不传参。
  const res = await apiClient.get('/patients', {
    params: {
      name: keyword || undefined,
      status: query?.status || undefined,
      page,
      pageSize,
    },
  })
  const patientsPage = parseApiResponse<PageResponse<PatientResponse>>(res.data)
  const details = await Promise.all(
    patientsPage.items.map(async (patient) => {
      try {
        return await getPatientDetail(patient.id)
      } catch {
        return mapPatientBasic(patient)
      }
    }),
  )
  return {
    list: details,
    total: patientsPage.total,
    page: patientsPage.page,
    pageSize: patientsPage.pageSize,
  }
}

export async function getAdminDevices(): Promise<DeviceResponse[]> {
  return getRealDevices()
}

export async function createDevice(payload: Partial<DeviceResponse>): Promise<DeviceResponse> {
  const res = await apiClient.post('/devices', backendCreateDevicePayload(payload))
  return mapBackendDevice(parseApiResponse<BackendDeviceResponse>(res.data))
}

export async function updateDevice(
  id: number,
  payload: Partial<DeviceResponse>,
): Promise<DeviceResponse> {
  const res = await apiClient.put(`/devices/${id}`, backendUpdateDevicePayload(payload))
  return mapBackendDevice(parseApiResponse<BackendDeviceResponse>(res.data))
}

export async function setDeviceStatus(
  id: number,
  status: DeviceStatus,
  reason: string,
): Promise<DeviceResponse> {
  return changeRealDeviceStatus(id, status, reason)
}

export async function getDeviceStatusHistory(
  id: number,
): Promise<DeviceStatusHistory[]> {
  return getRealDeviceStatusHistory(id)
}

export async function getTriageRecords(query?: {
  page?: number
  pageSize?: number
}): Promise<PageResult<AdminTriageRecord>> {
  const page = query?.page ?? 1
  const pageSize = query?.pageSize ?? 20
  const res = await apiClient.get('/triage', {
    params: { page, size: pageSize },
  })
  const pageRes = parseApiResponse<PageResponse<BackendTriageRecordResponse>>(res.data)
  return {
    list: pageRes.items.map(mapTriageRecord),
    total: pageRes.total,
    page: pageRes.page,
    pageSize: pageRes.pageSize,
  }
}

export async function getStatisticsSummary(): Promise<StatisticsSummary> {
  const [dashboardRes, departments, doctors, devices] = await Promise.all([
    apiClient.get('/statistics/dashboard'),
    getDepartments(),
    getDoctors(),
    getAdminDevices(),
  ])
  const dashboard = parseApiResponse<DashboardSummaryResponse>(dashboardRes.data)
  return {
    todayAppointments: dashboard.todayAppointmentCount,
    todayCompletedEncounters: dashboard.todayCompletedEncounterCount,
    todayActiveDoctors: dashboard.currentOnDutyDoctorCount,
    todayAvailableDevices: dashboard.currentAvailableDeviceCount,
    todayHighPriorityTriages: dashboard.highPriorityTriageCount,
    totalPatients: dashboard.totalPatientCount,
    totalDoctors: doctors.length,
    totalDepartments: departments.length,
    totalDevices: devices.length,
  }
}

export async function getStatisticsTrend(
  days: number,
): Promise<StatisticsTrendItem[]> {
  const res = await apiClient.get('/statistics/outpatient/daily', {
    params: { days },
  })
  return parseApiResponse<DailyOutpatientStatisticsResponse[]>(res.data).map((item) => ({
    date: item.date,
    appointments: item.completedCount + item.cancelledCount,
    completedEncounters: item.completedCount,
  }))
}

export async function getDepartmentStats(
  query?: StatisticsQuery,
): Promise<DepartmentStatItem[]> {
  const range = defaultDateRange(query)
  const res = await apiClient.get('/statistics/department/outpatient', {
    params: range,
  })
  return parseApiResponse<DepartmentOutpatientStatisticsResponse[]>(res.data)
    .filter((item) => !query?.departmentId || item.departmentId === query.departmentId)
    .map((item) => ({
      departmentId: item.departmentId,
      departmentName: item.departmentName,
      appointmentCount: item.encounterCount,
      encounterCount: item.encounterCount,
    }))
}

export async function getDoctorRanking(
  query?: StatisticsQuery,
): Promise<DoctorRankingItem[]> {
  const range = defaultDateRange(query)
  const res = await apiClient.get('/statistics/doctor/encounter', {
    params: { ...range, departmentId: query?.departmentId },
  })
  return parseApiResponse<DoctorEncounterStatisticsResponse[]>(res.data).map((item) => ({
    doctorId: item.doctorId,
    doctorName: item.doctorName,
    departmentName: item.departmentName,
    encounterCount: item.encounterCount,
  }))
}

export async function getDeviceUsageStats(): Promise<DeviceUsageStatItem[]> {
  const range = defaultDateRange()
  const res = await apiClient.get('/statistics/device/usage', {
    params: range,
  })
  return parseApiResponse<DeviceUsageStatisticsResponse[]>(res.data).map((item) => ({
    deviceId: item.deviceId,
    deviceName: item.deviceName,
    deviceCode: item.deviceType,
    totalUsageCount: item.usageCount,
    totalUsageDuration: Math.round(item.totalUsageSeconds / 60),
    utilizationRate: item.usageRate,
  }))
}

export async function getAiCallStats(): Promise<AiCallStatItem> {
  const range = defaultDateRange()
  const [summaryRes, byCapabilityRes] = await Promise.all([
    apiClient.get('/statistics/ai/summary', { params: range }),
    apiClient.get('/statistics/ai/by-capability', { params: range }),
  ])
  const summary = parseApiResponse<AIStatisticsResponse>(summaryRes.data)
  const byCapability = parseApiResponse<AICapabilityStatisticsResponse[]>(byCapabilityRes.data)
  return {
    totalCalls: summary.totalInvocations,
    successCount: summary.successCount,
    failureCount: summary.failedCount,
    averageDuration: summary.avgDurationMs,
    byType: byCapability.map((item) => ({
      type: item.capability,
      count: item.totalInvocations,
      successCount: item.successCount,
    })),
  }
}

export async function getLoginLogs(): Promise<LoginLog[]> {
  const res = await apiClient.get('/audit/logs', {
    params: { action: 'AUTH_LOGIN', page: 1, size: 100 },
  })
  const logs = parseApiResponse<PageResponse<AuditLogResponse>>(res.data).items
  return logs.map((log) => {
    // F-HW-09：username 不再用固定 'SYSTEM' 占位；优先 operatorName，回退到
    // 「未识别用户」避免误导审计读者；IP/role 也做安全降级。
    const rawName = log.operatorName?.trim()
    const username = rawName && rawName.toUpperCase() !== 'SYSTEM' ? rawName : '未识别用户'
    const role: UserRole | null =
      log.operatorType === 'ADMIN' ||
      log.operatorType === 'DOCTOR' ||
      log.operatorType === 'PATIENT'
        ? log.operatorType
        : null
    return {
      id: log.id,
      userId: log.operatorId ?? 0,
      username,
      role,
      loginTime: log.createdAt,
      ip: log.ipAddress ?? '',
      success: log.result === 'SUCCESS',
      failReason: log.errorMessage,
    }
  })
}

export async function getOperationLogs(): Promise<OperationLog[]> {
  const res = await apiClient.get('/audit/logs', {
    params: { page: 1, size: 100 },
  })
  return parseApiResponse<PageResponse<AuditLogResponse>>(res.data).items.map(mapAuditLog)
}

export async function getAiInvocationLogs(query?: {
  page?: number
  pageSize?: number
  capability?: string
  businessType?: string
  // 后端参数为 success: Boolean（true=仅 SUCCESS；false=非 SUCCESS，含 FAILED/PENDING；不传=全部）
  success?: boolean
  startDate?: string
  endDate?: string
}): Promise<PageResult<AiInvocationLog>> {
  const page = query?.page ?? 1
  const pageSize = query?.pageSize ?? 20
  const res = await apiClient.get('/audit/ai/invocations', {
    params: {
      page,
      size: pageSize,
      capability: query?.capability,
      businessType: query?.businessType,
      success: query?.success,
      startDate: query?.startDate,
      endDate: query?.endDate,
    },
  })
  const pageRes = parseApiResponse<PageResponse<AIInvocationResponse>>(res.data)
  return {
    list: pageRes.items.map(mapAiInvocation),
    total: pageRes.total,
    page: pageRes.page,
    pageSize: pageRes.pageSize,
  }
}

export async function getAiInvocationAttempts(
  invocationId: number,
): Promise<AiInvocationAttempt[]> {
  const res = await apiClient.get(
    `/audit/ai/invocations/${invocationId}/attempts`,
  )
  return parseApiResponse<AIInvocationAttemptResponse[]>(res.data).map(
    mapAiInvocationAttempt,
  )
}
