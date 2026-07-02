<script setup lang="ts">
// 接诊工作台（父级外壳）
// 设计来源：roles/12_前端开发AI任务书.md §3.3、product/11_功能需求.md §8、product/12_业务流程与状态机.md §6
// 职责：
// - 加载就诊（:id），写入 encounterStore 供子页面共享
// - 展示患者基本信息与就诊状态
// - 提供状态流转控制（等待检查 / 继续诊疗 / 完成就诊）
// - 提供子页面导航（概览/诊断/检查检验/病历/处方）
// - 子页面通过 <RouterView/> 渲染
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEncounterById, waitForExam, resumeEncounter, completeEncounter } from '@/api/encounter'
import { getPatientDetail } from '@/api/patient'
import { useEncounterStore } from '@/stores/encounter'
import type { EncounterResponse, EncounterStatus } from '@/types/encounter'
import type { PatientDetailResponse } from '@/types/patient'

const route = useRoute()
const router = useRouter()
const encounterStore = useEncounterStore()

const loading = ref(true)
const loadError = ref('')
const acting = ref(false)

const encounter = ref<EncounterResponse | null>(null)
const patientDetail = ref<PatientDetailResponse | null>(null)

const encounterId = computed(() => Number(route.params.id))

const patientSummary = computed(() => patientDetail.value)

const statusText = computed(() => {
  if (!encounter.value) return ''
  return statusLabel(encounter.value.status)
})

function statusLabel(status: EncounterStatus): string {
  switch (status) {
    case 'CREATED':
      return '已创建'
    case 'IN_PROGRESS':
      return '接诊中'
    case 'WAITING_EXAM':
      return '等待检查'
    case 'COMPLETED':
      return '已完成'
    case 'CANCELLED':
      return '已取消'
    default:
      return status
  }
}

function statusClass(status: EncounterStatus): string {
  switch (status) {
    case 'IN_PROGRESS':
      return 'tag-progress'
    case 'WAITING_EXAM':
      return 'tag-waiting'
    case 'COMPLETED':
      return 'tag-completed'
    case 'CANCELLED':
      return 'tag-cancelled'
    default:
      return 'tag-created'
  }
}

// 是否可流转到「等待检查」（仅 IN_PROGRESS）
const canWaitExam = computed(() => encounter.value?.status === 'IN_PROGRESS')
// 是否可「继续诊疗」（仅 WAITING_EXAM）
const canResume = computed(() => encounter.value?.status === 'WAITING_EXAM')
// 是否可「完成就诊」（仅 IN_PROGRESS，完成前置条件由后端/MOCK 校验）
const canComplete = computed(() => encounter.value?.status === 'IN_PROGRESS')
// 就诊是否已结束（不可再操作）
const isFinished = computed(
  () => encounter.value?.status === 'COMPLETED' || encounter.value?.status === 'CANCELLED',
)

const navTabs = [
  { name: '概览', path: '', childName: 'doctor-encounter-overview' },
  { name: 'AI 诊断', path: 'diagnosis', childName: 'doctor-encounter-diagnosis' },
  { name: '检查检验', path: 'examinations', childName: 'doctor-encounter-examinations' },
  { name: '病历', path: 'medical-record', childName: 'doctor-encounter-medical-record' },
  { name: '处方', path: 'prescription', childName: 'doctor-encounter-prescription' },
]

const activeTabName = computed(() => {
  const matched = route.name?.toString() ?? ''
  const tab = navTabs.find((t) => matched.includes(t.childName))
  return tab?.childName ?? 'doctor-encounter-overview'
})

function tabPath(path: string): string {
  return `/doctor/encounter/${encounterId.value}${path ? '/' + path : ''}`
}

function genderText(gender?: string): string {
  if (gender === 'MALE') return '男'
  if (gender === 'FEMALE') return '女'
  return '--'
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '--'
  }
}

async function loadEncounter() {
  loading.value = true
  loadError.value = ''
  try {
    const enc = await getEncounterById(encounterId.value)
    encounter.value = enc
    encounterStore.setActiveEncounter(enc)
    await loadPatientDetail(enc.patientId)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载就诊失败'
    console.error('[DoctorEncounter] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

async function loadPatientDetail(patientId: number) {
  try {
    patientDetail.value = await getPatientDetail(patientId)
  } catch (e) {
    patientDetail.value = null
    console.warn('[DoctorEncounter] patient detail unavailable', patientId, e)
  }
}

/** 刷新就诊状态（子页面操作后可调用） */
async function refreshEncounter() {
  try {
    const enc = await getEncounterById(encounterId.value)
    encounter.value = enc
    encounterStore.setActiveEncounter(enc)
  } catch (e) {
    console.error('[DoctorEncounter] 刷新失败：', e)
  }
}

/** 等待检查（IN_PROGRESS → WAITING_EXAM） */
async function handleWaitExam() {
  try {
    await ElMessageBox.confirm(
      '确认转入「等待检查」状态吗？患者将前往检查检验，待结果回报后可继续诊疗。',
      '等待检查确认',
      { confirmButtonText: '确认转入', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const enc = await waitForExam(encounterId.value)
    encounter.value = enc
    encounterStore.setActiveEncounter(enc)
    ElMessage.success('已转入等待检查状态')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

/** 继续诊疗（WAITING_EXAM → IN_PROGRESS） */
async function handleResume() {
  acting.value = true
  try {
    const enc = await resumeEncounter(encounterId.value)
    encounter.value = enc
    encounterStore.setActiveEncounter(enc)
    ElMessage.success('已继续诊疗')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

/** 完成就诊（IN_PROGRESS → COMPLETED，前置条件校验在服务端） */
async function handleComplete() {
  try {
    await ElMessageBox.confirm(
      '确认完成本次就诊吗？完成前需满足：已确认正式病历、已下达医生最终诊断、检查检验已审核、处方已确认或作废。',
      '完成就诊确认',
      { confirmButtonText: '确认完成', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    const enc = await completeEncounter(encounterId.value)
    encounter.value = enc
    encounterStore.setActiveEncounter(enc)
    ElMessage.success('就诊已完成')
  } catch (e) {
    const msg = e instanceof Error ? e.message : '完成就诊失败'
    ElMessage.error(msg)
    // 完成失败时刷新一次，便于查看前置条件是否变化
    await refreshEncounter()
  } finally {
    acting.value = false
  }
}

function backToQueue() {
  encounterStore.reset()
  router.push('/doctor/queue')
}

/** 查看患者档案（含诊疗时间线，§3.3/§3.4） */
function goPatientDetail() {
  if (!encounter.value) return
  router.push({
    name: 'doctor-patient-detail',
    params: { patientId: encounter.value.patientId },
    query: { fromEncounterId: String(encounter.value.id) },
  })
}

// 路由参数变化时重新加载
watch(
  () => route.params.id,
  (newId) => {
    if (newId && Number(newId) !== encounter.value?.id) {
      loadEncounter()
    }
  },
)

onMounted(loadEncounter)
</script>

<template>
  <div class="encounter-view">
    <!-- 顶部导航条 -->
    <div class="topbar">
      <button class="ghost-btn" @click="backToQueue">← 返回队列</button>
      <span class="topbar-title">接诊工作台</span>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载就诊信息…</span>
    </div>

    <div v-else-if="loadError || !encounter" class="fallback-card error-card">
      <div class="fallback-title">就诊加载失败</div>
      <div class="fallback-desc">{{ loadError || '未找到就诊记录' }}</div>
      <button class="primary-btn" @click="backToQueue">返回队列</button>
    </div>

    <template v-else>
      <!-- 患者信息卡片 -->
      <div class="patient-bar">
        <div class="patient-main">
          <div class="patient-name-row">
            <span class="patient-name">{{ encounter.patientName }}</span>
            <span class="status-tag" :class="statusClass(encounter.status)">
              {{ statusText }}
            </span>
            <button class="link-btn" @click="goPatientDetail">查看档案 →</button>
          </div>
          <div class="patient-meta">
            <span v-if="patientSummary">
              {{ genderText(patientSummary.gender) }} · {{ patientSummary.age }} 岁
            </span>
            <span v-if="patientSummary">电话 {{ patientSummary.phone }}</span>
            <span>{{ encounter.departmentName }}</span>
            <span>就诊号 #{{ encounter.id }}</span>
            <span>开始 {{ formatDateTime(encounter.startedAt) }}</span>
          </div>
          <!-- 过敏史警示 -->
          <div
            v-if="patientSummary && patientSummary.allergies && patientSummary.allergies !== '无'"
            class="allergy-banner"
          >
            过敏史：{{ patientSummary.allergies }}
          </div>
          <div v-if="patientSummary" class="history-line">
            既往史：{{ patientSummary.medicalHistory || '无' }}
          </div>
        </div>
      </div>

      <!-- 状态操作条 -->
      <div v-if="!isFinished" class="action-bar">
        <button
          v-if="canWaitExam"
          class="action-btn btn-wait"
          :disabled="acting"
          @click="handleWaitExam"
        >
          转入等待检查
        </button>
        <button
          v-if="canResume"
          class="action-btn btn-resume"
          :disabled="acting"
          @click="handleResume"
        >
          继续诊疗
        </button>
        <button
          v-if="canComplete"
          class="action-btn btn-complete"
          :disabled="acting"
          @click="handleComplete"
        >
          完成就诊
        </button>
        <span v-if="acting" class="acting-spinner" />
      </div>

      <!-- 子页面导航 -->
      <div class="nav-tabs">
        <RouterLink
          v-for="tab in navTabs"
          :key="tab.childName"
          :to="tabPath(tab.path)"
          class="nav-tab"
          :class="{ active: activeTabName === tab.childName }"
        >
          {{ tab.name }}
        </RouterLink>
      </div>

      <!-- 子页面内容 -->
      <div class="child-content">
        <RouterView />
      </div>
    </template>
  </div>
</template>

<style scoped>
.encounter-view {
  padding: 12px 16px 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.ghost-btn {
  padding: 6px 12px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #4a5568;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.ghost-btn:hover {
  border-color: #4facfe;
  color: #4facfe;
}

.topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 20px;
  background: #ffffff;
  border-radius: 14px;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #e0e0e0;
  border-top-color: #4facfe;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 14px;
  color: #8e8e93;
}

.fallback-card {
  padding: 32px 20px;
  background: #ffffff;
  border-radius: 14px;
  text-align: center;
}

.error-card .fallback-title {
  color: #f56c6c;
}

.fallback-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.fallback-desc {
  font-size: 14px;
  color: #8e8e93;
  margin-bottom: 16px;
  line-height: 1.5;
}

.primary-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.primary-btn:hover {
  opacity: 0.92;
}

.patient-bar {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 18px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  margin-bottom: 12px;
}

.patient-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.patient-name {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
}

.link-btn {
  margin-left: 4px;
  background: none;
  border: none;
  color: #4facfe;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.15s;
}

.link-btn:hover {
  opacity: 0.8;
}

.status-tag {
  font-size: 13px;
  padding: 3px 12px;
  border-radius: 12px;
  font-weight: 500;
}

.tag-created {
  background: #f0f0f0;
  color: #8e8e93;
}

.tag-progress {
  background: #e6f7ff;
  color: #1890ff;
}

.tag-waiting {
  background: #fff7e6;
  color: #fa8c16;
}

.tag-completed {
  background: #f0fff4;
  color: #67c23a;
}

.tag-cancelled {
  background: #fff1f0;
  color: #f56c6c;
}

.patient-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 8px;
}

.allergy-banner {
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  color: #d46b08;
  font-weight: 500;
  margin-bottom: 6px;
}

.history-line {
  font-size: 13px;
  color: #8e8e93;
  line-height: 1.5;
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #ffffff;
  border-radius: 14px;
  padding: 12px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  margin-bottom: 12px;
}

.action-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
  color: #ffffff;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-wait {
  background: #fa8c16;
}

.btn-wait:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-resume {
  background: #1890ff;
}

.btn-resume:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-complete {
  background: linear-gradient(135deg, #67c23a 0%, #4facfe 100%);
}

.btn-complete:hover:not(:disabled) {
  opacity: 0.9;
}

.acting-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #e0e0e0;
  border-top-color: #4facfe;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.nav-tabs {
  display: flex;
  gap: 4px;
  background: #ffffff;
  border-radius: 12px 12px 0 0;
  padding: 0 8px;
  border-bottom: 1px solid #f0f0f0;
}

.nav-tab {
  padding: 12px 18px;
  font-size: 14px;
  color: #8e8e93;
  text-decoration: none;
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
  white-space: nowrap;
}

.nav-tab:hover {
  color: #4facfe;
}

.nav-tab.active {
  color: #4facfe;
  border-bottom-color: #4facfe;
  font-weight: 500;
}

.child-content {
  background: #ffffff;
  border-radius: 0 0 12px 12px;
  padding: 20px;
  min-height: 320px;
}
</style>
