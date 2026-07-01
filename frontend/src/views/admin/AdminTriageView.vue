<script setup lang="ts">
// 分诊记录（§6）
// 设计来源：product/11_功能需求.md §6 AI 智能问诊与分诊
// 查看全部分诊记录，支持按优先级与关键字筛选，并展示优先级分布
// 注：后端 /api/triage 当前仅支持 page/size；优先级与关键字筛选在前端完成
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTriageRecords } from '@/api/admin'
import type { AdminTriageRecord, TriagePriority } from '@/types/triage'

// ---- 列表状态 ----
const loading = ref(false)
const loadError = ref('')
const records = ref<AdminTriageRecord[]>([])
// 后端 PageResponse.total：分诊全量记录数（不受单页 size=100 限制）
const totalRecords = ref(0)
// 后端单页 size 上限 100：超过后最早数据被截断，统计基于已加载数据计算
const BACKEND_PAGE_SIZE = 100

// ---- 筛选 ----
const priorityFilter = ref<'ALL' | TriagePriority>('ALL')
const keyword = ref('')

// ---- 分页 ----
const PAGE_SIZE = 20
const currentPage = ref(1)

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

// ---- 统计（基于当前已加载的全量数据，注意后端单页 size 上限 100） ----
const stats = computed(() => {
  const all = records.value
  const count = (p: TriagePriority) => all.filter((r) => r.priority === p).length
  return {
    loaded: all.length,
    total: totalRecords.value,
    low: count('LOW'),
    medium: count('MEDIUM'),
    high: count('HIGH'),
    emergency: count('EMERGENCY'),
  }
})

// ---- 筛选结果（按创建时间倒序） ----
const filteredRecords = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return records.value
    .filter((r) => {
      if (priorityFilter.value !== 'ALL' && r.priority !== priorityFilter.value) return false
      if (!kw) return true
      const hay = [
        r.patientName,
        r.symptoms,
        r.aiSummary,
        r.recommendedDepartmentName,
        r.reason,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      return hay.includes(kw)
    })
    .slice()
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
})

// ---- 分页结果（基于筛选结果） ----
const paginatedRecords = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filteredRecords.value.slice(start, start + PAGE_SIZE)
})

// 切换筛选条件时回到第一页
function onFilterChange() {
  currentPage.value = 1
}

// ---- 加载 ----
async function loadRecords() {
  loading.value = true
  loadError.value = ''
  try {
    // 后端单页 size 上限 100；分优先级统计基于已加载数据计算
    // （全量统计需后端聚合接口 /triage/stats，已记录到想法文档 D-1）
    const result = await getTriageRecords({ page: 1, pageSize: BACKEND_PAGE_SIZE })
    records.value = result.list
    totalRecords.value = result.total
    currentPage.value = 1
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载分诊记录失败'
    console.error('[AdminTriage] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

onMounted(loadRecords)
</script>

<template>
  <div class="triage-view">
    <!-- 页头 -->
    <div class="page-header">
      <h1 class="page-title">分诊记录</h1>
      <div class="header-sub">查看 AI 智能分诊的全部记录与优先级分布（§6）</div>
    </div>

    <!-- 统计概览：总记录 = 后端全量；优先级分布 = 已加载（受 size=100 限制） -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-value">{{ stats.total }}</div>
        <div class="stat-label">总记录（后端）</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-low">{{ stats.low }}</div>
        <div class="stat-label">低优先级</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-medium">{{ stats.medium }}</div>
        <div class="stat-label">中优先级</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-high">{{ stats.high }}</div>
        <div class="stat-label">高优先级</div>
      </div>
      <div class="stat-card">
        <div class="stat-value text-emergency">{{ stats.emergency }}</div>
        <div class="stat-label">急诊</div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">优先级</span>
        <el-select v-model="priorityFilter" style="width: 160px" @change="onFilterChange">
          <el-option
            v-for="o in priorityOptions"
            :key="o.value"
            :label="o.label"
            :value="o.value"
          />
        </el-select>
      </div>
      <div class="filter-item filter-grow">
        <span class="filter-label">关键字</span>
        <el-input
          v-model="keyword"
          placeholder="搜索患者姓名、症状、推荐科室、推荐理由"
          clearable
          @input="onFilterChange"
        />
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
    <div v-else-if="filteredRecords.length === 0" class="state-card">
      <div class="state-title">暂无分诊记录</div>
      <div class="state-desc">尝试调整筛选条件</div>
    </div>

    <!-- 记录列表 -->
    <div v-else class="triage-list">
      <div
        v-for="r in paginatedRecords"
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

      <!-- 分页（当前基于已加载 100 条客户端分页；超量数据需后端分页接口） -->
      <div v-if="filteredRecords.length > PAGE_SIZE || stats.loaded < stats.total" class="triage-pagination-hint">
        <span v-if="stats.loaded < stats.total">
          已加载 {{ stats.loaded }} / {{ stats.total }} 条，剩余数据需后端分页/筛选参数（已记录到想法文档 D-1）
        </span>
        <span v-else>分页（基于已加载 {{ stats.loaded }} 条）</span>
      </div>
      <el-pagination
        v-if="filteredRecords.length > PAGE_SIZE"
        class="triage-pagination"
        layout="prev, pager, next, jumper, total"
        :page-size="PAGE_SIZE"
        :current-page="currentPage"
        :total="filteredRecords.length"
        background
        @current-change="(p: number) => (currentPage = p)"
      />
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
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-grow {
  flex: 1;
  min-width: 240px;
}

.filter-label {
  font-size: 13px;
  color: #8e8e93;
  white-space: nowrap;
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
}

.primary-btn:hover {
  opacity: 0.92;
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
.triage-pagination {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}

.triage-pagination-hint {
  text-align: center;
  font-size: 12px;
  color: #8e8e93;
  margin-top: 12px;
  padding: 8px 12px;
  background: #fff7e6;
  border-radius: 8px;
}

/* 响应式 */
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
