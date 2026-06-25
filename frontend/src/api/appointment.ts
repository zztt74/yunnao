import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import type {
  AppointmentResponse,
  AppointmentCreateRequest,
  AppointmentCancelRequest,
  ScheduleResponse,
} from '@/types/appointment'
import {
  getMockAppointments,
  getMockSchedules,
  addMockAppointment,
  updateMockAppointment,
  bookMockSchedule,
  releaseMockSchedule,
  hasMockActiveAppointment,
} from '@/api/mock/medical-mock'

// MOCK 模式：后端 /api/schedules/available、/api/appointments 暂未就绪，使用本地演示数据
// 后端就绪后请把下面 MOCK 实现替换为真实调用即可

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function getPatientIdFromAuth(): number {
  // 真实接口会从 JWT 中解析 userId，再换算 patientId；
  // MOCK 阶段直接返回一个固定演示 ID
  try {
    const raw = sessionStorage.getItem('cloud-brain.user')
    if (raw) {
      const user = JSON.parse(raw) as { userId: number }
      return user.userId || 1
    }
  } catch {
    /* ignore */
  }
  return 1
}

/* ============ 排班 ============ */

/** 查询可预约排班（按科室 + 日期），patient 端使用 */
export async function getAvailableSchedules(params: {
  departmentId?: number
  date?: string
}): Promise<ScheduleResponse[]> {
  // MOCK：使用本地演示排班
  console.warn('[MOCK] /api/schedules/available 后端未就绪，使用本地虚构演示数据')
  await delay(500)
  return getMockSchedules(params)
}

/* ============ 挂号 ============ */

/** 创建挂号 */
export async function createAppointment(
  payload: AppointmentCreateRequest,
): Promise<AppointmentResponse> {
  // MOCK：本地扣减号源并返回新挂号
  console.warn('[MOCK] /api/appointments POST 后端未就绪，使用本地虚构演示数据')
  await delay(600)
  const schedule = getMockSchedules({}).find((s) => s.id === payload.scheduleId)
  if (!schedule) {
    throw new Error('排班不存在')
  }
  if (schedule.remainingCount <= 0) {
    throw new Error('号源已满，请选择其他时段')
  }

  // §7.5 重复预约拦截：同一患者在同一排班未取消的挂号只允许一条
  if (hasMockActiveAppointment(payload.patientId, payload.scheduleId)) {
    throw new Error('您已预约该排班，请勿重复预约')
  }

  // 1) 先扣减号源（不通过则不创建挂号）
  const booked = bookMockSchedule(payload.scheduleId)
  if (!booked) {
    throw new Error('号源已被占用，请选择其他时段')
  }

  // 2) 写入挂号记录
  const now = new Date().toISOString()
  const draft: Omit<AppointmentResponse, 'id'> = {
    patientId: payload.patientId,
    patientName: '本人',
    scheduleId: schedule.id,
    doctorId: schedule.doctorId,
    doctorName: schedule.doctorName,
    departmentId: schedule.departmentId,
    departmentName: schedule.departmentName,
    appointmentNumber: `A${Date.now().toString().slice(-6)}`,
    status: 'BOOKED',
    bookedAt: now,
    checkInTime: null,
    cancellationReason: null,
    cancellationSource: null,
    cancelledAt: null,
    createdAt: now,
    updatedAt: now,
  }
  const newItem = addMockAppointment(draft)

  // 后端就绪后请替换为：
  // const res = await apiClient.post('/appointments', payload)
  // return parseApiResponse(res.data)
  return newItem
}

/** 查询当前患者的挂号列表 */
export async function getMyAppointments(): Promise<AppointmentResponse[]> {
  // MOCK：返回演示挂号
  console.warn('[MOCK] /api/appointments/patient-list/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return getMockAppointments(getPatientIdFromAuth())
}

/** 取消挂号 */
export async function cancelAppointment(
  id: number,
  payload: AppointmentCancelRequest,
): Promise<AppointmentResponse> {
  // MOCK：把演示数据里对应 id 的状态改为 CANCELLED
  console.warn('[MOCK] /api/appointments/{id}/cancel 后端未就绪，使用本地虚构演示数据')
  await delay(500)

  // 直接查 store 里的真实数据（不要用 getMockAppointments，那会返回拷贝）
  const list = getMockAppointments(getPatientIdFromAuth())
  const target = list.find((a) => a.id === id)
  if (!target) {
    throw new Error('挂号记录不存在')
  }
  if (target.status === 'CANCELLED') {
    return { ...target }
  }
  if (['IN_PROGRESS', 'WAITING_EXAM', 'COMPLETED'].includes(target.status)) {
    throw new Error('接诊已开始，无法取消')
  }

  const now = new Date().toISOString()
  const updated = updateMockAppointment(id, {
    status: 'CANCELLED',
    cancellationReason: payload.reason,
    cancellationSource: 'PATIENT',
    cancelledAt: now,
    updatedAt: now,
  })

  // 取消成功后恢复号源
  if (updated?.scheduleId) {
    releaseMockSchedule(updated.scheduleId)
  }

  if (!updated) {
    throw new Error('挂号记录不存在')
  }
  return updated

  // 后端就绪后请替换为：
  // const res = await apiClient.post(`/appointments/${id}/cancel`, payload)
  // return parseApiResponse(res.data)
}

/** 真实接口占位（暂时不启用，避免打包产生未使用变量警告） */
export const _realApiReserved = {
  async create(payload: AppointmentCreateRequest): Promise<AppointmentResponse> {
    const res = await apiClient.post('/appointments', payload)
    return parseApiResponse(res.data)
  },
}
