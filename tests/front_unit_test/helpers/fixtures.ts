// 共享测试数据：覆盖正常/边界/异常值

import type { EncounterResponse } from '@/types/encounter'

export const TIMESTAMP = '2026-07-01T10:00:00'
export const TIMESTAMP_PAST = '2024-01-01T00:00:00'
export const TIMESTAMP_FUTURE = '2026-12-31T23:59:59'

export function backendPatientFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 42,
    userId: 7,
    name: '张三',
    gender: 'MALE',
    birthDate: '1990-01-01',
    phone: '13800000000',
    status: 'ACTIVE',
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendProfileFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 9,
    patientId: 42,
    address: null,
    emergencyContact: null,
    emergencyPhone: null,
    allergies: '青霉素',
    medicalHistory: null,
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendTriageAnalyzeFixture(overrides: Record<string, unknown> = {}) {
  return {
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
    createdAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendExaminationFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 100,
    encounterId: 9,
    patientId: 42,
    doctorId: 3,
    orderType: 'LABORATORY',
    itemCode: '血常规',
    itemName: '血常规',
    status: 'ORDERED',
    orderedAt: TIMESTAMP,
    inProgressAt: null,
    resultEnteredAt: null,
    reviewedAt: null,
    cancelledAt: null,
    cancelReason: null,
    returnReason: null,
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendExaminationResultFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 200,
    orderId: 100,
    resultText: '白细胞 6.5',
    normalRange: '4-10',
    conclusion: '正常',
    abnormalFlag: 'NORMAL',
    enteredBy: 3,
    reviewedBy: 4,
    aiInterpretation: '未见明显异常',
    aiAbnormalItems: null,
    aiFollowUpAdvice: '建议 3 个月复查',
    aiStatus: 'SUCCESS',
    aiFailureReason: null,
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendPrescriptionFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 300,
    encounterId: 9,
    patientId: 42,
    doctorId: 3,
    status: 'CONFIRMED' as const,
    aiReviewStatus: 'REVIEWED' as const,
    createdAt: TIMESTAMP,
    confirmedAt: TIMESTAMP,
    confirmedBy: 3,
    voidedAt: null,
    voidedBy: null,
    voidedReason: null,
    items: [
      {
        id: 301,
        drugCode: 'D-001',
        drugName: '阿莫西林',
        dosage: '500mg',
        dosageValue: 500,
        frequency: 'TID',
        duration: 7,
        quantity: 21,
        instructions: '饭后服用',
      },
    ],
    review: {
      id: 400,
      prescriptionId: 300,
      reviewStatus: 'REVIEWED',
      riskLevel: 'LOW' as const,
      allergyWarnings: ['青霉素过敏警告'],
      interactionWarnings: [],
      dosageWarnings: [],
      contraindicationWarnings: [],
      suggestions: '可正常服用',
      summary: '无严重风险',
      ruleCheckSummary: '规则检查通过',
      reviewedAt: TIMESTAMP,
      createdAt: TIMESTAMP,
    },
    ...overrides,
  }
}

export function backendMedicalRecordFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 500,
    encounterId: 9,
    patientId: 42,
    doctorId: 3,
    content: JSON.stringify({
      chiefComplaint: '咳嗽',
      presentIllness: '3 天',
      pastHistory: '无',
      physicalExam: '双肺呼吸音清',
      preliminaryDiagnosis: '急性支气管炎',
      treatmentAdvice: '对症治疗',
    }),
    source: 'DOCTOR',
    status: 'CONFIRMED',
    createdBy: 3,
    confirmedBy: 4,
    confirmedAt: TIMESTAMP,
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendDeviceFixture(overrides: Record<string, unknown> = {}) {
  return {
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
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendDoctorFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 11,
    userId: 22,
    departmentId: 2,
    departmentName: '心血管内科',
    name: '李医生',
    title: 'ATTENDING',
    specialty: '高血压',
    status: 'ENABLED',
    education: '本科',
    experienceYears: 10,
    introduction: '从医 10 年',
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendEncounterFixture(overrides: Partial<EncounterResponse> & Record<string, unknown> = {}): EncounterResponse {
  return {
    id: 200,
    appointmentId: 1,
    patientId: 42,
    patientName: '张三',
    doctorId: 11,
    doctorName: '李医生',
    departmentId: 2,
    departmentName: '内科',
    status: 'IN_PROGRESS',
    startedAt: TIMESTAMP,
    waitingExamAt: null,
    completedAt: null,
    cancelledAt: null,
    cancelReason: null,
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}

export function backendDiagnosisFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    encounterId: 200,
    diagnosisCode: 'I10',
    diagnosisName: '高血压',
    type: 'PRELIMINARY' as const,
    source: 'DOCTOR' as const,
    aiInvocationId: null,
    doctorId: 11,
    confirmedAt: null,
    notes: null,
    createdAt: TIMESTAMP,
    updatedAt: TIMESTAMP,
    ...overrides,
  }
}
