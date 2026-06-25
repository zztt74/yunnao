import type {
  TriageConsultRequest,
  TriageResultResponse,
  TriagePriority,
} from '@/types/triage'
import { addMockTriageRecord, getMockTriageById, getMockTriageRecords } from '@/api/mock/medical-mock'

// MOCK: 后端 /api/triage/* 就绪后替换为真实调用，删除下方 mock 实现
// import { apiClient } from '@/api/client'
// import { parseApiResponse } from '@/api/response'

// 虚构分诊规则：根据主诉关键词匹配科室与优先级（演示数据，非真实诊断）
interface MockRule {
  keywords: string[]
  departmentId: number
  departmentName: string
  priority: TriagePriority
  reason: string
  safetyAdvice: string
  emergencyAdvice?: string
  followUpQuestion?: string
}

const mockRules: MockRule[] = [
  {
    keywords: ['胸痛', '胸闷', '心悸', '呼吸困难', '气短'],
    departmentId: 1,
    departmentName: '急诊科',
    priority: 'EMERGENCY',
    reason: '胸痛可能涉及心血管急症，建议立即急诊评估，排除心肌梗死等危险情况。',
    safetyAdvice: '请保持安静休息，避免剧烈活动，立即前往最近急诊。',
    emergencyAdvice: '症状存在急诊风险，请立即前往急诊科或拨打 120。',
  },
  {
    keywords: ['头痛', '头晕', '眩晕', '偏头痛'],
    departmentId: 2,
    departmentName: '神经内科',
    priority: 'HIGH',
    reason: '头痛伴眩晕可能与神经系统相关，建议尽快到神经内科排查病因。',
    safetyAdvice: '注意休息，避免驾驶和高空作业，如出现剧烈头痛或意识改变请立即急诊。',
    emergencyAdvice: '若头痛剧烈突发或伴呕吐、意识模糊，请立即急诊。',
    followUpQuestion: '头痛是持续性还是间歇性？是否伴有视力模糊或肢体麻木？',
  },
  {
    keywords: ['腹痛', '肚子痛', '腹泻', '恶心', '呕吐', '胃痛'],
    departmentId: 3,
    departmentName: '消化内科',
    priority: 'MEDIUM',
    reason: '腹部症状多与消化系统相关，建议到消化内科进一步检查。',
    safetyAdvice: '注意清淡饮食，避免油腻辛辣，如出现持续剧烈腹痛或便血请就诊。',
  },
  {
    keywords: ['发烧', '发热', '咳嗽', '咳痰', '感冒', '咽痛', '鼻塞'],
    departmentId: 4,
    departmentName: '内科',
    priority: 'MEDIUM',
    reason: '发热咳嗽多为呼吸道感染，建议内科就诊评估是否需要抗感染治疗。',
    safetyAdvice: '多饮水，注意休息，监测体温，高热不退请及时就诊。',
  },
  {
    keywords: ['骨折', '扭伤', '外伤', '摔伤', '关节痛', '腰痛', '颈椎'],
    departmentId: 5,
    departmentName: '骨科',
    priority: 'MEDIUM',
    reason: '外伤或关节症状建议骨科评估，必要时拍片检查。',
    safetyAdvice: '避免负重和剧烈活动，急性外伤请先冷敷制动。',
  },
  {
    keywords: ['皮疹', '瘙痒', '湿疹', '痤疮', '过敏'],
    departmentId: 6,
    departmentName: '皮肤科',
    priority: 'LOW',
    reason: '皮肤症状建议皮肤科面诊，避免自行用药。',
    safetyAdvice: '避免搔抓和接触可疑过敏原，保持皮肤清洁。',
  },
]

const defaultRule: MockRule = {
  keywords: [],
  departmentId: 7,
  departmentName: '全科',
  priority: 'LOW',
  reason: '根据您描述的症状，建议先到全科进行初步评估，再进一步分诊。',
  safetyAdvice: '如症状加重或持续不缓解，请及时就诊。',
  followUpQuestion: '请问症状是最近几天才出现的，还是已经持续较长时间了？',
}

function matchRule(chiefComplaint: string): MockRule {
  for (const rule of mockRules) {
    if (rule.keywords.some((kw) => chiefComplaint.includes(kw))) {
      return rule
    }
  }
  return defaultRule
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 多轮追问的请求载荷
 * - chiefComplaint: 本轮主诉（追问时可以是用户的回复文本）
 * - duration / additionalInfo: 仍然作为补充信息透传
 * - history: 历轮对话快照（只读），让 AI 看到完整上下文
 */
export interface TriageConsultWithHistoryRequest extends TriageConsultRequest {
  history?: TriageTurn[]
}

export interface TriageTurn {
  role: 'user' | 'ai'
  text: string
  /** ai 轮携带的追问/原因等 */
  meta?: {
    followUpQuestion?: string
    reason?: string
  }
}

// MOCK: 模拟 AI 分诊 + 追问；后端就绪后请用真实接口替换
export async function consultTriage(
  payload: TriageConsultWithHistoryRequest,
  patientId = 1,
): Promise<TriageResultResponse> {
  console.warn('[MOCK] /api/triage/consult 后端接口未就绪，使用本地虚构演示数据')
  await delay(1500)

  const rule = matchRule(payload.chiefComplaint)
  const result: TriageResultResponse = {
    id: 0, // 0 占位，由 addMockTriageRecord 重新分配
    patientId,
    recommendedDepartmentId: rule.departmentId,
    recommendedDepartmentName: rule.departmentName,
    priority: rule.priority,
    reason: rule.reason,
    safetyAdvice: rule.safetyAdvice,
    emergencyAdvice: rule.emergencyAdvice,
    followUpQuestion: rule.followUpQuestion,
    createdAt: new Date().toISOString(),
  }
  // 持久化到内存 store（分诊历史和诊疗时间线均会用到）
  const saved = addMockTriageRecord(result)
  return saved

  // 后端就绪后替换为：
  // const res = await apiClient.post('/triage/consult', payload)
  // return parseApiResponse(res.data)
}

/**
 * 获取当前患者的分诊历史（按时间倒序）
 */
export async function getMyTriageRecords(
  patientId = 1,
): Promise<TriageResultResponse[]> {
  console.warn('[MOCK] /api/triage/records 后端接口未就绪，使用本地虚构演示数据')
  await delay(300)
  return getMockTriageRecords(patientId)
}

/**
 * 获取单条分诊记录
 */
export async function getTriageRecordById(
  id: number,
): Promise<TriageResultResponse> {
  console.warn('[MOCK] /api/triage/records/{id} 后端接口未就绪，使用本地虚构演示数据')
  await delay(200)
  const item = getMockTriageById(id)
  if (!item) {
    throw new Error('分诊记录不存在')
  }
  return { ...item }
}
