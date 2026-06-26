<script setup lang="ts">
// 待诊队列
// 设计来源：roles/12_前端开发AI任务书.md §3.3、product/11_功能需求.md §8.3、product/12_业务流程与状态机.md §6
// 功能：
// - 列出当前医生名下 BOOKED 挂号（待诊）与 IN_PROGRESS/WAITING_EXAM 挂号（进行中）
// - 接诊：BOOKED → 开始接诊（CREATED → IN_PROGRESS，同步挂号），进入工作台
// - 继续接诊：进行中就诊直接进入工作台
// - 展示患者必要信息（过敏史、既往史，§3.5）
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDoctorPendingQueue,
  getDoctorActiveAppointments,
  startPatientEncounter,
  getEncounterByAppointmentId,
} from '@/api/encounter'
import { mockPatientSummaries, type MockPatientSummary } from '@/api/mock/doctor-mock'
import { useEncounterStore } from '@/stores/encounter'
import type { AppointmentResponse } from '@/types/appointment'

const router = useRouter()
const route = useRoute()
const encounterStore = useEncounterStore()

const loading = ref(true)
const loadError = ref('')
const pendingList = ref<AppointmentResponse[]>([])
const activeList = ref<AppointmentResponse[]>([])
const startingId = ref<number | null>(null)
const continuingId = ref<number | null>(null)

const pendingCount = computed(() => pendingList.value.length)
const activeCount = computed(() => activeList.value.length)

function getPatientSummary(patientId: number): MockPatientSummary | undefined {
  return mockPatientSummaries[patientId]
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

function genderText(gender?: string): string {
  if (gender === 'MALE') return '男'
  if (gender === 'FEMALE') return '女'
  return '--'
}

/** 查看患者档案（含诊疗时间线，§3.3/§3.4） */
function goPatientDetail(patientId: number) {
  router.push({ name: 'doctor-patient-detail', params: { patientId } })
}

async function loadQueue() {
  loading.value = true
  loadError.value = ''
  try {
    const [pending, active] = await Promise.all([
      getDoctorPendingQueue(),
      getDoctorActiveAppointments(),
    ])
    pendingList.value = pending
    activeList.value = active

    // 来自首页「继续接诊」的跳转：自动定位并继续
    const apptIdQuery = route.query.appointmentId
    if (apptIdQuery) {
      const apptId = Number(apptIdQuery)
      const appt = active.find((a) => a.id === apptId)
      if (appt) {
        await continueEncounter(appt)
      }
    }
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载队列失败'
    console.error('[DoctorQueue] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

/** 开始接诊（BOOKED 挂号 → 创建 Encounter → 进入工作台） */
async function startEncounter(appt: AppointmentResponse) {
  try {
    await ElMessageBox.confirm(
      `确认开始接诊患者「${appt.patientName}」吗？接诊后将进入诊疗工作台。`,
      '开始接诊确认',
      { confirmButtonText: '开始接诊', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return // 取消
  }

  startingId.value = appt.id
  try {
    const enc = await startPatientEncounter({ appointmentId: appt.id })
    encounterStore.setActiveEncounter(enc)
    ElMessage.success(`已开始接诊：${appt.patientName}`)
    router.push(`/doctor/encounter/${enc.id}`)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '开始接诊失败'
    ElMessage.error(msg)
  } finally {
    startingId.value = null
  }
}

/** 继续接诊（进行中就诊 → 进入工作台） */
async function continueEncounter(appt: AppointmentResponse) {
  continuingId.value = appt.id
  try {
    let enc = encounterStore.activeEncounter
    if (!enc || enc.appointmentId !== appt.id) {
      const found = await getEncounterByAppointmentId(appt.id)
      if (!found) {
        ElMessage.error('未找到对应的就诊记录')
        return
      }
      enc = found
      encounterStore.setActiveEncounter(enc)
    }
    router.push(`/doctor/encounter/${enc.id}`)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '继续接诊失败'
    ElMessage.error(msg)
  } finally {
    continuingId.value = null
  }
}

onMounted(loadQueue)
</script>

<template>
  <div class="queue-view">
    <div class="page-header">
      <div class="header-title">待诊队列</div>
      <div class="header-sub">
        待诊 {{ pendingCount }} 位 · 进行中 {{ activeCount }} 位
      </div>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载待诊队列…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadQueue">重新加载</button>
    </div>

    <template v-else>
      <!-- 进行中就诊 -->
      <div v-if="activeList.length > 0" class="section">
        <div class="section-title">
          进行中的就诊
          <span class="section-count">{{ activeCount }}</span>
        </div>
        <div class="card-list">
          <div
            v-for="appt in activeList"
            :key="appt.id"
            class="patient-card active-card"
          >
            <div class="card-head">
              <div
                class="patient-name clickable"
                @click="goPatientDetail(appt.patientId)"
              >
                {{ appt.patientName }}
                <span class="name-link-hint">查看档案 →</span>
              </div>
              <span
                class="status-tag"
                :class="appt.status === 'WAITING_EXAM' ? 'tag-waiting' : 'tag-progress'"
              >
                {{ appt.status === 'WAITING_EXAM' ? '等待检查' : '接诊中' }}
              </span>
            </div>
            <div class="card-meta">
              <span>挂号号 {{ appt.appointmentNumber }}</span>
              <span>预约 {{ formatTime(appt.bookedAt) }}</span>
            </div>
            <div class="card-actions">
              <button
                class="primary-btn"
                :disabled="continuingId === appt.id"
                @click="continueEncounter(appt)"
              >
                <span v-if="continuingId === appt.id" class="btn-spinner" />
                {{ continuingId === appt.id ? '进入中…' : '继续接诊' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 待诊列表 -->
      <div class="section">
        <div class="section-title">
          待诊患者
          <span class="section-count">{{ pendingCount }}</span>
        </div>
        <div v-if="pendingList.length === 0" class="empty-card">
          <div class="empty-icon">✓</div>
          <div class="empty-text">当前没有待诊患者</div>
        </div>
        <div v-else class="card-list">
          <div
            v-for="appt in pendingList"
            :key="appt.id"
            class="patient-card"
          >
            <div class="card-head">
              <div
                class="patient-name clickable"
                @click="goPatientDetail(appt.patientId)"
              >
                {{ appt.patientName }}
                <span class="name-link-hint">查看档案 →</span>
              </div>
              <span class="status-tag tag-booked">待诊</span>
            </div>
            <div class="card-meta">
              <span>挂号号 {{ appt.appointmentNumber }}</span>
              <span>预约 {{ formatTime(appt.bookedAt) }}</span>
            </div>

            <!-- 患者必要信息（接诊前可见，§3.5） -->
            <div v-if="getPatientSummary(appt.patientId)" class="patient-info">
              <div class="info-row">
                <span class="info-label">性别/年龄</span>
                <span class="info-value">
                  {{ genderText(getPatientSummary(appt.patientId)?.gender) }} ·
                  {{ getPatientSummary(appt.patientId)?.age }} 岁
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">过敏史</span>
                <span
                  class="info-value"
                  :class="{ 'allergy-warn': getPatientSummary(appt.patientId)?.allergies && getPatientSummary(appt.patientId)?.allergies !== '无' }"
                >
                  {{ getPatientSummary(appt.patientId)?.allergies || '无' }}
                </span>
              </div>
              <div class="info-row">
                <span class="info-label">既往史</span>
                <span class="info-value">{{ getPatientSummary(appt.patientId)?.medicalHistory || '无' }}</span>
              </div>
            </div>

            <div class="card-actions">
              <button
                class="primary-btn"
                :disabled="startingId === appt.id"
                @click="startEncounter(appt)"
              >
                <span v-if="startingId === appt.id" class="btn-spinner" />
                {{ startingId === appt.id ? '接诊中…' : '开始接诊' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.queue-view {
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
  transition: opacity 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.primary-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgb(255 255 255 / 40%);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.section {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 12px;
  padding-left: 4px;
}

.section-count {
  font-size: 13px;
  color: #8e8e93;
  background: #f0f0f0;
  padding: 1px 8px;
  border-radius: 10px;
  font-weight: 500;
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
  color: #67c23a;
}

.empty-text {
  font-size: 14px;
  color: #8e8e93;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.patient-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.active-card {
  border-left: 3px solid #e6a23c;
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

.patient-name.clickable {
  cursor: pointer;
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  transition: color 0.15s;
}

.patient-name.clickable:hover {
  color: #4facfe;
}

.name-link-hint {
  font-size: 12px;
  font-weight: 400;
  color: #4facfe;
  opacity: 0;
  transition: opacity 0.15s;
}

.patient-name.clickable:hover .name-link-hint {
  opacity: 1;
}

.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.tag-booked {
  background: #e6f7ff;
  color: #1890ff;
}

.tag-progress {
  background: #fff7e6;
  color: #fa8c16;
}

.tag-waiting {
  background: #fff1f0;
  color: #f56c6c;
}

.card-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #8e8e93;
  margin-bottom: 12px;
}

.patient-info {
  background: #fafbfc;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  font-size: 13px;
  line-height: 1.8;
}

.info-label {
  width: 70px;
  color: #8e8e93;
  flex-shrink: 0;
}

.info-value {
  color: #1a1a1a;
  flex: 1;
}

.allergy-warn {
  color: #f56c6c;
  font-weight: 500;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
