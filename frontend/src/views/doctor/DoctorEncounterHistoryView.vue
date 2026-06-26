<script setup lang="ts">
// 我的接诊历史（医生端）
// 设计来源：product/11_功能需求.md §8 医生工作台、§11.3 查看病历列表和详情
// 功能：
// - 列出当前医生历史接诊记录（含进行中/等待检查/已完成/已取消）
// - 状态筛选与患者姓名搜索
// - 展示每条就诊的最终诊断摘要
// - 进行中/等待检查 → 继续接诊；已完成 → 查看记录
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDoctorEncounters, getEncounterDiagnoses } from '@/api/encounter'
import { useEncounterStore } from '@/stores/encounter'
import type { EncounterResponse, EncounterDiagnosisResponse } from '@/types/encounter'

const router = useRouter()
const encounterStore = useEncounterStore()

const loading = ref(true)
const loadError = ref('')
const encounters = ref<EncounterResponse[]>([])
// 每个就诊的最终诊断摘要
const diagnosisMap = ref<Record<number, EncounterDiagnosisResponse[]>>({})

// 筛选
const statusFilter = ref<'ALL' | EncounterResponse['status']>('ALL')
const searchKey = ref('')

const statusOptions: { value: 'ALL' | EncounterResponse['status']; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'IN_PROGRESS', label: '接诊中' },
  { value: 'WAITING_EXAM', label: '等待检查' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

const filteredList = computed(() => {
  let list = encounters.value
  if (statusFilter.value !== 'ALL') {
    list = list.filter((e) => e.status === statusFilter.value)
  }
  if (searchKey.value.trim()) {
    const key = searchKey.value.trim().toLowerCase()
    list = list.filter(
      (e) =>
        e.patientName.toLowerCase().includes(key) ||
        String(e.id).includes(key),
    )
  }
  return list
})

// 统计
const stats = computed(() => ({
  total: encounters.value.length,
  inProgress: encounters.value.filter((e) => e.status === 'IN_PROGRESS').length,
  waiting: encounters.value.filter((e) => e.status === 'WAITING_EXAM').length,
  completed: encounters.value.filter((e) => e.status === 'COMPLETED').length,
}))

async function loadHistory() {
  loading.value = true
  loadError.value = ''
  try {
    const list = await getDoctorEncounters()
    encounters.value = list
    // 并行加载每条就诊的最终诊断摘要
    const diagResults = await Promise.all(
      list.map(async (e) => {
        try {
          const diags = await getEncounterDiagnoses(e.id)
          return [e.id, diags] as const
        } catch {
          return [e.id, []] as const
        }
      }),
    )
    diagnosisMap.value = Object.fromEntries(diagResults)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载接诊历史失败'
    console.error('[EncounterHistory] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function finalDiagnoses(encounterId: number): EncounterDiagnosisResponse[] {
  return (diagnosisMap.value[encounterId] || []).filter(
    (d) => d.type === 'FINAL' && d.source === 'DOCTOR',
  )
}

function statusText(status: string): string {
  switch (status) {
    case 'IN_PROGRESS':
      return '接诊中'
    case 'WAITING_EXAM':
      return '等待检查'
    case 'COMPLETED':
      return '已完成'
    case 'CANCELLED':
      return '已取消'
    case 'CREATED':
      return '已创建'
    default:
      return status
  }
}

function statusClass(status: string): string {
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
      return 'tag-default'
  }
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '--'
  }
}

/** 继续接诊 / 查看记录 */
function openEncounter(enc: EncounterResponse) {
  encounterStore.setActiveEncounter(enc)
  router.push(`/doctor/encounter/${enc.id}`)
}

onMounted(loadHistory)
</script>

<template>
  <div class="history-view">
    <div class="page-header">
      <div class="header-title">我的接诊历史</div>
      <div class="header-sub">
        共 {{ stats.total }} 次 · 接诊中 {{ stats.inProgress }} · 等待检查 {{ stats.waiting }} · 已完成 {{ stats.completed }}
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="status-tabs">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          class="status-tab"
          :class="{ active: statusFilter === opt.value }"
          @click="statusFilter = opt.value"
        >
          {{ opt.label }}
        </button>
      </div>
      <input
        v-model="searchKey"
        class="search-input"
        placeholder="搜索患者姓名或就诊号"
      />
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载接诊历史…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadHistory">重新加载</button>
    </div>

    <div v-else-if="filteredList.length === 0" class="empty-card">
      <div class="empty-icon">--</div>
      <div class="empty-text">
        {{ encounters.length === 0 ? '暂无接诊记录' : '没有符合条件的就诊记录' }}
      </div>
    </div>

    <div v-else class="encounter-list">
      <div
        v-for="enc in filteredList"
        :key="enc.id"
        class="encounter-card"
        :class="statusClass(enc.status)"
      >
        <div class="card-head">
          <div class="patient-name">{{ enc.patientName }}</div>
          <span class="status-tag" :class="statusClass(enc.status)">
            {{ statusText(enc.status) }}
          </span>
        </div>
        <div class="card-meta">
          <span>就诊号 {{ enc.id }}</span>
          <span>{{ enc.departmentName }}</span>
          <span>接诊医生 {{ enc.doctorName }}</span>
        </div>
        <div class="card-time">
          <span>开始 {{ formatDateTime(enc.startedAt) }}</span>
          <span v-if="enc.completedAt">· 完成 {{ formatDateTime(enc.completedAt) }}</span>
          <span v-else-if="enc.cancelledAt">· 取消 {{ formatDateTime(enc.cancelledAt) }}</span>
        </div>

        <!-- 最终诊断摘要 -->
        <div v-if="finalDiagnoses(enc.id).length > 0" class="diag-summary">
          <span class="diag-label">最终诊断：</span>
          <span
            v-for="d in finalDiagnoses(enc.id)"
            :key="d.id"
            class="diag-tag"
          >
            {{ d.diagnosisName }}
          </span>
        </div>
        <div v-else-if="enc.status === 'COMPLETED'" class="diag-summary">
          <span class="diag-label diag-empty">未下达最终诊断</span>
        </div>

        <!-- 操作 -->
        <div class="card-actions">
          <button
            v-if="enc.status === 'IN_PROGRESS' || enc.status === 'WAITING_EXAM'"
            class="primary-btn"
            @click="openEncounter(enc)"
          >
            继续接诊
          </button>
          <button
            v-else-if="enc.status === 'COMPLETED'"
            class="ghost-btn"
            @click="openEncounter(enc)"
          >
            查看记录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.history-view {
  padding: 16px 16px 24px;
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 16px;
}

.header-title {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.header-sub {
  font-size: 14px;
  color: #8e8e93;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.status-tabs {
  display: flex;
  gap: 4px;
  background: #ffffff;
  padding: 4px;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.status-tab {
  padding: 6px 14px;
  background: none;
  border: none;
  border-radius: 7px;
  font-size: 14px;
  color: #8e8e93;
  cursor: pointer;
  transition: all 0.15s;
}

.status-tab.active {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  font-weight: 500;
}

.search-input {
  flex: 1;
  min-width: 200px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
}

.search-input:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
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
}

.primary-btn:hover {
  opacity: 0.92;
}

.ghost-btn {
  padding: 8px 20px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  color: #4a5568;
  font-size: 14px;
  cursor: pointer;
}

.ghost-btn:hover {
  border-color: #4facfe;
  color: #4facfe;
}

.empty-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 10px;
}

.empty-text {
  font-size: 14px;
  color: #8e8e93;
}

.encounter-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.encounter-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  border-left: 3px solid #e0e0e0;
}

.encounter-card.tag-progress {
  border-left-color: #fa8c16;
}

.encounter-card.tag-waiting {
  border-left-color: #f56c6c;
}

.encounter-card.tag-completed {
  border-left-color: #67c23a;
}

.encounter-card.tag-cancelled {
  border-left-color: #b0b0b0;
  opacity: 0.75;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.patient-name {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
}

.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.tag-progress {
  background: #fff7e6;
  color: #fa8c16;
}

.tag-waiting {
  background: #fff1f0;
  color: #f56c6c;
}

.tag-completed {
  background: #f0fff4;
  color: #67c23a;
}

.tag-cancelled {
  background: #f5f5f5;
  color: #8e8e93;
}

.tag-default {
  background: #f0f0f0;
  color: #8e8e93;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 6px;
}

.card-time {
  font-size: 13px;
  color: #b0b0b0;
  margin-bottom: 10px;
}

.diag-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 8px 12px;
  background: #fafbfc;
  border-radius: 8px;
  margin-bottom: 12px;
}

.diag-label {
  font-size: 13px;
  color: #8e8e93;
}

.diag-empty {
  color: #b0b0b0;
}

.diag-tag {
  font-size: 13px;
  color: #1abc9c;
  background: #e8fff8;
  padding: 2px 10px;
  border-radius: 10px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
