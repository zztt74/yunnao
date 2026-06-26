// 医疗设备模块 API（医生端）
// 设计来源：product/11_功能需求.md §13 医疗设备管理
// 当前后端 /api/devices 未就绪，使用本地 MOCK 实现
// 后端就绪后请用真实调用替换，并删除 doctor-mock 引用

import type {
  DeviceResponse,
  DeviceUsageResponse,
  DeviceUsageCreateRequest,
  DeviceUsageEndRequest,
  DeviceStatusHistory,
  DeviceCategory,
} from '@/types/device'
import {
  getAvailableDevices as mockGetAvailable,
  getAllDevices as mockGetAll,
  createDeviceUsage as mockCreateUsage,
  endDeviceUsage as mockEndUsage,
  getEncounterDeviceUsages as mockGetEncUsages,
  getDeviceStatusHistory as mockGetHistory,
  getCurrentDoctorId,
  CURRENT_DOCTOR,
} from '@/api/mock/doctor-mock'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 查询可用设备（§13.3：开立检查时查询；仅返回启用且 AVAILABLE） */
export async function getAvailableDevices(
  category?: DeviceCategory,
): Promise<DeviceResponse[]> {
  console.warn('[MOCK] /api/devices/available 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetAvailable(category)
}

/** 查询全部设备（含占用/维修，医生端可查看状态） */
export async function getAllDevices(
  category?: DeviceCategory,
): Promise<DeviceResponse[]> {
  console.warn('[MOCK] /api/devices 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetAll(category)
}

/**
 * 创建设备使用记录（§13.3、§13.5）
 * - 校验设备可用；创建后设备转 IN_USE
 * - 同一设备同一时间只允许一条进行中记录
 */
export async function createDeviceUsage(
  payload: DeviceUsageCreateRequest,
): Promise<DeviceUsageResponse> {
  console.warn('[MOCK] /api/devices/usages POST 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockCreateUsage(getCurrentDoctorId(), CURRENT_DOCTOR.doctorName, payload)
}

/**
 * 结束设备使用（§13.4：使用结束 → 保存结果 → 设备恢复空闲或转故障）
 * - deviceEndStatus=AVAILABLE 设备恢复正常；MAINTENANCE 转维修
 */
export async function endDeviceUsage(
  usageId: number,
  payload: DeviceUsageEndRequest,
): Promise<DeviceUsageResponse> {
  console.warn('[MOCK] /api/devices/usages/{id}/end 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockEndUsage(usageId, getCurrentDoctorId(), CURRENT_DOCTOR.doctorName, payload)
}

/** 查询就诊关联的设备使用记录（§13.6） */
export async function getEncounterDeviceUsages(
  encounterId: number,
): Promise<DeviceUsageResponse[]> {
  console.warn('[MOCK] /api/devices/usages/encounter/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetEncUsages(encounterId)
}

/** 查询设备状态历史（§13.6） */
export async function getDeviceStatusHistory(
  deviceId: number,
): Promise<DeviceStatusHistory[]> {
  console.warn('[MOCK] /api/devices/{id}/status-history 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetHistory(deviceId)
}
