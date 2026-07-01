<script setup lang="ts">
// AI 调用记录（§16.3）
// 设计来源：product/11_功能需求.md §16
// 接入后端 B5 GET /api/audit/ai/invocations：服务端筛选 + 分页
// 后端不返回 provider/model（实体不存储），此处不展示这两列。
import { ref, computed, reactive, onMounted } from 'vue'
import { getAiInvocationLogs } from '@/api/admin'
import type { AiInvocationLog, AiInvocationLogQuery } from '@/types/admin'

const loading = ref(true)
const loadError = ref('')
const logs = ref<AiInvocationLog[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 调用类型筛选项：value 为原始 capability
const capabilityOptions: Array<{ value: string; label: string }> = [
  { value: '', label: '全部' },
  { value: 'triage', label: '分诊' },
  { value: 'diagnosis', label: '诊断' },
  { value: 'medical-record', label: '病历生成' },
  { value: 'prescription', label: '处方审核' },
  { value: 'examination', label: '检查解读' },
]

const capabilityLabels: Record<string, string> = {
  triage: '分诊',
  diagnosis: '诊断',
  'medical-record': '病历生成',
  prescription: '处方审核',
  examination: '检查解读',
}

const SLOW_THRESHOLD = 5000 // 毫秒

const filter = reactive<{
  capability: string
  result: 'ALL' | 'SUCCESS' | 'FAIL'
  businessType: string
  startDate: string
  endDate: string
}>({
  capability: '',
  result: 'ALL',
  businessType: '',
  startDate: '',
  endDate: '',
})

function capabilityText(capability: string): string {
  return capabilityLabels[capability] ?? capability
}

function errorTypeText(type: string): string {
  const map: Record<string, string> = {
    timeout: '超时',
    auth_failure: '认证失败',
    quota_exceeded: '配额超限',
    model_error: '模型错误',
    validation_error: '入参校验',
  }
  return map[type] || type
}

function formatDateTime(iso: string): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch {
    return iso
  }
}

function businessIdText(id: number | null): string {
  return id === null || id === undefined ? '--' : String(id)
}

function attemptText(count: number | null): string {
  return count === null || count === undefined ? '--' : String(count)
}

function isSlow(duration: number): boolean {
  return duration > SLOW_THRESHOLD
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

// 汇总统计（基于当前已加载页，仅作概览）
const stats = computed(() => {
  const list = logs.value
  const visibleTotal = list.length
  const successCount = list.filter((l) => l.success).length
  const successRate = visibleTotal === 0 ? 0 : Math.round((successCount / visibleTotal) * 1000) / 10
  const avgDuration =
    visibleTotal === 0
      ? 0
      : Math.round(list.reduce((sum, l) => sum + l.duration, 0) / visibleTotal)
  return { total: total.value, visibleTotal, successRate, avgDuration }
})

async function loadLogs() {
  loading.value = true
  loadError.value = ''
  try {
    const query: AiInvocationLogQuery = {
      page: currentPage.value,
      pageSize: pageSize.value,
    }
    if (filter.capability) query.capability = filter.capability
    if (filter.result === 'SUCCESS') query.success = true
    else if (filter.result === 'FAIL') query.success = false
    if (filter.businessType.trim()) query.businessType = filter.businessType.trim()
    if (filter.startDate) query.startDate = filter.startDate
    if (filter.endDate) query.endDate = filter.endDate
    const result = await getAiInvocationLogs(query)
    logs.value = result.list
    total.value = result.total
    pageSize.value = result.pageSize
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载 AI 调用记录失败'
    console.error('[AdminAiLogs] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadLogs()
}

function resetFilter() {
  filter.capability = ''
  filter.result = 'ALL'
  filter.businessType = ''
  filter.startDate = ''
  filter.endDate = ''
  currentPage.value = 1
  loadLogs()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadLogs()
}

onMounted(loadLogs)
</script>

<template>
  <div class="logs-view">
    <div class="page-header">
      <div class="header-title">AI 调用记录</div>
      <div class="header-sub">记录分诊、诊断、病历生成、处方审核、检查解读等 AI 服务的调用情况与耗时</div>
    </div>

    <!-- 汇总统计 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">调用总数</div>
        <div class="stat-value">{{ stats.total }}</div>
      </div>
      <div class="stat-card stat-success">
        <div class="stat-label">本页成功率</div>
        <div class="stat-value">{{ stats.successRate }}%</div>
      </div>
      <div class="stat-card stat-duration">
        <div class="stat-label">本页平均耗时</div>
        <div class="stat-value">{{ stats.avgDuration }}<span class="stat-unit">ms</span></div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-card">
      <div class="filter-item">
        <label class="filter-label">调用类型</label>
        <select v-model="filter.capability" class="filter-select">
          <option v-for="opt in capabilityOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>
      <div class="filter-item">
        <label class="filter-label">调用结果</label>
        <select v-model="filter.result" class="filter-select">
          <option value="ALL">全部</option>
          <option value="SUCCESS">成功</option>
          <option value="FAIL">失败</option>
        </select>
      </div>
      <div class="filter-item filter-item-grow">
        <label class="filter-label">业务类型</label>
        <input
          v-model="filter.businessType"
          type="text"
          class="filter-input"
          placeholder="精确匹配业务类型"
        />
      </div>
      <div class="filter-item">
        <label class="filter-label">开始日期</label>
        <input type="date" v-model="filter.startDate" class="filter-input" />
      </div>
      <div class="filter-item">
        <label class="filter-label">结束日期</label>
        <input type="date" v-model="filter.endDate" class="filter-input" />
      </div>
      <button class="primary-btn" @click="handleSearch">查询</button>
      <button class="ghost-btn" @click="resetFilter">重置</button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-card">
      <span class="loading-spinner" />
      <span class="state-text">正在加载 AI 调用记录…</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-card error-card">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadLogs">重新加载</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="logs.length === 0" class="state-card">
      <div class="state-title">暂无记录</div>
      <div class="state-desc">没有符合筛选条件的 AI 调用记录</div>
    </div>

    <!-- 日志表格 -->
    <div v-else class="table-card">
      <div class="table-meta">共 {{ total }} 条记录 · 当前第 {{ currentPage }} / {{ totalPages }} 页</div>
      <div class="table-scroll">
        <table class="log-table">
          <thead>
            <tr>
              <th>调用时间</th>
              <th>调用类型</th>
              <th>业务类型</th>
              <th>业务 ID</th>
              <th>尝试次数</th>
              <th>耗时</th>
              <th>结果</th>
              <th>错误类型</th>
              <th>错误信息</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td class="cell-time">{{ formatDateTime(log.calledAt) }}</td>
              <td>
                <span class="badge badge-type">{{ capabilityText(log.callType) }}</span>
              </td>
              <td class="cell-target-type">{{ log.businessType || '--' }}</td>
              <td class="cell-target-id">{{ businessIdText(log.businessId) }}</td>
              <td class="cell-target-id">{{ attemptText(log.attemptCount) }}</td>
              <td>
                <span
                  class="duration"
                  :class="{ 'duration-slow': isSlow(log.duration) }"
                >
                  {{ log.duration }} ms
                </span>
              </td>
              <td>
                <span class="badge" :class="log.success ? 'badge-success' : 'badge-fail'">
                  {{ log.success ? '成功' : '失败' }}
                </span>
              </td>
              <td>
                <span v-if="log.errorType" class="error-type-badge">{{ errorTypeText(log.errorType) }}</span>
                <span v-else>--</span>
              </td>
              <td class="cell-error">
                <span v-if="log.errorMessage" class="error-text">{{ log.errorMessage }}</span>
                <span v-else class="error-dash">--</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <button
          class="page-btn"
          :disabled="currentPage <= 1"
          @click="handlePageChange(currentPage - 1)"
        >
          上一页
        </button>
        <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button
          class="page-btn"
          :disabled="currentPage >= totalPages"
          @click="handlePageChange(currentPage + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.logs-view {
  padding: 16px 16px 24px;
  max-width: 1200px;
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
  font-size: 13px;
  color: #8e8e93;
  line-height: 1.5;
}

/* ============ 汇总统计 ============ */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 18px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.stat-label {
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
}

.stat-unit {
  font-size: 14px;
  font-weight: 500;
  color: #8e8e93;
  margin-left: 4px;
}

.stat-success .stat-value {
  color: #67c23a;
}

.stat-duration .stat-value {
  color: #1a73e8;
}

/* ============ 筛选栏 ============ */
.filter-card {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-item-grow {
  flex: 1;
  min-width: 180px;
}

.filter-label {
  font-size: 12px;
  font-weight: 500;
  color: #475569;
}

.filter-select,
.filter-input {
  height: 36px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a1a;
  outline: none;
  background: #ffffff;
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

.filter-select:focus,
.filter-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
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
  height: 36px;
}

.primary-btn:hover {
  opacity: 0.92;
}

.ghost-btn {
  padding: 8px 16px;
  background: #ffffff;
  color: #475569;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  height: 36px;
}

.ghost-btn:hover {
  border-color: #4facfe;
  color: #1a73e8;
}

/* ============ 状态卡 ============ */
.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 20px;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  text-align: center;
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

.state-text {
  font-size: 14px;
  color: #8e8e93;
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
  line-height: 1.5;
  margin-bottom: 4px;
}

/* ============ 表格 ============ */
.table-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  overflow: hidden;
}

.table-meta {
  font-size: 12px;
  color: #8e8e93;
  padding: 12px 16px 0;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 960px;
}

.log-table thead th {
  text-align: left;
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #8e8e93;
  background: #fafbfc;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}

.log-table tbody td {
  padding: 12px 16px;
  color: #1a1a1a;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.log-table tbody tr:last-child td {
  border-bottom: none;
}

.log-table tbody tr:hover {
  background: #fafbfc;
}

.cell-time {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  color: #475569;
}

.cell-target-type {
  color: #475569;
  white-space: nowrap;
}

.cell-target-id {
  color: #475569;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.cell-error {
  max-width: 220px;
}

.error-text {
  color: #f56c6c;
  line-height: 1.5;
}

.error-dash {
  color: #c0c4cc;
}

/* ============ 耗时 ============ */
.duration {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  color: #475569;
}

.duration-slow {
  color: #fa8c16;
  font-weight: 600;
}

/* ============ 徽章 ============ */
.badge {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 10px;
  white-space: nowrap;
}

.badge-type {
  background: #f0f5ff;
  color: #2f54eb;
}

.badge-success {
  background: #f6ffed;
  color: #389e0d;
}

.badge-fail {
  background: #fff1f0;
  color: #cf1322;
}

/* ============ 错误类型徽章 ============ */
.error-type-badge {
  display: inline-block;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  white-space: nowrap;
  background: #fff1f0;
  color: #cf1322;
  border: 1px solid #ffccc7;
}

/* ============ 分页 ============ */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 14px 16px;
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
</style>
