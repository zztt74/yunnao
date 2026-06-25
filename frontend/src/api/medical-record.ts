import type { MedicalRecord } from '@/types/medical-record'
import { mockMedicalRecords } from '@/api/mock/medical-mock'

// MOCK：后端 /api/medical-records 接口未就绪，使用本地演示数据
// 后端就绪后请删除本文件并替换为真实调用

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 查询当前患者的病历列表（按时间倒序） */
export async function getMyMedicalRecords(params?: {
  fromDate?: string
  toDate?: string
}): Promise<MedicalRecord[]> {
  console.warn('[MOCK] /api/medical-records 后端未就绪，使用本地虚构演示数据')
  await delay(400)
  let list = [...mockMedicalRecords]
  // 仅展示已 CONFIRMED 的（按设计文档 §14.4「只展示医生确认或审核后的正式结果」）
  list = list.filter((r) => r.status === 'CONFIRMED')
  if (params?.fromDate) {
    list = list.filter((r) => r.encounterDate >= params.fromDate!)
  }
  if (params?.toDate) {
    list = list.filter((r) => r.encounterDate <= params.toDate!)
  }
  return list.sort(
    (a, b) => new Date(b.encounterDate).getTime() - new Date(a.encounterDate).getTime(),
  )
}

/** 获取病历详情 */
export async function getMedicalRecordById(id: number): Promise<MedicalRecord> {
  console.warn('[MOCK] /api/medical-records/{id} 后端未就绪，使用本地虚构演示数据')
  await delay(300)
  const found = mockMedicalRecords.find((r) => r.id === id)
  if (!found) {
    throw new Error('病历不存在')
  }
  return found
}
