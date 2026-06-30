import type { EncounterResponse } from '@/types/encounter'
import type { PageResponse } from '@/types/api'
import type {
  ExaminationCreateRequest,
  ExaminationResponse,
  ExaminationStatus,
  ExaminationType,
} from '@/types/examination'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import { getPatientInfo } from '@/api/patient'

interface BackendExaminationOrder {
  id: number
  encounterId: number
  patientId: number
  doctorId: number
  orderType: string
  itemCode?: string | null
  itemName: string
  status: ExaminationStatus
  orderedAt: string
  inProgressAt?: string | null
  resultEnteredAt?: string | null
  reviewedAt?: string | null
  cancelledAt?: string | null
  cancelReason?: string | null
  returnReason?: string | null
  createdAt: string
  updatedAt: string
}

interface BackendExaminationResult {
  id: number
  orderId: number
  resultText: string
  normalRange?: string | null
  conclusion?: string | null
  abnormalFlag?: string | null
  enteredBy?: number | null
  reviewedBy?: number | null
  aiInterpretation?: string | null
  aiAbnormalItems?: string | null
  aiFollowUpAdvice?: string | null
  aiStatus?: string | null
  aiFailureReason?: string | null
  createdAt: string
  updatedAt: string
}

export async function getMyExaminations(params?: {
  type?: ExaminationType
  fromDate?: string
  toDate?: string
}): Promise<ExaminationResponse[]> {
  const patient = await getPatientInfo()
  const res = await apiClient.get(`/examinations/patient/${patient.id}`)
  let list = parseApiResponse<PageResponse<BackendExaminationOrder>>(res.data).items
    .map((order) => mapExamination(order))
    .filter((item) => item.status === 'REVIEWED')
  if (params?.type) {
    list = list.filter((item) => item.type === params.type)
  }
  if (params?.fromDate) {
    list = list.filter((item) => (item.reportedAt || item.orderedAt) >= params.fromDate!)
  }
  if (params?.toDate) {
    const end = `${params.toDate}T23:59:59`
    list = list.filter((item) => (item.reportedAt || item.orderedAt) <= end)
  }
  return list.sort((a, b) => new Date(b.orderedAt).getTime() - new Date(a.orderedAt).getTime())
}

export async function getExaminationById(id: number): Promise<ExaminationResponse> {
  const res = await apiClient.get(`/examinations/${id}`)
  return withResult(parseApiResponse<BackendExaminationOrder>(res.data))
}

export async function getEncounterExaminations(encounterId: number): Promise<ExaminationResponse[]> {
  const res = await apiClient.get(`/examinations/encounter/${encounterId}`)
  const orders = parseApiResponse<BackendExaminationOrder[]>(res.data)
  return Promise.all(orders.map((order) => withResult(order)))
}

export async function createExamination(
  encounter: EncounterResponse,
  payload: ExaminationCreateRequest,
): Promise<ExaminationResponse> {
  const res = await apiClient.post('/examinations', {
    encounterId: encounter.id,
    orderType: payload.type,
    itemCode: payload.itemName,
    itemName: payload.itemName,
  })
  return mapExamination(parseApiResponse<BackendExaminationOrder>(res.data), encounter)
}

export async function simulateEnterResult(id: number): Promise<ExaminationResponse> {
  const order = await getOrder(id)
  if (order.status === 'ORDERED') {
    await apiClient.post(`/examinations/${id}/start`)
  }
  const result = await apiClient.post(`/examinations/${id}/result`, {
    resultText: '已完成检查检验，结果录入来自联调模拟人员。',
    normalRange: '参考范围见原始报告',
    conclusion: '未见明显异常',
    abnormalFlag: 'NORMAL',
  })
  return mapExamination(await getOrder(id), undefined, parseApiResponse<BackendExaminationResult>(result.data))
}

export async function reviewExamination(id: number): Promise<ExaminationResponse> {
  const result = await apiClient.post(`/examinations/${id}/review`)
  return mapExamination(await getOrder(id), undefined, parseApiResponse<BackendExaminationResult>(result.data))
}

export async function aiInterpretExamination(id: number): Promise<ExaminationResponse> {
  return getExaminationById(id)
}

async function getOrder(id: number): Promise<BackendExaminationOrder> {
  const res = await apiClient.get(`/examinations/${id}`)
  return parseApiResponse<BackendExaminationOrder>(res.data)
}

async function withResult(order: BackendExaminationOrder): Promise<ExaminationResponse> {
  if (order.status !== 'RESULT_ENTERED' && order.status !== 'REVIEWED') {
    return mapExamination(order)
  }
  try {
    const result = await apiClient.get(`/examinations/${order.id}/result`)
    return mapExamination(order, undefined, parseApiResponse<BackendExaminationResult>(result.data))
  } catch {
    return mapExamination(order)
  }
}

function mapExamination(
  order: BackendExaminationOrder,
  encounter?: EncounterResponse,
  result?: BackendExaminationResult,
): ExaminationResponse {
  return {
    id: order.id,
    encounterId: order.encounterId,
    patientId: order.patientId,
    doctorId: order.doctorId,
    doctorName: encounter?.doctorName ?? '',
    departmentName: encounter?.departmentName ?? '',
    type: order.orderType === 'LABORATORY' ? 'LABORATORY' : 'EXAMINATION',
    itemName: order.itemName,
    purpose: order.itemCode ?? '',
    orderedAt: order.orderedAt,
    reportedAt: order.resultEnteredAt ?? result?.createdAt ?? null,
    reviewedAt: order.reviewedAt ?? null,
    reporterName: result?.enteredBy ? `user-${result.enteredBy}` : null,
    status: order.status,
    labItems: result && order.orderType === 'LABORATORY'
      ? [{
        id: result.id,
        itemName: order.itemName,
        resultValue: result.resultText,
        unit: '',
        referenceRange: result.normalRange ?? '',
        abnormalFlag: result.abnormalFlag === 'HIGH' || result.abnormalFlag === 'LOW'
          ? result.abnormalFlag
          : 'NORMAL',
      }]
      : [],
    findings: result?.resultText,
    impression: result?.conclusion ?? undefined,
    aiInterpretation: result?.aiInterpretation ?? undefined,
    createdAt: order.createdAt,
    updatedAt: result?.updatedAt ?? order.updatedAt,
  }
}
