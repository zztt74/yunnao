// 医生个人信息 API
// 设计来源：product/11_功能需求.md §4.3 科室与医生管理、§2.3 密码修改
// 后端 /api/doctors/me 未就绪，使用本地 MOCK 实现
// 后端就绪后请替换为真实调用并删除 doctor-mock 引用

import type {
  DoctorProfile,
  DoctorProfileUpdateRequest,
} from '@/types/doctor'
import type { ScheduleResponse } from '@/types/appointment'
import {
  getMockDoctorProfile,
  updateMockDoctorProfile,
  getMockDoctorSchedules,
  getCurrentDoctorId,
} from '@/api/mock/doctor-mock'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 获取当前医生个人信息（§4.3） */
export async function getDoctorProfile(): Promise<DoctorProfile> {
  console.warn('[MOCK] /api/doctors/me 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDoctorProfile()

  // 后端就绪后替换为：
  // const res = await apiClient.get('/doctors/me')
  // return parseApiResponse(res.data)
}

/** 更新当前医生可编辑的个人信息（§4.3：擅长方向、简介等） */
export async function updateDoctorProfile(
  payload: DoctorProfileUpdateRequest,
): Promise<DoctorProfile> {
  console.warn('[MOCK] /api/doctors/me PUT 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return updateMockDoctorProfile(payload)

  // 后端就绪后替换为：
  // const res = await apiClient.put('/doctors/me', payload)
  // return parseApiResponse(res.data)
}

/** 查询当前医生排班（§8.3：医生仅查看本人排班） */
export async function getDoctorSchedules(): Promise<ScheduleResponse[]> {
  console.warn('[MOCK] /api/schedules/doctor/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockDoctorSchedules(getCurrentDoctorId())

  // 后端就绪后替换为：
  // const res = await apiClient.get('/schedules/doctor/me')
  // return parseApiResponse(res.data)
}

/** 查询当前医生今日排班 */
export async function getDoctorTodaySchedules(): Promise<ScheduleResponse[]> {
  await delay(200)
  const today = new Date().toISOString().slice(0, 10)
  return getMockDoctorSchedules(getCurrentDoctorId()).filter(
    (s) => s.scheduleDate === today,
  )
}
