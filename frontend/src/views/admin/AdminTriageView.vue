<script setup lang="ts">
// 分诊记录（§6）
// 设计来源：product/11_功能需求.md §6 AI 智能问诊与分诊
// 接入后端 B4 GET /api/triage：服务端筛选 + 分页，批量补齐患者姓名
import { ref, computed, onMounted, reactive } from 'vue'
import { getTriageRecords, getDepartments } from '@/api/admin'
import type { AdminTriageRecord } from '@/types/triage'
import type { AdminTriageQuery } from '@/types/triage'
import type { TriagePriority } from '@/types/triage'
import type { DepartmentResponse } from '@/types/admin'

// ---- 列表状态 ----
const loading = ref(false)
const loadError = ref('')
const records = ref<AdminTriageRecord[]>([])
const total = ref(0)
const pageSize = ref(10)
const currentPage = ref(1)

// ---- 科室（用于筛选下拉）----
const departments = ref<DepartmentResponse[]>([])

// ---- 筛选（服务端）----
const filter = reactive<{
  departmentId?: number
  startDate: string
  endDate: string
  patientId?: number
}>({
  departmentId: undefined,
  startDate: '',
  endDate: '',
  patientId: undefined,
})

// ---- 展开 ----
const expandedId = ref<number | null>(null)

// ---- 元信息映射 ----
const priorityMeta: Record<string, { label: string; cls: string; text: string }> = {
  LOW: { label: '低', cls: 'pr-low', text: 'text-low' },
  MEDIUM: { label: '中', cls: 'pr-medium', text: 'text-medium' },
  HIGH: { label: '高', cls: 'pr-high', text: 'text-high' },
  EMERGENCY: { label: '急诊', cls: 'pr-emergency', text: 'text-emergency' },
}

const priorityOptions: { value: 'ALL' | TriagePriority; label: string }[] = [
  { value: 'ALL', label: '全部优先级' },
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
  { value: 'EMERGENCY', label: '急诊' },
]

const priorityFilter = ref<'ALL' | TriagePriority>('ALL')

function priorityLabel(p: string): string {
  return priorityMeta[p]?.label ?? p
}

function priorityClass(p: string): string {
  return priorityMeta[p]?.cls ?? ''
}

function formatDateTime(iso: string): string {
  if (!iso) return '--'
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return iso
  }
}

// ---- 当前页统计（仅基于已加载页数据，作为概览） ----
const stats = computed(() => {
  const all = records.value
  const count = (p: TriagePriority) => all.filter((r) => r.priority === p).length
  return {
    total: total.value,
    low: count('LOW'),
    medium: count('MEDIUM'),
    high: count('HIGH'),
    emergency: count('EMERGENCY'),
  }
})

const totalPages = computed(() => Math.max(1, Math.ceil(stats.value.total / pageSize.value)))

// ---- 加载 ----
async function loadDepartments() {
  try {
    departments.value = await getDepartments()
  } catch (e) {
    console.error('[AdminTriage] 加载科室列表失败：', e)
  }
}

async function loadRecords() {
  loading.value = true
  loadError.value = ''
  try {
    const query: AdminTriageQuery = {
      page: currentPage.value,
      pageSize: pageSize.value,
    }
    if (priorityFilter.value !== 'ALL') query.priority = priorityFilter.value
    if (filter.departmentId) query.departmentId = filter.departmentId
    if (filter.startDate) query.startDate = filter.startDate
    if (filter.endDate) query.endDate = filter.endDate
    if (filter.patientId) query.patientId = filter.patientId
    const result = await getTriageRecords(query)
    records.value = result.list
    total.value = result.total
    pageSize.value = result.pageSize
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载分诊记录失败'
    console.error('[AdminTriage] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadRecords()
}

function handleReset() {
  priorityFilter.value = 'ALL'
  filter.departmentId = undefined
  filter.startDate = ''
  filter.endDate = ''
  filter.patientId = undefined
  currentPage.value = 1
  loadRecords()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadRecords()
}

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

onMounted(() => {
  loadDepartments()
  loadRecords()
})
</script>

<template>
  <div class="triage-view">
    <!-- 页头 -->
    <div class="page-header">
      <h1 class="page-title">分诊记录</h1>
      <div class="header-sub">查看 AI 智能分诊的全部记录与优先级分布（§6）</div>
    </div>

    <!-- 统计概览 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-value">{{ stats.total }}</div>
        <div class="stat-label">总记录</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-low">{{ stats.low }}</div>
        <div class="stat-label">本页低优先级</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-medium">{{ stats.medium }}</div>
        <div class="stat-label">本页中优先级</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-high">{{ stats.high }}</div>
        <div class="stat-label">本页高优先级</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-emergency">{{ stats.emergency }}</div>
        <div class="stat-label">本页急诊</div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">优先级</span>
        <select v-model="priorityFilter" class="filter-select">
          <option v-for="o in priorityOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <div class="filter-item">
        <span class="filter-label">科室</span>
        <select v-model="filter.departmentId" class="filter-select">
          <option :value="undefined">全部科室</option>
          <option v-for="dept in departments" :key="dept.id" :value="dept.id">
            {{ dept.name }}
          </option>
        </select>
      </div>
      <div class="filter-item">
        <span class="filter-label">开始日期</span>
        <input type="date" v-model="filter.startDate" class="filter-input" />
      </div>
      <div class="filter-item">
        <span class="filter-label">结束日期</span>
        <input type="date" v-model="filter.endDate" class="filter-input" />
      </div>
      <div class="filter-item">
        <span class="filter-label">患者 ID</span>
        <input
          type="number"
          min="1"
          v-model.number="filter.patientId"
          class="filter-input small"
          placeholder="精确匹配"
        />
      </div>
      <div class="filter-actions">
        <button class="primary-btn" @click="handleSearch">查询</button>
        <button class="ghost-btn" @click="handleReset">重置</button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-card">
      <span class="loading-spinner" />
      <span class="state-text">正在加载分诊记录…</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-card error-card">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadRecords">重新加载</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="records.length === 0" class="state-card">
      <div class="state-title">暂无分诊记录</div>
      <div class="state-desc">尝试调整筛选条件</div>
    </div>

    <!-- 记录列表 -->
    <div v-else class="triage-list">
      <div
        v-for="r in records"
        :key="r.id"
        class="triage-card"
        :class="[`priority-${r.priority}`, { expanded: expandedId === r.id }]"
      >
        <div class="card-head" @click="toggleExpand(r.id)">
          <div class="head-left">
            <span class="patient-name">{{ r.patientName || '未知患者' }}</span>
            <span class="priority-badge" :class="priorityClass(r.priority)">
              {{ priorityLabel(r.priority) }}优先级
            </span>
            <span v-if="r.priority === 'EMERGENCY' && r.emergencyAdvice" class="emergency-hint">
              急诊建议：{{ r.emergencyAdvice }}
            </span>
          </div>
          <div class="head-right">
            <span class="created-time">{{ formatDateTime(r.createdAt) }}</span>
            <span class="expand-toggle">{{ expandedId === r.id ? '收起' : '展开' }}</span>
          </div>
        </div>

        <div class="card-body">
          <div class="body-row">
            <span class="meta-label">推荐科室</span>
            <span class="dept-value">{{ r.recommendedDepartmentName || '--' }}</span>
          </div>
          <div class="body-row">
            <span class="meta-label">症状</span>
            <span class="meta-value">{{ r.symptoms || '--' }}</span>
          </div>
          <div class="body-row">
            <span class="meta-label">AI 摘要</span>
            <span class="meta-value">{{ r.aiSummary || '--' }}</span>
          </div>
        </div>

        <div v-if="expandedId === r.id" class="card-detail">
          <div class="detail-block">
            <div class="detail-label">完整症状描述</div>
            <div class="detail-value">{{ r.symptoms || '--' }}</div>
          </div>
          <div class="detail-block">
            <div class="detail-label">AI 推荐理由</div>
            <div class="detail-value">{{ r.reason || '--' }}</div>
          </div>
          <div class="detail-block">
            <div class="detail-label">安全提示</div>
            <div class="detail-value">{{ r.safetyAdvice || '--' }}</div>
          </div>
          <div v-if="r.emergencyAdvice" class="detail-block emergency">
            <div class="detail-label">急诊建议</div>
            <div class="detail-value">{{ r.emergencyAdvice }}</div>
          </div>
          <div v-if="r.followUpQuestion" class="detail-block">
            <div class="detail-label">AI 追问</div>
            <div class="detail-value">{{ r.followUpQuestion }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="!loading && !loadError && records.length > 0" class="pagination">
      <button
        class="page-btn"
        :disabled="currentPage <= 1"
        @click="handlePageChange(currentPage - 1)"
      >
        上一页
      </button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页 · 共 {{ total }} 条</span>
      <button
        class="page-btn"
        :disabled="currentPage >= totalPages"
        @click="handlePageChange(currentPage + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<style scoped>
.triage-view {
  max-width: 1200px;
  margin: 0 auto;
}

/* 页头 */
.page-header {
  margin-bottom: 18px;
}

.page-title {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #8e8e93;
}

/* 统计概览 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 16px 14px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.2;
}

.stat-label {
  margin-top: 6px;
  font-size: 12px;
  color: #8e8e93;
}

.text-low {
  color: #389e0d;
}

.text-medium {
  color: #1890ff;
}

.text-high {
  color: #d48806;
}

.text-emergency {
  color: #cf1322;
}

/* 筛选栏 */
.filter-bar {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-label {
  font-size: 13px;
  color: #8e8e93;
  white-space: nowrap;
}

.filter-select,
.filter-input {
  height: 36px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 10px;
  font-size: 13px;
  color: #1a1a1a;
  background: #ffffff;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.filter-select {
  min-width: 130px;
  cursor: pointer;
}

.filter-input {
  width: 150px;
}

.filter-input.small {
  width: 110px;
}

.filter-select:focus,
.filter-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

/* 按钮 */
.primary-btn {
  padding: 8px 18px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
  white-space: nowrap;
  height: 36px;
}

.primary-btn:hover {
  opacity: 0.92;
}

.ghost-btn {
  padding: 8px 16px;
  background: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
  height: 36px;
}

.ghost-btn:hover {
  border-color: #4facfe;
  color: #4facfe;
}

/* 状态卡片（加载/错误/空） */
.state-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 40px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.state-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.error-card .state-title {
  color: #f56c6c;
}

.state-desc {
  font-size: 13px;
  color: #8e8e93;
}

.state-text {
  font-size: 14px;
  color: #8e8e93;
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

/* 记录列表 */
.triage-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.triage-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 14px 18px;
  transition: box-shadow 0.15s;
}

.triage-card.expanded {
  box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
}

.triage-card.priority-EMERGENCY {
  border-left: 3px solid #f56c6c;
  background: #fff5f5;
}

.triage-card.priority-HIGH {
  border-left: 3px solid #fa8c16;
  background: #fff7e6;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.patient-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.priority-badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
  line-height: 1.6;
  white-space: nowrap;
}

.pr-low {
  background: #f6ffed;
  color: #389e0d;
}

.pr-medium {
  background: #e6f7ff;
  color: #1890ff;
}

.pr-high {
  background: #fff7e6;
  color: #d48806;
}

.pr-emergency {
  background: #fff1f0;
  color: #cf1322;
}

.emergency-hint {
  font-size: 12px;
  color: #cf1322;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.created-time {
  font-size: 12px;
  color: #8e8e93;
}

.expand-toggle {
  font-size: 12px;
  color: #4facfe;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.body-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.meta-label {
  font-size: 12px;
  color: #8e8e93;
  flex-shrink: 0;
  min-width: 64px;
}

.meta-value {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}

.dept-value {
  font-size: 14px;
  font-weight: 600;
  color: #4facfe;
}

/* 展开详情 */
.card-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e8edf3;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-block {
  background: #f8fafc;
  border-radius: 10px;
  padding: 10px 12px;
}

.detail-block.emergency {
  background: #fff1f0;
  border-left: 3px solid #cf1322;
}

.detail-label {
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 4px;
}

.detail-value {
  font-size: 13px;
  color: #1a1a1a;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.page-btn {
  padding: 6px 16px;
  background: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}

.page-btn:hover:not(:disabled) {
  border-color: #4facfe;
  color: #4facfe;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #8e8e93;
}

/* 响应式 */
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-actions {
    margin-left: 0;
    width: 100%;
  }
}
</style>
