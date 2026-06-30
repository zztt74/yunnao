<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getPatientInfo } from '@/api/patient'
import type { PatientResponse } from '@/types/patient'

const auth = useAuthStore()
const router = useRouter()

const patientInfo = ref<PatientResponse | null>(null)

const quickEntries = [
  { path: '/patient/triage', title: 'AI 预问诊', desc: '提交症状描述，获取智能分诊建议', color: '#4facfe', icon: 'AI' },
  { path: '/patient/appointments', title: '预约挂号', desc: '查看真实排班并预约医生', color: '#67c23a', icon: '挂' },
  { path: '/patient/timeline', title: '诊疗时间线', desc: '查看挂号、就诊、检查、病历记录', color: '#9b59b6', icon: '线' },
  { path: '/patient/triage-history', title: '分诊记录', desc: '查看历史 AI 分诊结果', color: '#1abc9c', icon: '诊' },
  { path: '/patient/medical-records', title: '我的病历', desc: '查看已确认电子病历', color: '#e6a23c', icon: '历' },
  { path: '/patient/examinations', title: '检查检验', desc: '查看检查申请和结果', color: '#f56c6c', icon: '检' },
  { path: '/patient/prescriptions', title: '我的处方', desc: '查看处方详情', color: '#909399', icon: '方' },
]

function goTo(path: string) {
  router.push(path)
}

onMounted(async () => {
  try {
    patientInfo.value = await getPatientInfo()
  } catch (e) {
    console.error('加载患者信息失败', e)
  }
})
</script>

<template>
  <div class="patient-home">
    <div class="welcome-card">
      <div class="welcome-text">
        <div class="welcome-hi">您好，{{ patientInfo?.name || auth.userInfo?.username }}</div>
        <div class="welcome-tip">请选择需要办理的诊疗服务</div>
      </div>
      <div class="welcome-icon">云脑</div>
    </div>

    <div class="triage-entry" @click="goTo('/patient/triage')">
      <div class="triage-left">
        <div class="triage-title">AI 预问诊</div>
        <div class="triage-desc">先描述症状，再进入挂号和就诊流程</div>
      </div>
      <div class="triage-arrow">›</div>
    </div>

    <div class="section">
      <div class="section-title">常用服务</div>
      <div class="quick-grid">
        <button
          v-for="entry in quickEntries"
          :key="entry.path"
          class="quick-card"
          type="button"
          @click="goTo(entry.path)"
        >
          <span class="quick-icon" :style="{ background: entry.color }">{{ entry.icon }}</span>
          <span class="quick-body">
            <span class="quick-title">{{ entry.title }}</span>
            <span class="quick-desc">{{ entry.desc }}</span>
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.patient-home {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.welcome-card,
.triage-entry,
.section {
  background: #ffffff;
  border-radius: 12px;
  padding: 18px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 8%);
}

.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome-hi {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
}

.welcome-tip {
  margin-top: 6px;
  font-size: 14px;
  color: #6b7280;
}

.welcome-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #e8f3ff;
  color: #1677ff;
  font-weight: 700;
}

.triage-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
}

.triage-title {
  font-size: 18px;
  font-weight: 700;
}

.triage-desc {
  margin-top: 6px;
  font-size: 14px;
  opacity: 0.9;
}

.triage-arrow {
  font-size: 34px;
  line-height: 1;
}

.section-title {
  margin-bottom: 14px;
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.quick-card:hover {
  border-color: #4facfe;
  box-shadow: 0 8px 20px rgb(79 172 254 / 14%);
}

.quick-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  color: #ffffff;
  font-weight: 700;
  flex: 0 0 auto;
}

.quick-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quick-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
}

.quick-desc {
  font-size: 13px;
  color: #6b7280;
}
</style>
