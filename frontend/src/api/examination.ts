import type { ExaminationResponse, ExaminationCreateRequest } from '@/types/examination'
import type { EncounterResponse } from '@/types/encounter'
import { mockExaminations } from '@/api/mock/medical-mock'
import {
  getEncounterExaminations as mockGetEncounterExams,
  createExamination as mockCreateExam,
  simulateEnterExaminationResult as mockEnterResult,
  reviewExamination as mockReviewExam,
  aiInterpretExamination as mockAiInterp,
  shouldSimulateAiFailure,
} from '@/api/mock/doctor-mock'

// MOCK：后端 /api/examinations、/api/laboratory 接口未就绪，使用本地演示数据
// 后端就绪后请删除本文件并替换为真实调用

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 查询当前患者的检查检验申请列表（仅返回已审核的，按时间倒序） */
export async function getMyExaminations(params?: {
  type?: 'EXAMINATION' | 'LABORATORY'
  /** 开始日期（含），格式 yyyy-MM-dd，按 reportedAt 过滤 */
  fromDate?: string
  /** 结束日期（含），格式 yyyy-MM-dd，按 reportedAt 过滤 */
  toDate?: string
}): Promise<ExaminationResponse[]> {
  console.warn('[MOCK] /api/examinations 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  let list = [...mockExaminations]
  // 按设计文档 §14.4、§10.6：未审核结果不向患者展示
  list = list.filter((r) => r.status === 'REVIEWED')
  if (params?.type) {
    list = list.filter((r) => r.type === params.type)
  }
  if (params?.fromDate) {
    list = list.filter(
      (r) => (r.reportedAt || r.orderedAt) >= params.fromDate!,
    )
  }
  if (params?.toDate) {
    // toDate 取到当天 23:59:59
    const end = `${params.toDate}T23:59:59+08:00`
    list = list.filter((r) => (r.reportedAt || r.orderedAt) <= end)
  }
  return list.sort(
    (a, b) => new Date(b.orderedAt).getTime() - new Date(a.orderedAt).getTime(),
  )
}

/** 获取检查检验详情 */
export async function getExaminationById(id: number): Promise<ExaminationResponse> {
  console.warn('[MOCK] /api/examinations/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  const found = mockExaminations.find((r) => r.id === id)
  if (!found) {
    throw new Error('检查检验记录不存在')
  }
  return found
}

// ===== 医生端：检查检验开立与审核 =====

/** 查询某就诊下的检查检验列表（医生端，含未审核） */
export async function getEncounterExaminations(encounterId: number): Promise<ExaminationResponse[]> {
  console.warn('[MOCK] /api/examinations/encounter/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockGetEncounterExams(encounterId)
}

/** 医生开立检查检验申请（§10.3） */
export async function createExamination(
  encounter: EncounterResponse,
  payload: ExaminationCreateRequest,
): Promise<ExaminationResponse> {
  console.warn('[MOCK] /api/examinations POST 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  return mockCreateExam(encounter, payload)
}

/**
 * 模拟结果录入（演示用，§10.4：结果由管理员或模拟人员录入）
 * 后端就绪后由独立的结果录入接口承担，本函数仅供演示流程闭环
 */
export async function simulateEnterResult(id: number): Promise<ExaminationResponse> {
  console.warn('[MOCK] 模拟检查检验结果录入（演示）')
  await delay(500)
  return mockEnterResult(id)
}

/** 医生审核结果（RESULT_ENTERED → REVIEWED，§10 状态机） */
export async function reviewExamination(id: number): Promise<ExaminationResponse> {
  console.warn('[MOCK] /api/examinations/{id}/review 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  return mockReviewExam(id)
}

/** AI 解读检查检验（§10.3）：不修改原始结果，仅追加解读 */
export async function aiInterpretExamination(id: number): Promise<ExaminationResponse> {
  console.warn('[MOCK] /api/examinations/{id}/ai-interpretation 后端未就绪，使用本地虚构演示数据')
  await delay(1200)
  if (shouldSimulateAiFailure()) {
    sessionStorage.removeItem('cloud-brain.mock-ai-fail')
    // AI 解读失败：原始结果仍可查看（§10.7），不阻断医生流程
    throw new Error('AI_INTERPRETATION_FAILED：AI 解读失败，原始结果仍可查看。')
  }
  return mockAiInterp(id)
}
