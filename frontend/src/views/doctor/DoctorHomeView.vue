<script setup lang="ts">
// 医生首页（工作台概览）
// 设计来源：roles/12_前端开发AI任务书.md §3.3、product/11_功能需求.md §8
// 展示：当日待诊人数、进行中就诊、今日排班摘要、快捷入口
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useEncounterStore } from '@/stores/encounter'
import {
  getDoctorPendingQueue,
  getDoctorActiveAppointments,
  getDoctorTodayAppointments,
} from '@/api/encounter'
import { getDoctorProfile, getDoctorTodaySchedules } from '@/api/doctor'
import type { AppointmentResponse } from '@/types/appointment'
import type { DoctorProfile } from '@/types/doctor'
import type { ScheduleResponse } from '@/types/appointment'

const router = useRouter()
const auth = useAuthStore()
const encounterStore = useEncounterStore()

const loading = ref(true)
const loadError = ref('')
const pendingList = ref<AppointmentResponse[]>([])
const activeList = ref<AppointmentResponse[]>([])
const todayList = ref<AppointmentResponse[]>([])
const profile = ref<DoctorProfile | null>(null)
const todaySchedules = ref<ScheduleResponse[]>([])

const doctorName = computed(
  () => profile.value?.doctorName || auth.userInfo?.username || '医生',
)
const departmentName = computed(() => profile.value?.departmentName || '')

const pendingCount = computed(() => pendingList.value.length)
const activeCount = computed(() => activeList.value.length)
const todayTotal = computed(() => todayList.value.length)

const quickEntries = [
  {
    path: '/doctor/queue',
    title: '待诊队列',
    desc: '查看并接诊候诊患者',
    color: '#4facfe',
  },
  {
    path: '/doctor/encounters',
    title: '接诊历史',
    desc: '回顾历次就诊记录与诊断',
    color: '#9b59b6',
  },
  {
    path: '/doctor/schedules',
    title: '我的排班',
    desc: '查看门诊排班安排',
    color: '#67c23a',
  },
  {
    path: '/doctor/profile',
    title: '个人信息',
    desc: '维护个人档案与修改密码',
    color: '#e6a23c',
  },
]

function goTo(path: string) {
  router.push(path)
}

function resumeEncounter(appointmentId: number) {
  // 跳转到待诊队列并定位该挂号，由队列页解析就诊
  router.push({ path: '/doctor/queue', query: { appointmentId: String(appointmentId) } })
}

async function loadDashboard() {
  loading.value = true
  loadError.value = ''
  try {
    const [pending, active, today, docProfile, schedules] = await Promise.all([
      getDoctorPendingQueue(),
      getDoctorActiveAppointments(),
      getDoctorTodayAppointments(),
      getDoctorProfile(),
      getDoctorTodaySchedules(),
    ])
    pendingList.value = pending
    activeList.value = active
    todayList.value = today
    profile.value = docProfile
    todaySchedules.value = schedules
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载首页数据失败'
    console.error('[DoctorHome] 加载失败：', e)
  } finally {
    loading.value = false
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

onMounted(() => {
  // 进入首页时清理上一次残留的接诊状态
  encounterStore.reset()
  loadDashboard()
})
</script>

<template>
  <div class="doctor-home">
    <!-- 顶部欢迎卡片 -->
    <div class="welcome-card">
      <div class="welcome-text">
        <div class="welcome-hi">{{ doctorName }} 医生，您好</div>
        <div class="welcome-tip">
          {{ departmentName }} · 今日 {{ todayTotal }} 位挂号，{{ pendingCount }} 位候诊
        </div>
      </div>
      <div class="welcome-icon">云脑</div>
    </div>

    <!-- 概览统计 -->
    <div class="stat-grid">
      <div class="stat-card stat-pending" @click="goTo('/doctor/queue')">
        <div class="stat-value">{{ pendingCount }}</div>
        <div class="stat-label">待诊患者</div>
      </div>
      <div class="stat-card stat-active">
        <div class="stat-value">{{ activeCount }}</div>
        <div class="stat-label">进行中就诊</div>
      </div>
      <div class="stat-card stat-schedule" @click="goTo('/doctor/schedules')">
        <div class="stat-value">{{ todaySchedules.length }}</div>
        <div class="stat-label">今日班次</div>
      </div>
    </div>

    <!-- 加载/错误状态 -->
    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载工作台数据…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadDashboard">重新加载</button>
    </div>

    <template v-else>
      <!-- 进行中就诊（继续接诊入口） -->
      <div class="section">
        <div class="section-title">进行中的就诊</div>
        <div v-if="activeList.length === 0" class="empty-card">
          <div class="empty-icon">✓</div>
          <div class="empty-text">当前没有进行中的就诊</div>
        </div>
        <div v-else class="active-list">
          <div
            v-for="appt in activeList"
            :key="appt.id"
            class="active-item"
          >
            <div class="active-info">
              <div class="active-name">{{ appt.patientName }}</div>
              <div class="active-meta">
                <span class="status-tag" :class="appt.status === 'WAITING_EXAM' ? 'tag-waiting' : 'tag-progress'">
                  {{ appt.status === 'WAITING_EXAM' ? '等待检查' : '接诊中' }}
                </span>
                <span class="active-number">挂号号 {{ appt.appointmentNumber }}</span>
              </div>
            </div>
            <button class="primary-btn sm" @click="resumeEncounter(appt.id)">继续接诊</button>
          </div>
        </div>
      </div>

      <!-- 今日排班摘要 -->
      <div class="section">
        <div class="section-title">今日排班</div>
        <div v-if="todaySchedules.length === 0" class="empty-card">
          <div class="empty-icon">--</div>
          <div class="empty-text">今日暂无排班</div>
        </div>
        <div v-else class="schedule-list">
          <div
            v-for="sch in todaySchedules"
            :key="sch.id"
            class="schedule-item"
          >
            <div class="sch-time">
              {{ formatTime(sch.startTime) }} - {{ formatTime(sch.endTime) }}
            </div>
            <div class="sch-meta">
              <span class="sch-booked">已约 {{ sch.bookedCount }}/{{ sch.maxAppointments }}</span>
              <span class="sch-remaining">剩余 {{ sch.remainingCount }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 常用功能 -->
      <div class="section">
        <div class="section-title">常用功能</div>
        <div class="quick-list">
          <div
            v-for="item in quickEntries"
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
      本平台由 AI 辅助，所有 AI 输出均为候选建议，不作为最终诊断依据<br />
      最终诊疗决定由医生作出
    </div>
  </div>
</template>

<style scoped>
.doctor-home {
  padding: 16px 16px 24px;
  max-width: 960px;
  margin: 0 auto;
}

.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  border-radius: 16px;
  color: #ffffff;
  margin-bottom: 16px;
}

.welcome-hi {
  font-size: 19px;
  font-weight: 600;
  margin-bottom: 4px;
}

.welcome-tip {
  font-size: 14px;
  opacity: 0.9;
}

.welcome-icon {
  width: 48px;
  height: 48px;
  background: rgb(255 255 255 / 25%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  backdrop-filter: blur(10px);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 12px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  text-align: center;
  cursor: pointer;
  transition: transform 0.15s;
}

.stat-card:active {
  transform: scale(0.97);
}

.stat-value {
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-pending .stat-value {
  color: #4facfe;
}

.stat-active .stat-value {
  color: #e6a23c;
}

.stat-schedule .stat-value {
  color: #67c23a;
}

.stat-label {
  font-size: 13px;
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
  margin-bottom: 20px;
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
  margin-bottom: 20px;
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

.primary-btn.sm {
  padding: 6px 14px;
  font-size: 13px;
}

.section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 12px;
  padding-left: 4px;
}

.empty-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 28px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 30px;
  margin-bottom: 8px;
}

.empty-text {
  font-size: 14px;
  color: #8e8e93;
}

.active-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.active-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.active-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 6px;
}

.active-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.tag-progress {
  background: #e6f7ff;
  color: #1890ff;
}

.tag-waiting {
  background: #fff7e6;
  color: #fa8c16;
}

.active-number {
  font-size: 13px;
  color: #8e8e93;
}

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.schedule-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.sch-time {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.sch-meta {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #8e8e93;
}

.sch-remaining {
  color: #67c23a;
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
  border-radius: 12px;
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
  font-size: 12px;
  color: #8e8e93;
  line-height: 1.6;
  margin-top: 24px;
  padding: 0 20px;
}
</style>
