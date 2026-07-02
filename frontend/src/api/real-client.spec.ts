import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiClientMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  apiClient: apiClientMock,
}))

import { login } from '@/api/auth'
import { getDepartments } from '@/api/department'
import { getPatientInfo, updatePatientProfile } from '@/api/patient'
import { getAvailableSchedules, getMyAppointments } from '@/api/appointment'
import { consultTriage, getMyTriageRecords } from '@/api/triage'
import { createDeviceUsage, getAllDevices } from '@/api/device'
import { getMyExaminations } from '@/api/examination'

function success<T>(data: T) {
  return {
    data: {
      code: 'SUCCESS',
      message: '操作成功',
      data,
      traceId: 'trace-test',
    },
  }
}

describe('real API clients', () => {
  beforeEach(() => {
    apiClientMock.get.mockReset()
    apiClientMock.post.mockReset()
    apiClientMock.put.mockReset()
  })

  it('calls the backend auth login contract', async () => {
    const payload = { username: 'patient', password: 'secret123' }
    const response = {
      accessToken: 'token',
      tokenType: 'Bearer',
      userId: 1,
      username: 'patient',
      roles: ['PATIENT'],
      mustChangePassword: false,
      expiresIn: 7200,
    }
    apiClientMock.post.mockResolvedValueOnce(success(response))

    await expect(login(payload)).resolves.toEqual(response)
    expect(apiClientMock.post).toHaveBeenCalledWith('/auth/login', payload)
  })

  it('uses patient endpoints from the frozen OpenAPI contract', async () => {
    const patient = {
      id: 42,
      userId: 7,
      name: '张三',
      gender: 'MALE',
      birthDate: '1990-01-01',
      phone: '13800000000',
      status: 'ACTIVE',
      createdAt: '2026-06-28T10:00:00',
      updatedAt: '2026-06-28T10:00:00',
    }
    apiClientMock.get.mockResolvedValueOnce(success(patient))

    await expect(getPatientInfo()).resolves.toEqual(patient)
    expect(apiClientMock.get).toHaveBeenCalledWith('/patients/me')

    const profilePayload = { allergies: '青霉素' }
    const profile = {
      id: 9,
      patientId: 42,
      address: null,
      emergencyContact: null,
      emergencyPhone: null,
      allergies: '青霉素',
      medicalHistory: null,
      createdAt: '2026-06-28T10:00:00',
      updatedAt: '2026-06-28T10:00:00',
    }
    apiClientMock.get.mockResolvedValueOnce(success(patient))
    apiClientMock.put.mockResolvedValueOnce(success(profile))

    await expect(updatePatientProfile(profilePayload)).resolves.toEqual(profile)
    expect(apiClientMock.put).toHaveBeenCalledWith('/patients/42/profile', profilePayload)
  })

  it('uses the backend department list contract for appointment booking', async () => {
    const departments = [
      {
        id: 1,
        code: 'DEPT_INTERNAL',
        name: '内科',
        parentId: null,
        level: 1,
        sortOrder: 1,
        status: 'ENABLED',
        description: '内科系统',
        createdAt: '2026-06-28T10:00:00',
        updatedAt: '2026-06-28T10:00:00',
        children: [],
      },
    ]
    apiClientMock.get.mockResolvedValueOnce(success(departments))

    await expect(getDepartments()).resolves.toEqual(departments)
    expect(apiClientMock.get).toHaveBeenCalledWith('/departments')
  })

  it('uses schedule and appointment endpoints without local medical mocks', async () => {
    const schedule = {
      id: 3,
      doctorId: 11,
      doctorName: '李医生',
      departmentId: 2,
      departmentName: '心血管内科',
      scheduleDate: '2026-06-29',
      startTime: '2026-06-29T09:00:00',
      endTime: '2026-06-29T09:30:00',
      maxAppointments: 20,
      bookedCount: 1,
      remainingCount: 19,
      status: 'AVAILABLE',
      createdAt: '2026-06-28T10:00:00',
      updatedAt: '2026-06-28T10:00:00',
    }
    apiClientMock.get.mockResolvedValueOnce(success([schedule]))

    await expect(getAvailableSchedules({ departmentId: 2, date: '2026-06-29' })).resolves.toEqual([
      schedule,
    ])
    expect(apiClientMock.get).toHaveBeenCalledWith('/schedules/available', {
      params: {
        departmentId: 2,
        date: '2026-06-29T00:00:00',
      },
    })

    const patient = { id: 42 }
    const appointment = {
      id: 5,
      patientId: 42,
      patientName: '张三',
      scheduleId: 3,
      doctorId: 11,
      doctorName: '李医生',
      departmentId: 2,
      departmentName: '心血管内科',
      appointmentNumber: 'A000001',
      status: 'BOOKED',
      bookedAt: '2026-06-28T10:00:00',
      createdAt: '2026-06-28T10:00:00',
      updatedAt: '2026-06-28T10:00:00',
    }
    apiClientMock.get.mockResolvedValueOnce(success(patient))
    apiClientMock.get.mockResolvedValueOnce(
      success({ items: [appointment], page: 1, pageSize: 20, total: 1, totalPages: 1 }),
    )

    await expect(getMyAppointments()).resolves.toEqual([appointment])
    expect(apiClientMock.get).toHaveBeenCalledWith('/appointments/patient/42')
  })

  it('maps triage calls to the backend consult and history contracts', async () => {
    const patient = { id: 42 }
    apiClientMock.get.mockResolvedValueOnce(success(patient))
    apiClientMock.post.mockResolvedValueOnce(
      success({
        triageRecordId: 8,
        patientId: 42,
        symptoms: '胸痛',
        duration: '30分钟',
        supplement: null,
        aiDepartmentCode: 'DEPT_EMERGENCY',
        aiPriority: 'EMERGENCY',
        aiReason: '存在急诊风险',
        aiSafetyNotice: '请立即就诊',
        aiEmergencySuggested: true,
        aiStatus: 'SUCCESS',
        aiFailureReason: null,
        mappedDepartmentId: 7,
        mappedDepartmentName: '急诊科',
        mappingStatus: 'MAPPED',
        createdAt: '2026-06-28T10:00:00',
        conversationId: null,
        round: 1,
        isFinal: true,
        followUpQuestion: null,
      }),
    )

    const triage = await consultTriage({ chiefComplaint: '胸痛', duration: '30分钟' })
    expect(apiClientMock.post).toHaveBeenCalledWith('/triage/consult', {
      patientId: 42,
      symptoms: '胸痛',
      duration: '30分钟',
      supplement: undefined,
    })
    expect(triage.recommendedDepartmentName).toBe('急诊科')
    expect(triage.priority).toBe('EMERGENCY')
    expect(triage.round).toBe(1)
    expect(triage.isFinal).toBe(true)

    apiClientMock.get.mockResolvedValueOnce(success(patient))
    apiClientMock.post.mockResolvedValueOnce(
      success({
        triageRecordId: 9,
        patientId: 42,
        symptoms: '胸痛伴出汗',
        duration: '30分钟',
        supplement: '无过敏',
        aiDepartmentCode: 'DEPT_EMERGENCY',
        aiPriority: 'EMERGENCY',
        aiReason: '存在急诊风险',
        aiSafetyNotice: '请立即就诊',
        aiEmergencySuggested: true,
        aiStatus: 'SUCCESS',
        aiFailureReason: null,
        mappedDepartmentId: 7,
        mappedDepartmentName: '急诊科',
        mappingStatus: 'MAPPED',
        createdAt: '2026-06-28T10:01:00',
        conversationId: '12345678-1234-1234-1234-123456789abc',
        round: 2,
        isFinal: false,
        followUpQuestion: '是否伴有呼吸困难？',
      }),
    )

    await expect(consultTriage({
      chiefComplaint: '胸痛伴出汗',
      duration: '30分钟',
      additionalInfo: '无过敏',
      conversationId: '12345678-1234-1234-1234-123456789abc',
      round: 2,
      history: [
        { role: 'user', text: '胸痛' },
        { role: 'ai', text: '存在急诊风险' },
      ],
    })).resolves.toMatchObject({
      conversationId: '12345678-1234-1234-1234-123456789abc',
      round: 2,
      isFinal: false,
      followUpQuestion: '是否伴有呼吸困难？',
    })
    expect(apiClientMock.post).toHaveBeenLastCalledWith('/triage/consult', {
      patientId: 42,
      symptoms: '胸痛伴出汗',
      duration: '30分钟',
      supplement: '无过敏',
      conversationId: '12345678-1234-1234-1234-123456789abc',
      round: 2,
      history: [
        { role: 'USER', content: '胸痛' },
        { role: 'ASSISTANT', content: '存在急诊风险' },
      ],
    })

    apiClientMock.get.mockResolvedValueOnce(success(patient))
    apiClientMock.get.mockResolvedValueOnce(
      success({
        items: [
          {
            id: 8,
            patientId: 42,
            symptoms: '胸痛',
            aiDepartmentCode: 'DEPT_EMERGENCY',
            aiPriority: 'EMERGENCY',
            aiReason: '存在急诊风险',
            aiSafetyNotice: '请立即就诊',
            aiEmergencySuggested: true,
            mappedDepartmentId: 7,
            mappingStatus: 'MAPPED',
            aiStatus: 'SUCCESS',
            aiFailureReason: null,
            createdAt: '2026-06-28T10:00:00',
          },
        ],
        page: 1,
        pageSize: 20,
        total: 1,
        totalPages: 1,
      }),
    )

    await expect(getMyTriageRecords()).resolves.toHaveLength(1)
    expect(apiClientMock.get).toHaveBeenCalledWith('/triage/patient/42')
  })

  it('uses backend device contracts with API-layer field mapping', async () => {
    const backendDevice = {
      id: 12,
      code: 'DEV-ECG-001',
      name: 'ECG',
      type: 'ECG',
      departmentId: 1,
      status: 'AVAILABLE',
      purchaseDate: null,
      warrantyUntil: null,
      lastMaintenance: null,
      location: 'Room 1',
      manufacturer: 'GE',
      model: 'MAC 5500',
      serialNumber: 'SN-1',
      notes: 'routine',
      createdAt: '2026-06-28T10:00:00',
      updatedAt: '2026-06-28T10:00:00',
    }
    apiClientMock.get.mockResolvedValueOnce(
      success({ items: [backendDevice], page: 1, pageSize: 100, total: 1, totalPages: 1 }),
    )

    await expect(getAllDevices()).resolves.toMatchObject([
      {
        id: 12,
        code: 'DEV-ECG-001',
        category: 'EXAMINATION',
        status: 'AVAILABLE',
        enabled: true,
      },
    ])
    expect(apiClientMock.get).toHaveBeenCalledWith('/devices', {
      params: { page: 1, size: 100 },
    })

    apiClientMock.post.mockResolvedValueOnce(
      success({
        id: 77,
        deviceId: 12,
        encounterId: 9,
        usedBy: 3,
        startTime: '2026-06-28T10:05:00',
        endTime: null,
        status: 'IN_USAGE',
        notes: 'for exam',
        createdAt: '2026-06-28T10:05:00',
        updatedAt: '2026-06-28T10:05:00',
      }),
    )
    apiClientMock.get.mockResolvedValueOnce(success(backendDevice))

    await expect(createDeviceUsage({
      deviceId: 12,
      encounterId: 9,
      purpose: 'for exam',
    })).resolves.toMatchObject({
      id: 77,
      deviceId: 12,
      status: 'IN_USE',
      deviceCode: 'DEV-ECG-001',
    })
    expect(apiClientMock.post).toHaveBeenCalledWith('/devices/12/usage/start', {
      deviceId: 12,
      encounterId: 9,
      notes: 'for exam',
    })
  })

  it('uses the patient examination tracking contract', async () => {
    const patient = { id: 42 }
    apiClientMock.get.mockResolvedValueOnce(success(patient))
    apiClientMock.get.mockResolvedValueOnce(
      success([
        {
          orderId: 18,
          encounterId: 9,
          orderType: 'EXAMINATION',
          itemCode: 'XRAY-CHEST',
          itemName: '胸部 X 光',
          status: 'ORDERED',
          doctorName: '李医生',
          departmentId: 2,
          departmentName: '影像科',
          departmentLocation: '2 楼影像中心',
          nextAction: '医生已开立，请前往影像科检查',
          deviceName: 'X 光机 01',
          deviceLocation: '201 室',
          orderedAt: '2026-06-28T10:00:00',
          inProgressAt: null,
          resultEnteredAt: null,
          reviewedAt: null,
          cancelledAt: null,
          cancelReason: null,
        },
      ]),
    )

    await expect(getMyExaminations()).resolves.toMatchObject([
      {
        id: 18,
        status: 'ORDERED',
        nextAction: '医生已开立，请前往影像科检查',
        departmentLocation: '2 楼影像中心',
        deviceName: 'X 光机 01',
        deviceLocation: '201 室',
      },
    ])
    expect(apiClientMock.get).toHaveBeenCalledWith('/examinations/patient/42/tracking')
  })
})
