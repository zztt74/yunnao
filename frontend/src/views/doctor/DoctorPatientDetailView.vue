<script setup lang="ts">
// 患者详情（医生端）
// 设计来源：roles/12_前端开发AI任务书.md §3.3、product/11_功能需求.md §3.3、§3.4
// 功能：
// - 展示患者基本档案（§3.3：性别、出生日期、联系方式、过敏史、既往史等扩展信息）
// - 诊疗时间线（§3.4：分诊/挂号/就诊/检查检验/电子病历/处方，按时间倒序）
// - §3.5：医生只能在接诊关系成立时查看患者必要信息
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPatientDetail, getPatientTimeline } from '@/api/patient'
import type {
  PatientDetailResponse,
  PatientTimelineEntry,
  TimelineEntryType,
} from '@/types/patient'

const route = useRoute()
const router = useRouter()

const patientId = computed(() => Number(route.params.patientId))

const loading = ref(true)
const loadError = ref('')
const patient = ref<PatientDetailResponse | null>(null)
const timeline = ref<PatientTimelineEntry[]>([])

// 从 query 带回就诊 ID，便于"返回工作台"
const fromEncounterId = computed(() => {
  const v = route.query.encounterId
  return v ? Number(v) : null
})

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const [detail, tl] = await Promise.all([
      getPatientDetail(patientId.value),
      getPatientTimeline(patientId.value),
    ])
    patient.value = detail
    timeline.value = tl
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载患者信息失败'
    console.error('[PatientDetail] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function genderText(gender?: string): string {
  if (gender === 'MALE') return '男'
  if (gender === 'FEMALE') return '女'
  return '--'
}

function formatDate(iso?: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
  } catch {
    return '--'
  }
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '--'
  }
}

const hasAllergy = computed(
  () => patient.value?.allergies && patient.value.allergies !== '无',
)

// 时间线条目样式映射
const entryStyle: Record<
  TimelineEntryType,
  { icon: string; label: string; color: string }
> = {
  TRIAGE: { icon: '分', label: '分诊', color: '#1890ff' },
  APPOINTMENT: { icon: '号', label: '挂号', color: '#67c23a' },
  ENCOUNTER: { icon: '诊', label: '就诊', color: '#9b59b6' },
  EXAMINATION: { icon: '检', label: '检查检验', color: '#e6a23c' },
  MEDICAL_RECORD: { icon: '历', label: '病历', color: '#1abc9c' },
  PRESCRIPTION: { icon: '药', label: '处方', color: '#ec407a' },
}

function goBack() {
  if (fromEncounterId.value) {
    router.push(`/doctor/encounter/${fromEncounterId.value}`)
  } else {
    router.push('/doctor/queue')
  }
}

onMounted(loadData)
</script>

<template>
  <div class="detail-view">
    <div class="page-header">
      <button class="back-btn" @click="goBack">← 返回</button>
      <div class="header-title">患者详情</div>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载患者信息…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadData">重新加载</button>
    </div>

    <template v-else-if="patient">
      <!-- 基本档案 -->
      <div class="profile-card">
        <div class="profile-head">
          <div class="avatar" :class="patient.gender === 'MALE' ? 'male' : 'female'">
            {{ patient.name.charAt(0) }}
          </div>
          <div class="profile-main">
            <div class="profile-name">
              {{ patient.name }}
              <span class="profile-tag">{{ genderText(patient.gender) }} · {{ patient.age }} 岁</span>
            </div>
            <div class="profile-sub">患者编号 {{ patient.id }} · 建档 {{ formatDate(patient.createdAt) }}</div>
          </div>
        </div>

        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">出生日期</span>
            <span class="info-value">{{ formatDate(patient.birthDate) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">联系电话</span>
            <span class="info-value">{{ patient.phone || '--' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">地址</span>
            <span class="info-value">{{ patient.address || '--' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">紧急联系人</span>
            <span class="info-value">
              {{ patient.emergencyContact || '--' }}
              <span v-if="patient.emergencyPhone" class="sub-phone">{{ patient.emergencyPhone }}</span>
            </span>
          </div>
        </div>
      </div>

      <!-- 过敏史 / 既往史（§3.3 扩展信息，§8.3 接诊需记录） -->
      <div class="alert-row">
        <div class="alert-card" :class="{ 'allergy-alert': hasAllergy }">
          <div class="alert-title">过敏史</div>
          <div class="alert-text" :class="{ 'allergy-text': hasAllergy }">
            {{ patient.allergies || '无' }}
          </div>
        </div>
        <div class="alert-card">
          <div class="alert-title">既往史</div>
          <div class="alert-text">{{ patient.medicalHistory || '无' }}</div>
        </div>
      </div>

      <!-- 诊疗时间线（§3.4） -->
      <div class="section">
        <div class="section-title">
          诊疗时间线
          <span class="section-count">{{ timeline.length }}</span>
        </div>

        <div v-if="timeline.length === 0" class="empty-card">
          <div class="empty-icon">--</div>
          <div class="empty-text">该患者暂无历史诊疗记录</div>
        </div>

        <div v-else class="timeline">
          <div
            v-for="(entry, idx) in timeline"
            :key="entry.id"
            class="timeline-item"
          >
            <div class="timeline-axis">
              <div
                class="timeline-dot"
                :style="{ background: entryStyle[entry.type].color }"
              >
                {{ entryStyle[entry.type].icon }}
              </div>
              <div v-if="idx < timeline.length - 1" class="timeline-line" />
            </div>
            <div class="timeline-content">
              <div class="timeline-head">
                <span class="timeline-type" :style="{ color: entryStyle[entry.type].color }">
                  {{ entryStyle[entry.type].label }}
                </span>
                <span class="timeline-title">{{ entry.title }}</span>
                <span v-if="entry.statusLabel" class="timeline-status">{{ entry.statusLabel }}</span>
              </div>
              <div class="timeline-desc">{{ entry.description }}</div>
              <div class="timeline-time">{{ formatDateTime(entry.occurredAt) }}</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-view {
  padding: 16px 16px 24px;
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.back-btn {
  background: none;
  border: none;
  color: #4facfe;
  font-size: 15px;
  cursor: pointer;
  padding: 4px 8px;
}

.back-btn:hover {
  opacity: 0.8;
}

.header-title {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
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
}

.primary-btn:hover {
  opacity: 0.92;
}

.profile-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 18px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  margin-bottom: 14px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 600;
  color: #ffffff;
}

.avatar.male {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
}

.avatar.female {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.profile-name {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-tag {
  font-size: 13px;
  font-weight: 400;
  color: #8e8e93;
  background: #f0f0f0;
  padding: 2px 10px;
  border-radius: 10px;
}

.profile-sub {
  font-size: 13px;
  color: #8e8e93;
  margin-top: 4px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
}

.info-item {
  display: flex;
  font-size: 14px;
  line-height: 1.8;
}

.info-label {
  width: 90px;
  color: #8e8e93;
  flex-shrink: 0;
}

.info-value {
  color: #1a1a1a;
  flex: 1;
}

.sub-phone {
  color: #8e8e93;
  margin-left: 8px;
}

.alert-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
}

.alert-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  border-left: 3px solid #e0e0e0;
}

.alert-card.allergy-alert {
  border-left-color: #f56c6c;
  background: #fff8f8;
}

.alert-title {
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
  margin-bottom: 6px;
}

.alert-text {
  font-size: 14px;
  color: #1a1a1a;
  line-height: 1.6;
}

.allergy-text {
  color: #f56c6c;
  font-weight: 500;
}

.section {
  margin-bottom: 20px;
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
}

.empty-text {
  font-size: 14px;
  color: #8e8e93;
}

.timeline {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 18px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.timeline-item {
  display: flex;
  gap: 14px;
  padding-bottom: 4px;
}

.timeline-axis {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.timeline-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #ffffff;
  flex-shrink: 0;
}

.timeline-line {
  width: 2px;
  flex: 1;
  background: #f0f0f0;
  margin: 4px 0;
  min-height: 24px;
}

.timeline-content {
  flex: 1;
  padding-bottom: 18px;
}

.timeline-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.timeline-type {
  font-size: 12px;
  font-weight: 600;
  padding: 1px 8px;
  background: #f7f7f7;
  border-radius: 4px;
}

.timeline-title {
  font-size: 15px;
  font-weight: 500;
  color: #1a1a1a;
}

.timeline-status {
  font-size: 12px;
  color: #67c23a;
  background: #f0fff4;
  padding: 1px 8px;
  border-radius: 10px;
}

.timeline-desc {
  font-size: 13px;
  color: #4a5568;
  line-height: 1.6;
  margin-bottom: 4px;
}

.timeline-time {
  font-size: 12px;
  color: #b0b0b0;
}
</style>
