import type { ExaminationResponse } from '@/types/examination'
import { mockExaminations } from '@/api/mock/medical-mock'

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
