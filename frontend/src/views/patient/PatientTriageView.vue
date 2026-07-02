<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { consultTriage, type TriageTurn } from '@/api/triage'
import { getDepartments } from '@/api/department'
import { getDoctorById } from '@/api/doctor'
import { useDoctorStore, type DoctorSummary } from '@/stores/doctor'
import { useAppointmentStore } from '@/stores/appointment'
import type { DoctorProfile } from '@/types/doctor'
import type { TriageResultResponse, TriagePriority } from '@/types/triage'

const router = useRouter()
const doctorStore = useDoctorStore()
const appointmentStore = useAppointmentStore()

/**
 * 状态机：
 * - 初始：表单态（输入主诉）
 * - 提交中：loading
 * - 追问中：结果态（带可回复的追问输入框）
 * - 已完成：结果态（无追问）
 * - 失败降级：手动选科室
 *
 * 增强（F1）：
 * - 成功后异步加载推荐医生卡片（2-3 张）
 * - 医生列表加载失败时降级为"医生列表暂不可用"
 */

const form = reactive({
  chiefComplaint: '',
  duration: '',
  additionalInfo: '',
})

const submitting = ref(false)
const triageResult = ref<TriageResultResponse | null>(null)
const aiFailed = ref(false)

/** 多轮对话快照（每次结果更新时追加 ai 轮 + 用户回复） */
const history = ref<TriageTurn[]>([])
/** 追问时用户正在输入的回复 */
const followUpText = ref('')
/** 已经历的总轮数（用于限制最大轮数） */
const round = ref(0)
const conversationId = ref<string | undefined>()
const MAX_ROUNDS = 3

/** F1: 推荐医生状态 */
const recommendedDoctors = ref<DoctorSummary[]>([])
const doctorsLoading = ref(false)
const doctorsFailed = ref(false)
/** 拉取职称/简介失败不影响主流程 */
const doctorProfiles = ref<Map<number, DoctorProfile>>(new Map())

const canSubmit = computed(
  () => form.chiefComplaint.trim().length > 0 && !submitting.value,
)
const canAnswerFollowUp = computed(
  () => followUpText.value.trim().length > 0 && !submitting.value,
)

const durationOptions = [
  { value: '', label: '请选择持续时间' },
  { value: '几小时', label: '几小时' },
  { value: '1-3天', label: '1-3 天' },
  { value: '3-7天', label: '3-7 天' },
  { value: '一周以上', label: '一周以上' },
]

const priorityConfig: Record<
  TriagePriority,
  { label: string; bg: string; border: string; color: string; icon: string }
> = {
  EMERGENCY: { label: '紧急', bg: '#fff1f0', border: '#ffa39e', color: '#cf1322', icon: '🚨' },
  HIGH: { label: '较高', bg: '#fff7e6', border: '#ffd591', color: '#d4380d', icon: '⚠️' },
  MEDIUM: { label: '一般', bg: '#fffbe6', border: '#ffe58f', color: '#d48806', icon: '💡' },
  LOW: { label: '较低', bg: '#f6ffed', border: '#b7eb8f', color: '#389e0d', icon: '✓' },
}

const showEmergency = computed(
  () =>
    triageResult.value &&
    (triageResult.value.priority === 'EMERGENCY' ||
      triageResult.value.priority === 'HIGH') &&
    !!triageResult.value.emergencyAdvice,
)

const canAskMore = computed(
  () => round.value < MAX_ROUNDS,
)

// 是否有 AI 显式追问：决定追问区显示"AI 追问"还是"自由补充"
const hasFollowUpQuestion = computed(
  () => !!triageResult.value?.followUpQuestion,
)

function createConversationId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}`
}

async function callTriage(chiefComplaint: string, nextRound: number) {
  const result = await consultTriage({
    chiefComplaint,
    duration: form.duration || undefined,
    additionalInfo: form.additionalInfo.trim() || undefined,
    conversationId: conversationId.value,
    history: history.value,
    round: nextRound,
  })
  conversationId.value = result.conversationId ?? conversationId.value
  round.value = result.round || nextRound
  return result
}

/** F1: 加载推荐医生（不阻塞分诊结果展示） */
async function loadRecommendedDoctors(departmentId: number) {
  doctorsLoading.value = true
  doctorsFailed.value = false
  recommendedDoctors.value = []
  try {
    const list = await doctorStore.loadDoctorsByDepartment(departmentId, { daysAhead: 7 })
    recommendedDoctors.value = list.slice(0, 3)
    // 后台拉取前 3 个医生的详情（title/introduction），失败不阻塞
    void enrichDoctorProfiles(recommendedDoctors.value)
  } catch (e) {
    console.error('[Triage] 加载推荐医生失败：', e)
    doctorsFailed.value = true
    ElMessage.warning('医生列表暂不可用，可进入科室挂号')
  } finally {
    doctorsLoading.value = false
  }
}

async function enrichDoctorProfiles(list: DoctorSummary[]) {
  // 并行拉取，单个失败不影响其他
  await Promise.all(
    list.map(async (d) => {
      try {
        const profile = await getDoctorById(d.doctorId)
        doctorProfiles.value.set(d.doctorId, profile)
      } catch (e) {
        console.warn(`[Triage] 医生 ${d.doctorId} 详情加载失败：`, e)
      }
    }),
  )
}

async function handleSubmit() {
  if (!form.chiefComplaint.trim()) {
    ElMessage.warning('请描述您的主要症状')
    return
  }
  if (submitting.value) return

  submitting.value = true
  aiFailed.value = false
  triageResult.value = null
  history.value = []
  conversationId.value = createConversationId()
  round.value = 1

  // 用户首轮输入
  history.value.push({ role: 'user', text: form.chiefComplaint.trim() })

  try {
    triageResult.value = await callTriage(form.chiefComplaint.trim(), 1)
    history.value.push({
      role: 'ai',
      text: triageResult.value.reason,
      meta: {
        followUpQuestion: triageResult.value.followUpQuestion,
        reason: triageResult.value.reason,
      },
    })
    // F1: 异步加载推荐医生，不 await 以避免阻塞后续追问
    void loadRecommendedDoctors(triageResult.value.recommendedDepartmentId)
  } catch (e) {
    console.error('AI 分诊失败：', e)
    aiFailed.value = true
    ElMessage.error('AI 分诊暂时不可用，请手动选择科室')
  } finally {
    submitting.value = false
  }
}

async function handleFollowUp() {
  if (!canAnswerFollowUp.value) return
  if (round.value >= MAX_ROUNDS) {
    ElMessage.warning(`最多追问 ${MAX_ROUNDS} 轮`)
    return
  }

  const answer = followUpText.value.trim()
  const nextRound = round.value + 1
  submitting.value = true
  history.value.push({ role: 'user', text: answer })
  followUpText.value = ''

  try {
    const next = await callTriage(answer, nextRound)
    // 必须在赋值前先保存旧科室，否则比较的两个值都是 next 的字段
    const oldDeptId = triageResult.value?.recommendedDepartmentId
    triageResult.value = next
    history.value.push({
      role: 'ai',
      text: next.reason,
      meta: { followUpQuestion: next.followUpQuestion, reason: next.reason },
    })
    // 追问后如科室变化，刷新推荐医生
    if (next.recommendedDepartmentId !== oldDeptId) {
      void loadRecommendedDoctors(next.recommendedDepartmentId)
    }
  } catch (e) {
    console.error('追问失败：', e)
    ElMessage.error('AI 追问失败，请重试')
  } finally {
    submitting.value = false
  }
}

function goToAppointment() {
  if (triageResult.value) {
    appointmentStore.setPreSelection({
      departmentId: triageResult.value.recommendedDepartmentId,
      departmentName: triageResult.value.recommendedDepartmentName,
    })
    router.push({
      path: '/patient/appointments',
      query: {
        departmentId: triageResult.value.recommendedDepartmentId,
        departmentName: triageResult.value.recommendedDepartmentName,
      },
    })
  }
}

/** F1: 直接预约指定医生 */
function bookDoctor(doctor: DoctorSummary) {
  if (!triageResult.value) return
  appointmentStore.setPreSelection({
    departmentId: doctor.departmentId,
    departmentName: doctor.departmentName,
    doctorId: doctor.doctorId,
    doctorName: doctor.doctorName,
    scheduleId: doctor.nextScheduleId ?? undefined,
  })
  const query: Record<string, string | number> = {
    departmentId: doctor.departmentId,
    departmentName: doctor.departmentName,
    doctorId: doctor.doctorId,
    doctorName: doctor.doctorName,
  }
  if (doctor.nextScheduleId) {
    query.scheduleId = doctor.nextScheduleId
  }
  router.push({ path: '/patient/appointments', query })
}

const manualDepartments = ref<Array<{ id: number; name: string }>>([])
const selectedManualDeptId = ref<number | null>(null)

async function loadManualDepartments() {
  try {
    manualDepartments.value = (await getDepartments())
      .filter((d) => d.status === 'ENABLED')
      .map((d) => ({ id: d.id, name: d.name }))
  } catch (e) {
    console.error('加载科室失败：', e)
    ElMessage.error('科室加载失败，请稍后重试')
  }
}

function goToManualAppointment() {
  if (selectedManualDeptId.value === null) {
    ElMessage.warning('请先选择科室')
    return
  }
  const dept = manualDepartments.value.find((d) => d.id === selectedManualDeptId.value)
  router.push({
    path: '/patient/appointments',
    query: { departmentId: dept?.id, departmentName: dept?.name },
  })
}

function resetForm() {
  form.chiefComplaint = ''
  form.duration = ''
  form.additionalInfo = ''
  triageResult.value = null
  aiFailed.value = false
  history.value = []
  conversationId.value = undefined
  followUpText.value = ''
  round.value = 0
  recommendedDoctors.value = []
  doctorsLoading.value = false
  doctorsFailed.value = false
  doctorProfiles.value.clear()
}

function formatAvailableDates(dates: string[]): string {
  if (!dates.length) return '暂无可预约日期'
  const today = new Date()
  const todayStr = today.toISOString().slice(0, 10)
  const tomorrow = new Date(today)
  tomorrow.setDate(tomorrow.getDate() + 1)
  const tomorrowStr = tomorrow.toISOString().slice(0, 10)
  const labels = dates.slice(0, 3).map((d) => {
    if (d === todayStr) return '今天'
    if (d === tomorrowStr) return '明天'
    return d.slice(5).replace('-', '/')
  })
  if (dates.length > 3) return `${labels.join('、')} 等 ${dates.length} 天`
  return labels.join('、')
}

onMounted(loadManualDepartments)
</script>

<template>
  <div class="triage-page">
    <div class="assist-tip">
      <span class="tip-icon">💡</span>
      AI 分诊结果仅供辅助参考，不能替代医生诊断
    </div>

    <!-- 问诊表单 -->
    <div v-if="!submitting && !triageResult && !aiFailed" class="form-card">
      <h2 class="card-title">AI 智能分诊</h2>
      <p class="card-desc">描述您的症状，AI 将推荐合适科室</p>

      <div class="form-item">
        <label class="form-label">症状描述 <span class="required">*</span></label>
        <textarea
          v-model="form.chiefComplaint"
          class="form-textarea"
          placeholder="请详细描述您的主要症状，例如：发烧三天，咳嗽有黄痰"
          rows="4"
          maxlength="500"
        />
      </div>

      <div class="form-item">
        <label class="form-label">持续时间</label>
        <select v-model="form.duration" class="form-select">
          <option v-for="opt in durationOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>

      <div class="form-item">
        <label class="form-label">补充信息</label>
        <textarea
          v-model="form.additionalInfo"
          class="form-textarea"
          placeholder="既往用药、过敏史等补充信息（选填）"
          rows="3"
          maxlength="300"
        />
      </div>

      <button class="submit-btn" :disabled="!canSubmit" @click="handleSubmit">
        开始 AI 分诊
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="submitting" class="loading-card">
      <div class="loading-spinner"></div>
      <div class="loading-text">AI 正在分析您的症状...</div>
      <div class="loading-tip">通常需要数秒，请稍候</div>
    </div>

    <!-- 分诊结果 + 追问 -->
    <div v-if="triageResult && !submitting" class="result-card">
      <div
        class="priority-card"
        :style="{
          background: priorityConfig[triageResult.priority].bg,
          borderColor: priorityConfig[triageResult.priority].border,
        }"
      >
        <div class="priority-header">
          <span class="priority-icon">{{ priorityConfig[triageResult.priority].icon }}</span>
          <span
            class="priority-label"
            :style="{ color: priorityConfig[triageResult.priority].color }"
          >
            紧急程度：{{ priorityConfig[triageResult.priority].label }}
          </span>
          <span class="round-tag">问诊轮次 {{ round }}</span>
        </div>
        <div class="dept-name">{{ triageResult.recommendedDepartmentName }}</div>
        <div class="dept-tip">推荐科室</div>
      </div>

      <!-- F1: 推荐医生卡片 -->
      <div class="doctors-card">
        <div class="doctors-header">
          <span class="doctors-title">👨‍⚕️ 推荐医生</span>
          <span v-if="doctorsLoading" class="doctors-loading">加载中…</span>
          <span v-else-if="!doctorsLoading && !doctorsFailed && recommendedDoctors.length > 0" class="doctors-count">
            共 {{ recommendedDoctors.length }} 位
          </span>
        </div>

        <div v-if="doctorsLoading" class="doctors-skeleton">
          <div v-for="i in 2" :key="i" class="skeleton-card"></div>
        </div>

        <div v-else-if="doctorsFailed" class="doctors-fallback">
          <div class="fallback-text">医生列表暂不可用，可进入科室挂号</div>
        </div>

        <div v-else-if="recommendedDoctors.length === 0" class="doctors-empty">
          <div class="empty-text">该科室暂无可预约医生</div>
        </div>

        <div v-else class="doctors-list">
          <div
            v-for="doc in recommendedDoctors"
            :key="doc.doctorId"
            class="doctor-card"
          >
            <div class="doctor-head">
              <div class="doctor-avatar">{{ doc.doctorName.charAt(0) }}</div>
              <div class="doctor-meta">
                <div class="doctor-name">{{ doc.doctorName }} 医生</div>
                <div class="doctor-title">
                  {{ doctorProfiles.get(doc.doctorId)?.title || doc.title }} · {{ doc.departmentName }}
                </div>
              </div>
            </div>
            <div v-if="doctorProfiles.get(doc.doctorId)?.introduction" class="doctor-intro">
              {{ doctorProfiles.get(doc.doctorId)?.introduction }}
            </div>
            <div class="doctor-schedule">
              <div class="schedule-row">
                <span class="schedule-icon">📅</span>
                <span class="schedule-text">{{ formatAvailableDates(doc.availableDates) }}</span>
              </div>
              <div class="schedule-row">
                <span class="schedule-icon">🎫</span>
                <span class="schedule-text">剩余 {{ doc.remainingTotal }} 个号源</span>
              </div>
            </div>
            <button class="book-btn" @click="bookDoctor(doc)">
              预约该医生
            </button>
          </div>
        </div>
      </div>

      <!-- 多轮对话快照：AI 气泡内已含 reason，避免与下方"推荐理由"重复 -->
      <div v-if="history.length > 1" class="history-card">
        <div class="history-title">💬 问诊对话记录</div>
        <div
          v-for="(turn, idx) in history"
          :key="idx"
          class="turn"
          :class="turn.role"
        >
          <div class="turn-role">
            {{ turn.role === 'user' ? '我' : 'AI' }}
          </div>
          <div class="turn-bubble">{{ turn.text }}</div>
        </div>
      </div>

      <div v-if="triageResult.safetyAdvice" class="result-section safety">
        <div class="section-label">⚠️ 安全提示</div>
        <div class="section-content">{{ triageResult.safetyAdvice }}</div>
      </div>

      <div v-if="showEmergency" class="result-section emergency">
        <div class="section-label">🚨 急诊建议</div>
        <div class="section-content">{{ triageResult.emergencyAdvice }}</div>
      </div>

      <!-- 追问 / 继续输入区 -->
      <div v-if="canAskMore" class="followup-card">
        <div class="followup-label">
          <span v-if="hasFollowUpQuestion">AI 追问 · 第 {{ round }}/{{ MAX_ROUNDS }} 轮</span>
          <span v-else>继续描述 · 第 {{ round }}/{{ MAX_ROUNDS }} 轮</span>
        </div>
        <div v-if="hasFollowUpQuestion" class="followup-question">{{ triageResult.followUpQuestion }}</div>
        <div v-else class="followup-question followup-hint">
          如需进一步描述症状、补充病史或调整主诉，请在下方继续输入
        </div>
        <textarea
          v-model="followUpText"
          class="form-textarea"
          :placeholder="hasFollowUpQuestion ? '请回复 AI 的追问...' : '继续输入症状或补充信息...'"
          rows="2"
          maxlength="300"
        />
        <button
          class="submit-btn compact"
          :disabled="!canAnswerFollowUp"
          @click="handleFollowUp"
        >
          {{ hasFollowUpQuestion ? '继续追问' : '继续描述' }}
        </button>
      </div>

      <div v-else class="result-section done">
        <div class="section-label">⏹ 已达到最大问诊轮数 ({{ MAX_ROUNDS }})</div>
        <div class="section-content">如需进一步咨询，请重新发起问诊或前往科室挂号。</div>
      </div>

      <div class="result-actions">
        <button class="action-btn primary" @click="goToAppointment">
          去该科室挂号
        </button>
        <button class="action-btn ghost" @click="resetForm">重新问诊</button>
      </div>
    </div>

    <!-- AI 失败降级 -->
    <div v-if="aiFailed && !submitting" class="fallback-card">
      <div class="fallback-icon">🔧</div>
      <div class="fallback-title">AI 分诊暂时不可用</div>
      <div class="fallback-desc">您可以手动选择科室进行挂号</div>

      <div class="form-item">
        <label class="form-label">选择科室</label>
        <div class="dept-list">
          <div
            v-for="dept in manualDepartments"
            :key="dept.id"
            class="dept-item"
            :class="{ active: selectedManualDeptId === dept.id }"
            @click="selectedManualDeptId = dept.id"
          >
            {{ dept.name }}
          </div>
        </div>
      </div>

      <button class="submit-btn" @click="goToManualAppointment">去挂号</button>
    </div>

    <div class="footer-tip">
      本平台由 AI 辅助，所有诊断结果仅供参考<br />
      具体诊疗请以医生意见为准
    </div>
  </div>
</template>

<style scoped>
.triage-page {
  padding: 16px 16px 24px;
}

.assist-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #e3f0ff;
  border-radius: 10px;
  font-size: 12px;
  color: #1a73e8;
  margin-bottom: 16px;
}

.tip-icon {
  font-size: 14px;
}

.form-card,
.result-card,
.fallback-card,
.loading-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 20px 18px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.card-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.card-desc {
  margin: 0 0 18px;
  font-size: 13px;
  color: #8e8e93;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
}

.required {
  color: #f56c6c;
}

.form-textarea {
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #1a1a1a;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  resize: vertical;
  font-family: inherit;
  background: #f8f9fa;
  line-height: 1.5;
}

.form-textarea:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background: #ffffff;
}

.form-textarea::placeholder {
  color: #c0c4cc;
}

.form-select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #1a1a1a;
  outline: none;
  background: #f8f9fa;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238e8e93' d='M6 8L2 4h8z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 32px;
}

.form-select:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background: #ffffff;
}

.submit-btn {
  width: 100%;
  padding: 12px 0;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
}

.submit-btn.compact {
  margin-top: 10px;
  padding: 10px 0;
  font-size: 14px;
}

.submit-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.submit-btn:hover:not(:disabled) {
  box-shadow: 0 4px 12px rgb(79 172 254 / 30%);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-card {
  text-align: center;
  padding: 40px 20px;
}

.loading-spinner {
  width: 44px;
  height: 44px;
  border: 4px solid #e3f0ff;
  border-top-color: #4facfe;
  border-radius: 50%;
  margin: 0 auto 16px;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 15px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 6px;
}

.loading-tip {
  font-size: 12px;
  color: #8e8e93;
}

.priority-card {
  border: 1px solid;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
}

.priority-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.priority-icon {
  font-size: 18px;
}

.priority-label {
  font-size: 13px;
  font-weight: 600;
  flex: 1;
}

.round-tag {
  font-size: 11px;
  color: #8e8e93;
  background: #ffffff;
  padding: 2px 8px;
  border-radius: 6px;
}

.dept-name {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.dept-tip {
  font-size: 12px;
  color: #8e8e93;
}

/* F1: 推荐医生卡片 */
.doctors-card {
  background: linear-gradient(135deg, #f0f9ff 0%, #faf5ff 100%);
  border: 1px solid #d6e4ff;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
}

.doctors-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.doctors-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.doctors-loading,
.doctors-count {
  font-size: 12px;
  color: #8e8e93;
}

.doctors-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton-card {
  height: 110px;
  border-radius: 10px;
  background: linear-gradient(90deg, #f0f0f0 0%, #f8f8f8 50%, #f0f0f0 100%);
  background-size: 200% 100%;
  animation: skeleton-shine 1.4s ease-in-out infinite;
}

@keyframes skeleton-shine {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.doctors-fallback {
  padding: 14px;
  background: #ffffff;
  border-radius: 10px;
  border: 1px dashed #d9d9d9;
  text-align: center;
}

.fallback-text {
  font-size: 13px;
  color: #8e8e93;
}

.doctors-empty {
  padding: 14px;
  background: #ffffff;
  border-radius: 10px;
  text-align: center;
}

.empty-text {
  font-size: 13px;
  color: #8e8e93;
}

.doctors-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.doctor-card {
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  padding: 12px 14px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.doctor-card:hover {
  border-color: #4facfe;
  box-shadow: 0 2px 10px rgb(79 172 254 / 18%);
}

.doctor-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.doctor-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  flex-shrink: 0;
}

.doctor-meta {
  flex: 1;
  min-width: 0;
}

.doctor-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 2px;
}

.doctor-title {
  font-size: 12px;
  color: #8e8e93;
}

.doctor-intro {
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
  background: #f8f9fa;
  padding: 6px 8px;
  border-radius: 6px;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doctor-schedule {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.schedule-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
}

.schedule-icon {
  font-size: 13px;
}

.schedule-text {
  font-size: 12px;
}

.book-btn {
  width: 100%;
  padding: 8px 0;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.book-btn:active {
  opacity: 0.85;
}

/* 对话快照 */
.history-card {
  background: #f8f9fa;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 14px;
  max-height: 220px;
  overflow-y: auto;
}

.history-title {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}

.turn {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: flex-start;
}

.turn:last-child {
  margin-bottom: 0;
}

.turn-role {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
}

.turn.user .turn-role {
  background: #4facfe;
  color: #fff;
}

.turn.ai .turn-role {
  background: #fff7e6;
  color: #d48806;
}

.turn-bubble {
  font-size: 13px;
  color: #1a1a1a;
  line-height: 1.5;
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 6px 10px;
  word-break: break-word;
}

.turn.user .turn-bubble {
  background: #e3f0ff;
}

.result-section {
  margin-bottom: 14px;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: #8e8e93;
  margin-bottom: 6px;
}

.section-content {
  font-size: 14px;
  color: #1a1a1a;
  line-height: 1.6;
}

.result-section.emergency {
  padding: 12px;
  background: #fff1f0;
  border-radius: 10px;
  border: 1px solid #ffa39e;
}

.result-section.emergency .section-label {
  color: #cf1322;
}

.result-section.safety {
  padding: 12px;
  background: #fffbe6;
  border-radius: 10px;
}

.result-section.done {
  padding: 12px;
  background: #f0f9ff;
  border-radius: 10px;
  border: 1px solid #bae0ff;
}

/* 追问输入区 */
.followup-card {
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
  border: 1px dashed #91d5ff;
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 14px;
}

.followup-label {
  font-size: 12px;
  font-weight: 600;
  color: #1890ff;
  margin-bottom: 6px;
}

.followup-question {
  font-size: 14px;
  color: #1a1a1a;
  line-height: 1.6;
  margin-bottom: 10px;
}

.followup-hint {
  color: #475569;
  font-size: 13px;
  background: #ffffff;
  padding: 8px 10px;
  border-radius: 8px;
  border-left: 3px solid #91d5ff;
}

.result-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;
}

.action-btn {
  width: 100%;
  padding: 11px 0;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, opacity 0.2s;
  border: none;
}

.action-btn:active {
  transform: scale(0.98);
}

.action-btn.primary {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
}

.action-btn.ghost {
  background: #f1f5f9;
  color: #475569;
}

.fallback-card {
  text-align: center;
}

.fallback-icon {
  font-size: 36px;
  margin-bottom: 10px;
}

.fallback-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.fallback-desc {
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 18px;
}

.dept-list {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
}

.dept-item {
  padding: 10px 6px;
  text-align: center;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 13px;
  color: #475569;
  background: #f8f9fa;
  cursor: pointer;
  transition: all 0.2s;
}

.dept-item.active {
  border-color: #4facfe;
  background: #e3f0ff;
  color: #1a73e8;
  font-weight: 600;
}

.footer-tip {
  text-align: center;
  font-size: 11px;
  color: #8e8e93;
  line-height: 1.6;
  margin-top: 24px;
  padding: 0 20px;
}
</style>
