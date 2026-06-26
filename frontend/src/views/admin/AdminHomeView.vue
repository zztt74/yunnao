<script setup lang="ts">
// 管理首页（管理控制台概览）
// 设计来源：product/11_功能需求.md §15
// 展示：今日核心指标、全院规模、近 7 日就诊趋势、快捷入口
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStatisticsSummary, getStatisticsTrend } from '@/api/admin'
import type { StatisticsSummary, StatisticsTrendItem } from '@/types/admin'

const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const summary = ref<StatisticsSummary | null>(null)
const trend = ref<StatisticsTrendItem[]>([])

const primaryStats = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    { label: '今日挂号量', value: s.todayAppointments, color: '#4facfe' },
    { label: '今日完成就诊', value: s.todayCompletedEncounters, color: '#67c23a' },
    { label: '出诊医生数', value: s.todayActiveDoctors, color: '#9b59b6' },
    { label: '可用设备数', value: s.todayAvailableDevices, color: '#e6a23c' },
    { label: '高优先级分诊', value: s.todayHighPriorityTriages, color: '#f56c6c' },
  ]
})

const secondaryStats = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    { label: '总患者数', value: s.totalPatients },
    { label: '总医生数', value: s.totalDoctors },
    { label: '总科室数', value: s.totalDepartments },
    { label: '总设备数', value: s.totalDevices },
  ]
})

const trendMax = computed(() => {
  let max = 0
  for (const item of trend.value) {
    if (item.appointments > max) max = item.appointments
    if (item.completedEncounters > max) max = item.completedEncounters
  }
  return max || 1
})

function barHeight(value: number): string {
  return `${Math.max(4, (value / trendMax.value) * 100)}%`
}

function formatDay(iso: string): string {
  const parts = iso.split('-')
  if (parts.length < 3) return iso
  return `${parts[1]}/${parts[2]}`
}

const quickLinks = [
  { path: '/admin/schedules', title: '排班管理', desc: '查看与维护门诊排班安排', color: '#4facfe' },
  { path: '/admin/doctors', title: '医生管理', desc: '维护医生信息与执业状态', color: '#9b59b6' },
  { path: '/admin/devices', title: '设备管理', desc: '管理医疗设备与状态流转', color: '#e6a23c' },
  { path: '/admin/statistics', title: '统计驾驶舱', desc: '查看全院运营统计数据', color: '#67c23a' },
]

function goTo(path: string) {
  router.push(path)
}

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const [s, t] = await Promise.all([
      getStatisticsSummary(),
      getStatisticsTrend(7),
    ])
    summary.value = s
    trend.value = t
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载管理首页数据失败'
    console.error('[AdminHome] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="admin-home">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-title">管理首页</div>
      <div class="page-subtitle">云脑医院管理控制台 · 实时运营概览</div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载管理首页数据…</span>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadData">重新加载</button>
    </div>

    <!-- 成功状态 -->
    <template v-else>
      <!-- 今日核心指标 -->
      <div class="section">
        <div class="section-title">今日概览</div>
        <div class="primary-grid">
          <div
            v-for="item in primaryStats"
            :key="item.label"
            class="primary-card"
            :style="{ borderLeftColor: item.color }"
          >
            <div class="primary-value" :style="{ color: item.color }">{{ item.value }}</div>
            <div class="primary-label">{{ item.label }}</div>
          </div>
        </div>
      </div>

      <!-- 全院规模 -->
      <div class="section">
        <div class="section-title">全院规模</div>
        <div class="secondary-grid">
          <div
            v-for="item in secondaryStats"
            :key="item.label"
            class="secondary-card"
          >
            <div class="secondary-value">{{ item.value }}</div>
            <div class="secondary-label">{{ item.label }}</div>
          </div>
        </div>
      </div>

      <!-- 近 7 日趋势 -->
      <div class="section">
        <div class="section-title">近 7 日就诊趋势</div>
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
          <div v-if="trend.length === 0" class="empty-card">
            <div class="empty-icon">--</div>
            <div class="empty-text">暂无趋势数据</div>
          </div>
          <div v-else class="chart-body">
            <div
              v-for="item in trend"
              :key="item.date"
              class="chart-col"
            >
              <div class="chart-bars">
                <div
                  class="bar bar-blue"
                  :style="{ height: barHeight(item.appointments) }"
                  :title="`挂号量 ${item.appointments}`"
                >
                  <span class="bar-value">{{ item.appointments }}</span>
                </div>
                <div
                  class="bar bar-green"
                  :style="{ height: barHeight(item.completedEncounters) }"
                  :title="`完成就诊 ${item.completedEncounters}`"
                >
                  <span class="bar-value">{{ item.completedEncounters }}</span>
                </div>
              </div>
              <div class="chart-label">{{ formatDay(item.date) }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 快捷入口 -->
      <div class="section">
        <div class="section-title">快捷入口</div>
        <div class="quick-list">
          <div
            v-for="item in quickLinks"
            :key="item.title"
            class="quick-row"
            :style="{ borderLeftColor: item.color }"
            @click="goTo(item.path)"
          >
            <div class="quick-info">
              <div class="quick-name">{{ item.title }}</div>
              <div class="quick-desc">{{ item.desc }}</div>
            </div>
            <span class="quick-arrow">→</span>
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
.admin-home {
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

.primary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.primary-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 18px 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  border-left: 4px solid #e0e0e0;
}

.primary-value {
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 6px;
  line-height: 1.1;
}

.primary-label {
  font-size: 13px;
  color: #8e8e93;
}

.secondary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.secondary-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 12px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  text-align: center;
}

.secondary-value {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 4px;
  line-height: 1.1;
}

.secondary-label {
  font-size: 13px;
  color: #8e8e93;
}

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
  gap: 8px;
  height: 180px;
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
  gap: 4px;
  height: 100%;
  width: 100%;
  justify-content: center;
}

.bar {
  width: 14px;
  min-height: 4px;
  border-radius: 4px 4px 0 0;
  position: relative;
  display: flex;
  justify-content: center;
  transition: height 0.3s ease;
}

.bar-blue {
  background: linear-gradient(180deg, #4facfe 0%, #2d8ee0 100%);
}

.bar-green {
  background: linear-gradient(180deg, #67c23a 0%, #4e9f2a 100%);
}

.bar-value {
  position: absolute;
  top: -18px;
  font-size: 12px;
  color: #8e8e93;
  white-space: nowrap;
}

.chart-label {
  margin-top: 8px;
  font-size: 12px;
  color: #8e8e93;
}

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

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  border-left: 3px solid #e0e0e0;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}

.quick-row:active {
  transform: scale(0.99);
}

.quick-row:hover {
  background: #f8fbff;
}

.quick-info {
  flex: 1;
}

.quick-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 2px;
}

.quick-desc {
  font-size: 13px;
  color: #8e8e93;
  line-height: 1.4;
}

.quick-arrow {
  font-size: 16px;
  color: #c0c0c0;
  flex-shrink: 0;
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
  .primary-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .secondary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
