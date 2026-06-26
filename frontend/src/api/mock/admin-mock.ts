// 管理端 MOCK 数据与内存操作
// 设计来源：product/11_功能需求.md §2/§4/§5/§6/§13/§15/§16
//          product/12_业务流程与状态机.md §2/§3/§11/§13
// 后端接口就绪后请删除本文件，并将 api/admin.ts 等替换为真实调用

import type {
  UserManageResponse,
  UserStatus,
  UserCreateRequest,
  UserUpdateRequest,
  UserStatusChangeRequest,
  DepartmentResponse,
  DepartmentStatus,
  DepartmentCreateRequest,
  DepartmentUpdateRequest,
  DoctorManageResponse,
  DoctorManageStatus,
  DoctorCreateRequest,
  DoctorUpdateRequest,
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
import type { ScheduleResponse, AppointmentResponse } from '@/types/appointment'
import type { DeviceResponse, DeviceStatusHistory } from '@/types/device'
import type { PatientResponse, PatientDetailResponse } from '@/types/patient'
import type { AdminTriageRecord } from '@/types/triage'
import type { UserRole } from '@/types/auth'

// ============================================================
// 工具函数
// ============================================================

const now = () => new Date().toISOString()

function ymd(offsetDays = 0): string {
  const dt = new Date()
  dt.setDate(dt.getDate() + offsetDays)
  return dt.toISOString().slice(0, 10)
}

function fmtDateTime(date: string, hhmm: string): string {
  return `${date}T${hhmm}:00+08:00`
}

// ============================================================
// 管理端扩展类型（在已有响应类型基础上补充管理视角字段）
// ============================================================

/** 患者记录（管理端视图，合并基本信息与扩展档案） */
interface AdminPatientRecord extends PatientResponse {
  age: number
  allergies: string
  medicalHistory: string
  address: string
  emergencyContact: string
  emergencyPhone: string
}

// ============================================================
// §4.3 科室管理
// ============================================================

const mockDepartmentStore: DepartmentResponse[] = [
  { id: 1, code: 'DEPT_EMERGENCY', name: '急诊科', description: '24 小时急诊救治，处理突发疾病与意外伤害。', status: 'ACTIVE', doctorCount: 2, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 2, code: 'DEPT_NEURO', name: '神经内科', description: '脑血管疾病、头痛、眩晕、癫痫等神经系统疾病诊疗。', status: 'ACTIVE', doctorCount: 1, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 3, code: 'DEPT_GI', name: '消化内科', description: '消化性溃疡、肝炎、胃肠功能紊乱等消化系统疾病诊疗。', status: 'ACTIVE', doctorCount: 1, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 4, code: 'DEPT_INTERNAL', name: '内科', description: '高血压、糖尿病、冠心病等慢性病综合管理与常见内科疾病诊疗。', status: 'ACTIVE', doctorCount: 3, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 5, code: 'DEPT_ORTHO', name: '骨科', description: '骨折、关节损伤、运动医学等骨科疾病诊疗。', status: 'ACTIVE', doctorCount: 1, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 6, code: 'DEPT_DERM', name: '皮肤科', description: '湿疹、皮炎、真菌感染等皮肤疾病诊疗。', status: 'ACTIVE', doctorCount: 1, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 7, code: 'DEPT_GP', name: '全科', description: '常见病、多发病的初步诊断与分流，健康管理与预防保健。', status: 'ACTIVE', doctorCount: 1, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 8, code: 'DEPT_CARDIO', name: '心内科', description: '冠心病、心律失常、心力衰竭等心血管疾病诊疗。', status: 'ACTIVE', doctorCount: 1, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
  { id: 9, code: 'DEPT_RESP', name: '呼吸内科', description: '肺炎、哮喘、慢阻肺等呼吸系统疾病诊疗。', status: 'ACTIVE', doctorCount: 1, createdAt: '2024-01-01T00:00:00+08:00', updatedAt: '2026-06-01T00:00:00+08:00' },
]

let mockDepartmentSeq = 100

export function getMockDepartments(): DepartmentResponse[] {
  return mockDepartmentStore.map((d) => ({ ...d }))
}

export function getMockDepartmentById(id: number): DepartmentResponse | undefined {
  const target = mockDepartmentStore.find((d) => d.id === id)
  return target ? { ...target } : undefined
}

export function createMockDepartment(payload: DepartmentCreateRequest): DepartmentResponse {
  mockDepartmentSeq++
  const ts = now()
  const dept: DepartmentResponse = {
    id: mockDepartmentSeq,
    code: payload.code,
    name: payload.name,
    description: payload.description ?? '',
    status: 'ACTIVE',
    doctorCount: 0,
    createdAt: ts,
    updatedAt: ts,
  }
  mockDepartmentStore.push(dept)
  return { ...dept }
}

export function updateMockDepartment(
  id: number,
  payload: DepartmentUpdateRequest,
): DepartmentResponse | undefined {
  const target = mockDepartmentStore.find((d) => d.id === id)
  if (!target) return undefined
  if (payload.name !== undefined) target.name = payload.name
  if (payload.description !== undefined) target.description = payload.description
  target.updatedAt = now()
  return { ...target }
}

export function changeMockDepartmentStatus(
  id: number,
  status: DepartmentStatus,
): DepartmentResponse | undefined {
  const target = mockDepartmentStore.find((d) => d.id === id)
  if (!target) return undefined
  target.status = status
  target.updatedAt = now()
  return { ...target }
}

// ============================================================
// §2.3 用户管理
// ============================================================

const mockUserStore: UserManageResponse[] = [
  {
    id: 1,
    username: 'admin',
    realName: '系统管理员',
    roles: ['ADMIN'],
    status: 'ENABLED',
    phone: '13800000001',
    email: 'admin@cloudbrain.demo',
    lastLoginAt: '2026-06-26T08:30:00+08:00',
    createdAt: '2024-01-01T00:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 2,
    username: 'zhangsan',
    realName: '张三',
    roles: ['PATIENT'],
    status: 'ENABLED',
    phone: '13800138000',
    email: 'zhangsan@cloudbrain.demo',
    patientId: 1,
    lastLoginAt: '2026-06-25T19:10:00+08:00',
    createdAt: '2024-05-10T08:30:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 3,
    username: 'qian.siqi',
    realName: '钱思齐',
    roles: ['DOCTOR'],
    status: 'ENABLED',
    phone: '13900004010',
    email: 'qian.siqi@cloudbrain.demo',
    doctorId: 401,
    lastLoginAt: '2026-06-26T07:45:00+08:00',
    createdAt: '2024-01-15T09:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 4,
    username: 'sun.ruoxi',
    realName: '孙若曦',
    roles: ['DOCTOR'],
    status: 'ENABLED',
    phone: '13900004020',
    email: 'sun.ruoxi@cloudbrain.demo',
    doctorId: 402,
    lastLoginAt: '2026-06-26T07:50:00+08:00',
    createdAt: '2024-02-20T09:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 5,
    username: 'li.wenbo',
    realName: '李文博',
    roles: ['DOCTOR'],
    status: 'ENABLED',
    phone: '13900001010',
    email: 'li.wenbo@cloudbrain.demo',
    doctorId: 101,
    lastLoginAt: '2026-06-26T08:00:00+08:00',
    createdAt: '2024-01-10T09:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 6,
    username: 'wang.xinyi',
    realName: '王心怡',
    roles: ['DOCTOR'],
    status: 'ENABLED',
    phone: '13900002010',
    email: 'wang.xinyi@cloudbrain.demo',
    doctorId: 201,
    lastLoginAt: '2026-06-25T17:30:00+08:00',
    createdAt: '2024-03-05T09:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 7,
    username: 'zhao.yaqin',
    realName: '赵雅琴',
    roles: ['DOCTOR'],
    status: 'ENABLED',
    phone: '13900003010',
    email: 'zhao.yaqin@cloudbrain.demo',
    doctorId: 301,
    lastLoginAt: '2026-06-25T08:15:00+08:00',
    createdAt: '2024-02-12T09:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 8,
    username: 'zhou.haoran',
    realName: '周浩然',
    roles: ['DOCTOR'],
    status: 'ENABLED',
    phone: '13900005010',
    email: 'zhou.haoran@cloudbrain.demo',
    doctorId: 501,
    lastLoginAt: '2026-06-24T08:00:00+08:00',
    createdAt: '2024-04-18T09:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 9,
    username: 'wu.wanqing',
    realName: '吴婉清',
    roles: ['DOCTOR'],
    status: 'DISABLED',
    phone: '13900006010',
    email: 'wu.wanqing@cloudbrain.demo',
    doctorId: 601,
    lastLoginAt: '2026-05-10T09:00:00+08:00',
    createdAt: '2024-06-01T09:00:00+08:00',
    updatedAt: '2026-05-15T10:00:00+08:00',
  },
  {
    id: 10,
    username: 'lisi',
    realName: '李四',
    roles: ['PATIENT'],
    status: 'LOCKED',
    phone: '13800138001',
    email: 'lisi@cloudbrain.demo',
    patientId: 2,
    lastLoginAt: '2026-06-10T14:00:00+08:00',
    createdAt: '2024-08-15T09:00:00+08:00',
    updatedAt: '2026-06-20T16:00:00+08:00',
  },
]

let mockUserSeq = 100

export function getMockUsers(query?: {
  status?: UserStatus
  role?: UserRole
  keyword?: string
}): UserManageResponse[] {
  let list = mockUserStore.map((u) => ({ ...u, roles: [...u.roles] }))
  if (query?.status) list = list.filter((u) => u.status === query.status)
  if (query?.role) list = list.filter((u) => u.roles.includes(query.role!))
  if (query?.keyword) {
    const k = query.keyword.toLowerCase()
    list = list.filter(
      (u) => u.username.toLowerCase().includes(k) || u.realName.toLowerCase().includes(k),
    )
  }
  return list
}

export function getMockUserById(id: number): UserManageResponse | undefined {
  const target = mockUserStore.find((u) => u.id === id)
  return target ? { ...target, roles: [...target.roles] } : undefined
}

export function createMockUser(payload: UserCreateRequest): UserManageResponse {
  mockUserSeq++
  const ts = now()
  const user: UserManageResponse = {
    id: mockUserSeq,
    username: payload.username,
    realName: payload.realName,
    roles: [...payload.roles],
    status: 'ENABLED',
    phone: payload.phone,
    email: payload.email ?? '',
    lastLoginAt: null,
    createdAt: ts,
    updatedAt: ts,
  }
  mockUserStore.push(user)
  return { ...user, roles: [...user.roles] }
}

export function updateMockUser(
  id: number,
  payload: UserUpdateRequest,
): UserManageResponse | undefined {
  const target = mockUserStore.find((u) => u.id === id)
  if (!target) return undefined
  if (payload.realName !== undefined) target.realName = payload.realName
  if (payload.roles !== undefined) target.roles = [...payload.roles]
  if (payload.phone !== undefined) target.phone = payload.phone
  if (payload.email !== undefined) target.email = payload.email
  target.updatedAt = now()
  return { ...target, roles: [...target.roles] }
}

export function changeMockUserStatus(
  id: number,
  payload: UserStatusChangeRequest,
): UserManageResponse | undefined {
  const target = mockUserStore.find((u) => u.id === id)
  if (!target) return undefined
  target.status = payload.status
  target.updatedAt = now()
  return { ...target, roles: [...target.roles] }
}

/** 重置用户密码（§2.3：管理员重置密码，MOCK 仅记录日志） */
export function resetMockUserPassword(id: number, _payload: { newPassword: string }): void {
  const target = mockUserStore.find((u) => u.id === id)
  if (!target) return
  target.updatedAt = now()
  // MOCK 阶段不真实校验密码，仅标记更新时间
}

// ============================================================
// §4.3 医生管理
// ============================================================

const mockDoctorStore: DoctorManageResponse[] = [
  {
    id: 401,
    userId: 3,
    username: 'qian.siqi',
    name: '钱思齐',
    title: '主任医师',
    departmentId: 4,
    departmentName: '内科',
    gender: 'MALE',
    phone: '13900004010',
    email: 'qian.siqi@cloudbrain.demo',
    specialty: '高血压、糖尿病、冠心病等慢性病综合管理；呼吸系统感染诊治',
    introduction: '内科主任医师，从事临床工作 20 余年，擅长慢性病长期管理与老年多病共存患者的综合诊治。',
    status: 'ACTIVE',
    scheduleCount: 16,
    encounterCount: 86,
    createdAt: '2024-01-15T09:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 402,
    userId: 4,
    username: 'sun.ruoxi',
    name: '孙若曦',
    title: '副主任医师',
    departmentId: 4,
    departmentName: '内科',
    gender: 'FEMALE',
    phone: '13900004020',
    email: 'sun.ruoxi@cloudbrain.demo',
    specialty: '呼吸系统疾病、哮喘、慢性咳嗽、肺部感染',
    introduction: '内科副主任医师，擅长呼吸系统疾病诊治与肺功能评估，从事临床工作 15 年。',
    status: 'ACTIVE',
    scheduleCount: 14,
    encounterCount: 42,
    createdAt: '2024-02-20T09:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 101,
    userId: 5,
    username: 'li.wenbo',
    name: '李文博',
    title: '主任医师',
    departmentId: 1,
    departmentName: '急诊科',
    gender: 'MALE',
    phone: '13900001010',
    email: 'li.wenbo@cloudbrain.demo',
    specialty: '急危重症救治、中毒抢救、心肺复苏',
    introduction: '急诊科主任医师，从事急诊急救工作 18 年，擅长急危重症的快速评估与抢救。',
    status: 'ACTIVE',
    scheduleCount: 16,
    encounterCount: 72,
    createdAt: '2024-01-10T09:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 201,
    userId: 6,
    username: 'wang.xinyi',
    name: '王心怡',
    title: '副主任医师',
    departmentId: 2,
    departmentName: '神经内科',
    gender: 'FEMALE',
    phone: '13900002010',
    email: 'wang.xinyi@cloudbrain.demo',
    specialty: '脑血管疾病、头痛、眩晕、癫痫',
    introduction: '神经内科副主任医师，擅长脑血管病急性期诊治与头痛眩晕的鉴别诊断。',
    status: 'ACTIVE',
    scheduleCount: 14,
    encounterCount: 48,
    createdAt: '2024-03-05T09:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 301,
    userId: 7,
    username: 'zhao.yaqin',
    name: '赵雅琴',
    title: '主任医师',
    departmentId: 3,
    departmentName: '消化内科',
    gender: 'FEMALE',
    phone: '13900003010',
    email: 'zhao.yaqin@cloudbrain.demo',
    specialty: '消化性溃疡、肝炎、胃肠功能紊乱、内镜诊疗',
    introduction: '消化内科主任医师，从事消化系统疾病诊治 20 年，精通胃肠镜检查与内镜下治疗。',
    status: 'ACTIVE',
    scheduleCount: 16,
    encounterCount: 54,
    createdAt: '2024-02-12T09:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 501,
    userId: 8,
    username: 'zhou.haoran',
    name: '周浩然',
    title: '主治医师',
    departmentId: 5,
    departmentName: '骨科',
    gender: 'MALE',
    phone: '13900005010',
    email: 'zhou.haoran@cloudbrain.demo',
    specialty: '骨折、关节损伤、运动医学',
    introduction: '骨科主治医师，擅长四肢骨折微创治疗与运动损伤康复指导。',
    status: 'ACTIVE',
    scheduleCount: 12,
    encounterCount: 35,
    createdAt: '2024-04-18T09:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
]

let mockDoctorSeq = 1000

export function getMockDoctors(query?: {
  departmentId?: number
  status?: DoctorManageStatus
  keyword?: string
}): DoctorManageResponse[] {
  let list = mockDoctorStore.map((d) => ({ ...d }))
  if (query?.departmentId) list = list.filter((d) => d.departmentId === query.departmentId)
  if (query?.status) list = list.filter((d) => d.status === query.status)
  if (query?.keyword) {
    const k = query.keyword.toLowerCase()
    list = list.filter(
      (d) => d.name.toLowerCase().includes(k) || d.username.toLowerCase().includes(k),
    )
  }
  return list
}

export function getMockDoctorById(id: number): DoctorManageResponse | undefined {
  const target = mockDoctorStore.find((d) => d.id === id)
  return target ? { ...target } : undefined
}

export function createMockDoctor(payload: DoctorCreateRequest): DoctorManageResponse {
  const dept = mockDepartmentStore.find((d) => d.id === payload.departmentId)
  mockUserSeq++
  const userId = mockUserSeq
  mockDoctorSeq++
  const ts = now()
  const doctor: DoctorManageResponse = {
    id: mockDoctorSeq,
    userId,
    username: payload.username,
    name: payload.name,
    title: payload.title,
    departmentId: payload.departmentId,
    departmentName: dept?.name ?? '',
    gender: payload.gender,
    phone: payload.phone,
    email: payload.email ?? '',
    specialty: payload.specialty ?? '',
    introduction: payload.introduction ?? '',
    status: 'ACTIVE',
    scheduleCount: 0,
    encounterCount: 0,
    createdAt: ts,
    updatedAt: ts,
  }
  mockDoctorStore.push(doctor)
  if (dept) dept.doctorCount++
  return { ...doctor }
}

export function updateMockDoctor(
  id: number,
  payload: DoctorUpdateRequest,
): DoctorManageResponse | undefined {
  const target = mockDoctorStore.find((d) => d.id === id)
  if (!target) return undefined
  if (payload.title !== undefined) target.title = payload.title
  if (payload.departmentId !== undefined) {
    const oldDept = mockDepartmentStore.find((d) => d.id === target.departmentId)
    if (oldDept && oldDept.doctorCount > 0) oldDept.doctorCount--
    target.departmentId = payload.departmentId
    const newDept = mockDepartmentStore.find((d) => d.id === payload.departmentId)
    if (newDept) {
      target.departmentName = newDept.name
      newDept.doctorCount++
    }
  }
  if (payload.phone !== undefined) target.phone = payload.phone
  if (payload.email !== undefined) target.email = payload.email
  if (payload.specialty !== undefined) target.specialty = payload.specialty
  if (payload.introduction !== undefined) target.introduction = payload.introduction
  target.updatedAt = now()
  return { ...target }
}

export function changeMockDoctorStatus(
  id: number,
  status: DoctorManageStatus,
): DoctorManageResponse | undefined {
  const target = mockDoctorStore.find((d) => d.id === id)
  if (!target) return undefined
  target.status = status
  target.updatedAt = now()
  return { ...target }
}

// ============================================================
// §5 排班管理
// ============================================================

function generateInitialSchedules(): ScheduleResponse[] {
  const out: ScheduleResponse[] = []
  const doctors = [
    { id: 401, name: '钱思齐', deptId: 4, deptName: '内科' },
    { id: 402, name: '孙若曦', deptId: 4, deptName: '内科' },
  ]
  // 每天上午/下午的预约基数（确定性，不使用随机）
  const morningBooked = [15, 8, 12, 20, 6, 18, 10, 14]
  const afternoonBooked = [9, 5, 7, 16, 3, 12, 6, 8]
  for (let d = 0; d < 8; d++) {
    const date = ymd(d)
    for (const doc of doctors) {
      // 上午
      const mMax = 20
      const mBooked = morningBooked[d]
      out.push({
        id: doc.id * 100 + d * 10 + 1,
        doctorId: doc.id,
        doctorName: doc.name,
        departmentId: doc.deptId,
        departmentName: doc.deptName,
        scheduleDate: date,
        startTime: fmtDateTime(date, '08:00'),
        endTime: fmtDateTime(date, '11:30'),
        maxAppointments: mMax,
        bookedCount: mBooked,
        remainingCount: Math.max(0, mMax - mBooked),
        status: mBooked >= mMax ? 'FULL' : 'AVAILABLE',
        cancelledAt: null,
        cancelReason: null,
        createdAt: '2026-06-20T00:00:00+08:00',
        updatedAt: '2026-06-20T00:00:00+08:00',
      })
      // 下午
      const aMax = 16
      const aBooked = afternoonBooked[d]
      out.push({
        id: doc.id * 100 + d * 10 + 2,
        doctorId: doc.id,
        doctorName: doc.name,
        departmentId: doc.deptId,
        departmentName: doc.deptName,
        scheduleDate: date,
        startTime: fmtDateTime(date, '14:00'),
        endTime: fmtDateTime(date, '17:00'),
        maxAppointments: aMax,
        bookedCount: aBooked,
        remainingCount: Math.max(0, aMax - aBooked),
        status: aBooked >= aMax ? 'FULL' : 'AVAILABLE',
        cancelledAt: null,
        cancelReason: null,
        createdAt: '2026-06-20T00:00:00+08:00',
        updatedAt: '2026-06-20T00:00:00+08:00',
      })
    }
  }
  // 标记一条 CANCELLED 用于演示（钱思齐 第 3 天上午）
  const cancelled = out.find((s) => s.id === 401 * 100 + 2 * 10 + 1)
  if (cancelled) {
    cancelled.status = 'CANCELLED'
    cancelled.cancelledAt = '2026-06-25T10:00:00+08:00'
    cancelled.cancelReason = '医生临时出差，暂停门诊'
    cancelled.updatedAt = '2026-06-25T10:00:00+08:00'
  }
  return out
}

const mockScheduleStore: ScheduleResponse[] = generateInitialSchedules()
let mockScheduleSeq = 50000

export function getMockSchedules(query?: {
  doctorId?: number
  departmentId?: number
  date?: string
}): ScheduleResponse[] {
  let list = mockScheduleStore.map((s) => ({ ...s }))
  if (query?.doctorId) list = list.filter((s) => s.doctorId === query.doctorId)
  if (query?.departmentId) list = list.filter((s) => s.departmentId === query.departmentId)
  if (query?.date) list = list.filter((s) => s.scheduleDate === query.date)
  return list
}

export function getMockScheduleById(id: number): ScheduleResponse | undefined {
  const target = mockScheduleStore.find((s) => s.id === id)
  return target ? { ...target } : undefined
}

export function createMockSchedule(payload: ScheduleCreateRequest): ScheduleResponse {
  const doctor = mockDoctorStore.find((d) => d.id === payload.doctorId)
  const dept = mockDepartmentStore.find((d) => d.id === payload.departmentId)
  mockScheduleSeq++
  const ts = now()
  const schedule: ScheduleResponse = {
    id: mockScheduleSeq,
    doctorId: payload.doctorId,
    doctorName: doctor?.name ?? '',
    departmentId: payload.departmentId,
    departmentName: dept?.name ?? '',
    scheduleDate: payload.scheduleDate,
    startTime: fmtDateTime(payload.scheduleDate, payload.startTime),
    endTime: fmtDateTime(payload.scheduleDate, payload.endTime),
    maxAppointments: payload.maxAppointments,
    bookedCount: 0,
    remainingCount: payload.maxAppointments,
    status: 'AVAILABLE',
    cancelledAt: null,
    cancelReason: null,
    createdAt: ts,
    updatedAt: ts,
  }
  mockScheduleStore.push(schedule)
  if (doctor) doctor.scheduleCount++
  return { ...schedule }
}

export function cancelMockSchedule(
  id: number,
  payload: ScheduleCancelRequest,
): ScheduleResponse | undefined {
  const target = mockScheduleStore.find((s) => s.id === id)
  if (!target) return undefined
  const ts = now()
  target.status = 'CANCELLED'
  target.cancelledAt = ts
  target.cancelReason = payload.reason
  target.updatedAt = ts
  return { ...target }
}

// §5.3 修改未开始排班
// 规则：仅 AVAILABLE 状态可改；maxAppointments 不得低于已约数；结束时间须晚于开始时间
export function updateMockSchedule(
  id: number,
  payload: ScheduleUpdateRequest,
): ScheduleResponse | undefined {
  const target = mockScheduleStore.find((s) => s.id === id)
  if (!target) return undefined
  if (target.status !== 'AVAILABLE') {
    throw new Error('仅未开始的排班可修改')
  }
  const nextDate = payload.scheduleDate ?? target.scheduleDate
  const nextStart = payload.startTime ?? target.startTime.slice(11, 16)
  const nextEnd = payload.endTime ?? target.endTime.slice(11, 16)
  if (nextEnd <= nextStart) {
    throw new Error('结束时间必须晚于开始时间')
  }
  const nextMax = payload.maxAppointments ?? target.maxAppointments
  if (nextMax <= 0) {
    throw new Error('最大挂号数需大于 0')
  }
  if (nextMax < target.bookedCount) {
    throw new Error(`最大挂号数不能低于已约数（${target.bookedCount}）`)
  }
  target.scheduleDate = nextDate
  target.startTime = fmtDateTime(nextDate, nextStart)
  target.endTime = fmtDateTime(nextDate, nextEnd)
  target.maxAppointments = nextMax
  target.remainingCount = nextMax - target.bookedCount
  // 容量变化后同步状态
  if (target.remainingCount === 0) target.status = 'FULL'
  else target.status = 'AVAILABLE'
  target.updatedAt = now()
  return { ...target }
}

// ============================================================
// 挂号记录（管理端查看全部）
// ============================================================

const mockAppointmentStore: AppointmentResponse[] = [
  {
    id: 1001,
    patientId: 1,
    patientName: '张三',
    scheduleId: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001001',
    status: 'BOOKED',
    bookedAt: '2026-06-25T15:20:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-25T15:20:00+08:00',
    updatedAt: '2026-06-25T15:20:00+08:00',
  },
  {
    id: 1002,
    patientId: 2,
    patientName: '李四',
    scheduleId: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001002',
    status: 'CHECKED_IN',
    bookedAt: '2026-06-25T16:00:00+08:00',
    checkInTime: '2026-06-26T08:05:00+08:00',
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-25T16:00:00+08:00',
    updatedAt: '2026-06-26T08:05:00+08:00',
  },
  {
    id: 1003,
    patientId: 3,
    patientName: '王五',
    scheduleId: 40102,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001003',
    status: 'IN_PROGRESS',
    bookedAt: '2026-06-25T14:30:00+08:00',
    checkInTime: '2026-06-26T14:10:00+08:00',
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-25T14:30:00+08:00',
    updatedAt: '2026-06-26T14:30:00+08:00',
  },
  {
    id: 1004,
    patientId: 4,
    patientName: '赵六',
    scheduleId: 40102,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001004',
    status: 'WAITING_EXAM',
    bookedAt: '2026-06-25T17:00:00+08:00',
    checkInTime: '2026-06-26T14:30:00+08:00',
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-25T17:00:00+08:00',
    updatedAt: '2026-06-26T15:10:00+08:00',
  },
  {
    id: 1005,
    patientId: 5,
    patientName: '孙七',
    scheduleId: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001005',
    status: 'COMPLETED',
    bookedAt: '2026-06-24T10:00:00+08:00',
    checkInTime: '2026-06-26T08:00:00+08:00',
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-24T10:00:00+08:00',
    updatedAt: '2026-06-26T09:20:00+08:00',
  },
  {
    id: 1006,
    patientId: 1,
    patientName: '张三',
    scheduleId: 40201,
    doctorId: 402,
    doctorName: '孙若曦',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001006',
    status: 'CANCELLED',
    bookedAt: '2026-06-24T11:00:00+08:00',
    checkInTime: null,
    cancellationReason: '临时有事，无法就诊',
    cancellationSource: 'PATIENT',
    cancelledAt: '2026-06-25T09:00:00+08:00',
    createdAt: '2026-06-24T11:00:00+08:00',
    updatedAt: '2026-06-25T09:00:00+08:00',
  },
  {
    id: 1007,
    patientId: 2,
    patientName: '李四',
    scheduleId: 40201,
    doctorId: 402,
    doctorName: '孙若曦',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001007',
    status: 'NO_SHOW',
    bookedAt: '2026-06-24T13:00:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-24T13:00:00+08:00',
    updatedAt: '2026-06-26T12:00:00+08:00',
  },
  {
    id: 1008,
    patientId: 3,
    patientName: '王五',
    scheduleId: 40202,
    doctorId: 402,
    doctorName: '孙若曦',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001008',
    status: 'COMPLETED',
    bookedAt: '2026-06-23T14:00:00+08:00',
    checkInTime: '2026-06-25T14:05:00+08:00',
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-23T14:00:00+08:00',
    updatedAt: '2026-06-25T15:30:00+08:00',
  },
  {
    id: 1009,
    patientId: 4,
    patientName: '赵六',
    scheduleId: 40202,
    doctorId: 402,
    doctorName: '孙若曦',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001009',
    status: 'BOOKED',
    bookedAt: '2026-06-25T18:00:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-25T18:00:00+08:00',
    updatedAt: '2026-06-25T18:00:00+08:00',
  },
  {
    id: 1010,
    patientId: 5,
    patientName: '孙七',
    scheduleId: 40201,
    doctorId: 402,
    doctorName: '孙若曦',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A001010',
    status: 'COMPLETED',
    bookedAt: '2026-06-23T15:00:00+08:00',
    checkInTime: '2026-06-25T08:00:00+08:00',
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-23T15:00:00+08:00',
    updatedAt: '2026-06-25T09:15:00+08:00',
  },
]

export function getMockAppointments(query?: {
  status?: AppointmentResponse['status']
  patientId?: number
  doctorId?: number
  date?: string
}): AppointmentResponse[] {
  let list = mockAppointmentStore.map((a) => ({ ...a }))
  if (query?.status) list = list.filter((a) => a.status === query.status)
  if (query?.patientId) list = list.filter((a) => a.patientId === query.patientId)
  if (query?.doctorId) list = list.filter((a) => a.doctorId === query.doctorId)
  if (query?.date) {
    list = list.filter((a) => (a.bookedAt ?? '').startsWith(query.date!))
  }
  return list
}

export function getMockAppointmentById(id: number): AppointmentResponse | undefined {
  const target = mockAppointmentStore.find((a) => a.id === id)
  return target ? { ...target } : undefined
}

// ============================================================
// 患者管理（管理端查询，合并基本信息与扩展档案）
// ============================================================

const mockPatientStore: AdminPatientRecord[] = [
  {
    id: 1,
    userId: 2,
    name: '张三',
    gender: 'MALE',
    birthDate: '1990-01-15',
    phone: '13800138000',
    status: 'ACTIVE',
    age: 36,
    allergies: '青霉素过敏',
    medicalHistory: '高血压 3 年',
    address: '北京市朝阳区幸福路 12 号',
    emergencyContact: '张母',
    emergencyPhone: '13800001112',
    createdAt: '2024-05-10T08:30:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 2,
    userId: 10,
    name: '李四',
    gender: 'FEMALE',
    birthDate: '1985-05-20',
    phone: '13800138001',
    status: 'ACTIVE',
    age: 41,
    allergies: '无',
    medicalHistory: '糖尿病 2 年',
    address: '北京市海淀区学院路 88 号',
    emergencyContact: '李夫',
    emergencyPhone: '13800002223',
    createdAt: '2024-08-15T09:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 3,
    userId: 11,
    name: '王五',
    gender: 'MALE',
    birthDate: '1978-11-03',
    phone: '13800138002',
    status: 'ACTIVE',
    age: 47,
    allergies: '磺胺类过敏',
    medicalHistory: '冠心病 5 年，房颤',
    address: '北京市西城区西直门内大街 33 号',
    emergencyContact: '王女',
    emergencyPhone: '13800003334',
    createdAt: '2023-11-02T14:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 4,
    userId: 12,
    name: '赵六',
    gender: 'FEMALE',
    birthDate: '1995-03-08',
    phone: '13800138003',
    status: 'ACTIVE',
    age: 31,
    allergies: '无',
    medicalHistory: '既往体健',
    address: '北京市东城区东直门外大街 6 号',
    emergencyContact: '赵母',
    emergencyPhone: '13800004445',
    createdAt: '2025-01-20T10:15:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 5,
    userId: 13,
    name: '孙七',
    gender: 'MALE',
    birthDate: '2000-07-12',
    phone: '13800138004',
    status: 'ACTIVE',
    age: 25,
    allergies: '无',
    medicalHistory: '既往体健',
    address: '北京市丰台区南三环西路 21 号',
    emergencyContact: '孙父',
    emergencyPhone: '13800005556',
    createdAt: '2025-09-12T11:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 6,
    userId: 14,
    name: '周八',
    gender: 'FEMALE',
    birthDate: '1970-09-25',
    phone: '13800138005',
    status: 'INACTIVE',
    age: 55,
    allergies: '阿司匹林过敏',
    medicalHistory: '房颤、脑梗史，长期口服华法林',
    address: '北京市石景山区八角东路 9 号',
    emergencyContact: '周女',
    emergencyPhone: '13800006667',
    createdAt: '2023-06-10T09:30:00+08:00',
    updatedAt: '2026-05-20T00:00:00+08:00',
  },
]

function toPatientDetailResponse(r: AdminPatientRecord): PatientDetailResponse {
  return {
    id: r.id,
    name: r.name,
    gender: r.gender,
    birthDate: r.birthDate,
    age: r.age,
    phone: r.phone,
    allergies: r.allergies,
    medicalHistory: r.medicalHistory,
    address: r.address,
    emergencyContact: r.emergencyContact,
    emergencyPhone: r.emergencyPhone,
    createdAt: r.createdAt,
  }
}

export function getMockPatients(query?: {
  keyword?: string
  page?: number
  pageSize?: number
}): PageResult<PatientDetailResponse> {
  let list = mockPatientStore.map((r) => ({ ...r }))
  if (query?.keyword) {
    const k = query.keyword.toLowerCase()
    list = list.filter(
      (r) =>
        r.name.toLowerCase().includes(k) ||
        r.phone.includes(query.keyword!) ||
        String(r.id).includes(query.keyword!),
    )
  }
  const total = list.length
  const page = query?.page ?? 1
  const pageSize = query?.pageSize ?? 20
  const start = (page - 1) * pageSize
  const pageItems = list.slice(start, start + pageSize).map(toPatientDetailResponse)
  return { list: pageItems, total, page, pageSize }
}

export function getMockPatientById(id: number): PatientDetailResponse | undefined {
  const target = mockPatientStore.find((r) => r.id === id)
  return target ? toPatientDetailResponse(target) : undefined
}

// ============================================================
// §13 医疗设备管理
// ============================================================

const mockDeviceStore: DeviceResponse[] = [
  {
    id: 601,
    code: 'DEV-XRAY-01',
    name: '数字化 X 射线摄影系统',
    category: 'EXAMINATION',
    status: 'AVAILABLE',
    location: '放射科 1 室',
    enabled: true,
    applicableItems: ['胸部 X 光', '骨骼 X 光', '腹部 X 光'],
    createdAt: '2024-03-01T00:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 602,
    code: 'DEV-US-01',
    name: '彩色多普勒超声诊断仪',
    category: 'EXAMINATION',
    status: 'AVAILABLE',
    location: '超声科 2 室',
    enabled: true,
    applicableItems: ['腹部 B 超', '甲状腺超声', '心脏彩超'],
    createdAt: '2024-05-12T00:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 603,
    code: 'DEV-ECG-01',
    name: '十二导联心电图机',
    category: 'EXAMINATION',
    status: 'AVAILABLE',
    location: '内科诊室 3',
    enabled: true,
    applicableItems: ['心电图', '动态心电图'],
    createdAt: '2024-06-20T00:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 604,
    code: 'DEV-HEMO-01',
    name: '全自动血液细胞分析仪',
    category: 'LABORATORY',
    status: 'AVAILABLE',
    location: '检验科 1 区',
    enabled: true,
    applicableItems: ['血常规', '血型鉴定'],
    createdAt: '2024-02-15T00:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 605,
    code: 'DEV-BIO-01',
    name: '全自动生化分析仪',
    category: 'LABORATORY',
    status: 'AVAILABLE',
    location: '检验科 2 区',
    enabled: true,
    applicableItems: ['肝功能', '肾功能', '血脂', '血糖', '电解质'],
    createdAt: '2024-04-08T00:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 606,
    code: 'DEV-COAG-01',
    name: '凝血功能分析仪',
    category: 'LABORATORY',
    status: 'AVAILABLE',
    location: '检验科 2 区',
    enabled: true,
    applicableItems: ['凝血功能', 'D-二聚体'],
    createdAt: '2024-07-22T00:00:00+08:00',
    updatedAt: '2026-06-01T00:00:00+08:00',
  },
  {
    id: 607,
    code: 'DEV-MON-01',
    name: '多参数监护仪',
    category: 'MONITOR',
    status: 'IN_USE',
    location: '内科诊室 1',
    enabled: true,
    applicableItems: ['生命体征监护', '血氧饱和度'],
    createdAt: '2024-09-30T00:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 608,
    code: 'DEV-CT-01',
    name: 'CT 扫描系统',
    category: 'EXAMINATION',
    status: 'MAINTENANCE',
    location: '放射科 2 室',
    enabled: true,
    applicableItems: ['头部 CT', '胸部 CT', '腹部 CT'],
    createdAt: '2024-01-10T00:00:00+08:00',
    updatedAt: '2026-06-22T00:00:00+08:00',
  },
]

const mockDeviceStatusHistoryStore: DeviceStatusHistory[] = [
  { id: 7001, deviceId: 601, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-03-01T00:00:00+08:00' },
  { id: 7002, deviceId: 602, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-05-12T00:00:00+08:00' },
  { id: 7003, deviceId: 603, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-06-20T00:00:00+08:00' },
  { id: 7004, deviceId: 604, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-02-15T00:00:00+08:00' },
  { id: 7005, deviceId: 605, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-04-08T00:00:00+08:00' },
  { id: 7006, deviceId: 606, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-07-22T00:00:00+08:00' },
  { id: 7007, deviceId: 607, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-09-30T00:00:00+08:00' },
  { id: 7008, deviceId: 607, fromStatus: 'AVAILABLE', toStatus: 'IN_USE', operatorId: 401, operatorName: '钱思齐', reason: '接诊期间生命体征监护', changedAt: '2026-06-24T09:20:00+08:00' },
  { id: 7009, deviceId: 608, fromStatus: null, toStatus: 'AVAILABLE', operatorId: 0, operatorName: '系统初始化', reason: '设备建档', changedAt: '2024-01-10T00:00:00+08:00' },
  { id: 7010, deviceId: 608, fromStatus: 'AVAILABLE', toStatus: 'MAINTENANCE', operatorId: 1, operatorName: '系统管理员', reason: '球管老化，安排定期维修', changedAt: '2026-06-22T10:00:00+08:00' },
]

let mockDeviceStatusSeq = 7011
let mockDeviceSeq = 700

export function getMockDevices(): DeviceResponse[] {
  return mockDeviceStore.map((d) => ({ ...d, applicableItems: [...d.applicableItems] }))
}

export function getMockDeviceById(id: number): DeviceResponse | undefined {
  const target = mockDeviceStore.find((d) => d.id === id)
  return target ? { ...target, applicableItems: [...target.applicableItems] } : undefined
}

/** 创建设备（§13） */
export function createMockDevice(payload: Partial<DeviceResponse>): DeviceResponse {
  mockDeviceSeq++
  const ts = now()
  const device: DeviceResponse = {
    id: mockDeviceSeq,
    code: payload.code ?? `DEV-NEW-${mockDeviceSeq}`,
    name: payload.name ?? '新设备',
    category: payload.category ?? 'EXAMINATION',
    status: payload.status ?? 'AVAILABLE',
    location: payload.location ?? '',
    enabled: payload.enabled ?? true,
    applicableItems: payload.applicableItems ? [...payload.applicableItems] : [],
    createdAt: ts,
    updatedAt: ts,
  }
  mockDeviceStore.push(device)
  // 写入初始状态历史
  mockDeviceStatusHistoryStore.push({
    id: mockDeviceStatusSeq++,
    deviceId: device.id,
    fromStatus: null,
    toStatus: device.status,
    operatorId: 1,
    operatorName: '系统管理员',
    reason: '设备建档',
    changedAt: ts,
  })
  return { ...device, applicableItems: [...device.applicableItems] }
}

export function updateMockDevice(
  id: number,
  patch: Partial<Pick<DeviceResponse, 'name' | 'location' | 'status' | 'enabled' | 'category' | 'applicableItems'>>,
): DeviceResponse | undefined {
  const target = mockDeviceStore.find((d) => d.id === id)
  if (!target) return undefined
  if (patch.name !== undefined) target.name = patch.name
  if (patch.location !== undefined) target.location = patch.location
  if (patch.status !== undefined) target.status = patch.status
  if (patch.enabled !== undefined) target.enabled = patch.enabled
  if (patch.category !== undefined) target.category = patch.category
  if (patch.applicableItems !== undefined) target.applicableItems = [...patch.applicableItems]
  target.updatedAt = now()
  return { ...target, applicableItems: [...target.applicableItems] }
}

/** 变更设备状态（§13.6：记录来源/目标/操作人/原因） */
export function changeMockDeviceStatus(
  id: number,
  status: DeviceResponse['status'],
  reason?: string,
): DeviceResponse | undefined {
  const target = mockDeviceStore.find((d) => d.id === id)
  if (!target) return undefined
  const fromStatus = target.status
  target.status = status
  target.updatedAt = now()
  mockDeviceStatusHistoryStore.push({
    id: mockDeviceStatusSeq++,
    deviceId: id,
    fromStatus,
    toStatus: status,
    operatorId: 1,
    operatorName: '系统管理员',
    reason,
    changedAt: now(),
  })
  return { ...target, applicableItems: [...target.applicableItems] }
}

export function getMockDeviceStatusHistory(deviceId: number): DeviceStatusHistory[] {
  return mockDeviceStatusHistoryStore
    .filter((h) => h.deviceId === deviceId)
    .map((h) => ({ ...h }))
    .sort((a, b) => new Date(b.changedAt).getTime() - new Date(a.changedAt).getTime())
}

// ============================================================
// §6 AI 分诊记录
// ============================================================

const mockTriageStore: AdminTriageRecord[] = [
  {
    id: 1,
    patientId: 1,
    patientName: '张三',
    recommendedDepartmentId: 4,
    recommendedDepartmentName: '内科',
    priority: 'MEDIUM',
    reason: '发热伴咳嗽提示呼吸道感染可能，建议内科门诊评估。',
    safetyAdvice: '多饮水，注意休息，监测体温；如高热不退或呼吸困难请及时就诊。',
    emergencyAdvice: undefined,
    followUpQuestion: '是否有慢性基础疾病？',
    symptoms: '发热伴咳嗽 3 天，体温最高 38.5℃',
    aiSummary: '考虑上呼吸道感染，建议内科就诊，必要时查血常规。',
    createdAt: '2026-06-24T08:00:00+08:00',
  },
  {
    id: 2,
    patientId: 2,
    patientName: '李四',
    recommendedDepartmentId: 4,
    recommendedDepartmentName: '内科',
    priority: 'LOW',
    reason: '口干多饮需排查糖代谢异常，建议内科查空腹及餐后血糖。',
    safetyAdvice: '控制饮食，减少含糖饮料摄入，适当运动。',
    emergencyAdvice: undefined,
    followUpQuestion: undefined,
    symptoms: '口干多饮 2 月余',
    aiSummary: '建议检查空腹血糖与糖化血红蛋白，排除糖尿病。',
    createdAt: '2026-06-23T10:30:00+08:00',
  },
  {
    id: 3,
    patientId: 3,
    patientName: '王五',
    recommendedDepartmentId: 8,
    recommendedDepartmentName: '心内科',
    priority: 'HIGH',
    reason: '胸闷胸痛需优先排除心血管急症，建议尽快心内科或急诊就诊。',
    safetyAdvice: '保持安静，避免剧烈活动；如胸痛加剧或持续不缓解请立即拨打急救电话。',
    emergencyAdvice: '建议立即就医或拨打 120，排除急性冠脉综合征。',
    followUpQuestion: '胸痛是否伴有出汗、恶心或放射痛？',
    symptoms: '胸闷胸痛 2 小时，伴出汗',
    aiSummary: '胸痛需优先排除心血管急症，建议急诊或心内科就诊，完善心电图与心肌酶。',
    createdAt: '2026-06-26T07:30:00+08:00',
  },
  {
    id: 4,
    patientId: 4,
    patientName: '赵六',
    recommendedDepartmentId: 7,
    recommendedDepartmentName: '全科',
    priority: 'LOW',
    reason: '咽痛 1 天符合上呼吸道感染表现，建议全科对症处理。',
    safetyAdvice: '多饮水，注意休息，可含服润喉片；如发热或咽痛加重请复诊。',
    emergencyAdvice: undefined,
    followUpQuestion: undefined,
    symptoms: '咽痛 1 天，伴鼻塞',
    aiSummary: '普通感冒可能，建议全科就诊，对症处理。',
    createdAt: '2026-06-25T14:00:00+08:00',
  },
  {
    id: 5,
    patientId: 5,
    patientName: '孙七',
    recommendedDepartmentId: 1,
    recommendedDepartmentName: '急诊科',
    priority: 'MEDIUM',
    reason: '腹痛伴呕吐需排除急腹症，建议急诊评估。',
    safetyAdvice: '暂禁饮食，避免使用止痛药掩盖病情。',
    emergencyAdvice: '如腹痛加剧或出现发热、便血请立即急诊。',
    followUpQuestion: '腹痛的部位和性质？是否有不洁饮食史？',
    symptoms: '腹痛伴呕吐 5 小时',
    aiSummary: '腹痛原因待查，建议急诊评估，完善腹部检查。',
    createdAt: '2026-06-26T11:00:00+08:00',
  },
  {
    id: 6,
    patientId: 6,
    patientName: '周八',
    recommendedDepartmentId: 8,
    recommendedDepartmentName: '心内科',
    priority: 'EMERGENCY',
    reason: '突发头晕伴一侧肢体无力警惕急性脑血管事件，需立即就医。',
    safetyAdvice: '保持平卧，避免头部剧烈活动；不要自行服药。',
    emergencyAdvice: '立即拨打 120 急救，警惕脑卒中，争取溶栓时间窗。',
    followUpQuestion: '症状出现的确切时间？是否有房颤病史？',
    symptoms: '突发头晕伴一侧肢体无力 1 小时',
    aiSummary: '高度警惕急性脑卒中，建议立即急诊就诊，完善头颅影像学检查。',
    createdAt: '2026-06-26T09:15:00+08:00',
  },
]

export function getMockTriages(): AdminTriageRecord[] {
  return mockTriageStore.map((t) => ({ ...t }))
}

export function getMockTriageById(id: number): AdminTriageRecord | undefined {
  const target = mockTriageStore.find((t) => t.id === id)
  return target ? { ...target } : undefined
}

// ============================================================
// §15 统计驾驶舱
// ============================================================

const mockStatisticsSummary: StatisticsSummary = {
  todayAppointments: 28,
  todayCompletedEncounters: 12,
  todayActiveDoctors: 5,
  todayAvailableDevices: 6,
  todayHighPriorityTriages: 3,
  totalPatients: 156,
  totalDoctors: 12,
  totalDepartments: 9,
  totalDevices: 8,
}

export function getMockStatisticsSummary(_query?: StatisticsQuery): StatisticsSummary {
  return { ...mockStatisticsSummary }
}

export function getMockStatisticsTrend(days: number): StatisticsTrendItem[] {
  const baseAppointments = [22, 30, 26, 33, 28, 31, 28]
  const baseCompleted = [10, 14, 11, 15, 13, 14, 12]
  const items: StatisticsTrendItem[] = []
  for (let i = days - 1; i >= 0; i--) {
    const idx = (days - 1 - i) % 7
    items.push({
      date: ymd(-i),
      appointments: baseAppointments[idx],
      completedEncounters: baseCompleted[idx],
    })
  }
  return items
}

const mockDepartmentStats: DepartmentStatItem[] = [
  { departmentId: 1, departmentName: '急诊科', appointmentCount: 45, encounterCount: 20 },
  { departmentId: 2, departmentName: '神经内科', appointmentCount: 22, encounterCount: 10 },
  { departmentId: 3, departmentName: '消化内科', appointmentCount: 18, encounterCount: 8 },
  { departmentId: 4, departmentName: '内科', appointmentCount: 52, encounterCount: 24 },
  { departmentId: 5, departmentName: '骨科', appointmentCount: 15, encounterCount: 7 },
  { departmentId: 6, departmentName: '皮肤科', appointmentCount: 8, encounterCount: 4 },
  { departmentId: 7, departmentName: '全科', appointmentCount: 12, encounterCount: 6 },
  { departmentId: 8, departmentName: '心内科', appointmentCount: 20, encounterCount: 9 },
  { departmentId: 9, departmentName: '呼吸内科', appointmentCount: 16, encounterCount: 7 },
]

export function getMockDepartmentStats(_query?: StatisticsQuery): DepartmentStatItem[] {
  return mockDepartmentStats.map((s) => ({ ...s }))
}

const mockDoctorRanking: DoctorRankingItem[] = [
  { doctorId: 401, doctorName: '钱思齐', departmentName: '内科', encounterCount: 86 },
  { doctorId: 101, doctorName: '李文博', departmentName: '急诊科', encounterCount: 72 },
  { doctorId: 301, doctorName: '赵雅琴', departmentName: '消化内科', encounterCount: 54 },
  { doctorId: 201, doctorName: '王心怡', departmentName: '神经内科', encounterCount: 48 },
  { doctorId: 402, doctorName: '孙若曦', departmentName: '内科', encounterCount: 42 },
]

export function getMockDoctorRanking(_query?: StatisticsQuery): DoctorRankingItem[] {
  return mockDoctorRanking.map((r) => ({ ...r }))
}

const mockDeviceUsageStats: DeviceUsageStatItem[] = [
  { deviceId: 601, deviceName: '数字化 X 射线摄影系统', deviceCode: 'DEV-XRAY-01', totalUsageCount: 120, totalUsageDuration: 2400, utilizationRate: 65 },
  { deviceId: 602, deviceName: '彩色多普勒超声诊断仪', deviceCode: 'DEV-US-01', totalUsageCount: 95, totalUsageDuration: 1900, utilizationRate: 52 },
  { deviceId: 603, deviceName: '十二导联心电图机', deviceCode: 'DEV-ECG-01', totalUsageCount: 80, totalUsageDuration: 800, utilizationRate: 28 },
  { deviceId: 604, deviceName: '全自动血液细胞分析仪', deviceCode: 'DEV-HEMO-01', totalUsageCount: 200, totalUsageDuration: 600, utilizationRate: 35 },
  { deviceId: 605, deviceName: '全自动生化分析仪', deviceCode: 'DEV-BIO-01', totalUsageCount: 180, totalUsageDuration: 900, utilizationRate: 42 },
  { deviceId: 606, deviceName: '凝血功能分析仪', deviceCode: 'DEV-COAG-01', totalUsageCount: 60, totalUsageDuration: 300, utilizationRate: 18 },
  { deviceId: 607, deviceName: '多参数监护仪', deviceCode: 'DEV-MON-01', totalUsageCount: 45, totalUsageDuration: 2250, utilizationRate: 78 },
  { deviceId: 608, deviceName: 'CT 扫描系统', deviceCode: 'DEV-CT-01', totalUsageCount: 30, totalUsageDuration: 1500, utilizationRate: 15 },
]

export function getMockDeviceUsageStats(_query?: StatisticsQuery): DeviceUsageStatItem[] {
  return mockDeviceUsageStats.map((s) => ({ ...s }))
}

const mockAiCallStats: AiCallStatItem = {
  totalCalls: 528,
  successCount: 502,
  failureCount: 26,
  averageDuration: 1450,
  byType: [
    { type: 'triage', count: 180, successCount: 175 },
    { type: 'diagnosis', count: 120, successCount: 110 },
    { type: 'medical-record', count: 95, successCount: 92 },
    { type: 'prescription', count: 78, successCount: 75 },
    { type: 'examination', count: 55, successCount: 50 },
  ],
}

export function getMockAiCallStats(_query?: StatisticsQuery): AiCallStatItem {
  return {
    ...mockAiCallStats,
    byType: mockAiCallStats.byType.map((b) => ({ ...b })),
  }
}

// ============================================================
// §16 日志查询
// ============================================================

const mockLoginLogs: LoginLog[] = [
  { id: 1, userId: 1, username: 'admin', role: 'ADMIN', loginTime: '2026-06-26T08:30:00+08:00', ip: '192.168.1.100', success: true, failReason: null },
  { id: 2, userId: 3, username: 'qian.siqi', role: 'DOCTOR', loginTime: '2026-06-26T07:45:00+08:00', ip: '192.168.1.201', success: true, failReason: null },
  { id: 3, userId: 5, username: 'li.wenbo', role: 'DOCTOR', loginTime: '2026-06-26T08:00:00+08:00', ip: '192.168.1.202', success: true, failReason: null },
  { id: 4, userId: 2, username: 'zhangsan', role: 'PATIENT', loginTime: '2026-06-25T19:10:00+08:00', ip: '10.0.0.55', success: true, failReason: null },
  { id: 5, userId: 0, username: 'unknown', role: 'PATIENT', loginTime: '2026-06-26T03:15:00+08:00', ip: '203.45.67.89', success: false, failReason: '用户名不存在' },
  { id: 6, userId: 9, username: 'wu.wanqing', role: 'DOCTOR', loginTime: '2026-06-26T08:20:00+08:00', ip: '192.168.1.205', success: false, failReason: '账号已停用' },
  { id: 7, userId: 10, username: 'lisi', role: 'PATIENT', loginTime: '2026-06-26T09:00:00+08:00', ip: '10.0.0.88', success: false, failReason: '账号已锁定' },
  { id: 8, userId: 4, username: 'sun.ruoxi', role: 'DOCTOR', loginTime: '2026-06-26T07:50:00+08:00', ip: '192.168.1.203', success: true, failReason: null },
  { id: 9, userId: 6, username: 'wang.xinyi', role: 'DOCTOR', loginTime: '2026-06-25T17:30:00+08:00', ip: '192.168.1.204', success: true, failReason: null },
  { id: 10, userId: 1, username: 'admin', role: 'ADMIN', loginTime: '2026-06-25T18:00:00+08:00', ip: '192.168.1.100', success: true, failReason: null },
]

const mockOperationLogs: OperationLog[] = [
  { id: 1, operatorId: 1, operatorName: '系统管理员', action: '排班取消', targetType: 'SCHEDULE', targetId: 40121, detail: '取消钱思齐 6 月 28 日上午门诊排班，原因：医生临时出差', operatedAt: '2026-06-25T10:00:00+08:00' },
  { id: 2, operatorId: 401, operatorName: '钱思齐', action: '处方作废', targetType: 'PRESCRIPTION', targetId: 4001, detail: '作废处方 4001，原因：药品剂量需调整', operatedAt: '2026-06-24T15:30:00+08:00' },
  { id: 3, operatorId: 401, operatorName: '钱思齐', action: '病历确认', targetType: 'MEDICAL_RECORD', targetId: 2001, detail: '确认就诊 1001 的电子病历', operatedAt: '2026-06-24T10:30:00+08:00' },
  { id: 4, operatorId: 1, operatorName: '系统管理员', action: '账号停用', targetType: 'USER', targetId: 9, detail: '停用用户 wu.wanqing，原因：离职', operatedAt: '2026-05-15T10:00:00+08:00' },
  { id: 5, operatorId: 1, operatorName: '系统管理员', action: '设备维修', targetType: 'DEVICE', targetId: 608, detail: 'CT 机转入维修状态，原因：球管老化', operatedAt: '2026-06-22T10:00:00+08:00' },
  { id: 6, operatorId: 1, operatorName: '系统管理员', action: '科室创建', targetType: 'DEPARTMENT', targetId: 9, detail: '新建科室：呼吸内科（DEPT_RESP）', operatedAt: '2024-01-01T00:00:00+08:00' },
  { id: 7, operatorId: 1, operatorName: '系统管理员', action: '医生创建', targetType: 'DOCTOR', targetId: 401, detail: '新建医生账号：钱思齐（内科主任医师）', operatedAt: '2024-01-15T09:00:00+08:00' },
  { id: 8, operatorId: 1, operatorName: '系统管理员', action: '排班创建', targetType: 'SCHEDULE', targetId: 40101, detail: '创建钱思齐门诊排班：6 月 26 日上午', operatedAt: '2026-06-20T00:00:00+08:00' },
  { id: 9, operatorId: 1, operatorName: '系统管理员', action: '账号解锁', targetType: 'USER', targetId: 10, detail: '解锁用户 lisi，原因：用户申诉', operatedAt: '2026-06-20T16:00:00+08:00' },
  { id: 10, operatorId: 1, operatorName: '系统管理员', action: '设备启用', targetType: 'DEVICE', targetId: 606, detail: '启用凝血功能分析仪', operatedAt: '2024-07-22T00:00:00+08:00' },
]

const mockAiInvocationLogs: AiInvocationLog[] = [
  { id: 1, callType: 'triage', provider: 'local', model: 'cloudbrain-triage-v1', businessType: 'TRIAGE', businessId: 1, success: true, duration: 1200, errorType: null, errorMessage: null, calledAt: '2026-06-24T08:00:00+08:00' },
  { id: 2, callType: 'diagnosis', provider: 'local', model: 'cloudbrain-diag-v1', businessType: 'ENCOUNTER', businessId: 1001, success: true, duration: 2500, errorType: null, errorMessage: null, calledAt: '2026-06-24T09:25:00+08:00' },
  { id: 3, callType: 'medical-record', provider: 'local', model: 'cloudbrain-mr-v1', businessType: 'ENCOUNTER', businessId: 1001, success: true, duration: 1800, errorType: null, errorMessage: null, calledAt: '2026-06-24T09:20:00+08:00' },
  { id: 4, callType: 'prescription', provider: 'local', model: 'cloudbrain-rx-v1', businessType: 'PRESCRIPTION', businessId: 4001, success: true, duration: 1500, errorType: null, errorMessage: null, calledAt: '2026-06-24T10:25:00+08:00' },
  { id: 5, callType: 'examination', provider: 'local', model: 'cloudbrain-exam-v1', businessType: 'EXAMINATION', businessId: 3001, success: true, duration: 900, errorType: null, errorMessage: null, calledAt: '2026-06-24T10:00:00+08:00' },
  { id: 6, callType: 'triage', provider: 'local', model: 'cloudbrain-triage-v1', businessType: 'TRIAGE', businessId: 3, success: true, duration: 1100, errorType: null, errorMessage: null, calledAt: '2026-06-26T07:30:00+08:00' },
  { id: 7, callType: 'diagnosis', provider: 'local', model: 'cloudbrain-diag-v1', businessType: 'ENCOUNTER', businessId: 1003, success: false, duration: 3000, errorType: 'timeout', errorMessage: 'AI 服务超时（timeout 3000ms）', calledAt: '2026-06-26T14:35:00+08:00' },
  { id: 8, callType: 'medical-record', provider: 'local', model: 'cloudbrain-mr-v1', businessType: 'ENCOUNTER', businessId: 1003, success: true, duration: 2000, errorType: null, errorMessage: null, calledAt: '2026-06-26T14:40:00+08:00' },
  { id: 9, callType: 'prescription', provider: 'local', model: 'cloudbrain-rx-v1', businessType: 'PRESCRIPTION', businessId: 4002, success: false, duration: 500, errorType: 'validation_error', errorMessage: '入参校验失败：缺少诊断信息', calledAt: '2026-06-26T15:00:00+08:00' },
  { id: 10, callType: 'examination', provider: 'local', model: 'cloudbrain-exam-v1', businessType: 'EXAMINATION', businessId: 3002, success: true, duration: 850, errorType: null, errorMessage: null, calledAt: '2026-06-26T15:20:00+08:00' },
]

export function getMockLoginLogs(): LoginLog[] {
  return mockLoginLogs.map((l) => ({ ...l }))
}

export function getMockOperationLogs(): OperationLog[] {
  return mockOperationLogs.map((l) => ({ ...l }))
}

export function getMockAiInvocationLogs(): AiInvocationLog[] {
  return mockAiInvocationLogs.map((l) => ({ ...l }))
}
