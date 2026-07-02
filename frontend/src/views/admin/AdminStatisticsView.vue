<script setup lang="ts">
// 统计驾驶舱（全院运营统计）
// 设计来源：product/11_功能需求.md §15
// 展示：核心指标、30 日趋势、科室统计、医生排名、设备使用、AI 调用统计
import { ref, computed, onMounted } from 'vue'
import {
  getStatisticsSummary,
  getStatisticsTrend,
  getDepartmentStats,
  getDoctorRanking,
  getDeviceUsageStats,
  getAiCallStats,
  getDepartments,
} from '@/api/admin'
import type {
  StatisticsSummary,
  StatisticsTrendItem,
  DepartmentStatItem,
  DoctorRankingItem,
  DeviceUsageStatItem,
  AiCallStatItem,
  StatisticsQuery,
  DepartmentResponse,
} from '@/types/admin'

const loading = ref(true)
const summary = ref<StatisticsSummary | null>(null)
const trend = ref<StatisticsTrendItem[]>([])
const departmentStats = ref<DepartmentStatItem[]>([])
const doctorRanking = ref<DoctorRankingItem[]>([])
const deviceUsage = ref<DeviceUsageStatItem[]>([])
const aiCallStats = ref<AiCallStatItem | null>(null)

// F-HW-08：每个统计区域独立的错误状态与重试入口；
// 避免单个接口 500（如设备使用统计 SQL 抛错）通过 Promise.all 放大成整页崩溃。
const summaryError = ref('')
const trendError = ref('')
const departmentError = ref('')
const doctorError = ref('')
const deviceError = ref('')
const aiError = ref('')

// 查询参数（当前使用默认范围）
const query = ref<StatisticsQuery>({})

// 科室列表（用于筛选下拉）
const departments = ref<DepartmentResponse[]>([])

// 9 项核心指标
const summaryMetrics = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    { label: '今日挂号量', value: s.todayAppointments, color: '#4facfe' },
    { label: '今日完成就诊', value: s.todayCompletedEncounters, color: '#67c23a' },
    { label: '出诊医生数', value: s.todayActiveDoctors, color: '#9b59b6' },
    { label: '可用设备数', value: s.todayAvailableDevices, color: '#e6a23c' },
    { label: '高优先级分诊', value: s.todayHighPriorityTriages, color: '#f56c6c' },
    { label: '总患者数', value: s.totalPatients, color: '#4facfe' },
    { label: '总医生数', value: s.totalDoctors, color: '#9b59b6' },
    { label: '总科室数', value: s.totalDepartments, color: '#67c23a' },
    { label: '总设备数', value: s.totalDevices, color: '#e6a23c' },
  ]
})

// 30 日趋势最大值
const trendMax = computed(() => {
  let max = 0
  for (const item of trend.value) {
    if (item.appointments > max) max = item.appointments
    if (item.completedEncounters > max) max = item.completedEncounters
  }
  return max || 1
})

function trendBarHeight(value: number): string {
  return `${Math.max(3, (value / trendMax.value) * 100)}%`
}

function formatTrendDay(iso: string): string {
  const parts = iso.split('-')
  if (parts.length < 3) return iso
  return parts[2]
}

// 医生排名最大值（用于横向条形图）
const rankingMax = computed(() => {
  let max = 0
  for (const item of doctorRanking.value) {
    if (item.encounterCount > max) max = item.encounterCount
  }
  return max || 1
})

function rankingWidth(value: number): string {
  return `${Math.max(8, (value / rankingMax.value) * 100)}%`
}

// 设备使用率颜色
function utilizationColor(rate: number): string {
  if (rate >= 60) return '#67c23a'
  if (rate >= 30) return '#e6a23c'
  return '#f56c6c'
}

// 设备时长格式化（分钟 → 小时分钟）
function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
}

// AI 调用成功率
const aiSuccessRate = computed(() => {
  const a = aiCallStats.value
  if (!a || a.totalCalls === 0) return 0
  return Math.round((a.successCount / a.totalCalls) * 100)
})

// AI 平均耗时格式化（毫秒 → 秒）
function formatAvgDuration(ms: number): string {
  if (ms < 1000) return `${ms} 毫秒`
  return `${(ms / 1000).toFixed(2)} 秒`
}

// AI 调用类型中文标签
const aiTypeLabels: Record<string, string> = {
  triage: '智能分诊',
  diagnosis: '辅助诊断',
  'medical-record': '病历生成',
  prescription: '处方审核',
  examination: '检查开立',
}

function aiTypeLabel(type: string): string {
  return aiTypeLabels[type] ?? type
}

// AI 调用类型最大计数（用于横向条形图）
const aiTypeMax = computed(() => {
  const a = aiCallStats.value
  if (!a) return 1
  let max = 0
  for (const item of a.byType) {
    if (item.count > max) max = item.count
  }
  return max || 1
})

function aiTypeWidth(value: number): string {
  return `${Math.max(8, (value / aiTypeMax.value) * 100)}%`
}

async function loadData() {
  loading.value = true
  // F-HW-08：每个接口独立 try/catch，单接口失败不影响其他区域。
  summaryError.value = ''
  trendError.value = ''
  departmentError.value = ''
  doctorError.value = ''
  deviceError.value = ''
  aiError.value = ''

  const tasks: Array<Promise<void>> = [
    (async () => {
      try {
        summary.value = await getStatisticsSummary()
      } catch (e) {
        summaryError.value = e instanceof Error && e.message ? e.message : '核心指标加载失败'
        console.error('[AdminStatistics] 核心指标加载失败：', e)
      }
    })(),
    (async () => {
      try {
        trend.value = await getStatisticsTrend(30)
      } catch (e) {
        trendError.value = e instanceof Error && e.message ? e.message : '趋势数据加载失败'
        console.error('[AdminStatistics] 趋势数据加载失败：', e)
      }
    })(),
    (async () => {
      try {
        departmentStats.value = await getDepartmentStats(query.value)
      } catch (e) {
        departmentError.value = e instanceof Error && e.message ? e.message : '科室统计加载失败'
        console.error('[AdminStatistics] 科室统计加载失败：', e)
      }
    })(),
    (async () => {
      try {
        doctorRanking.value = await getDoctorRanking(query.value)
      } catch (e) {
        doctorError.value = e instanceof Error && e.message ? e.message : '医生排名加载失败'
        console.error('[AdminStatistics] 医生排名加载失败：', e)
      }
    })(),
    (async () => {
      try {
        deviceUsage.value = await getDeviceUsageStats()
      } catch (e) {
        deviceError.value = e instanceof Error && e.message ? e.message : '设备使用统计加载失败'
        console.error('[AdminStatistics] 设备使用统计加载失败：', e)
      }
    })(),
    (async () => {
      try {
        aiCallStats.value = await getAiCallStats()
      } catch (e) {
        aiError.value = e instanceof Error && e.message ? e.message : 'AI 调用统计加载失败'
        console.error('[AdminStatistics] AI 调用统计加载失败：', e)
      }
    })(),
  ]

  await Promise.all(tasks)
  loading.value = false
}

// F-HW-08：单区域重试入口；点击时仅重新拉取该区域，其它已成功区域不受影响。
async function retrySummary() {
  summaryError.value = ''
  try {
    summary.value = await getStatisticsSummary()
  } catch (e) {
    summaryError.value = e instanceof Error && e.message ? e.message : '核心指标加载失败'
  }
}
async function retryTrend() {
  trendError.value = ''
  try {
    trend.value = await getStatisticsTrend(30)
  } catch (e) {
    trendError.value = e instanceof Error && e.message ? e.message : '趋势数据加载失败'
  }
}
async function retryDepartment() {
  departmentError.value = ''
  try {
    departmentStats.value = await getDepartmentStats(query.value)
  } catch (e) {
    departmentError.value = e instanceof Error && e.message ? e.message : '科室统计加载失败'
  }
}
async function retryDoctor() {
  doctorError.value = ''
  try {
    doctorRanking.value = await getDoctorRanking(query.value)
  } catch (e) {
    doctorError.value = e instanceof Error && e.message ? e.message : '医生排名加载失败'
  }
}
async function retryDevice() {
  deviceError.value = ''
  try {
    deviceUsage.value = await getDeviceUsageStats()
  } catch (e) {
    deviceError.value = e instanceof Error && e.message ? e.message : '设备使用统计加载失败'
  }
}
async function retryAi() {
  aiError.value = ''
  try {
    aiCallStats.value = await getAiCallStats()
  } catch (e) {
    aiError.value = e instanceof Error && e.message ? e.message : 'AI 调用统计加载失败'
  }
}

async function loadDepartments() {
  try {
    departments.value = await getDepartments()
  } catch (e) {
    console.error('[AdminStatistics] 加载科室列表失败：', e)
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  query.value = {}
  loadData()
}

onMounted(() => {
  loadDepartments()
  loadData()
})
</script>

<template>
  <div class="admin-statistics">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-title">统计驾驶舱</div>
      <div class="page-subtitle">全院运营数据统计 · 默认展示最近 30 天数据</div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-item">
        <label class="filter-label" for="filter-start">开始日期</label>
        <input
          id="filter-start"
          type="date"
          class="filter-input"
          v-model="query.startDate"
        />
      </div>
      <div class="filter-item">
        <label class="filter-label" for="filter-end">结束日期</label>
        <input
          id="filter-end"
          type="date"
          class="filter-input"
          v-model="query.endDate"
        />
      </div>
      <div class="filter-item">
        <label class="filter-label" for="filter-dept">科室</label>
        <select
          id="filter-dept"
          class="filter-select"
          v-model="query.departmentId"
        >
          <option :value="undefined">全部科室</option>
          <option v-for="dept in departments" :key="dept.id" :value="dept.id">
            {{ dept.name }}
          </option>
        </select>
      </div>
      <div class="filter-actions">
        <button class="primary-btn" @click="handleSearch">查询</button>
        <button class="ghost-btn" @click="handleReset">重置</button>
      </div>
    </div>

    <!-- 加载状态：仅在所有区域首次加载时显示遮罩；之后由各区域独立降级 -->
    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载统计数据…</span>
    </div>

    <!-- 成功状态 -->
    <template v-else>
      <!-- 核心指标 -->
      <div class="section">
        <div class="section-title">核心指标</div>
        <div v-if="summaryError" class="area-fallback">
          <div class="area-fallback-text">核心指标暂不可用：{{ summaryError }}</div>
          <button class="area-retry-btn" @click="retrySummary">重试</button>
        </div>
        <div v-else-if="summaryMetrics.length === 0" class="area-fallback">
          <div class="area-fallback-text">暂无核心指标数据</div>
        </div>
        <div v-else class="metric-grid">
          <div
            v-for="item in summaryMetrics"
            :key="item.label"
            class="metric-card"
            :style="{ borderLeftColor: item.color }"
          >
            <div class="metric-value" :style="{ color: item.color }">{{ item.value }}</div>
            <div class="metric-label">{{ item.label }}</div>
          </div>
        </div>
      </div>

      <!-- 30 日趋势 -->
      <div class="section">
        <div class="section-title">近 30 日就诊趋势</div>
        <div class="chart-card">
          <div class="chart-legend">
            <span class="legend-item">
              <span class="legend-dot legend-blue" />
              <span class="legend-text">挂号量</span>
            </span>
            <span class="legend-item">
              <span class="legend-dot legend-green" />
              <span class="legend-text">完成就诊</span>
            </span>
          </div>
          <div v-if="trendError" class="area-fallback">
            <div class="area-fallback-text">趋势数据暂不可用：{{ trendError }}</div>
            <button class="area-retry-btn" @click="retryTrend">重试</button>
          </div>
          <div v-else-if="trend.length === 0" class="empty-card">
            <div class="empty-icon">--</div>
            <div class="empty-text">暂无趋势数据</div>
          </div>
          <div v-else class="chart-body">
            <div
              v-for="item in trend"
              :key="item.date"
              class="chart-col"
              :title="`${item.date} 挂号 ${item.appointments} / 完成 ${item.completedEncounters}`"
            >
              <div class="chart-bars">
                <div
                  class="bar bar-blue"
                  :style="{ height: trendBarHeight(item.appointments) }"
                />
                <div
                  class="bar bar-green"
                  :style="{ height: trendBarHeight(item.completedEncounters) }"
                />
              </div>
              <div class="chart-label">{{ formatTrendDay(item.date) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 科室统计 -->
      <div class="section">
        <div class="section-title">科室统计</div>
        <div class="table-card">
          <div v-if="departmentError" class="area-fallback">
            <div class="area-fallback-text">科室统计暂不可用：{{ departmentError }}</div>
            <button class="area-retry-btn" @click="retryDepartment">重试</button>
          </div>
          <div v-else-if="departmentStats.length === 0" class="empty-card">
            <div class="empty-icon">--</div>
            <div class="empty-text">暂无科室统计数据</div>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th class="col-name">科室名称</th>
                <th class="col-num">挂号量</th>
                <th class="col-num">完成就诊</th>
                <th class="col-num">就诊完成率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dept in departmentStats" :key="dept.departmentId">
                <td class="col-name">{{ dept.departmentName }}</td>
                <td class="col-num">{{ dept.appointmentCount }}</td>
                <td class="col-num">{{ dept.encounterCount }}</td>
                <td class="col-num">
                  {{ dept.appointmentCount === 0
                    ? '--'
                    : Math.round((dept.encounterCount / dept.appointmentCount) * 100) + '%' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 医生排名 -->
      <div class="section">
        <div class="section-title">医生接诊排名 Top 5</div>
        <div class="rank-card">
          <div v-if="doctorError" class="area-fallback">
            <div class="area-fallback-text">医生排名暂不可用：{{ doctorError }}</div>
            <button class="area-retry-btn" @click="retryDoctor">重试</button>
          </div>
          <div v-else-if="doctorRanking.length === 0" class="empty-card">
            <div class="empty-icon">--</div>
            <div class="empty-text">暂无医生排名数据</div>
          </div>
          <div v-else class="rank-list">
            <div
              v-for="(doc, index) in doctorRanking"
              :key="doc.doctorId"
              class="rank-item"
            >
              <div class="rank-no" :class="{ 'rank-top': index < 3 }">{{ index + 1 }}</div>
              <div class="rank-info">
                <div class="rank-head">
                  <span class="rank-name">{{ doc.doctorName }}</span>
                  <span class="rank-dept">{{ doc.departmentName }}</span>
                  <span class="rank-count">{{ doc.encounterCount }} 例</span>
                </div>
                <div class="rank-bar-track">
                  <div
                    class="rank-bar-fill"
                    :style="{ width: rankingWidth(doc.encounterCount) }"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 设备使用统计 -->
      <div class="section">
        <div class="section-title">设备使用统计</div>
        <div class="table-card">
          <div v-if="deviceError" class="area-fallback">
            <div class="area-fallback-text">设备使用统计暂不可用：{{ deviceError }}</div>
            <button class="area-retry-btn" @click="retryDevice">重试</button>
          </div>
          <div v-else-if="deviceUsage.length === 0" class="empty-card">
            <div class="empty-icon">--</div>
            <div class="empty-text">暂无设备使用数据</div>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th class="col-name">设备名称</th>
                <th class="col-code">设备编码</th>
                <th class="col-num">使用次数</th>
                <th class="col-num">累计时长</th>
                <th class="col-rate">利用率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dev in deviceUsage" :key="dev.deviceId">
                <td class="col-name">{{ dev.deviceName }}</td>
                <td class="col-code">{{ dev.deviceCode }}</td>
                <td class="col-num">{{ dev.totalUsageCount }}</td>
                <td class="col-num">{{ formatDuration(dev.totalUsageDuration) }}</td>
                <td class="col-rate">
                  <div class="rate-cell">
                    <div class="rate-track">
                      <div
                        class="rate-fill"
                        :style="{
                          width: Math.min(100, dev.utilizationRate) + '%',
                          background: utilizationColor(dev.utilizationRate),
                        }"
                      />
                    </div>
                    <span class="rate-text" :style="{ color: utilizationColor(dev.utilizationRate) }">
                      {{ dev.utilizationRate }}%
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- AI 调用统计 -->
      <div class="section">
        <div class="section-title">AI 调用统计</div>
        <div v-if="aiError" class="table-card">
          <div class="area-fallback">
            <div class="area-fallback-text">AI 调用统计暂不可用：{{ aiError }}</div>
            <button class="area-retry-btn" @click="retryAi">重试</button>
          </div>
        </div>
        <div v-else-if="!aiCallStats" class="table-card">
          <div class="empty-card">
            <div class="empty-icon">--</div>
            <div class="empty-text">暂无 AI 调用数据</div>
          </div>
        </div>
        <div v-else class="ai-wrap">
          <!-- 调用概览 -->
          <div class="ai-summary-grid">
            <div class="ai-summary-card ai-total">
              <div class="ai-summary-value">{{ aiCallStats.totalCalls }}</div>
              <div class="ai-summary-label">总调用次数</div>
            </div>
            <div class="ai-summary-card ai-success">
              <div class="ai-summary-value">{{ aiCallStats.successCount }}</div>
              <div class="ai-summary-label">成功次数</div>
            </div>
            <div class="ai-summary-card ai-fail">
              <div class="ai-summary-value">{{ aiCallStats.failureCount }}</div>
              <div class="ai-summary-label">失败次数</div>
            </div>
            <div class="ai-summary-card ai-rate">
              <div class="ai-summary-value">{{ aiSuccessRate }}%</div>
              <div class="ai-summary-label">成功率</div>
            </div>
            <div class="ai-summary-card ai-duration">
              <div class="ai-summary-value">{{ formatAvgDuration(aiCallStats.averageDuration) }}</div>
              <div class="ai-summary-label">平均耗时</div>
            </div>
          </div>

          <!-- 按类型分布 -->
          <div class="ai-type-card">
            <div class="ai-type-title">按业务类型分布</div>
            <div v-if="aiCallStats.byType.length === 0" class="empty-card">
              <div class="empty-icon">--</div>
              <div class="empty-text">暂无分类数据</div>
            </div>
            <div v-else class="ai-type-list">
              <div
                v-for="item in aiCallStats.byType"
                :key="item.type"
                class="ai-type-item"
              >
                <div class="ai-type-head">
                  <span class="ai-type-name">{{ aiTypeLabel(item.type) }}</span>
                  <span class="ai-type-count">
                    {{ item.count }} 次 · 成功 {{ item.successCount }} ·
                    失败 {{ item.count - item.successCount }}
                  </span>
                </div>
                <div class="ai-type-track">
                  <div
                    class="ai-type-fill"
                    :style="{ width: aiTypeWidth(item.count) }"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 底部提示 -->
    <div class="footer-tip">
      本平台由 AI 辅助，所有 AI 输出均为候选建议，不作为最终诊断依据
    </div>
  </div>
</template>

<style scoped>
.admin-statistics {
  padding: 16px 16px 24px;
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
  padding: 0 4px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.page-subtitle {
  font-size: 13px;
  color: #8e8e93;
}

/* 筛选栏 */
.filter-bar {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  margin-bottom: 20px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-label {
  font-size: 12px;
  color: #8e8e93;
}

.filter-input,
.filter-select {
  height: 34px;
  padding: 0 10px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a1a;
  background: #ffffff;
  outline: none;
  transition: border-color 0.15s;
}

.filter-input:focus,
.filter-select:focus {
  border-color: #4facfe;
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.ghost-btn {
  padding: 8px 18px;
  background: #ffffff;
  color: #4facfe;
  border: 1px solid #4facfe;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.ghost-btn:hover {
  opacity: 0.85;
}

.section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 12px;
  padding-left: 4px;
}

/* 核心指标卡片 */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.metric-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  border-left: 4px solid #e0e0e0;
}

.metric-value {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 6px;
  line-height: 1.1;
}

.metric-label {
  font-size: 13px;
  color: #8e8e93;
}

/* 图表卡片 */
.chart-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 18px 16px 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.chart-legend {
  display: flex;
  gap: 18px;
  margin-bottom: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.legend-blue {
  background: #4facfe;
}

.legend-green {
  background: #67c23a;
}

.legend-text {
  font-size: 12px;
  color: #8e8e93;
}

.chart-body {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 200px;
  padding-top: 10px;
}

.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 100%;
  width: 100%;
  justify-content: center;
}

.bar {
  width: 8px;
  min-height: 3px;
  border-radius: 3px 3px 0 0;
  transition: height 0.3s ease;
}

.bar-blue {
  background: linear-gradient(180deg, #4facfe 0%, #2d8ee0 100%);
}

.bar-green {
  background: linear-gradient(180deg, #67c23a 0%, #4e9f2a 100%);
}

.chart-label {
  margin-top: 8px;
  font-size: 12px;
  color: #8e8e93;
}

/* 表格卡片 */
.table-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 8px 4px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table thead th {
  text-align: left;
  font-weight: 600;
  color: #8e8e93;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
  white-space: nowrap;
}

.data-table tbody td {
  padding: 12px 14px;
  border-bottom: 1px solid #f5f5f5;
  color: #1a1a1a;
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr:hover {
  background: #f8fbff;
}

.col-name {
  text-align: left;
  min-width: 96px;
}

.col-code {
  text-align: left;
  color: #8e8e93;
  font-size: 12px;
  white-space: nowrap;
}

.col-num {
  text-align: right;
  white-space: nowrap;
}

.col-rate {
  text-align: left;
  min-width: 140px;
}

.rate-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rate-track {
  flex: 1;
  height: 6px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
  min-width: 60px;
}

.rate-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.rate-text {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  min-width: 38px;
  text-align: right;
}

/* 医生排名 */
.rank-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 14px;
}

.rank-no {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f0f0f0;
  color: #8e8e93;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rank-top {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.rank-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.rank-dept {
  font-size: 12px;
  color: #8e8e93;
}

.rank-count {
  font-size: 12px;
  color: #4facfe;
  font-weight: 600;
  margin-left: auto;
}

.rank-bar-track {
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.rank-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #4facfe 0%, #00c6ff 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

/* AI 调用统计 */
.ai-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-summary-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.ai-summary-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 12px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  text-align: center;
  border-top: 3px solid #e0e0e0;
}

.ai-total {
  border-top-color: #4facfe;
}

.ai-success {
  border-top-color: #67c23a;
}

.ai-fail {
  border-top-color: #f56c6c;
}

.ai-rate {
  border-top-color: #9b59b6;
}

.ai-duration {
  border-top-color: #e6a23c;
}

.ai-summary-value {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 4px;
  line-height: 1.1;
}

.ai-summary-label {
  font-size: 12px;
  color: #8e8e93;
}

.ai-type-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.ai-type-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 14px;
}

.ai-type-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-type-item {
  width: 100%;
}

.ai-type-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.ai-type-name {
  font-size: 13px;
  font-weight: 600;
  color: #1a1a1a;
}

.ai-type-count {
  font-size: 12px;
  color: #8e8e93;
}

.ai-type-track {
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.ai-type-fill {
  height: 100%;
  background: linear-gradient(90deg, #4facfe 0%, #00c6ff 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

/* 加载状态 */
.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 20px;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
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

/* 错误状态 */
.fallback-card {
  padding: 32px 20px;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
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

/* 空状态 */
.empty-card {
  padding: 36px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 24px;
  color: #c0c0c0;
  margin-bottom: 8px;
}

.empty-text {
  font-size: 14px;
  color: #8e8e93;
}

/* F-HW-08：单区域独立降级卡片（错误时展示具体原因 + 重试） */
.area-fallback {
  padding: 22px 18px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.area-fallback-text {
  font-size: 13px;
  color: #f56c6c;
  line-height: 1.5;
  max-width: 480px;
  word-break: break-all;
}

.area-retry-btn {
  padding: 6px 16px;
  background: #ffffff;
  color: #4facfe;
  border: 1px solid #4facfe;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.area-retry-btn:hover {
  opacity: 0.85;
}

.footer-tip {
  text-align: center;
  font-size: 11px;
  color: #8e8e93;
  line-height: 1.6;
  margin-top: 24px;
  padding: 0 20px;
}

@media (max-width: 720px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .ai-summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .chart-body {
    height: 160px;
  }

  .bar {
    width: 6px;
  }
}
</style>
