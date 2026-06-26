import type {
  PrescriptionResponse,
  PrescriptionSaveRequest,
} from '@/types/prescription'
import type { EncounterResponse } from '@/types/encounter'
import { mockPrescriptions } from '@/api/mock/medical-mock'
import {
  getEncounterPrescription as mockGetEncPres,
  savePrescriptionDraft as mockSaveDraft,
  aiReviewPrescription as mockAiReview,
  confirmPrescription as mockConfirm,
  voidPrescription as mockVoid,
  shouldSimulateAiFailure,
} from '@/api/mock/doctor-mock'

// MOCK：后端 /api/prescriptions 接口未就绪，使用本地演示数据
// 后端就绪后请删除本文件并替换为真实调用

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 查询当前患者的处方列表（CONFIRMED/VOIDED，按时间倒序） */
export async function getMyPrescriptions(params?: {
  /** 开始日期（含），格式 yyyy-MM-dd，按 confirmedAt 过滤 */
  fromDate?: string
  /** 结束日期（含），格式 yyyy-MM-dd，按 confirmedAt 过滤 */
  toDate?: string
}): Promise<PrescriptionResponse[]> {
  console.warn('[MOCK] /api/prescriptions 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  // 按设计文档 §14.4、§12.6：正式处方才向患者展示（不含 DRAFT 草稿）
  let list = mockPrescriptions.filter((p) => p.status !== 'DRAFT')
  if (params?.fromDate) {
    list = list.filter(
      (p) => (p.confirmedAt || p.createdAt) >= params.fromDate!,
    )
  }
  if (params?.toDate) {
    const end = `${params.toDate}T23:59:59+08:00`
    list = list.filter(
      (p) => (p.confirmedAt || p.createdAt) <= end,
    )
  }
  return list.sort(
    (a, b) =>
      new Date(b.confirmedAt || b.createdAt).getTime() -
      new Date(a.confirmedAt || a.createdAt).getTime(),
  )
}

/** 获取处方详情 */
export async function getPrescriptionById(id: number): Promise<PrescriptionResponse> {
  console.warn('[MOCK] /api/prescriptions/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  const found = mockPrescriptions.find((r) => r.id === id)
  if (!found) {
    throw new Error('处方不存在')
  }
  return found
}

// ===== 医生端：处方开立与审核 =====

/** 查询某就诊的处方（医生端，含草稿） */
export async function getEncounterPrescription(encounterId: number): Promise<PrescriptionResponse | null> {
  console.warn('[MOCK] /api/prescriptions/encounter/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetEncPres(encounterId) ?? null
}

/** 医生创建/更新处方草稿（DRAFT，§12.4） */
export async function savePrescriptionDraft(
  encounter: EncounterResponse,
  payload: PrescriptionSaveRequest,
): Promise<PrescriptionResponse> {
  console.warn('[MOCK] /api/prescriptions POST（草稿）后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockSaveDraft(encounter, payload)
}

/**
 * AI 处方审核（§12.4、§12.6）
 * - 先执行确定性规则检查（过敏/相互作用/剂量），不被 AI 覆盖
 * - AI 负责解释风险与补充建议
 * - AI 失败时返回 FAILED 状态，医生可手工继续（§12.6）
 */
export async function aiReviewPrescription(
  prescriptionId: number,
  patientAllergies: string,
): Promise<PrescriptionResponse> {
  console.warn('[MOCK] /api/prescriptions/{id}/ai-review 后端未就绪，使用本地虚构演示数据')
  await delay(1500)
  if (shouldSimulateAiFailure()) {
    sessionStorage.removeItem('cloud-brain.mock-ai-fail')
    // AI 失败：仍执行确定性规则（§12.6），标记为 FAILED，医生可手工继续
    const pres = mockAiReview(prescriptionId, patientAllergies)
    // 覆盖为 FAILED 状态（确定性规则结果保留在 aiReview）
    return { ...pres, aiReviewStatus: 'FAILED' }
  }
  return mockAiReview(prescriptionId, patientAllergies)
}

/** 医生确认处方（DRAFT → CONFIRMED，§12.6；高风险需二次确认） */
export async function confirmPrescription(
  prescriptionId: number,
  forceHighRisk = false,
): Promise<PrescriptionResponse> {
  console.warn('[MOCK] /api/prescriptions/{id}/confirm 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockConfirm(prescriptionId, forceHighRisk)
}

/** 作废处方（CONFIRMED → VOIDED，§9 状态机；不可物理删除） */
export async function voidPrescription(
  prescriptionId: number,
  reason: string,
): Promise<PrescriptionResponse> {
  console.warn('[MOCK] /api/prescriptions/{id}/void 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockVoid(prescriptionId, reason)
}
