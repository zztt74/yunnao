<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getMyAppointments } from '@/api/appointment'
import { getMyMedicalRecords } from '@/api/medical-record'
import { getMyExaminations } from '@/api/examination'
import { getMyPrescriptions } from '@/api/prescription'
import { getMyTriageRecords } from '@/api/triage'
import type { AppointmentResponse } from '@/types/appointment'
import type { MedicalRecord } from '@/types/medical-record'
import type { ExaminationResponse } from '@/types/examination'
import type { PrescriptionResponse } from '@/types/prescription'
import type { TriageResultResponse } from '@/types/triage'

const router = useRouter()
const loading = ref(false)

type EventType = 'triage' | 'appointment' | 'medical-record' | 'examination' | 'prescription'

interface TimelineEvent {
  id: string
  type: EventType
  /** 事件发生时间（用于排序） */
  occurredAt: string
  title: string
  summary: string
  /** 二级标签，如状态、优先级等 */
  badge?: { text: string; bg: string; color: string }
  /** 跳转目标 */
  route?: { name: string; params?: Record<string, any>; query?: Record<string, any> }
  /** 用于去重的原始对象引用 */
  raw?: any
}

const allEvents = ref<TimelineEvent[]>([])

const typeFilters: Array<{ value: EventType | 'all'; label: string; icon: string }> = [
  { value: 'all', label: '全部', icon: '🗂' },
  { value: 'triage', label: '分诊', icon: '🩺' },
  { value: 'appointment', label: '挂号', icon: '📅' },
  { value: 'medical-record', label: '病历', icon: '📋' },
  { value: 'examination', label: '检查', icon: '🔬' },
  { value: 'prescription', label: '处方', icon: '💊' },
]
const activeType = ref<EventType | 'all'>('all')

const filteredEvents = computed(() => {
  const list =
    activeType.value === 'all'
      ? allEvents.value
      : allEvents.value.filter((e) => e.type === activeType.value)
  return [...list].sort(
    (a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime(),
  )
})

/** 按 yyyy-MM-dd 分组 */
const groupedEvents = computed(() => {
  const map = new Map<string, TimelineEvent[]>()
  for (const ev of filteredEvents.value) {
    const day = dayjs(ev.occurredAt).format('YYYY-MM-DD')
    if (!map.has(day)) map.set(day, [])
    map.get(day)!.push(ev)
  }
  return Array.from(map.entries()).map(([day, list]) => ({ day, list }))
})

/** 类型对应的图标/颜色 */
const typeMeta: Record<
  EventType,
  { icon: string; color: string; bg: string; label: string }
> = {
  triage: { icon: '🩺', color: '#4facfe', bg: '#e3f0ff', label: '分诊' },
  appointment: { icon: '📅', color: '#67c23a', bg: '#f0f9eb', label: '挂号' },
  'medical-record': { icon: '📋', color: '#e6a23c', bg: '#fdf6ec', label: '病历' },
  examination: { icon: '🔬', color: '#f56c6c', bg: '#fef0f0', label: '检查' },
  prescription: { icon: '💊', color: '#909399', bg: '#f4f4f5', label: '处方' },
}

const statusMeta: Record<string, { text: string; bg: string; color: string }> = {
  BOOKED: { text: '待就诊', bg: '#e3f0ff', color: '#1a73e8' },
  COMPLETED: { text: '已完成', bg: '#f6ffed', color: '#389e0d' },
  CANCELLED: { text: '已取消', bg: '#f5f5f5', color: '#8e8e93' },
  IN_PROGRESS: { text: '就诊中', bg: '#fff7e6', color: '#d48806' },
  REVIEWED: { text: '已审核', bg: '#f0f9eb', color: '#67c23a' },
}

async function loadAll() {
  loading.value = true
  const events: TimelineEvent[] = []
  const errors: string[] = []

  const safe = async <T>(
    label: string,
    fn: () => Promise<T[]>,
    transform: (item: T) => TimelineEvent | null,
  ) => {
    try {
      const list = await fn()
      for (const item of list) {
        const ev = transform(item)
        if (ev) events.push(ev)
      }
    } catch (e: any) {
      console.error(`加载 ${label} 失败：`, e)
      errors.push(label)
    }
  }

  await Promise.all([
    safe('分诊', getMyTriageRecords, (t: TriageResultResponse) => ({
      id: `triage-${t.id}`,
      type: 'triage',
      occurredAt: t.createdAt,
      title: `AI 分诊 · ${t.recommendedDepartmentName}`,
      summary: t.reason,
      badge: {
        text:
          t.priority === 'EMERGENCY'
            ? '急诊'
            : t.priority === 'HIGH'
              ? '高'
              : t.priority === 'MEDIUM'
                ? '中'
                : '低',
        bg:
          t.priority === 'EMERGENCY'
            ? '#fff1f0'
            : t.priority === 'HIGH'
              ? '#fff7e6'
              : t.priority === 'MEDIUM'
                ? '#fffbe6'
                : '#f6ffed',
        color:
          t.priority === 'EMERGENCY'
            ? '#cf1322'
            : t.priority === 'HIGH'
              ? '#d4380d'
              : t.priority === 'MEDIUM'
                ? '#d48806'
                : '#389e0d',
      },
      route: { name: 'patient-triage-history' },
      raw: t,
    })),
    safe('挂号', getMyAppointments, (a: AppointmentResponse) => ({
      id: `appt-${a.id}`,
      type: 'appointment',
      occurredAt: a.bookedAt,
      title: `${a.doctorName} 医生 · ${a.departmentName}`,
      summary: `挂号编号：${a.appointmentNumber}`,
      badge: statusMeta[a.status] || { text: a.status, bg: '#f0f0f0', color: '#666' },
      route: { name: 'patient-appointments' },
      raw: a,
    })),
    safe('病历', getMyMedicalRecords, (r: MedicalRecord) => ({
      id: `rec-${r.id}`,
      type: 'medical-record',
      occurredAt: r.encounterDate,
      title: `${r.departmentName} · ${r.doctorName} 医生`,
      summary: r.chiefComplaint,
      badge: { text: '病历', bg: '#fdf6ec', color: '#e6a23c' },
      route: { name: 'patient-medical-records' },
      raw: r,
    })),
    safe('检查检验', getMyExaminations, (e: ExaminationResponse) => ({
      id: `exam-${e.id}`,
      type: 'examination',
      occurredAt: e.reportedAt || e.orderedAt,
      title: e.itemName,
      summary: e.purpose,
      badge: statusMeta[e.status] || { text: e.status, bg: '#f0f0f0', color: '#666' },
      route: { name: 'patient-examinations' },
      raw: e,
    })),
    safe('处方', getMyPrescriptions, (p: PrescriptionResponse) => ({
      id: `rx-${p.id}`,
      type: 'prescription',
      occurredAt: p.confirmedAt || p.createdAt,
      title: `${p.departmentName} · ${p.doctorName} 医生`,
      summary: p.diagnosis,
      badge: { text: '处方', bg: '#f4f4f5', color: '#909399' },
      route: { name: 'patient-prescriptions' },
      raw: p,
    })),
  ])

  allEvents.value = events
  loading.value = false

  if (errors.length) {
    ElMessage.warning(`部分记录加载失败：${errors.join('、')}`)
  }
}

function openEvent(ev: TimelineEvent) {
  // 这里统一跳转到对应列表页；未来可以让 detail 也直接打开
  if (ev.route) router.push(ev.route)
}

function formatDayHeader(day: string): string {
  const d = dayjs(day)
  const today = dayjs()
  if (d.isSame(today, 'day')) return '今天'
  if (d.isSame(today.subtract(1, 'day'), 'day')) return '昨天'
  if (d.isSame(today.subtract(2, 'day'), 'day')) return '前天'
  if (d.year() === today.year()) return d.format('MM-DD')
  return d.format('YYYY-MM-DD')
}

function formatTime(iso: string): string {
  return dayjs(iso).format('HH:mm')
}

const counts = computed(() => {
  const out: Record<EventType | 'all', number> = {
    all: allEvents.value.length,
    triage: 0,
    appointment: 0,
    'medical-record': 0,
    examination: 0,
    prescription: 0,
  }
  for (const ev of allEvents.value) out[ev.type]++
  return out
})

onMounted(() => {
  loadAll()
})
</script>

<template>
  <div class="timeline-page">
    <div class="page-header">
      <h1 class="page-title">诊疗时间线</h1>
      <div class="page-sub">汇总您所有的诊疗事件</div>
    </div>

    <!-- 类型筛选 -->
    <div class="type-bar">
      <div
        v-for="opt in typeFilters"
        :key="opt.value"
        class="type-chip"
        :class="{ active: activeType === opt.value }"
        @click="activeType = opt.value"
      >
        <span class="chip-icon">{{ opt.icon }}</span>
        <span class="chip-label">{{ opt.label }}</span>
        <span class="chip-count">{{ counts[opt.value] }}</span>
      </div>
    </div>

    <div v-loading="loading" class="list-wrap">
      <div v-if="!loading && allEvents.length === 0" class="empty-state">
        <div class="empty-icon">📅</div>
        <div class="empty-text">暂无诊疗事件</div>
        <div class="empty-tip">完成问诊、挂号或就诊后会自动汇总</div>
      </div>

      <div v-else class="timeline-list">
        <div v-for="group in groupedEvents" :key="group.day" class="day-group">
          <div class="day-header">
            <span class="day-text">{{ formatDayHeader(group.day) }}</span>
            <span class="day-meta">{{ group.list.length }} 项</span>
          </div>
          <div class="day-events">
            <div
              v-for="ev in group.list"
              :key="ev.id"
              class="event-card"
              :style="{ borderLeftColor: typeMeta[ev.type].color }"
              @click="openEvent(ev)"
            >
              <div class="event-icon" :style="{ background: typeMeta[ev.type].bg }">
                {{ typeMeta[ev.type].icon }}
              </div>
              <div class="event-content">
                <div class="event-top">
                  <span class="event-type" :style="{ color: typeMeta[ev.type].color }">
                    {{ typeMeta[ev.type].label }}
                  </span>
                  <span
                    v-if="ev.badge"
                    class="event-badge"
                    :style="{
                      background: ev.badge.bg,
                      color: ev.badge.color,
                    }"
                  >
                    {{ ev.badge.text }}
                  </span>
                </div>
                <div class="event-title">{{ ev.title }}</div>
                <div class="event-summary">{{ ev.summary }}</div>
                <div class="event-time">{{ formatTime(ev.occurredAt) }}</div>
              </div>
              <div class="event-arrow">›</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-page {
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  min-height: 100%;
}

.page-header {
  padding: 18px 16px 12px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #fff;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-sub {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.85;
}

.type-bar {
  display: flex;
  gap: 8px;
  padding: 12px 12px 0;
  overflow-x: auto;
  background: #f5f7fa;
  position: sticky;
  top: 0;
  z-index: 1;
  -webkit-overflow-scrolling: touch;
}

.type-bar::-webkit-scrollbar {
  display: none;
}

.type-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #fff;
  border-radius: 16px;
  font-size: 12px;
  color: #475569;
  border: 1px solid #e2e8f0;
  white-space: nowrap;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
}

.type-chip.active {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 2px 6px rgb(79 172 254 / 30%);
}

.chip-count {
  font-size: 11px;
  padding: 1px 6px;
  background: rgb(0 0 0 / 8%);
  border-radius: 8px;
  font-weight: 600;
}

.type-chip.active .chip-count {
  background: rgb(255 255 255 / 25%);
}

.list-wrap {
  flex: 1;
  padding: 12px 12px 24px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #8e8e93;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 15px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 6px;
}

.empty-tip {
  font-size: 12px;
  color: #8e8e93;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.day-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.day-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
}

.day-text {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.day-meta {
  font-size: 11px;
  color: #8e8e93;
}

.day-events {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 4%);
  border-left: 3px solid #4facfe;
  cursor: pointer;
  transition: transform 0.15s;
}

.event-card:active {
  transform: scale(0.99);
}

.event-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.event-content {
  flex: 1;
  min-width: 0;
}

.event-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.event-type {
  font-size: 11px;
  font-weight: 600;
}

.event-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.event-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.event-summary {
  font-size: 12px;
  color: #8e8e93;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 4px;
}

.event-time {
  font-size: 11px;
  color: #c0c4cc;
}

.event-arrow {
  font-size: 18px;
  color: #c0c4cc;
  flex-shrink: 0;
}
</style>
