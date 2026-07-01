import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import type { PageResponse } from '@/types/api'
import type {
  DeviceCategory,
  DeviceResponse,
  DeviceStatus,
  DeviceStatusHistory,
  DeviceUsageCreateRequest,
  DeviceUsageEndRequest,
  DeviceUsageResponse,
  DeviceUsageStatus,
} from '@/types/device'

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

interface BackendDeviceUsageResponse {
  id: number
  deviceId: number
  encounterId: number
  usedBy: number
  startTime: string
  endTime?: string | null
  status: string
  notes?: string | null
  createdAt: string
  updatedAt: string
}

interface BackendDeviceStatusHistoryResponse {
  id: number
  deviceId: number
  fromStatus?: DeviceStatus | null
  toStatus: DeviceStatus
  operatorId?: number | null
  reason?: string | null
  changedAt: string
}

function deviceCategory(type: string): DeviceCategory {
  if (type === 'MONITOR') return 'MONITOR'
  if (type === 'LABORATORY' || type === 'LAB') return 'LABORATORY'
  if (type === 'OTHER') return 'OTHER'
  return 'EXAMINATION'
}

function usageStatus(status: string): DeviceUsageStatus {
  if (status === 'IN_USAGE') return 'IN_USE'
  if (status === 'ABORTED') return 'ABNORMAL'
  if (status === 'COMPLETED') return 'COMPLETED'
  return status as DeviceUsageStatus
}

export function mapBackendDevice(device: BackendDeviceResponse): DeviceResponse {
  return {
    id: device.id,
    code: device.code,
    name: device.name,
    category: deviceCategory(device.type),
    status: device.status,
    location: device.location ?? '',
    enabled: device.status !== 'DISABLED',
    applicableItems: [device.type, device.model, device.notes].filter(Boolean) as string[],
    createdAt: device.createdAt,
    updatedAt: device.updatedAt,
  }
}

function mapBackendHistory(history: BackendDeviceStatusHistoryResponse): DeviceStatusHistory {
  return {
    id: history.id,
    deviceId: history.deviceId,
    fromStatus: history.fromStatus ?? null,
    toStatus: history.toStatus,
    operatorId: history.operatorId ?? 0,
    operatorName: history.operatorId ? `user-${history.operatorId}` : '',
    reason: history.reason ?? undefined,
    changedAt: history.changedAt,
  }
}

async function getBackendDevice(deviceId: number): Promise<BackendDeviceResponse> {
  const res = await apiClient.get(`/devices/${deviceId}`)
  return parseApiResponse<BackendDeviceResponse>(res.data)
}

async function mapBackendUsage(
  usage: BackendDeviceUsageResponse,
  deviceCache: Map<number, BackendDeviceResponse> = new Map(),
): Promise<DeviceUsageResponse> {
  let device = deviceCache.get(usage.deviceId)
  if (!device) {
    device = await getBackendDevice(usage.deviceId)
    deviceCache.set(usage.deviceId, device)
  }

  return {
    id: usage.id,
    deviceId: usage.deviceId,
    deviceName: device.name,
    deviceCode: device.code,
    encounterId: usage.encounterId,
    doctorId: usage.usedBy,
    doctorName: usage.usedBy ? `user-${usage.usedBy}` : '',
    purpose: usage.notes ?? undefined,
    startedAt: usage.startTime,
    endedAt: usage.endTime ?? null,
    result: usage.notes ?? undefined,
    endDeviceStatus: device.status,
    status: usageStatus(usage.status),
    createdAt: usage.createdAt,
    updatedAt: usage.updatedAt,
  }
}

async function mapBackendUsages(
  usages: BackendDeviceUsageResponse[],
): Promise<DeviceUsageResponse[]> {
  const deviceCache = new Map<number, BackendDeviceResponse>()
  return Promise.all(usages.map((usage) => mapBackendUsage(usage, deviceCache)))
}

export async function getAvailableDevices(
  category?: DeviceCategory,
): Promise<DeviceResponse[]> {
  const res = await apiClient.get('/devices/status/AVAILABLE')
  let devices = parseApiResponse<BackendDeviceResponse[]>(res.data).map(mapBackendDevice)
  if (category) {
    devices = devices.filter((device) => device.category === category)
  }
  return devices
}

export async function getAllDevices(
  category?: DeviceCategory,
): Promise<DeviceResponse[]> {
  const res = await apiClient.get('/devices', { params: { page: 1, size: 100 } })
  let devices = parseApiResponse<PageResponse<BackendDeviceResponse>>(res.data).items
    .map(mapBackendDevice)
  if (category) {
    devices = devices.filter((device) => device.category === category)
  }
  return devices
}

export async function createDeviceUsage(
  payload: DeviceUsageCreateRequest,
): Promise<DeviceUsageResponse> {
  const res = await apiClient.post(`/devices/${payload.deviceId}/usage/start`, {
    deviceId: payload.deviceId,
    encounterId: payload.encounterId,
    notes: payload.purpose,
  })
  return mapBackendUsage(parseApiResponse<BackendDeviceUsageResponse>(res.data))
}

export async function endDeviceUsage(
  deviceId: number,
  payload: DeviceUsageEndRequest,
): Promise<DeviceUsageResponse> {
  const res = await apiClient.post(`/devices/${deviceId}/usage/end`, {
    notes: payload.result,
  })
  const usage = await mapBackendUsage(parseApiResponse<BackendDeviceUsageResponse>(res.data))

  if (payload.deviceEndStatus === 'MAINTENANCE') {
    await changeDeviceStatus(deviceId, 'ABNORMAL', payload.result || '设备使用结束后转维修')
    await changeDeviceStatus(deviceId, 'MAINTENANCE', payload.result || '设备转维修')
    return { ...usage, endDeviceStatus: 'MAINTENANCE' }
  }

  return { ...usage, endDeviceStatus: 'AVAILABLE' }
}

export async function getEncounterDeviceUsages(
  encounterId: number,
): Promise<DeviceUsageResponse[]> {
  const res = await apiClient.get(`/devices/encounter/${encounterId}/usage`)
  return mapBackendUsages(parseApiResponse<BackendDeviceUsageResponse[]>(res.data))
}

export async function changeDeviceStatus(
  deviceId: number,
  status: DeviceStatus,
  reason: string,
): Promise<DeviceResponse> {
  const res = await apiClient.post(`/devices/${deviceId}/status`, {
    targetStatus: status,
    reason,
  })
  return mapBackendDevice(parseApiResponse<BackendDeviceResponse>(res.data))
}

export async function getDeviceStatusHistory(
  deviceId: number,
): Promise<DeviceStatusHistory[]> {
  const res = await apiClient.get(`/devices/${deviceId}/history`)
  return parseApiResponse<BackendDeviceStatusHistoryResponse[]>(res.data).map(mapBackendHistory)
}
