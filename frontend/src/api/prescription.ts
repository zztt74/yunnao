import type { PrescriptionResponse } from '@/types/prescription'
import { mockPrescriptions } from '@/api/mock/medical-mock'

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
