import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import { getPatientInfo } from '@/api/patient'
import type {
  AppointmentResponse,
  AppointmentCreateRequest,
  AppointmentCancelRequest,
  ScheduleResponse,
} from '@/types/appointment'
import type { PageResponse } from '@/types/api'

/* ============ 排班 ============ */

/** 查询可预约排班（按科室 + 日期），patient 端使用 */
export async function getAvailableSchedules(params: {
  departmentId?: number
  date?: string
}): Promise<ScheduleResponse[]> {
  const res = await apiClient.get('/schedules/available', {
    params: {
      ...params,
      date: params.date ? `${params.date}T00:00:00` : undefined,
    },
  })
  return parseApiResponse(res.data)
}

/* ============ 挂号 ============ */

/** 创建挂号 */
export async function createAppointment(
  payload: AppointmentCreateRequest,
): Promise<AppointmentResponse> {
  const res = await apiClient.post('/appointments', payload)
  return parseApiResponse(res.data)
}

/** 查询当前患者的挂号列表 */
export async function getMyAppointments(): Promise<AppointmentResponse[]> {
  const patientId = (await getPatientInfo()).id
  const res = await apiClient.get(`/appointments/patient/${patientId}`)
  const page = parseApiResponse<PageResponse<AppointmentResponse>>(res.data)
  return page.items
}

/** 取消挂号 */
export async function cancelAppointment(
  id: number,
  payload: AppointmentCancelRequest,
): Promise<AppointmentResponse> {
  const res = await apiClient.post(`/appointments/${id}/cancel`, payload)
  return parseApiResponse(res.data)
}

/** 真实接口占位（暂时不启用，避免打包产生未使用变量警告） */
export const _realApiReserved = {
  async create(payload: AppointmentCreateRequest): Promise<AppointmentResponse> {
    const res = await apiClient.post('/appointments', payload)
    return parseApiResponse(res.data)
  },
}
