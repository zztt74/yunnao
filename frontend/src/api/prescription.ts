import type { EncounterResponse } from '@/types/encounter'
import type { PageResponse } from '@/types/api'
import type {
  PrescriptionAiReview,
  PrescriptionResponse,
  PrescriptionRiskLevel,
  PrescriptionSaveRequest,
} from '@/types/prescription'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import { getPatientInfo } from '@/api/patient'

interface BackendPrescriptionResponse {
  id: number
  encounterId: number
  patientId: number
  doctorId: number
  status: 'DRAFT' | 'CONFIRMED' | 'VOIDED'
  aiReviewStatus: 'NOT_REQUESTED' | 'PENDING' | 'REVIEWED' | 'FAILED'
  createdAt: string
  confirmedAt?: string | null
  confirmedBy?: number | null
  voidedAt?: string | null
  voidedBy?: number | null
  voidedReason?: string | null
  items: BackendPrescriptionItem[]
  review?: BackendPrescriptionReview | null
}

interface BackendPrescriptionItem {
  id: number
  drugCode: string
  drugName: string
  dosage: string
  dosageValue?: number | null
  frequency: string
  duration: number
  quantity: number
  instructions?: string | null
}

interface BackendPrescriptionReview {
  id: number
  prescriptionId: number
  reviewStatus: string
  riskLevel: PrescriptionRiskLevel | 'CONTRAINDICATED'
  allergyWarnings: string[]
  interactionWarnings: string[]
  dosageWarnings: string[]
  contraindicationWarnings: string[]
  suggestions?: string | null
  summary?: string | null
  ruleCheckSummary?: string | null
  reviewedAt: string
  createdAt: string
}

export async function getMyPrescriptions(params?: {
  fromDate?: string
  toDate?: string
  includeDraft?: boolean
}): Promise<PrescriptionResponse[]> {
  const patient = await getPatientInfo()
  return getPatientPrescriptions(patient.id, params)
}

/**
 * 按患者 ID 查询处方列表（含草稿/已确认/已作废）。
 * 默认过滤 DRAFT，医生端如需查看全部可传 includeDraft=true。
 */
export async function getPatientPrescriptions(
  patientId: number,
  options: {
    fromDate?: string
    toDate?: string
    includeDraft?: boolean
  } = {},
): Promise<PrescriptionResponse[]> {
  const res = await apiClient.get(`/prescriptions/patient/${patientId}`)
  let list = parseApiResponse<PageResponse<BackendPrescriptionResponse>>(res.data).items
    .map((prescription) => mapPrescription(prescription))
  if (!options.includeDraft) {
    list = list.filter((p) => p.status !== 'DRAFT')
  }
  if (options.fromDate) {
    list = list.filter((p) => (p.confirmedAt || p.createdAt) >= options.fromDate!)
  }
  if (options.toDate) {
    const end = `${options.toDate}T23:59:59`
    list = list.filter((p) => (p.confirmedAt || p.createdAt) <= end)
  }
  return list.sort(
    (a, b) =>
      new Date(b.confirmedAt || b.createdAt).getTime() -
      new Date(a.confirmedAt || a.createdAt).getTime(),
  )
}

export async function getPrescriptionById(id: number): Promise<PrescriptionResponse> {
  const res = await apiClient.get(`/prescriptions/${id}`)
  return mapPrescription(parseApiResponse<BackendPrescriptionResponse>(res.data))
}

export async function getEncounterPrescription(encounterId: number): Promise<PrescriptionResponse | null> {
  const res = await apiClient.get(`/prescriptions/encounter/${encounterId}`)
  const prescriptions = parseApiResponse<BackendPrescriptionResponse[]>(res.data)
  return prescriptions.length > 0 ? mapPrescription(prescriptions[0]) : null
}

export async function savePrescriptionDraft(
  encounter: EncounterResponse,
  payload: PrescriptionSaveRequest,
): Promise<PrescriptionResponse> {
  const res = await apiClient.post('/prescriptions', {
    encounterId: encounter.id,
    items: payload.items.map((item) => ({
      drugCode: item.drugCode,
      drugName: item.drugName,
      dosage: item.dosage,
      dosageValue: Number.parseFloat(item.dosage) || 1,
      frequency: item.frequency,
      duration: parseDurationDays(item.duration),
      quantity: parseDurationDays(item.duration),
      instructions: [item.usage, item.remark].filter(Boolean).join('；') || undefined,
    })),
  })
  return mapPrescription(parseApiResponse<BackendPrescriptionResponse>(res.data), {
    diagnosis: payload.diagnosis,
    remark: payload.remark,
    encounter,
  })
}

export async function aiReviewPrescription(
  prescriptionId: number,
  _patientAllergies: string,
): Promise<PrescriptionResponse> {
  await apiClient.post('/prescription/check', { prescriptionId })
  return getPrescriptionById(prescriptionId)
}

export async function confirmPrescription(
  prescriptionId: number,
  _forceHighRisk = false,
): Promise<PrescriptionResponse> {
  const res = await apiClient.post(`/prescriptions/${prescriptionId}/confirm`)
  return mapPrescription(parseApiResponse<BackendPrescriptionResponse>(res.data))
}

export async function voidPrescription(
  prescriptionId: number,
  reason: string,
): Promise<PrescriptionResponse> {
  const res = await apiClient.post(`/prescriptions/${prescriptionId}/void`, { reason })
  return mapPrescription(parseApiResponse<BackendPrescriptionResponse>(res.data))
}

function mapPrescription(
  prescription: BackendPrescriptionResponse,
  context?: { diagnosis?: string; remark?: string; encounter?: EncounterResponse },
): PrescriptionResponse {
  // 字段映射兜底：后端 PrescriptionResponse 当前不下发医生/科室/患者姓名；
  // 在没有 encounter 上下文时使用占位文本，避免前端 charAt(0) 之类调用引发渲染异常。
  const doctorName = context?.encounter?.doctorName?.trim()
    || `医生 ${prescription.doctorId}`
  const departmentName = context?.encounter?.departmentName?.trim()
    || '所属科室待医生确认'
  const patientName = context?.encounter?.patientName?.trim()
    || `患者 ${prescription.patientId}`
  return {
    id: prescription.id,
    encounterId: prescription.encounterId,
    patientId: prescription.patientId,
    patientName,
    doctorId: prescription.doctorId,
    doctorName,
    departmentName,
    diagnosis: context?.diagnosis?.trim() || '待医生补充诊断',
    items: prescription.items.map((item) => ({
      id: item.id,
      drugId: item.id,
      drugCode: item.drugCode,
      drugName: item.drugName || '药品',
      strength: '',
      unit: '',
      dosage: item.dosage || '--',
      frequency: item.frequency || '--',
      usage: item.instructions ?? '',
      duration: item.duration ? `${item.duration}` : '按医嘱',
      remark: '',
    })),
    status: prescription.status,
    voidedReason: prescription.voidedReason ?? null,
    voidedAt: prescription.voidedAt ?? null,
    remark: context?.remark,
    aiReview: mapReview(prescription.review),
    aiReviewStatus: prescription.aiReviewStatus,
    confirmedAt: prescription.confirmedAt ?? null,
    createdAt: prescription.createdAt,
    updatedAt: prescription.confirmedAt ?? prescription.voidedAt ?? prescription.createdAt,
  }
}

function mapReview(review?: BackendPrescriptionReview | null): PrescriptionAiReview | null {
  if (!review) return null
  const warnings = [
    ...review.allergyWarnings,
    ...review.interactionWarnings,
    ...review.dosageWarnings,
    ...review.contraindicationWarnings,
    review.ruleCheckSummary,
  ].filter(Boolean) as string[]
  return {
    riskLevel: review.riskLevel === 'CONTRAINDICATED' ? 'HIGH' : review.riskLevel,
    warnings,
    advice: review.suggestions || review.summary || undefined,
    reviewedAt: review.reviewedAt,
  }
}

function parseDurationDays(duration: string): number {
  const parsed = Number.parseInt(duration, 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1
}
