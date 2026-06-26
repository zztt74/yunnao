// 医生端 MOCK 数据与内存操作
// 设计来源：product/11_功能需求.md §8-§12、product/12_业务流程与状态机.md §6-§10
// 后端接口就绪后请删除本文件，并将 api/encounter.ts 等替换为真实调用
//
// 演示登录医生：钱思齐（doctorId=401，内科 departmentId=4）
// 真实实现应从 JWT 解析 userId 再换算 doctorId；MOCK 阶段固定返回 401

import type { AppointmentResponse, ScheduleResponse } from '@/types/appointment'
import type {
  EncounterResponse,
  EncounterDiagnosisResponse,
  AiCandidateDiagnosis,
} from '@/types/encounter'
import type {
  MedicalRecord,
  MedicalRecordAiResponse,
} from '@/types/medical-record'
import type {
  ExaminationResponse,
  ExaminationCreateRequest,
} from '@/types/examination'
import type {
  PrescriptionResponse,
  PrescriptionSaveRequest,
  PrescriptionItem,
} from '@/types/prescription'
import type {
  PatientDetailResponse,
  PatientTimelineEntry,
} from '@/types/patient'
import type {
  DeviceResponse,
  DeviceUsageResponse,
  DeviceUsageCreateRequest,
  DeviceUsageEndRequest,
  DeviceStatusHistory,
  DeviceStatus,
  DeviceCategory,
} from '@/types/device'
import type {
  DoctorProfile,
  DoctorProfileUpdateRequest,
} from '@/types/doctor'

// ============================================================
// 当前登录医生身份（演示）
// ============================================================

export const CURRENT_DOCTOR = {
  doctorId: 401,
  doctorName: '钱思齐',
  title: '主任医师',
  departmentId: 4,
  departmentName: '内科',
  userId: 401,
}

export function getCurrentDoctorId(): number {
  // 真实接口会从 JWT 中解析 userId 再换算 doctorId；
  // MOCK 阶段固定返回演示医生 401（与演示数据绑定），忽略登录返回的 userId
  return CURRENT_DOCTOR.doctorId
}

// ============================================================
// 医生个人信息（§4.3：职称、擅长方向和简介）
// ============================================================

const mockDoctorProfile: DoctorProfile = {
  doctorId: CURRENT_DOCTOR.doctorId,
  doctorName: CURRENT_DOCTOR.doctorName,
  title: CURRENT_DOCTOR.title,
  departmentId: CURRENT_DOCTOR.departmentId,
  departmentName: CURRENT_DOCTOR.departmentName,
  gender: 'MALE',
  phone: '13900004010',
  email: 'qian.siqi@cloudbrain.demo',
  specialty: '高血压、糖尿病、冠心病等慢性病综合管理；呼吸系统感染诊治',
  introduction:
    '内科主任医师，从事临床工作 20 余年，擅长慢性病长期管理与老年多病共存患者的综合诊治。',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00+08:00',
  updatedAt: '2026-06-20T00:00:00+08:00',
}

export function getMockDoctorProfile(): DoctorProfile {
  return { ...mockDoctorProfile }
}

export function updateMockDoctorProfile(
  payload: DoctorProfileUpdateRequest,
): DoctorProfile {
  mockDoctorProfile.phone = payload.phone
  mockDoctorProfile.email = payload.email
  mockDoctorProfile.specialty = payload.specialty
  mockDoctorProfile.introduction = payload.introduction
  mockDoctorProfile.updatedAt = new Date().toISOString()
  return { ...mockDoctorProfile }
}

// ============================================================
// 固定虚构药品字典（§12.6：药品使用系统内固定虚构编码、名称、规格和基础安全标签）
// ============================================================

export interface MockDrug {
  id: number
  code: string
  name: string
  category: 'WESTERN' | 'CHINESE'
  strength: string
  unit: string
  defaultDosage: string
  defaultFrequency: string
  defaultUsage: string
  defaultDuration: string
  // 基础安全标签（用于确定性规则与 AI 审核演示）
  allergyTags: string[] // 过敏原标签
  interactionCodes: string[] // 与哪些药品 code 存在相互作用
  maxDailyDosage?: string
}

export const mockDrugDictionary: MockDrug[] = [
  {
    id: 1,
    code: 'DRG_AMOXICILLIN',
    name: '阿莫西林胶囊',
    category: 'WESTERN',
    strength: '0.25g*24粒',
    unit: '盒',
    defaultDosage: '0.5g',
    defaultFrequency: 'TID',
    defaultUsage: '口服',
    defaultDuration: '5 天',
    allergyTags: ['青霉素'],
    interactionCodes: ['DRG_WARFARIN'],
    maxDailyDosage: '4g',
  },
  {
    id: 2,
    code: 'DRG_COMPOUND_COLD',
    name: '复方感冒灵颗粒',
    category: 'CHINESE',
    strength: '10g*6袋',
    unit: '盒',
    defaultDosage: '10g',
    defaultFrequency: 'TID',
    defaultUsage: '口服',
    defaultDuration: '3 天',
    allergyTags: [],
    interactionCodes: [],
  },
  {
    id: 3,
    code: 'DRG_IBUPROFEN',
    name: '布洛芬缓释胶囊',
    category: 'WESTERN',
    strength: '0.3g*20粒',
    unit: '盒',
    defaultDosage: '0.3g',
    defaultFrequency: 'BID',
    defaultUsage: '口服',
    defaultDuration: '3 天',
    allergyTags: [],
    interactionCodes: ['DRG_WARFARIN', 'DRG_ASPIRIN'],
    maxDailyDosage: '1.2g',
  },
  {
    id: 4,
    code: 'DRG_ASPIRIN',
    name: '阿司匹林肠溶片',
    category: 'WESTERN',
    strength: '0.1g*30片',
    unit: '盒',
    defaultDosage: '0.1g',
    defaultFrequency: 'QD',
    defaultUsage: '口服',
    defaultDuration: '7 天',
    allergyTags: ['阿司匹林'],
    interactionCodes: ['DRG_IBUPROFEN', 'DRG_WARFARIN'],
    maxDailyDosage: '0.3g',
  },
  {
    id: 5,
    code: 'DRG_WARFARIN',
    name: '华法林钠片',
    category: 'WESTERN',
    strength: '2.5mg*60片',
    unit: '盒',
    defaultDosage: '2.5mg',
    defaultFrequency: 'QD',
    defaultUsage: '口服',
    defaultDuration: '14 天',
    allergyTags: [],
    interactionCodes: ['DRG_AMOXICILLIN', 'DRG_IBUPROFEN', 'DRG_ASPIRIN'],
    maxDailyDosage: '5mg',
  },
  {
    id: 6,
    code: 'DRG_LOVASTATIN',
    name: '洛伐他汀片',
    category: 'WESTERN',
    strength: '20mg*28片',
    unit: '盒',
    defaultDosage: '20mg',
    defaultFrequency: 'QN',
    defaultUsage: '口服',
    defaultDuration: '14 天',
    allergyTags: [],
    interactionCodes: [],
    maxDailyDosage: '40mg',
  },
  {
    id: 7,
    code: 'DRG_AMLODIPINE',
    name: '氨氯地平片',
    category: 'WESTERN',
    strength: '5mg*28片',
    unit: '盒',
    defaultDosage: '5mg',
    defaultFrequency: 'QD',
    defaultUsage: '口服',
    defaultDuration: '14 天',
    allergyTags: [],
    interactionCodes: [],
    maxDailyDosage: '10mg',
  },
  {
    id: 8,
    code: 'DRG_CETIRIZINE',
    name: '西替利嗪片',
    category: 'WESTERN',
    strength: '10mg*12片',
    unit: '盒',
    defaultDosage: '10mg',
    defaultFrequency: 'QD',
    defaultUsage: '口服',
    defaultDuration: '7 天',
    allergyTags: [],
    interactionCodes: [],
    maxDailyDosage: '10mg',
  },
]

// ============================================================
// 医生排班（演示：今日与未来 7 天）
// ============================================================

function fmtDateTime(date: string, hhmm: string): string {
  return `${date}T${hhmm}:00+08:00`
}

function ymd(offsetDays = 0): string {
  const dt = new Date()
  dt.setDate(dt.getDate() + offsetDays)
  return dt.toISOString().slice(0, 10)
}

export const mockDoctorSchedules = [
  {
    id: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    scheduleDate: ymd(0),
    startTime: fmtDateTime(ymd(0), '08:00'),
    endTime: fmtDateTime(ymd(0), '11:30'),
    maxAppointments: 20,
    bookedCount: 6,
    remainingCount: 14,
    status: 'AVAILABLE' as const,
    cancelledAt: null,
    cancelReason: null,
    createdAt: '2026-06-20T00:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 40102,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    scheduleDate: ymd(0),
    startTime: fmtDateTime(ymd(0), '14:00'),
    endTime: fmtDateTime(ymd(0), '17:00'),
    maxAppointments: 16,
    bookedCount: 9,
    remainingCount: 7,
    status: 'AVAILABLE' as const,
    cancelledAt: null,
    cancelReason: null,
    createdAt: '2026-06-20T00:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
  {
    id: 40111,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    scheduleDate: ymd(1),
    startTime: fmtDateTime(ymd(1), '08:00'),
    endTime: fmtDateTime(ymd(1), '11:30'),
    maxAppointments: 20,
    bookedCount: 3,
    remainingCount: 17,
    status: 'AVAILABLE' as const,
    cancelledAt: null,
    cancelReason: null,
    createdAt: '2026-06-20T00:00:00+08:00',
    updatedAt: '2026-06-20T00:00:00+08:00',
  },
]

/** 查询医生排班（§8.3：医生仅查看本人排班） */
export function getMockDoctorSchedules(doctorId: number): ScheduleResponse[] {
  return mockDoctorSchedules
    .filter((s) => s.doctorId === doctorId)
    .map((s) => ({ ...s })) as ScheduleResponse[]
}

// ============================================================
// 待诊队列（医生名下 BOOKED 挂号）
// ============================================================

const now = () => new Date().toISOString()

let mockAppointmentSeq = 500
const mockDoctorAppointmentStore: AppointmentResponse[] = [
  {
    id: 501,
    patientId: 11,
    patientName: '李建国',
    scheduleId: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'D000501',
    status: 'BOOKED',
    bookedAt: '2026-06-24T15:20:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-24T15:20:00+08:00',
    updatedAt: '2026-06-24T15:20:00+08:00',
  },
  {
    id: 502,
    patientId: 12,
    patientName: '王秀兰',
    scheduleId: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'D000502',
    status: 'BOOKED',
    bookedAt: '2026-06-24T16:00:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-24T16:00:00+08:00',
    updatedAt: '2026-06-24T16:00:00+08:00',
  },
  {
    id: 503,
    patientId: 13,
    patientName: '张伟',
    scheduleId: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'D000503',
    status: 'BOOKED',
    bookedAt: '2026-06-24T16:30:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-24T16:30:00+08:00',
    updatedAt: '2026-06-24T16:30:00+08:00',
  },
  {
    id: 504,
    patientId: 14,
    patientName: '刘芳',
    scheduleId: 40102,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'D000504',
    status: 'BOOKED',
    bookedAt: '2026-06-24T17:00:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-24T17:00:00+08:00',
    updatedAt: '2026-06-24T17:00:00+08:00',
  },
  // 一条进行中（演示继续接诊）
  {
    id: 505,
    patientId: 15,
    patientName: '陈强',
    scheduleId: 40101,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'D000505',
    status: 'WAITING_EXAM',
    bookedAt: '2026-06-24T09:00:00+08:00',
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-24T09:00:00+08:00',
    updatedAt: '2026-06-24T09:30:00+08:00',
  },
]

// 患者必要信息（接诊关系成立后医生可见，§3.3、§3.5）
export interface MockPatientSummary {
  id: number
  name: string
  gender: 'MALE' | 'FEMALE'
  age: number
  phone: string
  allergies: string
  medicalHistory: string
}

export const mockPatientSummaries: Record<number, MockPatientSummary> = {
  11: { id: 11, name: '李建国', gender: 'MALE', age: 52, phone: '13800001111', allergies: '青霉素过敏', medicalHistory: '高血压 5 年' },
  12: { id: 12, name: '王秀兰', gender: 'FEMALE', age: 47, phone: '13800002222', allergies: '无', medicalHistory: '糖尿病 3 年' },
  13: { id: 13, name: '张伟', gender: 'MALE', age: 35, phone: '13800003333', allergies: '无', medicalHistory: '既往体健' },
  14: { id: 14, name: '刘芳', gender: 'FEMALE', age: 61, phone: '13800004444', allergies: '阿司匹林过敏', medicalHistory: '冠心病、房颤，长期口服华法林' },
  15: { id: 15, name: '陈强', gender: 'MALE', age: 28, phone: '13800005555', allergies: '无', medicalHistory: '既往体健' },
}

export function getDoctorPendingAppointments(doctorId: number): AppointmentResponse[] {
  // 待诊队列：状态为 BOOKED 的挂号（§7.3、§8.3）
  return mockDoctorAppointmentStore
    .filter((a) => a.doctorId === doctorId && a.status === 'BOOKED')
    .map((a) => ({ ...a }))
    .sort((a, b) => new Date(a.bookedAt).getTime() - new Date(b.bookedAt).getTime())
}

export function getDoctorInprogressAppointments(doctorId: number): AppointmentResponse[] {
  // 进行中 / 等待检查（演示继续接诊）
  return mockDoctorAppointmentStore
    .filter(
      (a) =>
        a.doctorId === doctorId &&
        (a.status === 'IN_PROGRESS' || a.status === 'WAITING_EXAM'),
    )
    .map((a) => ({ ...a }))
}

export function getDoctorTodayAppointments(doctorId: number): AppointmentResponse[] {
  const today = ymd(0)
  return mockDoctorAppointmentStore
    .filter((a) => a.doctorId === doctorId)
    .map((a) => ({ ...a }))
    .filter((a) => {
      // 通过 scheduleId 末位判断上午/下午，这里简化为全部今日
      const sch = mockDoctorSchedules.find((s) => s.id === a.scheduleId)
      return sch?.scheduleDate === today
    })
}

export function getAppointmentById(id: number): AppointmentResponse | undefined {
  const target = mockDoctorAppointmentStore.find((a) => a.id === id)
  return target ? { ...target } : undefined
}

function patchAppointment(id: number, patch: Partial<AppointmentResponse>): AppointmentResponse | null {
  const target = mockDoctorAppointmentStore.find((a) => a.id === id)
  if (!target) return null
  Object.assign(target, patch, { updatedAt: now() })
  return { ...target }
}

// ============================================================
// 就诊 Encounter 内存 store
// ============================================================

let mockEncounterSeq = 1000
const mockEncounterStore: EncounterResponse[] = [
  // 演示种子：陈强的就诊处于 WAITING_EXAM（对应挂号 505）
  {
    id: 1001,
    appointmentId: 505,
    patientId: 15,
    patientName: '陈强',
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    status: 'WAITING_EXAM',
    startedAt: '2026-06-24T09:10:00+08:00',
    waitingExamAt: '2026-06-24T09:30:00+08:00',
    completedAt: null,
    cancelledAt: null,
    cancelReason: null,
    createdAt: '2026-06-24T09:10:00+08:00',
    updatedAt: '2026-06-24T09:30:00+08:00',
  },
]

export function getEncounterById(id: number): EncounterResponse | undefined {
  const target = mockEncounterStore.find((e) => e.id === id)
  return target ? { ...target } : undefined
}

export function getEncounterByAppointmentId(appointmentId: number): EncounterResponse | undefined {
  const target = mockEncounterStore.find((e) => e.appointmentId === appointmentId)
  return target ? { ...target } : undefined
}

export function getDoctorEncounters(doctorId: number): EncounterResponse[] {
  return mockEncounterStore
    .filter((e) => e.doctorId === doctorId)
    .map((e) => ({ ...e }))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
}

/**
 * 开始接诊（CREATED → IN_PROGRESS）
 * - 校验：挂号属于当前医生；挂号状态为 BOOKED 或 CHECKED_IN；
 *   一个挂号只能关联一个有效 Encounter（§6 转换约束）
 * - 同步：Appointment 状态更新为 IN_PROGRESS（§6）
 */
export function startEncounter(
  appointmentId: number,
  doctorId: number,
): EncounterResponse {
  const appt = mockDoctorAppointmentStore.find((a) => a.id === appointmentId)
  if (!appt) throw new Error('挂号不存在')
  if (appt.doctorId !== doctorId) throw new Error('非本人排班患者，无权接诊')
  if (appt.status !== 'BOOKED' && appt.status !== 'CHECKED_IN') {
    throw new Error('当前挂号状态不可开始接诊')
  }
  // 一个挂号只能关联一个有效 Encounter
  const existing = mockEncounterStore.find(
    (e) =>
      e.appointmentId === appointmentId &&
      e.status !== 'COMPLETED' &&
      e.status !== 'CANCELLED',
  )
  if (existing) {
    throw new Error('该挂号已存在进行中的就诊，不可重复开始')
  }

  mockEncounterSeq++
  const ts = now()
  const encounter: EncounterResponse = {
    id: mockEncounterSeq,
    appointmentId,
    patientId: appt.patientId,
    patientName: appt.patientName,
    doctorId: appt.doctorId,
    doctorName: appt.doctorName,
    departmentId: appt.departmentId,
    departmentName: appt.departmentName,
    status: 'IN_PROGRESS',
    startedAt: ts,
    waitingExamAt: null,
    completedAt: null,
    cancelledAt: null,
    cancelReason: null,
    createdAt: ts,
    updatedAt: ts,
  }
  mockEncounterStore.push(encounter)
  // 同步挂号状态
  patchAppointment(appointmentId, { status: 'IN_PROGRESS', checkInTime: ts })
  return { ...encounter }
}

/**
 * 等待检查（IN_PROGRESS → WAITING_EXAM），同步挂号
 */
export function waitForExam(encounterId: number): EncounterResponse {
  const enc = mockEncounterStore.find((e) => e.id === encounterId)
  if (!enc) throw new Error('就诊不存在')
  if (enc.status !== 'IN_PROGRESS') {
    throw new Error('ENCOUNTER_STATUS_CONFLICT：仅 IN_PROGRESS 可转为等待检查')
  }
  const ts = now()
  Object.assign(enc, { status: 'WAITING_EXAM', waitingExamAt: ts, updatedAt: ts })
  patchAppointment(enc.appointmentId, { status: 'WAITING_EXAM' })
  return { ...enc }
}

/**
 * 继续诊疗（WAITING_EXAM → IN_PROGRESS），同步挂号
 */
export function resumeEncounter(encounterId: number): EncounterResponse {
  const enc = mockEncounterStore.find((e) => e.id === encounterId)
  if (!enc) throw new Error('就诊不存在')
  if (enc.status !== 'WAITING_EXAM') {
    throw new Error('ENCOUNTER_STATUS_CONFLICT：仅 WAITING_EXAM 可继续诊疗')
  }
  const ts = now()
  Object.assign(enc, { status: 'IN_PROGRESS', updatedAt: ts })
  patchAppointment(enc.appointmentId, { status: 'IN_PROGRESS' })
  return { ...enc }
}

/**
 * 完成就诊（IN_PROGRESS → COMPLETED）
 * 前置条件（§6 转换约束）：
 * - 已确认正式病历
 * - 至少一条医生最终诊断（FINAL + DOCTOR）
 * - 所有检查检验为 REVIEWED 或 CANCELLED（不得有 ORDERED/IN_PROGRESS/RESULT_ENTERED）
 * - 处方（如有）状态为 CONFIRMED 或 VOIDED
 */
export function completeEncounter(encounterId: number): EncounterResponse {
  const enc = mockEncounterStore.find((e) => e.id === encounterId)
  if (!enc) throw new Error('就诊不存在')
  if (enc.status !== 'IN_PROGRESS') {
    throw new Error('ENCOUNTER_STATUS_CONFLICT：仅 IN_PROGRESS 可完成就诊')
  }

  // 病历
  const mr = mockMedicalRecordStore.find((m) => m.encounterId === encounterId)
  if (!mr || mr.status !== 'CONFIRMED') {
    throw new Error('MEDICAL_RECORD_NOT_CONFIRMED：完成就诊前需确认正式病历')
  }
  // 诊断
  const diags = mockDiagnosisStore.filter((d) => d.encounterId === encounterId)
  if (!diags.some((d) => d.type === 'FINAL' && d.source === 'DOCTOR')) {
    throw new Error('ENCOUNTER_FINAL_DIAGNOSIS_REQUIRED：需至少一条医生最终诊断')
  }
  // 检查检验
  const exams = mockExaminationStore.filter((x) => x.encounterId === encounterId)
  const pendingExams = exams.filter(
    (x) => x.status === 'ORDERED' || x.status === 'IN_PROGRESS' || x.status === 'RESULT_ENTERED',
  )
  if (pendingExams.length > 0) {
    throw new Error(
      `ENCOUNTER_EXAMINATION_PENDING：存在 ${pendingExams.length} 项未完成或未审核的检查检验`,
    )
  }
  // 处方
  const pres = mockPrescriptionStore.filter((p) => p.encounterId === encounterId)
  const badPres = pres.filter((p) => p.status === 'DRAFT')
  if (badPres.length > 0) {
    throw new Error('PRESCRIPTION_STATUS_INVALID：存在未确认的处方草稿')
  }

  const ts = now()
  Object.assign(enc, { status: 'COMPLETED', completedAt: ts, updatedAt: ts })
  patchAppointment(enc.appointmentId, { status: 'COMPLETED' })
  return { ...enc }
}

/**
 * 取消就诊（仅 CREATED → CANCELLED，§6）
 */
export function cancelEncounter(encounterId: number, reason?: string): EncounterResponse {
  const enc = mockEncounterStore.find((e) => e.id === encounterId)
  if (!enc) throw new Error('就诊不存在')
  if (enc.status !== 'CREATED') {
    throw new Error('ENCOUNTER_STATUS_CONFLICT：仅 CREATED 状态可取消')
  }
  const ts = now()
  Object.assign(enc, { status: 'CANCELLED', cancelledAt: ts, cancelReason: reason ?? null, updatedAt: ts })
  return { ...enc }
}

// ============================================================
// 诊断 store（§7、§9）
// ============================================================

let mockDiagnosisSeq = 1
const mockDiagnosisStore: EncounterDiagnosisResponse[] = [
  // 演示种子：陈强就诊 1001 已有一条医生最终诊断
  {
    id: 1,
    encounterId: 1001,
    diagnosisCode: 'J00',
    diagnosisName: '急性鼻咽炎',
    type: 'FINAL',
    source: 'DOCTOR',
    aiInvocationId: null,
    doctorId: 401,
    confirmedAt: '2026-06-24T09:25:00+08:00',
    notes: '结合症状与体征，考虑普通感冒。',
    createdAt: '2026-06-24T09:25:00+08:00',
    updatedAt: '2026-06-24T09:25:00+08:00',
  },
]

export function getEncounterDiagnoses(encounterId: number): EncounterDiagnosisResponse[] {
  return mockDiagnosisStore
    .filter((d) => d.encounterId === encounterId)
    .map((d) => ({ ...d }))
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
}

export function addAIDiagnosis(
  encounterId: number,
  payload: { diagnosisCode: string; diagnosisName: string; notes?: string },
): EncounterDiagnosisResponse {
  // 诊断隔离（§9.4）：AI 只能产生 PRELIMINARY + AI_SUGGESTION
  const ts = now()
  mockDiagnosisSeq++
  const diag: EncounterDiagnosisResponse = {
    id: mockDiagnosisSeq,
    encounterId,
    diagnosisCode: payload.diagnosisCode,
    diagnosisName: payload.diagnosisName,
    type: 'PRELIMINARY',
    source: 'AI_SUGGESTION',
    aiInvocationId: 9000 + mockDiagnosisSeq,
    doctorId: null,
    confirmedAt: null,
    notes: payload.notes ?? null,
    createdAt: ts,
    updatedAt: ts,
  }
  mockDiagnosisStore.push(diag)
  return { ...diag }
}

export function addDoctorDiagnosis(
  encounterId: number,
  doctorId: number,
  payload: { diagnosisCode: string; diagnosisName: string; notes?: string },
): EncounterDiagnosisResponse {
  // 诊断隔离（§9.4）：医生诊断必须 FINAL + DOCTOR
  const ts = now()
  mockDiagnosisSeq++
  const diag: EncounterDiagnosisResponse = {
    id: mockDiagnosisSeq,
    encounterId,
    diagnosisCode: payload.diagnosisCode,
    diagnosisName: payload.diagnosisName,
    type: 'FINAL',
    source: 'DOCTOR',
    aiInvocationId: null,
    doctorId,
    confirmedAt: ts,
    notes: payload.notes ?? null,
    createdAt: ts,
    updatedAt: ts,
  }
  mockDiagnosisStore.push(diag)
  return { ...diag }
}

// ============================================================
// AI 辅助诊断（§9）：基于主诉关键词生成候选诊断
// ============================================================

interface AiDiagRule {
  keywords: string[]
  candidates: AiCandidateDiagnosis[]
}

const aiDiagRules: AiDiagRule[] = [
  {
    keywords: ['发热', '发烧', '咳嗽', '咳痰', '咽痛', '鼻塞', '感冒'],
    candidates: [
      {
        diagnosisCode: 'J00',
        diagnosisName: '急性鼻咽炎（普通感冒）',
        reason: '发热、咳嗽、咽痛符合上呼吸道感染典型表现，病程短，多为病毒感染。',
        confidence: 0.82,
        riskFactors: ['体温持续 > 39℃', '呼吸困难', '意识改变'],
        informationGaps: ['是否接触类似患者', '是否有基础心肺疾病'],
        recommendedExaminations: ['血常规', 'C 反应蛋白', '胸部 X 光（如发热持续）'],
      },
      {
        diagnosisCode: 'J06.9',
        diagnosisName: '急性上呼吸道感染',
        reason: '症状组合提示上呼吸道感染，需排除下呼吸道受累。',
        confidence: 0.65,
        riskFactors: ['老年人或免疫低下者需警惕继发感染'],
        informationGaps: ['肺部听诊情况'],
        recommendedExaminations: ['血常规', '胸部 X 光'],
      },
    ],
  },
  {
    keywords: ['头痛', '头晕', '眩晕', '偏头痛'],
    candidates: [
      {
        diagnosisCode: 'G44.1',
        diagnosisName: '血管性头痛',
        reason: '反复发作性头痛伴眩晕，需排查血管性因素。',
        confidence: 0.55,
        riskFactors: ['突发剧烈头痛', '伴呕吐、意识改变'],
        informationGaps: ['头痛持续时间与诱因', '血压情况'],
        recommendedExaminations: ['血压测量', '头颅 CT（若高危）'],
      },
    ],
  },
  {
    keywords: ['腹痛', '腹泻', '恶心', '呕吐', '胃痛'],
    candidates: [
      {
        diagnosisCode: 'K30',
        diagnosisName: '功能性消化不良',
        reason: '腹部症状伴恶心，需先排除器质性病变。',
        confidence: 0.5,
        riskFactors: ['持续剧烈腹痛', '便血', '体重明显下降'],
        informationGaps: ['症状与进食关系', '大便性状'],
        recommendedExaminations: ['血常规', '腹部 B 超', '便常规'],
      },
      {
        diagnosisCode: 'A09',
        diagnosisName: '感染性腹泻',
        reason: '腹泻伴恶心，可能为感染性肠炎。',
        confidence: 0.6,
        riskFactors: ['脱水征象', '高热'],
        informationGaps: ['有不洁饮食史', '腹泻次数'],
        recommendedExaminations: ['便常规', '血常规', '电解质'],
      },
    ],
  },
  {
    keywords: ['胸闷', '心悸', '胸痛', '气短'],
    candidates: [
      {
        diagnosisCode: 'I20.9',
        diagnosisName: '心绞痛',
        reason: '胸闷胸痛需优先排除心血管病因。',
        confidence: 0.6,
        riskFactors: ['高龄', '高血压糖尿病', '放射痛', '活动后加重'],
        informationGaps: ['疼痛性质与持续时间', '心电图情况'],
        recommendedExaminations: ['心电图', '心肌酶', '血常规'],
      },
    ],
  },
]

const defaultAiCandidates: AiCandidateDiagnosis[] = [
  {
    diagnosisCode: 'R69',
    diagnosisName: '病因未明症状（待排查）',
    reason: '当前信息不足以给出明确候选诊断，建议补充问诊并完善基础检查。',
    confidence: 0.3,
    riskFactors: ['症状持续加重'],
    informationGaps: ['主诉细节不足', '既往史不详'],
    recommendedExaminations: ['血常规', '基础体格检查'],
  },
]

export function generateAiDiagnosis(ctx: {
  encounterId: number
  chiefComplaint: string
  presentIllness?: string
  pastHistory?: string
  physicalExam?: string
}): { candidates: AiCandidateDiagnosis[]; aiStatus: 'SUCCESS' | 'FAILED' } {
  const text = `${ctx.chiefComplaint} ${ctx.presentIllness ?? ''} ${ctx.pastHistory ?? ''} ${ctx.physicalExam ?? ''}`
  for (const rule of aiDiagRules) {
    if (rule.keywords.some((kw) => text.includes(kw))) {
      return { candidates: rule.candidates, aiStatus: 'SUCCESS' }
    }
  }
  return { candidates: defaultAiCandidates, aiStatus: 'SUCCESS' }
}

// ============================================================
// 病历 store（§11）
// ============================================================

let mockMedicalRecordSeq = 2000
const mockMedicalRecordStore: MedicalRecord[] = [
  // 演示种子：陈强就诊 1001 已有草稿病历（AI 生成未确认）
  {
    id: 2001,
    encounterId: 1001,
    patientId: 15,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentName: '内科',
    chiefComplaint: '发热伴咳嗽 3 天',
    presentIllness:
      '患者 3 天前受凉后出现发热，体温最高 38.5℃，伴阵发性咳嗽、少量白痰，自服退烧药体温可暂降，仍有反复。',
    pastHistory: '既往体健，否认慢性病史。',
    physicalExam: '咽部充血，双肺呼吸音粗，未闻及明显干湿啰音。心律齐。',
    preliminaryDiagnosis: '急性上呼吸道感染',
    treatmentAdvice: '多饮水，注意休息，对症退热，必要时复查血常规。',
    status: 'AI_GENERATED',
    diagnoses: [],
    encounterDate: '2026-06-24T09:10:00+08:00',
    confirmedAt: null,
    createdAt: '2026-06-24T09:20:00+08:00',
    updatedAt: '2026-06-24T09:20:00+08:00',
  },
]

export function getEncounterMedicalRecord(encounterId: number): MedicalRecord | undefined {
  const target = mockMedicalRecordStore.find((m) => m.encounterId === encounterId)
  return target ? { ...target } : undefined
}

/**
 * AI 生成病历草稿（§11.4）：仅生成草稿，不写入正式病历
 */
export function generateMedicalRecordDraft(
  encounterId: number,
  ctx: { chiefComplaint: string; presentIllness?: string; pastHistory?: string; physicalExam?: string },
): MedicalRecordAiResponse {
  // 基于 问诊上下文生成结构化草稿（演示）
  const cc = ctx.chiefComplaint || '（未提供主诉）'
  const pi =
    ctx.presentIllness ||
    `患者因"${cc}"就诊，起病情况及伴随症状待补充。`
  const ph = ctx.pastHistory || '既往史不详。'
  const pe = ctx.physicalExam || '体格检查待完善。'

  // 简单推断初步诊断
  let pd = '待明确'
  if (/发热|咳嗽|咳痰|咽痛|感冒/.test(cc + pi)) {
    pd = '急性上呼吸道感染'
  } else if (/头痛|头晕/.test(cc + pi)) {
    pd = '头痛待查'
  } else if (/腹痛|腹泻|恶心/.test(cc + pi)) {
    pd = '急性胃肠炎'
  }

  return {
    encounterId,
    chiefComplaint: cc,
    presentIllness: pi,
    pastHistory: ph,
    physicalExam: pe,
    preliminaryDiagnosis: pd,
    treatmentAdvice: '注意休息，多饮水，对症处理，如症状加重请及时复诊。',
    aiStatus: 'SUCCESS',
  }
}

/**
 * 保存病历草稿（医生编辑后），状态 DRAFT 或 AI_GENERATED
 */
export function saveMedicalRecord(
  encounterId: number,
  encounter: EncounterResponse,
  payload: {
    chiefComplaint: string
    presentIllness: string
    pastHistory?: string
    physicalExam?: string
    preliminaryDiagnosis?: string
    treatmentAdvice?: string
    status: 'DRAFT' | 'AI_GENERATED' | 'CONFIRMED'
  },
): MedicalRecord {
  const existing = mockMedicalRecordStore.find((m) => m.encounterId === encounterId)
  const ts = now()
  if (existing) {
    Object.assign(existing, {
      chiefComplaint: payload.chiefComplaint,
      presentIllness: payload.presentIllness,
      pastHistory: payload.pastHistory,
      physicalExam: payload.physicalExam,
      preliminaryDiagnosis: payload.preliminaryDiagnosis,
      treatmentAdvice: payload.treatmentAdvice,
      status: payload.status,
      confirmedAt: payload.status === 'CONFIRMED' ? ts : existing.confirmedAt,
      updatedAt: ts,
    })
    return { ...existing }
  }
  mockMedicalRecordSeq++
  const mr: MedicalRecord = {
    id: mockMedicalRecordSeq,
    encounterId,
    patientId: encounter.patientId,
    doctorId: encounter.doctorId,
    doctorName: encounter.doctorName,
    departmentName: encounter.departmentName,
    chiefComplaint: payload.chiefComplaint,
    presentIllness: payload.presentIllness,
    pastHistory: payload.pastHistory,
    physicalExam: payload.physicalExam,
    preliminaryDiagnosis: payload.preliminaryDiagnosis,
    treatmentAdvice: payload.treatmentAdvice,
    status: payload.status,
    diagnoses: [],
    encounterDate: encounter.startedAt ?? encounter.createdAt,
    confirmedAt: payload.status === 'CONFIRMED' ? ts : null,
    createdAt: ts,
    updatedAt: ts,
  }
  mockMedicalRecordStore.push(mr)
  return { ...mr }
}

// ============================================================
// 检查检验 store（§10）
// ============================================================

let mockExaminationSeq = 3000
const mockExaminationStore: ExaminationResponse[] = [
  // 演示种子：陈强就诊 1001 已开血常规（RESULT_ENTERED 待审核）
  {
    id: 3001,
    encounterId: 1001,
    patientId: 15,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentName: '内科',
    type: 'LABORATORY',
    itemName: '血常规',
    purpose: '排查感染',
    orderedAt: '2026-06-24T09:25:00+08:00',
    reportedAt: '2026-06-24T09:50:00+08:00',
    reviewedAt: null,
    reporterName: '检验师 周技师',
    status: 'RESULT_ENTERED',
    labItems: [
      { id: 31, itemName: '白细胞计数', resultValue: '11.2', unit: '10^9/L', referenceRange: '4.0-10.0', abnormalFlag: 'HIGH' },
      { id: 32, itemName: '中性粒细胞百分比', resultValue: '80.1', unit: '%', referenceRange: '40-75', abnormalFlag: 'HIGH' },
      { id: 33, itemName: '血红蛋白', resultValue: '142', unit: 'g/L', referenceRange: '120-160', abnormalFlag: 'NORMAL' },
    ],
    impression: '白细胞及中性粒细胞比例偏高，提示细菌感染可能。',
    createdAt: '2026-06-24T09:25:00+08:00',
    updatedAt: '2026-06-24T09:50:00+08:00',
  },
]

export function getEncounterExaminations(encounterId: number): ExaminationResponse[] {
  return mockExaminationStore
    .filter((x) => x.encounterId === encounterId)
    .map((x) => ({ ...x }))
    .sort((a, b) => new Date(a.orderedAt).getTime() - new Date(b.orderedAt).getTime())
}

export function createExamination(
  encounter: EncounterResponse,
  payload: ExaminationCreateRequest,
): ExaminationResponse {
  const ts = now()
  mockExaminationSeq++
  const exam: ExaminationResponse = {
    id: mockExaminationSeq,
    encounterId: encounter.id,
    patientId: encounter.patientId,
    doctorId: encounter.doctorId,
    doctorName: encounter.doctorName,
    departmentName: encounter.departmentName,
    type: payload.type,
    itemName: payload.itemName,
    purpose: payload.purpose ?? '',
    orderedAt: ts,
    reportedAt: null,
    reviewedAt: null,
    reporterName: null,
    status: 'ORDERED',
    createdAt: ts,
    updatedAt: ts,
  }
  mockExaminationStore.push(exam)
  return { ...exam }
}

/**
 * 模拟结果录入（ORDERED → IN_PROGRESS → RESULT_ENTERED）
 * 设计文档 §10.4：管理员或模拟人员录入结果。此处为演示提供入口，标注 MOCK。
 */
export function simulateEnterExaminationResult(id: number): ExaminationResponse {
  const exam = mockExaminationStore.find((x) => x.id === id)
  if (!exam) throw new Error('检查检验记录不存在')
  if (exam.status !== 'ORDERED' && exam.status !== 'IN_PROGRESS') {
    throw new Error('当前状态不可录入结果')
  }
  const ts = now()
  if (exam.type === 'LABORATORY') {
    exam.labItems = [
      { id: Math.floor(Math.random() * 10000), itemName: '白细胞计数', resultValue: '9.6', unit: '10^9/L', referenceRange: '4.0-10.0', abnormalFlag: 'NORMAL' },
      { id: Math.floor(Math.random() * 10000), itemName: '中性粒细胞百分比', resultValue: '76.4', unit: '%', referenceRange: '40-75', abnormalFlag: 'HIGH' },
      { id: Math.floor(Math.random() * 10000), itemName: '淋巴细胞百分比', resultValue: '17.8', unit: '%', referenceRange: '20-50', abnormalFlag: 'LOW' },
      { id: Math.floor(Math.random() * 10000), itemName: '血红蛋白', resultValue: '140', unit: 'g/L', referenceRange: '120-160', abnormalFlag: 'NORMAL' },
    ]
    exam.impression = '白细胞总数正常，中性粒细胞比例偏高，提示可能存在感染。'
  } else {
    exam.findings = '检查所见：未见明显异常影像征象。'
    exam.impression = '印象：未见明显异常。'
  }
  exam.reporterName = '模拟录入（演示）'
  exam.reportedAt = ts
  exam.status = 'RESULT_ENTERED'
  exam.updatedAt = ts
  return { ...exam }
}

/**
 * 医生审核结果（RESULT_ENTERED → REVIEWED，§10 状态机）
 */
export function reviewExamination(id: number): ExaminationResponse {
  const exam = mockExaminationStore.find((x) => x.id === id)
  if (!exam) throw new Error('检查检验记录不存在')
  if (exam.status !== 'RESULT_ENTERED') {
    throw new Error('仅 RESULT_ENTERED 状态可审核')
  }
  const ts = now()
  Object.assign(exam, { status: 'REVIEWED', reviewedAt: ts, updatedAt: ts })
  return { ...exam }
}

/**
 * AI 解读检查检验（§10.3）：不修改原始结果，仅追加 aiInterpretation
 */
export function aiInterpretExamination(id: number): ExaminationResponse {
  const exam = mockExaminationStore.find((x) => x.id === id)
  if (!exam) throw new Error('检查检验记录不存在')
  if (exam.status !== 'REVIEWED') {
    throw new Error('仅 REVIEWED 结果可进行 AI 解读')
  }
  const ts = now()
  let interp = '结果整体在可解释范围内，建议结合临床综合判断。'
  if (exam.labItems?.some((i) => i.abnormalFlag === 'HIGH' && i.itemName.includes('白细胞'))) {
    interp = '白细胞相关指标偏高，结合临床表现考虑感染可能，建议对症治疗并动态复查。'
  }
  Object.assign(exam, { aiInterpretation: interp, updatedAt: ts })
  return { ...exam }
}

// ============================================================
// 处方 store（§12）
// ============================================================

let mockPrescriptionSeq = 4000
const mockPrescriptionStore: PrescriptionResponse[] = []

export function getEncounterPrescription(encounterId: number): PrescriptionResponse | undefined {
  const target = mockPrescriptionStore.find((p) => p.encounterId === encounterId)
  return target ? { ...target } : undefined
}

function toPrescriptionItem(req: PrescriptionSaveRequest['items'][number]): PrescriptionItem {
  return {
    id: Math.floor(Math.random() * 100000),
    drugId: req.drugId,
    drugCode: req.drugCode,
    drugName: req.drugName,
    strength: req.strength,
    unit: req.unit,
    dosage: req.dosage,
    frequency: req.frequency,
    usage: req.usage,
    duration: req.duration,
    remark: req.remark,
  }
}

/**
 * 创建或更新处方草稿（DRAFT，§12.4）
 */
export function savePrescriptionDraft(
  encounter: EncounterResponse,
  payload: PrescriptionSaveRequest,
): PrescriptionResponse {
  const existing = mockPrescriptionStore.find((p) => p.encounterId === encounter.id)
  const ts = now()
  const items = payload.items.map(toPrescriptionItem)
  if (existing) {
    if (existing.status !== 'DRAFT') {
      throw new Error('处方已确认或已作废，不可再修改')
    }
    Object.assign(existing, {
      diagnosis: payload.diagnosis,
      items,
      remark: payload.remark,
      updatedAt: ts,
    })
    return { ...existing }
  }
  mockPrescriptionSeq++
  const pres: PrescriptionResponse = {
    id: mockPrescriptionSeq,
    encounterId: encounter.id,
    patientId: encounter.patientId,
    patientName: encounter.patientName,
    doctorId: encounter.doctorId,
    doctorName: encounter.doctorName,
    departmentName: encounter.departmentName,
    diagnosis: payload.diagnosis,
    items,
    status: 'DRAFT',
    voidedReason: null,
    voidedAt: null,
    remark: payload.remark,
    aiReview: null,
    aiReviewStatus: 'NOT_REQUESTED',
    confirmedAt: null,
    createdAt: ts,
    updatedAt: ts,
  }
  mockPrescriptionStore.push(pres)
  return { ...pres }
}

/**
 * AI 处方审核（§12.4、§12.6）
 * - 先基于确定性规则表执行过敏、相互作用、剂量检查（不被 AI 覆盖）
 * - 再生成 AI 解释与建议
 */
export function aiReviewPrescription(
  prescriptionId: number,
  patientAllergies: string,
): PrescriptionResponse {
  const pres = mockPrescriptionStore.find((p) => p.id === prescriptionId)
  if (!pres) throw new Error('处方不存在')
  if (pres.status !== 'DRAFT') {
    throw new Error('仅 DRAFT 处方可发起 AI 审核')
  }
  const ts = now()

  const warnings: string[] = []
  let riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' = 'LOW'

  // 1) 过敏检查（确定性规则）
  const allergySet = patientAllergies
    .split(/[、,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
  for (const item of pres.items) {
    const drug = mockDrugDictionary.find((d) => d.id === item.drugId)
    if (!drug) continue
    for (const tag of drug.allergyTags) {
      if (allergySet.some((a) => a.includes(tag) || tag.includes(a))) {
        warnings.push(`[过敏禁忌] ${item.drugName} 含过敏原「${tag}」，与患者过敏史冲突，禁止使用。`)
        riskLevel = 'HIGH'
      }
    }
  }

  // 2) 相互作用检查（确定性规则）
  const codes = pres.items.map((i) => i.drugCode)
  for (const item of pres.items) {
    const drug = mockDrugDictionary.find((d) => d.id === item.drugId)
    if (!drug) continue
    for (const interCode of drug.interactionCodes) {
      if (codes.includes(interCode) && interCode !== item.drugCode) {
        const other = pres.items.find((i) => i.drugCode === interCode)
        warnings.push(
          `[相互作用] ${item.drugName} 与 ${other?.drugName ?? interCode} 存在相互作用，请评估风险或调整用药。`,
        )
        if (riskLevel !== 'HIGH') riskLevel = 'MEDIUM'
      }
    }
  }

  // 3) 剂量检查（确定性规则：超过 maxDailyDosage 视为高风险，简化估算）
  for (const item of pres.items) {
    const drug = mockDrugDictionary.find((d) => d.id === item.drugId)
    if (!drug?.maxDailyDosage) continue
    // 仅做演示性提示，不精确换算
    const freqFactor = item.frequency === 'TID' ? 3 : item.frequency === 'BID' ? 2 : 1
    const perDose = parseFloat(item.dosage) || 0
    if (perDose * freqFactor > parseFloat(drug.maxDailyDosage) * 1.05) {
      warnings.push(`[剂量提示] ${item.drugName} 估算日剂量可能超过上限 ${drug.maxDailyDosage}，请核对。`)
      if (riskLevel !== 'HIGH') riskLevel = 'MEDIUM'
    }
  }

  if (warnings.length === 0) {
    warnings.push('未发现过敏、相互作用或剂量异常。')
  }

  const advice =
    riskLevel === 'HIGH'
      ? '存在高风险禁忌，强烈建议调整处方后再行确认。'
      : riskLevel === 'MEDIUM'
        ? '存在需关注的风险提示，请医生评估后确认。'
        : '用药组合整体合理，可按需确认。'

  Object.assign(pres, {
    aiReview: { riskLevel, warnings, advice, reviewedAt: ts },
    aiReviewStatus: 'REVIEWED',
    updatedAt: ts,
  })
  return { ...pres }
}

/**
 * 医生确认处方（DRAFT → CONFIRMED，§12.6）
 * - 高风险需二次确认（页面通过 confirm 参数传入）
 */
export function confirmPrescription(
  prescriptionId: number,
  forceHighRisk = false,
): PrescriptionResponse {
  const pres = mockPrescriptionStore.find((p) => p.id === prescriptionId)
  if (!pres) throw new Error('处方不存在')
  if (pres.status !== 'DRAFT') {
    throw new Error('仅 DRAFT 处方可确认')
  }
  if (pres.aiReview?.riskLevel === 'HIGH' && !forceHighRisk) {
    throw new Error('PRESCRIPTION_HIGH_RISK：高风险处方需二次确认')
  }
  const ts = now()
  Object.assign(pres, { status: 'CONFIRMED', confirmedAt: ts, updatedAt: ts })
  return { ...pres }
}

/**
 * 作废处方（CONFIRMED → VOIDED，§9 状态机）
 */
export function voidPrescription(
  prescriptionId: number,
  reason: string,
): PrescriptionResponse {
  const pres = mockPrescriptionStore.find((p) => p.id === prescriptionId)
  if (!pres) throw new Error('处方不存在')
  if (pres.status !== 'CONFIRMED') {
    throw new Error('仅 CONFIRMED 处方可作废')
  }
  if (!reason.trim()) throw new Error('请填写作废原因')
  const ts = now()
  Object.assign(pres, { status: 'VOIDED', voidedReason: reason, voidedAt: ts, updatedAt: ts })
  return { ...pres }
}

// ============================================================
// 工具：模拟 AI 失败（演示降级，§14）
// ============================================================

export function shouldSimulateAiFailure(): boolean {
  // 通过 sessionStorage 标记模拟下一次 AI 调用失败（演示降级用）
  return sessionStorage.getItem('cloud-brain.mock-ai-fail') === '1'
}

// ============================================================
// 患者详情与诊疗时间线（§3.3、§3.4）
// 接诊关系成立后医生可见患者必要信息；时间线按时间倒序串联历次诊疗
// ============================================================

// 患者扩展档案补充（mockPatientSummaries 只含必要字段，这里补全详情）
const mockPatientDetailExtra: Record<
  number,
  { birthDate: string; address: string; emergencyContact: string; emergencyPhone: string; createdAt: string }
> = {
  11: { birthDate: '1974-03-12', address: '北京市朝阳区幸福路 12 号', emergencyContact: '李母', emergencyPhone: '13800001112', createdAt: '2024-05-10T08:30:00+08:00' },
  12: { birthDate: '1979-07-25', address: '北京市海淀区学院路 88 号', emergencyContact: '王夫', emergencyPhone: '13800002223', createdAt: '2024-08-15T09:00:00+08:00' },
  13: { birthDate: '1991-11-03', address: '北京市西城区西直门内大街 33 号', emergencyContact: '张父', emergencyPhone: '13800003334', createdAt: '2025-01-20T10:15:00+08:00' },
  14: { birthDate: '1965-04-18', address: '北京市东城区东直门外大街 6 号', emergencyContact: '刘女', emergencyPhone: '13800004445', createdAt: '2023-11-02T14:00:00+08:00' },
  15: { birthDate: '1998-09-30', address: '北京市丰台区南三环西路 21 号', emergencyContact: '陈母', emergencyPhone: '13800005556', createdAt: '2025-09-12T11:00:00+08:00' },
}

/** 查询患者详情（医生端，§3.3） */
export function getPatientDetail(patientId: number): PatientDetailResponse | undefined {
  // §3.5/§2.5：接诊关系归属校验 — 当前医生必须有该患者的挂号或就诊记录
  const doctorId = getCurrentDoctorId()
  const hasRelation =
    mockDoctorAppointmentStore.some((a) => a.doctorId === doctorId && a.patientId === patientId) ||
    mockEncounterStore.some((e) => e.doctorId === doctorId && e.patientId === patientId)
  if (!hasRelation) return undefined

  const s = mockPatientSummaries[patientId]
  if (!s) return undefined
  const extra = mockPatientDetailExtra[patientId]
  return {
    id: s.id,
    name: s.name,
    gender: s.gender,
    birthDate: extra?.birthDate ?? '1990-01-01',
    age: s.age,
    phone: s.phone,
    allergies: s.allergies,
    medicalHistory: s.medicalHistory,
    address: extra?.address ?? '',
    emergencyContact: extra?.emergencyContact ?? '',
    emergencyPhone: extra?.emergencyPhone ?? '',
    createdAt: extra?.createdAt ?? '2024-01-01T00:00:00+08:00',
  }
}

// 患者诊疗时间线种子数据（§3.4：分诊/挂号/就诊/检查检验/病历/处方，按时间倒序）
const mockPatientTimelineStore: PatientTimelineEntry[] = [
  // 李建国（11）：高血压随访历史
  { id: 1101, type: 'PRESCRIPTION', title: '处方 · 氨氯地平片', description: '高血压常规用药，30 天用量', occurredAt: '2026-05-20T10:30:00+08:00', encounterId: 901, resourceId: 301, statusLabel: '已确认' },
  { id: 1102, type: 'MEDICAL_RECORD', title: '电子病历 · 高血压随访', description: '血压控制可，继续原方案服药', occurredAt: '2026-05-20T10:15:00+08:00', encounterId: 901, resourceId: 201, statusLabel: '已确认' },
  { id: 1103, type: 'EXAMINATION', title: '检验 · 血脂四项', description: '总胆固醇 6.2 mmol/L（偏高）', occurredAt: '2026-05-20T09:50:00+08:00', encounterId: 901, resourceId: 401, statusLabel: '已审核' },
  { id: 1104, type: 'ENCOUNTER', title: '就诊 · 内科', description: '主诉：头晕伴血压偏高 1 周', occurredAt: '2026-05-20T09:30:00+08:00', encounterId: 901, statusLabel: '已完成' },
  { id: 1105, type: 'APPOINTMENT', title: '挂号 · 钱思齐 主任医师', description: '内科门诊 5 月 20 日上午', occurredAt: '2026-05-18T14:00:00+08:00', encounterId: 901, statusLabel: '已完成' },
  { id: 1106, type: 'ENCOUNTER', title: '就诊 · 内科', description: '初诊高血压，建议生活方式调整', occurredAt: '2026-04-12T09:00:00+08:00', encounterId: 801, statusLabel: '已完成' },
  { id: 1107, type: 'APPOINTMENT', title: '挂号 · 钱思齐 主任医师', description: '内科门诊 4 月 12 日上午', occurredAt: '2026-04-10T10:00:00+08:00', encounterId: 801, statusLabel: '已完成' },
  // 王秀兰（12）：糖尿病随访
  { id: 1201, type: 'PRESCRIPTION', title: '处方 · 二甲双胍片', description: '糖尿病常规用药', occurredAt: '2026-06-05T10:20:00+08:00', encounterId: 902, resourceId: 302, statusLabel: '已确认' },
  { id: 1202, type: 'MEDICAL_RECORD', title: '电子病历 · 糖尿病复查', description: '空腹血糖 7.2，控制尚可', occurredAt: '2026-06-05T10:05:00+08:00', encounterId: 902, resourceId: 202, statusLabel: '已确认' },
  { id: 1203, type: 'EXAMINATION', title: '检验 · 糖化血红蛋白', description: 'HbA1c 6.8%（参考 4.0-6.0，偏高）', occurredAt: '2026-06-05T09:40:00+08:00', encounterId: 902, resourceId: 402, statusLabel: '已审核' },
  { id: 1204, type: 'ENCOUNTER', title: '就诊 · 内科', description: '主诉：口干多饮 3 月余', occurredAt: '2026-06-05T09:20:00+08:00', encounterId: 902, statusLabel: '已完成' },
  { id: 1205, type: 'APPOINTMENT', title: '挂号 · 钱思齐 主任医师', description: '内科门诊 6 月 5 日上午', occurredAt: '2026-06-03T15:00:00+08:00', encounterId: 902, statusLabel: '已完成' },
  // 刘芳（14）：冠心病房颤随访
  { id: 1401, type: 'MEDICAL_RECORD', title: '电子病历 · 冠心病复查', description: '房颤心律，建议继续华法林抗凝', occurredAt: '2026-06-10T10:30:00+08:00', encounterId: 903, resourceId: 203, statusLabel: '已确认' },
  { id: 1402, type: 'EXAMINATION', title: '检查 · 心电图', description: '心房颤动，心率 82 次/分', occurredAt: '2026-06-10T09:50:00+08:00', encounterId: 903, resourceId: 403, statusLabel: '已审核' },
  { id: 1403, type: 'EXAMINATION', title: '检验 · 凝血功能', description: 'INR 2.3（达标范围 2.0-3.0）', occurredAt: '2026-06-10T09:45:00+08:00', encounterId: 903, resourceId: 404, statusLabel: '已审核' },
  { id: 1404, type: 'ENCOUNTER', title: '就诊 · 内科', description: '主诉：胸闷心悸复查', occurredAt: '2026-06-10T09:30:00+08:00', encounterId: 903, statusLabel: '已完成' },
  { id: 1405, type: 'APPOINTMENT', title: '挂号 · 钱思齐 主任医师', description: '内科门诊 6 月 10 日上午', occurredAt: '2026-06-08T11:00:00+08:00', encounterId: 903, statusLabel: '已完成' },
]

/** 查询患者诊疗时间线（§3.4，按时间倒序） */
export function getPatientTimeline(patientId: number): PatientTimelineEntry[] {
  // §3.5/§2.5：接诊关系归属校验
  const doctorId = getCurrentDoctorId()
  const hasRelation =
    mockDoctorAppointmentStore.some((a) => a.doctorId === doctorId && a.patientId === patientId) ||
    mockEncounterStore.some((e) => e.doctorId === doctorId && e.patientId === patientId)
  if (!hasRelation) return []

  // 种子时间线按 patientId 前缀分组（11xx → 患者11，12xx → 患者12，14xx → 患者14）
  const prefix = String(patientId)
  const entries = mockPatientTimelineStore.filter((e) =>
    String(e.id).startsWith(prefix),
  )
  // 未有种子的患者返回空（新接诊患者尚无历史）
  return entries.sort(
    (a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime(),
  )
}

// ============================================================
// 医疗设备管理（§13）
// §13.6：每次设备状态变化必须记录来源状态、目标状态、操作人、时间和原因
// §13.5：一个设备同一时间只有一条进行中记录；异常、停用设备不可使用
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

// 设备状态历史（§13.6：创建设备时写入第一条，来源状态为空）
let mockDeviceStatusSeq = 7001
const mockDeviceStatusHistoryStore: DeviceStatusHistory[] = []
// 初始化：为每台设备写入创建时的第一条状态历史
mockDeviceStore.forEach((d) => {
  mockDeviceStatusHistoryStore.push({
    id: mockDeviceStatusSeq++,
    deviceId: d.id,
    fromStatus: null,
    toStatus: d.status,
    operatorId: 0,
    operatorName: '系统初始化',
    reason: '设备建档',
    changedAt: d.createdAt,
  })
})

// 设备使用记录 store
let mockUsageSeq = 8001
const mockDeviceUsageStore: DeviceUsageResponse[] = [
  // 演示种子：监护仪 607 正在使用（与 607 的 IN_USE 对应）
  {
    id: 8001,
    deviceId: 607,
    deviceName: '多参数监护仪',
    deviceCode: 'DEV-MON-01',
    encounterId: 1001,
    patientId: 15,
    patientName: '陈强',
    doctorId: 401,
    doctorName: '钱思齐',
    purpose: '接诊期间生命体征监护',
    startedAt: '2026-06-24T09:20:00+08:00',
    endedAt: null,
    status: 'IN_USE',
    createdAt: '2026-06-24T09:20:00+08:00',
    updatedAt: '2026-06-24T09:20:00+08:00',
  },
]

function pushStatusHistory(
  deviceId: number,
  fromStatus: DeviceStatus | null,
  toStatus: DeviceStatus,
  operatorId: number,
  operatorName: string,
  reason?: string,
) {
  mockDeviceStatusHistoryStore.push({
    id: mockDeviceStatusSeq++,
    deviceId,
    fromStatus,
    toStatus,
    operatorId,
    operatorName,
    reason,
    changedAt: now(),
  })
}

/** 查询可用设备（§13.3：开立检查时查询；仅返回启用且 AVAILABLE 的） */
export function getAvailableDevices(category?: DeviceCategory): DeviceResponse[] {
  return mockDeviceStore
    .filter((d) => d.enabled && d.status === 'AVAILABLE')
    .filter((d) => !category || d.category === category)
    .map((d) => ({ ...d }))
}

/** 查询全部设备（含占用/维修，医生端可查看状态） */
export function getAllDevices(category?: DeviceCategory): DeviceResponse[] {
  return mockDeviceStore
    .filter((d) => !category || d.category === category)
    .map((d) => ({ ...d }))
}

/**
 * 创建设备使用记录（§13.3、§13.5）
 * - 校验：设备启用且 AVAILABLE；同一设备同一时间只有一条进行中记录
 * - 创建后设备转为 IN_USE，并写入状态历史（§13.6）
 */
export function createDeviceUsage(
  doctorId: number,
  doctorName: string,
  payload: DeviceUsageCreateRequest,
): DeviceUsageResponse {
  const device = mockDeviceStore.find((d) => d.id === payload.deviceId)
  if (!device) throw new Error('设备不存在')
  if (!device.enabled) throw new Error('设备已停用，不可使用')
  if (device.status !== 'AVAILABLE') {
    throw new Error(`设备当前状态为 ${device.status}，不可使用（§13.5）`)
  }
  // 校验：同一设备无进行中记录（双重保险）
  const activeUsage = mockDeviceUsageStore.find(
    (u) => u.deviceId === payload.deviceId && u.status === 'IN_USE',
  )
  if (activeUsage) {
    throw new Error('该设备已有进行中的使用记录，不可重复占用（§13.5）')
  }

  const fromStatus = device.status
  device.status = 'IN_USE'
  device.updatedAt = now()
  pushStatusHistory(device.id, fromStatus, 'IN_USE', doctorId, doctorName, '开始使用')

  const usage: DeviceUsageResponse = {
    id: mockUsageSeq++,
    deviceId: device.id,
    deviceName: device.name,
    deviceCode: device.code,
    encounterId: payload.encounterId,
    examinationId: payload.examinationId,
    patientId: payload.patientId,
    patientName: payload.patientName,
    doctorId,
    doctorName,
    purpose: payload.purpose,
    startedAt: now(),
    endedAt: null,
    status: 'IN_USE',
    createdAt: now(),
    updatedAt: now(),
  }
  mockDeviceUsageStore.push(usage)
  return { ...usage }
}

/**
 * 结束设备使用（§13.4：使用结束 → 保存结果 → 设备恢复空闲或转为故障）
 * - 使用记录标记 COMPLETED
 * - 设备转为 payload.deviceEndStatus（默认 AVAILABLE）；若 MAINTENANCE 则标记故障
 * - 写入状态历史（§13.6）
 */
export function endDeviceUsage(
  usageId: number,
  doctorId: number,
  doctorName: string,
  payload: DeviceUsageEndRequest,
): DeviceUsageResponse {
  const usage = mockDeviceUsageStore.find((u) => u.id === usageId)
  if (!usage) throw new Error('使用记录不存在')
  if (usage.status !== 'IN_USE') throw new Error('该使用记录已结束')

  const device = mockDeviceStore.find((d) => d.id === usage.deviceId)
  if (!device) throw new Error('关联设备不存在')

  const endStatus: DeviceStatus = payload.deviceEndStatus ?? 'AVAILABLE'
  const fromStatus = device.status
  device.status = endStatus
  device.updatedAt = now()

  usage.endedAt = now()
  usage.result = payload.result
  usage.endDeviceStatus = endStatus
  usage.status = endStatus === 'MAINTENANCE' ? 'ABNORMAL' : 'COMPLETED'
  usage.updatedAt = now()

  pushStatusHistory(
    device.id,
    fromStatus,
    endStatus,
    doctorId,
    doctorName,
    endStatus === 'MAINTENANCE' ? '使用结束，设备转入维修' : '使用结束，恢复正常',
  )

  return { ...usage }
}

/** 查询就诊关联的设备使用记录（§13.6：使用记录关联 Encounter） */
export function getEncounterDeviceUsages(encounterId: number): DeviceUsageResponse[] {
  return mockDeviceUsageStore
    .filter((u) => u.encounterId === encounterId)
    .map((u) => ({ ...u }))
    .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime())
}

/** 查询设备状态历史（§13.6） */
export function getDeviceStatusHistory(deviceId: number): DeviceStatusHistory[] {
  return mockDeviceStatusHistoryStore
    .filter((h) => h.deviceId === deviceId)
    .map((h) => ({ ...h }))
    .sort((a, b) => new Date(b.changedAt).getTime() - new Date(a.changedAt).getTime())
}
