import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'
import { backendDeviceFixture } from './helpers/fixtures'

import {
  changeDeviceStatus,
  createDeviceUsage,
  endDeviceUsage,
  getAllDevices,
  getAvailableDevices,
  getDeviceStatusHistory,
  getEncounterDeviceUsages,
  mapBackendDevice,
} from '@/api/device'

const mock = getApiClientMock()

describe('device API', () => {
  beforeEach(() => resetApiClientMock())

  describe('mapBackendDevice', () => {
    it('maps MONITOR category', () => {
      const out = mapBackendDevice(backendDeviceFixture({ type: 'MONITOR' }))
      expect(out.category).toBe('MONITOR')
    })

    it('maps LABORATORY category', () => {
      const out = mapBackendDevice(backendDeviceFixture({ type: 'LABORATORY' }))
      expect(out.category).toBe('LABORATORY')
    })

    it('maps LAB alias to LABORATORY', () => {
      const out = mapBackendDevice(backendDeviceFixture({ type: 'LAB' }))
      expect(out.category).toBe('LABORATORY')
    })

    it('maps OTHER category', () => {
      const out = mapBackendDevice(backendDeviceFixture({ type: 'OTHER' }))
      expect(out.category).toBe('OTHER')
    })

    it('falls back to EXAMINATION for unknown type', () => {
      const out = mapBackendDevice(backendDeviceFixture({ type: 'ECG' }))
      expect(out.category).toBe('EXAMINATION')
    })

    it('marks enabled = true when status is not DISABLED', () => {
      const out = mapBackendDevice(backendDeviceFixture({ status: 'AVAILABLE' }))
      expect(out.enabled).toBe(true)
    })

    it('marks enabled = false when status is DISABLED', () => {
      const out = mapBackendDevice(backendDeviceFixture({ status: 'DISABLED' }))
      expect(out.enabled).toBe(false)
    })

    it('defaults location to empty string when null', () => {
      const out = mapBackendDevice(backendDeviceFixture({ location: null }))
      expect(out.location).toBe('')
    })

    it('keeps non-null location', () => {
      const out = mapBackendDevice(backendDeviceFixture({ location: 'Room 1' }))
      expect(out.location).toBe('Room 1')
    })

    it('builds applicableItems filtering falsy', () => {
      const out = mapBackendDevice(
        backendDeviceFixture({ type: 'ECG', model: 'MAC 5500', notes: null }),
      )
      expect(out.applicableItems).toEqual(['ECG', 'MAC 5500'])
    })
  })

  describe('getAvailableDevices', () => {
    it('fetches /devices/status/AVAILABLE', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([backendDeviceFixture()]))
      const result = await getAvailableDevices()
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenCalledWith('/devices/status/AVAILABLE')
    })

    it('filters by category when provided', async () => {
      mock.get.mockResolvedValueOnce(
        successEnvelope([
          backendDeviceFixture({ id: 1, type: 'ECG' }),
          backendDeviceFixture({ id: 2, type: 'LABORATORY' }),
        ]),
      )
      const result = await getAvailableDevices('LABORATORY')
      expect(result).toHaveLength(1)
      expect(result[0].category).toBe('LABORATORY')
    })
  })

  describe('getAllDevices', () => {
    it('paginates with page=1 size=100', async () => {
      mock.get.mockResolvedValueOnce(pageEnvelope([backendDeviceFixture()], { total: 1 }))
      const result = await getAllDevices()
      expect(result).toHaveLength(1)
      expect(mock.get).toHaveBeenCalledWith('/devices', { params: { page: 1, size: 100 } })
    })
  })

  describe('createDeviceUsage', () => {
    it('posts to /devices/:id/usage/start with notes=payload.purpose', async () => {
      const usage = {
        id: 1,
        deviceId: 12,
        encounterId: 9,
        usedBy: 3,
        startTime: '2026-06-28T10:05:00',
        endTime: null,
        status: 'IN_USAGE',
        notes: 'for exam',
        createdAt: '2026-06-28T10:05:00',
        updatedAt: '2026-06-28T10:05:00',
      }
      mock.post.mockResolvedValueOnce(successEnvelope(usage))
      mock.get.mockResolvedValueOnce(successEnvelope(backendDeviceFixture()))
      const result = await createDeviceUsage({ deviceId: 12, encounterId: 9, purpose: 'for exam' })
      expect(result.deviceCode).toBe('DEV-ECG-001')
      expect(mock.post).toHaveBeenCalledWith('/devices/12/usage/start', {
        deviceId: 12,
        encounterId: 9,
        notes: 'for exam',
      })
    })
  })

  describe('endDeviceUsage', () => {
    it('returns usage with endDeviceStatus=AVAILABLE when not MAINTENANCE', async () => {
      const usage = {
        id: 1,
        deviceId: 12,
        encounterId: 9,
        usedBy: 3,
        startTime: '2026-06-28T10:05:00',
        endTime: '2026-06-28T10:30:00',
        status: 'COMPLETED',
        notes: 'done',
        createdAt: '2026-06-28T10:05:00',
        updatedAt: '2026-06-28T10:30:00',
      }
      mock.post.mockResolvedValueOnce(successEnvelope(usage))
      mock.get.mockResolvedValueOnce(successEnvelope(backendDeviceFixture()))
      const result = await endDeviceUsage(12, { result: 'done' })
      expect(result.endDeviceStatus).toBe('AVAILABLE')
      expect(mock.post).toHaveBeenCalledWith('/devices/12/usage/end', { notes: 'done' })
    })

    it('transitions to MAINTENANCE when requested', async () => {
      const usage = {
        id: 1,
        deviceId: 12,
        encounterId: 9,
        usedBy: 3,
        startTime: '2026-06-28T10:05:00',
        endTime: '2026-06-28T10:30:00',
        status: 'COMPLETED',
        notes: 'broken',
        createdAt: '2026-06-28T10:05:00',
        updatedAt: '2026-06-28T10:30:00',
      }
      mock.post.mockResolvedValueOnce(successEnvelope(usage))
      mock.get.mockResolvedValueOnce(successEnvelope(backendDeviceFixture()))
      mock.post.mockResolvedValueOnce(successEnvelope(backendDeviceFixture()))
      mock.post.mockResolvedValueOnce(successEnvelope(backendDeviceFixture({ status: 'MAINTENANCE' })))
      const result = await endDeviceUsage(12, { result: 'broken', deviceEndStatus: 'MAINTENANCE' })
      expect(result.endDeviceStatus).toBe('MAINTENANCE')
      expect(mock.post).toHaveBeenCalledTimes(3)
    })
  })

  describe('changeDeviceStatus', () => {
    it('posts targetStatus and reason', async () => {
      mock.post.mockResolvedValueOnce(
        successEnvelope(backendDeviceFixture({ status: 'MAINTENANCE' })),
      )
      const result = await changeDeviceStatus(12, 'MAINTENANCE', 'periodic check')
      expect(result.status).toBe('MAINTENANCE')
      expect(mock.post).toHaveBeenCalledWith('/devices/12/status', {
        targetStatus: 'MAINTENANCE',
        reason: 'periodic check',
      })
    })
  })

  describe('getDeviceStatusHistory', () => {
    it('maps history records', async () => {
      const history = [
        {
          id: 1,
          deviceId: 12,
          fromStatus: 'AVAILABLE',
          toStatus: 'MAINTENANCE',
          operatorId: 3,
          reason: 'periodic',
          changedAt: '2026-06-28T10:00:00',
        },
        {
          id: 2,
          deviceId: 12,
          fromStatus: null,
          toStatus: 'AVAILABLE',
          operatorId: null,
          reason: null,
          changedAt: '2026-06-28T11:00:00',
        },
      ]
      mock.get.mockResolvedValueOnce(successEnvelope(history))
      const result = await getDeviceStatusHistory(12)
      expect(result).toHaveLength(2)
      expect(result[0].fromStatus).toBe('AVAILABLE')
      expect(result[0].operatorName).toBe('user-3')
      expect(result[1].fromStatus).toBeNull()
      expect(result[1].operatorName).toBe('')
    })
  })

  describe('getEncounterDeviceUsages', () => {
    it('maps usages with device cache lookup', async () => {
      const usage = {
        id: 1,
        deviceId: 12,
        encounterId: 9,
        usedBy: 3,
        startTime: '2026-06-28T10:05:00',
        endTime: null,
        status: 'IN_USAGE',
        notes: 'for exam',
        createdAt: '2026-06-28T10:05:00',
        updatedAt: '2026-06-28T10:05:00',
      }
      mock.get.mockResolvedValueOnce(successEnvelope([usage]))
      mock.get.mockResolvedValueOnce(successEnvelope(backendDeviceFixture()))
      const result = await getEncounterDeviceUsages(9)
      expect(result).toHaveLength(1)
      expect(result[0].deviceName).toBe('ECG')
      expect(result[0].doctorName).toBe('user-3')
    })

    it('returns empty array when no usages', async () => {
      mock.get.mockResolvedValueOnce(successEnvelope([]))
      const result = await getEncounterDeviceUsages(9)
      expect(result).toEqual([])
    })
  })
})
