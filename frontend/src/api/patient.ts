import type {
  PatientRegisterRequest,
  PatientResponse,
  PatientProfileResponse,
  PatientProfileUpdateRequest,
  PatientUpdateRequest,
} from '@/types/patient'
import {
  getMockPatientInfo,
  getMockPatientProfile,
  updateMockPatientInfo,
  updateMockPatientProfile,
} from '@/api/mock/medical-mock'

// MOCK 模式：后端 /api/patients/* 暂未就绪，使用本地演示数据
// 后端就绪后请把下面 MOCK 实现替换为真实调用即可，参考 patient.ts 真实接口的写法：
//   const res = await apiClient.put('/patients/me/profile', payload)
//   return parseApiResponse(res.data)

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 患者注册（演示用，本地直接返回成功 + 一份 MOCK 资料） */
export async function registerPatient(
  payload: PatientRegisterRequest,
): Promise<PatientResponse> {
  console.warn('[MOCK] /api/patients/register 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  // 真实接口就绪后请替换为：
  // const res = await apiClient.post('/patients/register', payload)
  // return parseApiResponse(res.data)
  return {
    id: 1,
    userId: 1,
    name: payload.name,
    gender: payload.gender,
    birthDate: payload.birthDate,
    phone: payload.phone,
    status: 'ACTIVE',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
}

/** 获取当前患者个人档案 */
export async function getPatientProfile(): Promise<PatientProfileResponse> {
  console.warn('[MOCK] /api/patients/me/profile GET 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockPatientProfile()

  // 后端就绪后请替换为：
  // const res = await apiClient.get('/patients/me/profile')
  // return parseApiResponse(res.data)
}

/** 更新当前患者个人档案 */
export async function updatePatientProfile(
  payload: PatientProfileUpdateRequest,
): Promise<PatientProfileResponse> {
  console.warn('[MOCK] /api/patients/me/profile PUT 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return updateMockPatientProfile(payload)

  // 后端就绪后请替换为：
  // const res = await apiClient.put('/patients/me/profile', payload)
  // return parseApiResponse(res.data)
}

/** 获取当前患者基本信息 */
export async function getPatientInfo(): Promise<PatientResponse> {
  console.warn('[MOCK] /api/patients/me GET 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockPatientInfo()

  // 后端就绪后请替换为：
  // const res = await apiClient.get('/patients/me')
  // return parseApiResponse(res.data)
}

/**
 * 更新当前患者基本信息（§3.3 / §3.6）
 * - 演示阶段写入内存 store
 * - §3.5 后端需要保留历史医疗事实，所以这份数据走的是"基本信息"表，不是医疗事实表
 */
export async function updatePatientInfo(
  payload: PatientUpdateRequest,
): Promise<PatientResponse> {
  console.warn('[MOCK] /api/patients/me PUT 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return updateMockPatientInfo(payload)

  // 后端就绪后请替换为：
  // const res = await apiClient.put('/patients/me', payload)
  // return parseApiResponse(res.data)
}
