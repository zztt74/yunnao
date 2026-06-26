<script setup lang="ts">
// 我的排班
// 设计来源：roles/12_前端开发AI任务书.md §3.3、product/11_功能需求.md §8
// 展示医生未来 7 天排班列表，按日期分组，含号源剩余与状态
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDoctorSchedules, getDoctorProfile } from '@/api/doctor'
import type { ScheduleResponse } from '@/types/appointment'
import type { DoctorProfile } from '@/types/doctor'

const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const profile = ref<DoctorProfile | null>(null)
const schedules = ref<ScheduleResponse[]>([])

// 按日期分组
const groupedSchedules = computed(() => {
  const map = new Map<string, ScheduleResponse[]>()
  for (const s of schedules.value) {
    const list = map.get(s.scheduleDate) ?? []
    list.push(s)
    map.set(s.scheduleDate, list)
  }
  return Array.from(map.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([date, list]) => ({ date, list }))
})

function formatDateLabel(date: string): string {
  try {
    const dt = new Date(date + 'T00:00:00')
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const diffDays = Math.round(
      (dt.getTime() - today.getTime()) / (24 * 60 * 60 * 1000),
    )
    const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][dt.getDay()]
    const md = `${dt.getMonth() + 1}月${dt.getDate()}日`
    if (diffDays === 0) return `今天 · ${md} · ${weekday}`
    if (diffDays === 1) return `明天 · ${md} · ${weekday}`
    return `${md} · ${weekday}`
  } catch {
    return date
  }
}

function formatTime(iso: string): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '--'
  }
}

function statusText(status: string): string {
  switch (status) {
    case 'AVAILABLE':
      return '可预约'
    case 'FULL':
      return '已满'
    case 'CANCELLED':
      return '已取消'
    case 'COMPLETED':
      return '已结束'
    default:
      return status
  }
}

function statusClass(status: string): string {
  switch (status) {
    case 'AVAILABLE':
      return 'tag-available'
    case 'FULL':
      return 'tag-full'
    case 'CANCELLED':
      return 'tag-cancelled'
    case 'COMPLETED':
      return 'tag-completed'
    default:
      return ''
  }
}

async function loadSchedules() {
  loading.value = true
  loadError.value = ''
  try {
    const [docProfile, list] = await Promise.all([
      getDoctorProfile(),
      getDoctorSchedules(),
    ])
    profile.value = docProfile
    schedules.value = list
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载排班失败'
    console.error('[DoctorSchedules] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function goQueue() {
  router.push('/doctor/queue')
}

onMounted(loadSchedules)
</script>

<template>
  <div class="schedules-view">
    <div class="page-header">
      <div class="header-title">我的排班</div>
      <div class="header-sub" v-if="profile">
        {{ profile.doctorName }} · {{ profile.departmentName }} · {{ profile.title }}
      </div>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载排班…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadSchedules">重新加载</button>
    </div>

    <div v-else-if="groupedSchedules.length === 0" class="empty-card">
      <div class="empty-icon">--</div>
      <div class="empty-text">近期暂无排班安排</div>
    </div>

    <template v-else>
      <div
        v-for="group in groupedSchedules"
        :key="group.date"
        class="date-group"
      >
        <div class="date-label">{{ formatDateLabel(group.date) }}</div>
        <div class="schedule-cards">
          <div
            v-for="sch in group.list"
            :key="sch.id"
            class="schedule-card"
            :class="{ disabled: sch.status !== 'AVAILABLE' }"
          >
            <div class="card-top">
              <div class="card-time">
                {{ formatTime(sch.startTime) }} - {{ formatTime(sch.endTime) }}
              </div>
              <span class="status-tag" :class="statusClass(sch.status)">
                {{ statusText(sch.status) }}
              </span>
            </div>
            <div class="card-meta">
              <span class="meta-item">
                已约 <strong>{{ sch.bookedCount }}</strong> / {{ sch.maxAppointments }}
              </span>
              <span
                class="meta-item remaining"
                :class="{ 'remaining-zero': sch.remainingCount === 0 }"
              >
                剩余 <strong>{{ sch.remainingCount }}</strong>
              </span>
            </div>
            <div class="card-bar">
              <div
                class="bar-fill"
                :style="{ width: `${Math.min(100, (sch.bookedCount / sch.maxAppointments) * 100)}%` }"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="action-card">
        <div class="action-text">查看今日候诊患者</div>
        <button class="primary-btn" @click="goQueue">前往待诊队列</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.schedules-view {
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

.fallback-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 32px 20px;
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

.date-group {
  margin-bottom: 20px;
}

.date-label {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 10px;
  padding-left: 4px;
}

.schedule-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.schedule-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.schedule-card.disabled {
  opacity: 0.65;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.card-time {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.tag-available {
  background: #e6f7ff;
  color: #1890ff;
}

.tag-full {
  background: #fff1f0;
  color: #f56c6c;
}

.tag-cancelled {
  background: #f5f5f5;
  color: #8e8e93;
}

.tag-completed {
  background: #f0f0f0;
  color: #8e8e93;
}

.card-meta {
  display: flex;
  gap: 18px;
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 10px;
}

.meta-item strong {
  color: #1a1a1a;
  font-weight: 600;
}

.meta-item.remaining strong {
  color: #67c23a;
}

.remaining-zero strong {
  color: #f56c6c !important;
}

.card-bar {
  height: 4px;
  background: #f0f0f0;
  border-radius: 2px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #4facfe 0%, #00c6ff 100%);
  border-radius: 2px;
  transition: width 0.3s;
}

.action-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 20px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  margin-top: 8px;
}

.action-text {
  font-size: 15px;
  color: #1a1a1a;
  font-weight: 500;
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
</style>
