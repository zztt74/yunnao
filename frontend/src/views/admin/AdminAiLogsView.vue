<script setup lang="ts">
// AI 调用记录（§16.3）
// 设计来源：product/11_功能需求.md §16
// 只读列表页：展示 AI 服务调用记录，支持按调用类型/结果/业务类型/日期范围/关键字筛选，含汇总统计
// 切页时不卸载表格，仅覆盖数据，避免 el-pagination 触发表格整体闪退
// 敏感字段（API Key、Token 等）不在前端展示；attempts 详情通过独立接口按需展开
import { ref, computed, onMounted, reactive } from 'vue'
import { getAiInvocationLogs, getAiInvocationAttempts } from '@/api/admin'
import type { AiInvocationLog, AiInvocationAttempt } from '@/types/admin'

const PAGE_SIZE = 20
const loading = ref(true)
const loadError = ref('')
const logs = ref<AiInvocationLog[]>([])
const total = ref(0)
const currentPage = ref(1)

// 调用类型筛选项：value 为原始 callType，label 为中文
const callTypeOptions: Array<{ value: string; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'triage', label: '分诊' },
  { value: 'diagnosis', label: '诊断' },
  { value: 'medical-record', label: '病历生成' },
  { value: 'prescription', label: '处方审核' },
  { value: 'examination', label: '检查解读' },
]

const callTypeLabels: Record<string, string> = {
  triage: '分诊',
  diagnosis: '诊断',
  'medical-record': '病历生成',
  prescription: '处方审核',
  examination: '检查解读',
}

const SLOW_THRESHOLD = 5000 // 毫秒，超过则标橙

const callTypeFilter = ref<string>('ALL')
// 后端 success: Boolean（true=仅 SUCCESS；false=非 SUCCESS；undefined=全部）
type ResultFilter = 'ALL' | 'SUCCESS' | 'FAIL'
const resultFilter = ref<ResultFilter>('ALL')
const businessTypeFilter = ref('')
const dateRange = reactive<{ start: string; end: string }>({ start: '', end: '' })
const keyword = ref('')

function callTypeText(callType: string): string {
  return callTypeLabels[callType] ?? callType
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

// F-HW-11：内部 provider key → 用户友好展示文本。
// 真实供应商使用品牌名；mock/未知来源显示「本地模拟」/「未知」，
// 避免把内部技术 key（mock / unknown 等）直接暴露给演示用户。
const providerDisplayMap: Record<string, string> = {
  deepseek: 'DeepSeek',
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  qwen: '通义千问',
  wenxin: '文心一言',
  glm: '智谱 GLM',
  mock: '本地模拟',
  local: '本地模拟',
  unknown: '未知来源',
}

function providerDisplayText(raw: string | null | undefined): string {
  if (!raw || !raw.trim()) return '--'
  const key = raw.trim().toLowerCase()
  return providerDisplayMap[key] ?? raw
}

function modelDisplayText(raw: string | null | undefined): string {
  if (!raw || !raw.trim()) return '--'
  return raw
}

function formatDateTime(iso: string | null | undefined): string {
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

function isSlow(duration: number): boolean {
  return duration > SLOW_THRESHOLD
}

// ---- attempts 缓存（F-HW-11：列表行展示与行展开共用，避免重复请求） ----
const attemptsCache = ref<Record<number, AiInvocationAttempt[]>>({})

// F-HW-11：尝试从已缓存的 attempts 中提取最近一次的 provider/model 填充列表行。
// 若 attempts 尚未拉取（首次进入 / 切换分页），返回 null，UI 降级为 '--'，
// 等待预取完成或用户展开行后再显示。
function lastAttemptSummary(id: number): { provider: string; model: string } | null {
  const attempts = attemptsCache.value[id]
  if (!attempts || attempts.length === 0) return null
  const sorted = [...attempts].sort((a, b) => a.attemptIndex - b.attemptIndex)
  const last = sorted[sorted.length - 1]
  return { provider: last.provider, model: last.model }
}

// 列表展示：仅在当前页做关键字客户端筛选；其它维度已由后端过滤
const filteredLogs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return logs.value
  return logs.value.filter((l) => {
    const summary = lastAttemptSummary(l.id)
    const providerRaw = summary?.provider ?? l.provider
    const modelRaw = summary?.model ?? l.model
    return (
      providerRaw.toLowerCase().includes(kw) ||
      modelRaw.toLowerCase().includes(kw) ||
      providerDisplayText(providerRaw).toLowerCase().includes(kw) ||
      l.businessType.toLowerCase().includes(kw) ||
      l.callType.toLowerCase().includes(kw) ||
      (l.errorMessage ?? '').toLowerCase().includes(kw)
    )
  })
})

// 汇总统计：基于当前页的 keyword 筛选结果，与表格行数严格一致
const stats = computed(() => {
  const list = filteredLogs.value
  const totalCalls = list.length
  const successCount = list.filter((l) => l.success).length
  const successRate = totalCalls === 0 ? 0 : Math.round((successCount / totalCalls) * 1000) / 10
  const successList = list.filter((l) => l.success)
  const avgDuration =
    successList.length === 0
      ? 0
      : Math.round(
          successList.reduce((sum, l) => sum + l.duration, 0) / successList.length,
        )
  return { totalCalls, successRate, avgDuration }
})

function buildQueryParams() {
  return {
    page: currentPage.value,
    pageSize: PAGE_SIZE,
    capability: callTypeFilter.value === 'ALL' ? undefined : callTypeFilter.value,
    businessType: businessTypeFilter.value.trim() || undefined,
    success:
      resultFilter.value === 'ALL'
        ? undefined
        : resultFilter.value === 'SUCCESS',
    startDate: dateRange.start || undefined,
    endDate: dateRange.end || undefined,
  }
}

async function loadLogs() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getAiInvocationLogs(buildQueryParams())
    logs.value = result.list
    total.value = result.total
    // F-HW-11：列表行 DTO 不下发 provider/model，并行预取 attempts 填充表格列。
    // 单条拉取失败不影响其他行；统一静默失败，UI 降级为 '--'。
    void prefetchAttempts(result.list.map((log) => log.id))
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载 AI 调用记录失败'
    console.error('[AdminAiLogs] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

async function prefetchAttempts(ids: number[]) {
  if (ids.length === 0) return
  const missing = ids.filter((id) => !attemptsCache.value[id])
  if (missing.length === 0) return
  await Promise.all(
    missing.map(async (id) => {
      try {
        attemptsCache.value[id] = await getAiInvocationAttempts(id)
      } catch (e) {
        console.warn(`[AdminAiLogs] 预取 attempts 失败（id=${id}）：`, e)
      }
    }),
  )
}

function resetFilter() {
  callTypeFilter.value = 'ALL'
  resultFilter.value = 'ALL'
  businessTypeFilter.value = ''
  dateRange.start = ''
  dateRange.end = ''
  keyword.value = ''
  currentPage.value = 1
  loadLogs()
}

function onFilterChange() {
  currentPage.value = 1
  loadLogs()
}

function onPageChange(page: number) {
  currentPage.value = page
  loadLogs()
}

// ---- attempts 详情（行展开） ----
const expandedId = ref<number | null>(null)
const attemptsLoading = ref<number | null>(null)
const attemptsError = ref('')

async function toggleAttempts(log: AiInvocationLog) {
  if (expandedId.value === log.id) {
    expandedId.value = null
    return
  }
  expandedId.value = log.id
  if (attemptsCache.value[log.id]) return
  attemptsLoading.value = log.id
  attemptsError.value = ''
  try {
    attemptsCache.value[log.id] = await getAiInvocationAttempts(log.id)
  } catch (e) {
    attemptsError.value =
      e instanceof Error ? e.message : '加载 attempts 失败'
    console.error('[AdminAiLogs] 加载 attempts 失败：', e)
  } finally {
    attemptsLoading.value = null
  }
}

function attemptStatusText(status: string): string {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    FAILED: '失败',
    PENDING: '等待中',
    RUNNING: '执行中',
    SKIPPED: '已跳过',
  }
  return map[status] ?? status
}

onMounted(loadLogs)
</script>

<template>
  <div class="logs-view">
    <div class="page-header">
      <div class="header-title">AI 调用记录</div>
      <div class="header-sub">记录分诊、诊断、病历生成、处方审核、检查解读等 AI 服务的调用情况与耗时</div>
    </div>

    <!-- 汇总统计：基于当前页 + 关键字筛选后的数据，与表格行数严格一致 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">调用总数（当前页）</div>
        <div class="stat-value">{{ stats.totalCalls }}</div>
      </div>
      <div class="stat-card stat-success">
        <div class="stat-label">成功率</div>
        <div class="stat-value">{{ stats.successRate }}%</div>
      </div>
      <div class="stat-card stat-duration">
        <div class="stat-label">平均耗时（仅成功）</div>
        <div class="stat-value">{{ stats.avgDuration }}<span class="stat-unit">ms</span></div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-card">
      <div class="filter-item">
        <label class="filter-label">调用类型</label>
        <select v-model="callTypeFilter" class="filter-select" @change="onFilterChange">
          <option
            v-for="opt in callTypeOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </option>
        </select>
      </div>
      <div class="filter-item">
        <label class="filter-label">调用结果</label>
        <select v-model="resultFilter" class="filter-select" @change="onFilterChange">
          <option value="ALL">全部</option>
          <option value="SUCCESS">成功</option>
          <option value="FAIL">失败</option>
        </select>
      </div>
      <div class="filter-item">
        <label class="filter-label">业务类型</label>
        <input
          v-model="businessTypeFilter"
          type="text"
          class="filter-input"
          placeholder="如 encounter / triage"
          @keyup.enter="onFilterChange"
        />
      </div>
      <div class="filter-item">
        <label class="filter-label">开始日期</label>
        <input
          v-model="dateRange.start"
          type="date"
          class="filter-input"
          @change="onFilterChange"
        />
      </div>
      <div class="filter-item">
        <label class="filter-label">结束日期</label>
        <input
          v-model="dateRange.end"
          type="date"
          class="filter-input"
          @change="onFilterChange"
        />
      </div>
      <div class="filter-item filter-item-grow">
        <label class="filter-label">关键字搜索</label>
        <input
          v-model="keyword"
          type="text"
          class="filter-input"
          placeholder="供应商 / 模型 / 业务类型 / 错误信息"
          @keyup.enter="onFilterChange"
        />
      </div>
      <button class="ghost-btn" @click="resetFilter">重置</button>
      <button class="primary-btn" @click="onFilterChange">刷新</button>
    </div>

    <!-- 加载失败（独立区域，不会卸载表格） -->
    <div v-if="!loading && loadError" class="state-card error-card">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadLogs">重新加载</button>
    </div>

    <!-- 日志表格（始终挂载；首次加载时显示遮罩；空态/有数据共用同一表格） -->
    <div v-else class="table-card" :class="{ 'is-loading': loading }">
      <div class="table-meta">
        共 {{ total }} 条记录（当前页 {{ filteredLogs.length }} 条）
        <span v-if="loading" class="loading-inline">加载中…</span>
      </div>
      <div class="table-scroll">
        <table class="log-table">
          <thead>
            <tr>
              <th style="width: 36px"></th>
              <th>调用时间</th>
              <th>能力类型</th>
              <th>供应商</th>
              <th>模型</th>
              <th>业务类型</th>
              <th>业务 ID</th>
              <th>耗时</th>
              <th>结果</th>
              <th>重试</th>
              <th>错误类型</th>
              <th>错误信息</th>
            </tr>
          </thead>
          <tbody>
            <!-- 空态行（与正常行共享表头，不切走 v-if） -->
            <tr v-if="filteredLogs.length === 0 && !loading">
              <td colspan="12" class="empty-row">
                <div class="state-title">暂无记录</div>
                <div class="state-desc">没有符合筛选条件的 AI 调用记录</div>
              </td>
            </tr>
            <template v-for="log in filteredLogs" :key="log.id">
              <tr class="row-log" @click="toggleAttempts(log)">
                <td class="cell-expand">
                  <span class="expand-toggle" :class="{ open: expandedId === log.id }">▶</span>
                </td>
                <td class="cell-time">{{ formatDateTime(log.calledAt) }}</td>
                <td>
                  <span class="badge badge-type">{{ callTypeText(log.callType) }}</span>
                </td>
                <td class="cell-mono">{{ providerDisplayText(lastAttemptSummary(log.id)?.provider ?? log.provider) }}</td>
                <td class="cell-mono">{{ modelDisplayText(lastAttemptSummary(log.id)?.model ?? log.model) }}</td>
                <td class="cell-target-type">{{ log.businessType }}</td>
                <td class="cell-target-id">{{ businessIdText(log.businessId) }}</td>
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
                <td class="cell-target-id">{{ log.attemptCount }}</td>
                <td>
                  <span v-if="log.errorType" class="error-type-badge">{{ errorTypeText(log.errorType) }}</span>
                  <span v-else>--</span>
                </td>
                <td class="cell-error">
                  <span v-if="log.errorMessage" class="error-text">{{ log.errorMessage }}</span>
                  <span v-else class="error-dash">--</span>
                </td>
              </tr>
              <tr v-if="expandedId === log.id" class="row-attempts">
                <td colspan="12">
                  <div class="attempts-panel">
                    <div v-if="attemptsLoading === log.id" class="attempts-loading">正在加载 attempts…</div>
                    <div v-else-if="attemptsError" class="attempts-error">{{ attemptsError }}</div>
                    <div v-else-if="!attemptsCache[log.id] || attemptsCache[log.id].length === 0" class="attempts-empty">该调用无 attempts 详情</div>
                    <table v-else class="attempts-table">
                      <thead>
                        <tr>
                          <th>序号</th>
                          <th>状态</th>
                          <th>供应商</th>
                          <th>模型</th>
                          <th>Prompt 版本</th>
                          <th>HTTP</th>
                          <th>耗时</th>
                          <th>开始时间</th>
                          <th>结束时间</th>
                          <th>请求摘要</th>
                          <th>响应摘要</th>
                          <th>错误</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="a in attemptsCache[log.id]" :key="a.id">
                          <td class="cell-target-id">#{{ a.attemptIndex }}</td>
                          <td>
                            <span class="badge" :class="a.status === 'SUCCESS' ? 'badge-success' : 'badge-fail'">
                              {{ attemptStatusText(a.status) }}
                            </span>
                          </td>
                          <td class="cell-mono">{{ providerDisplayText(a.provider) }}</td>
                          <td class="cell-mono">{{ modelDisplayText(a.model) }}</td>
                          <td class="cell-mono">{{ a.promptVersion || '--' }}</td>
                          <td class="cell-target-id">{{ a.httpStatus ?? '--' }}</td>
                          <td>{{ a.duration ?? '--' }} ms</td>
                          <td class="cell-time">{{ formatDateTime(a.startedAt) }}</td>
                          <td class="cell-time">{{ formatDateTime(a.finishedAt) }}</td>
                          <td class="cell-error"><span class="error-text">{{ a.requestSummary || '--' }}</span></td>
                          <td class="cell-error"><span class="error-text">{{ a.responseSummary || '--' }}</span></td>
                          <td class="cell-error"><span class="error-text">{{ a.errorMessage || '--' }}</span></td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>

      <!-- 首次加载时不显示分页（避免空态闪烁） -->
      <el-pagination
        v-if="!loading && total > PAGE_SIZE"
        class="ai-logs-pagination"
        layout="prev, pager, next, jumper, total"
        :page-size="PAGE_SIZE"
        :current-page="currentPage"
        :total="total"
        background
        @current-change="onPageChange"
      />
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
  min-width: 200px;
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
  background: #f8f9fa;
}

.filter-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238e8e93' d='M6 8L2 4h8z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 30px;
  min-width: 130px;
}

.filter-select:focus,
.filter-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background-color: #ffffff;
}

.filter-input {
  width: 100%;
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

/* ============ 状态卡（仅错误） ============ */
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

.error-card .state-title {
  color: #f56c6c;
}

.state-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
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
  position: relative;
}

.table-card.is-loading {
  opacity: 0.55;
  pointer-events: none;
}

.table-meta {
  font-size: 12px;
  color: #8e8e93;
  padding: 12px 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-inline {
  color: #4facfe;
  font-weight: 500;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 1040px;
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

.row-log {
  cursor: pointer;
  transition: background 0.12s;
}

.row-log:hover {
  background: #fafbfc;
}

.row-log td {
  user-select: none;
}

.empty-row {
  text-align: center;
  padding: 40px 16px;
}

.cell-expand {
  text-align: center;
  width: 36px;
}

.expand-toggle {
  display: inline-block;
  font-size: 11px;
  color: #8e8e93;
  transition: transform 0.15s;
}

.expand-toggle.open {
  transform: rotate(90deg);
  color: #4facfe;
}

.cell-time {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  color: #475569;
}

.cell-mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
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

/* ============ attempts 详情 ============ */
.row-attempts td {
  background: #f8fafc;
  padding: 14px 16px;
}

.attempts-panel {
  width: 100%;
}

.attempts-loading,
.attempts-error,
.attempts-empty {
  font-size: 12px;
  color: #8e8e93;
  padding: 8px 0;
}

.attempts-error {
  color: #f56c6c;
}

.attempts-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  background: #ffffff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.attempts-table thead th {
  text-align: left;
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  color: #8e8e93;
  background: #fafbfc;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}

.attempts-table tbody td {
  padding: 8px 10px;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: top;
}

.attempts-table tbody tr:last-child td {
  border-bottom: none;
}

/* ============ 分页 ============ */
.ai-logs-pagination {
  display: flex;
  justify-content: center;
  margin: 14px 0 8px;
}
</style>
