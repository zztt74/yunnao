// 集中管理所有患者端 MOCK 演示数据
// 后端接口就绪后请删除整个 mock 目录

import type {
  ScheduleResponse,
  AppointmentResponse,
} from '@/types/appointment'
import type { MedicalRecord } from '@/types/medical-record'
import type { ExaminationResponse } from '@/types/examination'
import type { PrescriptionResponse } from '@/types/prescription'
import type { PatientResponse, PatientProfileResponse, PatientUpdateRequest } from '@/types/patient'
import type { TriageResultResponse } from '@/types/triage'

// 演示科室
export const mockDepartments = [
  { id: 1, name: '急诊科' },
  { id: 2, name: '神经内科' },
  { id: 3, name: '消化内科' },
  { id: 4, name: '内科' },
  { id: 5, name: '骨科' },
  { id: 6, name: '皮肤科' },
  { id: 7, name: '全科' },
  { id: 8, name: '心内科' },
  { id: 9, name: '呼吸内科' },
]

// 演示医生
export const mockDoctors: Record<number, Array<{ id: number; name: string; title: string; departmentId: number; departmentName: string }>> = {
  1: [{ id: 101, name: '李文博', title: '主任医师', departmentId: 1, departmentName: '急诊科' }],
  2: [
    { id: 201, name: '王心怡', title: '副主任医师', departmentId: 2, departmentName: '神经内科' },
    { id: 202, name: '陈明远', title: '主治医师', departmentId: 2, departmentName: '神经内科' },
  ],
  3: [{ id: 301, name: '赵雅琴', title: '主任医师', departmentId: 3, departmentName: '消化内科' }],
  4: [
    { id: 401, name: '钱思齐', title: '主任医师', departmentId: 4, departmentName: '内科' },
    { id: 402, name: '孙若曦', title: '副主任医师', departmentId: 4, departmentName: '内科' },
  ],
  5: [{ id: 501, name: '周浩然', title: '主治医师', departmentId: 5, departmentName: '骨科' }],
  6: [{ id: 601, name: '吴婉清', title: '副主任医师', departmentId: 6, departmentName: '皮肤科' }],
  7: [{ id: 701, name: '郑仁杰', title: '主治医师', departmentId: 7, departmentName: '全科' }],
  8: [{ id: 801, name: '冯诗雨', title: '副主任医师', departmentId: 8, departmentName: '心内科' }],
  9: [{ id: 901, name: '蒋丽华', title: '主任医师', departmentId: 9, departmentName: '呼吸内科' }],
}

/* ============ 排班 ============ */

function fmtDateTime(date: string, hhmm: string): string {
  return `${date}T${hhmm.length === 4 ? `${hhmm.slice(0, 2)}:${hhmm.slice(2, 4)}:00` : hhmm}+08:00`
}

function generateSchedules(): ScheduleResponse[] {
  const out: ScheduleResponse[] = []
  const today = new Date()
  // 接下来 7 天
  for (let d = 0; d < 7; d++) {
    const dt = new Date(today)
    dt.setDate(today.getDate() + d)
    const ymd = dt.toISOString().slice(0, 10)
    mockDepartments.forEach((dept) => {
      const doctors = mockDoctors[dept.id] || []
      doctors.forEach((doc) => {
        // 上午场
        out.push({
          id: Number(`${doc.id}${d}1`),
          doctorId: doc.id,
          doctorName: doc.name,
          departmentId: dept.id,
          departmentName: dept.name,
          scheduleDate: ymd,
          startTime: fmtDateTime(ymd, '08:00'),
          endTime: fmtDateTime(ymd, '11:30'),
          maxAppointments: 20,
          bookedCount: Math.floor(Math.random() * 15),
          remainingCount: 0,
          status: 'AVAILABLE',
          cancelledAt: null,
          cancelReason: null,
          createdAt: '2026-06-20T00:00:00+08:00',
          updatedAt: '2026-06-20T00:00:00+08:00',
        })
        // 下午场
        out.push({
          id: Number(`${doc.id}${d}2`),
          doctorId: doc.id,
          doctorName: doc.name,
          departmentId: dept.id,
          departmentName: dept.name,
          scheduleDate: ymd,
          startTime: fmtDateTime(ymd, '14:00'),
          endTime: fmtDateTime(ymd, '17:00'),
          maxAppointments: 16,
          bookedCount: Math.floor(Math.random() * 12),
          remainingCount: 0,
          status: 'AVAILABLE',
          cancelledAt: null,
          cancelReason: null,
          createdAt: '2026-06-20T00:00:00+08:00',
          updatedAt: '2026-06-20T00:00:00+08:00',
        })
      })
    })
  }
  // 补 remainingCount 和 status
  for (const s of out) {
    s.remainingCount = Math.max(0, s.maxAppointments - s.bookedCount)
    s.status = s.remainingCount <= 0 ? 'FULL' : 'AVAILABLE'
  }
  return out
}

const mockScheduleCache: ScheduleResponse[] = generateSchedules()

export function getMockSchedules(params: {
  departmentId?: number
  date?: string
}): ScheduleResponse[] {
  let list = mockScheduleCache
  if (params.departmentId) {
    list = list.filter((s) => s.departmentId === params.departmentId)
  }
  if (params.date) {
    list = list.filter((s) => s.scheduleDate === params.date)
  }
  return list.map((s) => ({ ...s }))
}

export function getMockScheduleById(id: number): ScheduleResponse | undefined {
  return mockScheduleCache.find((s) => s.id === id)
}

/* ============ 挂号 ============ */

let mockAppointmentSeq = 100
const mockAppointmentStore: AppointmentResponse[] = [
  {
    id: 1,
    patientId: 1,
    patientName: '本人',
    scheduleId: mockScheduleCache[0]?.id ?? 0,
    doctorId: mockScheduleCache[0]?.doctorId ?? 101,
    doctorName: mockScheduleCache[0]?.doctorName ?? '李文博',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A000001',
    status: 'COMPLETED',
    bookedAt: '2026-06-18T08:30:00+08:00',
    checkInTime: '2026-06-18T09:05:00+08:00',
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: '2026-06-18T08:30:00+08:00',
    updatedAt: '2026-06-18T10:30:00+08:00',
  },
  {
    id: 2,
    patientId: 1,
    patientName: '本人',
    scheduleId: mockScheduleCache[20]?.id ?? 0,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentId: 4,
    departmentName: '内科',
    appointmentNumber: 'A000002',
    status: 'CANCELLED',
    bookedAt: '2026-06-20T10:00:00+08:00',
    checkInTime: null,
    cancellationReason: '临时有事',
    cancellationSource: 'PATIENT',
    cancelledAt: '2026-06-20T18:00:00+08:00',
    createdAt: '2026-06-20T10:00:00+08:00',
    updatedAt: '2026-06-20T18:00:00+08:00',
  },
]

export function getMockAppointments(patientId: number): AppointmentResponse[] {
  return mockAppointmentStore
    .filter((a) => a.patientId === patientId || patientId === 1)
    .map((a) => ({ ...a }))
}

/**
 * 检查指定患者是否已在指定排班上存在未取消的挂号
 * - 用于 createAppointment 前的重复预约拦截（§7.5）
 */
export function hasMockActiveAppointment(
  patientId: number,
  scheduleId: number,
): boolean {
  return mockAppointmentStore.some(
    (a) =>
      a.patientId === patientId &&
      a.scheduleId === scheduleId &&
      a.status !== 'CANCELLED',
  )
}

/**
 * 写入新挂号到内存 store
 * - 自动分配递增 id
 * - 返回写入后的完整对象（含新分配的 id），便于调用方直接拿到
 */
export function addMockAppointment(
  item: Omit<AppointmentResponse, 'id'> & { id?: number },
): AppointmentResponse {
  mockAppointmentSeq++
  const newItem: AppointmentResponse = { ...item, id: mockAppointmentSeq }
  mockAppointmentStore.push(newItem)
  return newItem
}

/**
 * 在内存 store 中更新指定挂号
 * - 直接修改 store 中的原对象（不是拷贝），保证后续读取能拿到最新状态
 * - 返回更新后的对象；id 不存在时返回 null
 */
export function updateMockAppointment(
  id: number,
  patch: Partial<AppointmentResponse>,
): AppointmentResponse | null {
  const target = mockAppointmentStore.find((a) => a.id === id)
  if (!target) return null
  Object.assign(target, patch)
  return { ...target }
}

/**
 * 扣减指定排班的剩余号源（预约时调用）
 * - bookedCount + 1
 * - remainingCount - 1（不会小于 0）
 * - 排班不存在或号源已满时返回 false
 */
export function bookMockSchedule(scheduleId: number): boolean {
  const schedule = mockScheduleCache.find((s) => s.id === scheduleId)
  if (!schedule) return false
  if (schedule.remainingCount <= 0) return false
  schedule.bookedCount += 1
  schedule.remainingCount = Math.max(0, schedule.remainingCount - 1)
  if (schedule.remainingCount === 0) schedule.status = 'FULL'
  return true
}

/**
 * 释放指定排班的剩余号源（取消预约时调用）
 * - bookedCount - 1（不会小于 0）
 * - remainingCount + 1（不会超过 maxAppointments）
 * - 排班不存在时返回 false
 */
export function releaseMockSchedule(scheduleId: number): boolean {
  const schedule = mockScheduleCache.find((s) => s.id === scheduleId)
  if (!schedule) return false
  if (schedule.bookedCount > 0) schedule.bookedCount -= 1
  schedule.remainingCount = Math.min(
    schedule.maxAppointments,
    schedule.remainingCount + 1,
  )
  if (schedule.status === 'FULL' && schedule.remainingCount > 0) {
    schedule.status = 'AVAILABLE'
  }
  return true
}

/* ============ 病历 ============ */

export const mockMedicalRecords: MedicalRecord[] = [
  {
    id: 1,
    encounterId: 1001,
    patientId: 1,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentName: '内科',
    chiefComplaint: '咳嗽 5 天，伴低热 2 天',
    presentIllness:
      '患者 5 天前受凉后出现阵发性咳嗽，初为干咳，2 天前出现低热（最高 37.8℃），咳少量白黏痰，无明显胸痛及呼吸困难，自服感冒药后症状未明显缓解。',
    pastHistory: '既往体健，否认高血压、糖尿病病史。',
    physicalExam:
      '一般情况可，咽部充血，扁桃体不大。双肺呼吸音粗，未闻及明显干湿啰音。心律齐，腹软无压痛。',
    preliminaryDiagnosis: '急性上呼吸道感染',
    treatmentAdvice:
      '注意休息，多饮水；口服清热解毒中成药；如高热不退或出现呼吸困难请及时复诊。',
    status: 'CONFIRMED',
    diagnoses: [
      {
        id: 1,
        type: 'FINAL',
        source: 'DOCTOR',
        diagnosisCode: 'J06.9',
        diagnosisName: '急性上呼吸道感染',
        description: '病毒感染可能性大，对症处理为主。',
        createdAt: '2026-06-18T10:20:00+08:00',
      },
    ],
    encounterDate: '2026-06-18T09:30:00+08:00',
    confirmedAt: '2026-06-18T10:30:00+08:00',
    createdAt: '2026-06-18T10:00:00+08:00',
    updatedAt: '2026-06-18T10:30:00+08:00',
  },
]

/* ============ 检查检验 ============ */

export const mockExaminations: ExaminationResponse[] = [
  {
    id: 1,
    encounterId: 1001,
    patientId: 1,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentName: '内科',
    type: 'LABORATORY',
    itemName: '血常规',
    purpose: '排查感染及血液系统异常',
    orderedAt: '2026-06-18T09:45:00+08:00',
    reportedAt: '2026-06-18T10:05:00+08:00',
    reviewedAt: '2026-06-18T10:25:00+08:00',
    reporterName: '检验师 周技师',
    status: 'REVIEWED',
    labItems: [
      {
        id: 1,
        itemName: '白细胞计数',
        resultValue: '9.8',
        unit: '10^9/L',
        referenceRange: '4.0-10.0',
        abnormalFlag: 'NORMAL',
      },
      {
        id: 2,
        itemName: '中性粒细胞百分比',
        resultValue: '78.5',
        unit: '%',
        referenceRange: '40-75',
        abnormalFlag: 'HIGH',
      },
      {
        id: 3,
        itemName: '淋巴细胞百分比',
        resultValue: '15.2',
        unit: '%',
        referenceRange: '20-50',
        abnormalFlag: 'LOW',
      },
      {
        id: 4,
        itemName: '血红蛋白',
        resultValue: '138',
        unit: 'g/L',
        referenceRange: '120-160',
        abnormalFlag: 'NORMAL',
      },
      {
        id: 5,
        itemName: '血小板计数',
        resultValue: '256',
        unit: '10^9/L',
        referenceRange: '125-350',
        abnormalFlag: 'NORMAL',
      },
    ],
    impression: '白细胞总数正常，中性粒细胞比例偏高，提示细菌感染可能。',
    aiInterpretation:
      '血常规结果提示存在轻度细菌感染征象，结合临床表现考虑为急性上呼吸道感染，建议结合症状对症处理并复诊评估。',
    createdAt: '2026-06-18T09:45:00+08:00',
    updatedAt: '2026-06-18T10:25:00+08:00',
  },
  {
    id: 2,
    encounterId: 1001,
    patientId: 1,
    doctorId: 401,
    doctorName: '钱思齐',
    departmentName: '内科',
    type: 'EXAMINATION',
    itemName: '胸部 X 光检查',
    purpose: '排查肺部病变',
    orderedAt: '2026-06-18T09:50:00+08:00',
    reportedAt: '2026-06-18T10:20:00+08:00',
    reviewedAt: '2026-06-18T10:30:00+08:00',
    reporterName: '影像师 王技师',
    status: 'REVIEWED',
    findings: '双肺纹理稍增粗，未见明显实变影，心影大小正常。',
    impression: '双肺纹理增粗，请结合临床。',
    aiInterpretation:
      '胸片表现符合轻度支气管炎征象，未见明显肺炎或占位性病变。',
    createdAt: '2026-06-18T09:50:00+08:00',
    updatedAt: '2026-06-18T10:30:00+08:00',
  },
]

/* ============ 处方 ============ */

export const mockPrescriptions: PrescriptionResponse[] = [
  {
    id: 1,
    encounterId: 1001,
    patientId: 1,
    patientName: '本人',
    doctorId: 401,
    doctorName: '钱思齐',
    departmentName: '内科',
    diagnosis: '急性上呼吸道感染',
    status: 'CONFIRMED',
    voidedReason: null,
    voidedAt: null,
    remark: '饭后服用，多饮水，注意休息。',
    aiReview: {
      riskLevel: 'LOW',
      warnings: [],
      advice: '所用药品安全性良好，无明显相互作用风险。',
      reviewedAt: '2026-06-18T10:25:00+08:00',
    },
    aiReviewStatus: 'REVIEWED',
    items: [
      {
        id: 1,
        drugId: 1,
        drugCode: 'DRG_AMOXICILLIN',
        drugName: '阿莫西林胶囊',
        strength: '0.25g*24粒',
        unit: '盒',
        dosage: '0.5g',
        frequency: 'TID',
        usage: '口服',
        duration: '5 天',
      },
      {
        id: 2,
        drugId: 2,
        drugCode: 'DRG_COMPOUND_COLD',
        drugName: '复方感冒灵颗粒',
        strength: '10g*6袋',
        unit: '盒',
        dosage: '10g',
        frequency: 'TID',
        usage: '口服',
        duration: '3 天',
      },
    ],
    confirmedAt: '2026-06-18T10:30:00+08:00',
    createdAt: '2026-06-18T10:20:00+08:00',
    updatedAt: '2026-06-18T10:30:00+08:00',
  },
]

/* ============ 患者基本信息 & 个人档案 ============ */

// 基本信息：单条 MOCK（演示用固定账号）
const mockPatientInfo: PatientResponse = {
  id: 1,
  userId: 1,
  name: '张三',
  gender: 'MALE',
  birthDate: '1990-01-01',
  phone: '13800138000',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00+08:00',
  updatedAt: '2026-01-01T00:00:00+08:00',
}

// 个人档案：MOCK + 内存 store，保存后可立即在 loadData 看到
const mockPatientProfile: PatientProfileResponse = {
  id: 1,
  patientId: 1,
  address: '',
  emergencyContact: '',
  emergencyPhone: '',
  allergies: '',
  medicalHistory: '',
  createdAt: '2026-01-01T00:00:00+08:00',
  updatedAt: '2026-01-01T00:00:00+08:00',
}

export function getMockPatientInfo(): PatientResponse {
  return { ...mockPatientInfo }
}

export function getMockPatientProfile(): PatientProfileResponse {
  return { ...mockPatientProfile }
}

export function updateMockPatientProfile(
  patch: Partial<PatientProfileResponse>,
): PatientProfileResponse {
  // 真实接口就绪后请删除本函数
  const now = new Date().toISOString()
  if (patch.address !== undefined) mockPatientProfile.address = patch.address
  if (patch.emergencyContact !== undefined) mockPatientProfile.emergencyContact = patch.emergencyContact
  if (patch.emergencyPhone !== undefined) mockPatientProfile.emergencyPhone = patch.emergencyPhone
  if (patch.allergies !== undefined) mockPatientProfile.allergies = patch.allergies
  if (patch.medicalHistory !== undefined) mockPatientProfile.medicalHistory = patch.medicalHistory
  mockPatientProfile.updatedAt = now
  return { ...mockPatientProfile }
}

/**
 * 修改患者基本信息（按文档 §3.6 "患者能修改本人档案" + §3.3 "维护性别、出生日期、联系方式"）
 * - 演示阶段直接改内存 store
 * - §3.5 "修改基本信息不能覆盖历史医疗事实" 由后端在写历史表时保留旧值实现
 */
export function updateMockPatientInfo(
  patch: PatientUpdateRequest,
): PatientResponse {
  // 基本校验
  if (!patch.name || !patch.name.trim()) throw new Error('姓名不能为空')
  if (!patch.birthDate) throw new Error('请选择出生日期')
  if (!/^1\d{10}$/.test(patch.phone)) throw new Error('手机号格式不正确（11 位 1 开头）')
  const now = new Date().toISOString()
  mockPatientInfo.name = patch.name.trim()
  mockPatientInfo.gender = patch.gender
  mockPatientInfo.birthDate = patch.birthDate
  mockPatientInfo.phone = patch.phone
  mockPatientInfo.updatedAt = now
  return { ...mockPatientInfo }
}

/* ============ AI 分诊记录 ============ */

// 分诊记录：内存 store，每次 consultTriage 后写入
let mockTriageSeq = 0
const mockTriageStore: TriageResultResponse[] = [
  // 演示种子：让首次进入就有历史数据可看
  {
    id: ++mockTriageSeq,
    patientId: 1,
    recommendedDepartmentId: 4,
    recommendedDepartmentName: '内科',
    priority: 'MEDIUM',
    reason: '根据您描述的症状，建议到内科评估。',
    safetyAdvice: '多饮水，注意休息，监测体温。',
    createdAt: '2026-06-18T08:00:00+08:00',
  },
]

export function getMockTriageRecords(patientId: number): TriageResultResponse[] {
  return mockTriageStore
    .filter((t) => t.patientId === patientId || patientId === 1)
    .map((t) => ({ ...t }))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
}

export function getMockTriageById(id: number): TriageResultResponse | undefined {
  return mockTriageStore.find((t) => t.id === id)
}

/**
 * 写入一条分诊记录；自动分配递增 id
 * @returns 写入后的完整对象（含分配的 id 与标准化后的 createdAt）
 */
export function addMockTriageRecord(
  item: Omit<TriageResultResponse, 'id'> & { id?: number },
): TriageResultResponse {
  mockTriageSeq++
  const newItem: TriageResultResponse = { ...item, id: mockTriageSeq }
  mockTriageStore.push(newItem)
  return newItem
}
