<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { getPatientInfo } from '@/api/patient'
import type { PatientResponse } from '@/types/patient'

const auth = useAuthStore()
const router = useRouter()

const patientInfo = ref<PatientResponse | null>(null)

const quickEntries = [
  { path: '/patient/triage', title: 'AI 智能问诊', desc: '描述症状获取分诊建议', color: '#4facfe', icon: '🩺' },
  { path: '/patient/appointments', title: '在线挂号', desc: '选择科室和医生挂号', color: '#67c23a', icon: '📅' },
  { path: '/patient/timeline', title: '诊疗时间线', desc: '查看完整就诊记录', color: '#9b59b6', icon: '🕐' },
  { path: '/patient/triage-history', title: '分诊历史', desc: '历次 AI 分诊记录', color: '#1abc9c', icon: '📝' },
  { path: '/patient/medical-records', title: '我的病历', desc: '查看历史诊疗记录', color: '#e6a23c', icon: '📋' },
  { path: '/patient/examinations', title: '检查检验', desc: '查看检查检验结果', color: '#f56c6c', icon: '🔬' },
  { path: '/patient/prescriptions', title: '我的处方', desc: '查看处方详情', color: '#909399', icon: '💊' },
]

function goTo(path: string) {
  router.push(path)
}

onMounted(async () => {
  // 拉取真实姓名等信息（mock 阶段不会失败）
  try {
    patientInfo.value = await getPatientInfo()
  } catch (e) {
    console.error('加载患者信息失败：', e)
  }
})
</script>

<template>
  <div class="patient-home">
    <!-- 顶部欢迎卡片 -->
    <div class="welcome-card">
      <div class="welcome-text">
        <div class="welcome-hi">您好，{{ patientInfo?.name || auth.userInfo?.username }}</div>
        <div class="welcome-tip">今天也要好好照顾自己</div>
      </div>
      <div class="welcome-icon">云脑</div>
    </div>

    <!-- AI 问诊入口 -->
    <div class="triage-entry" @click="goTo('/patient/triage')">
      <div class="triage-left">
        <div class="triage-title">AI 智能分诊</div>
        <div class="triage-desc">描述症状，10 秒获取推荐科室</div>
      </div>
      <div class="triage-arrow">→</div>
    </div>

    <!-- 快捷功能 -->
    <div class="section">
      <div class="section-title">常用功能</div>
      <div class="quick-grid">
        <div
          v-for="item in quickEntries"
          :key="item.path"
          class="quick-item"
          @click="goTo(item.path)"
        >
          <div class="quick-icon" :style="{ background: item.color }">{{ item.icon }}</div>
          <div class="quick-name">{{ item.title }}</div>
          <div class="quick-desc">{{ item.desc }}</div>
        </div>
      </div>
    </div>

    <!-- 底部提示 -->
    <div class="footer-tip">
      本平台由 AI 辅助，所有诊断结果仅供参考<br />
      具体诊疗请以医生意见为准
    </div>
  </div>
</template>

<style scoped>
.patient-home {
  padding: 16px 16px 24px;
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
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
}

.welcome-tip {
  font-size: 13px;
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
  font-size: 13px;
  font-weight: 700;
  backdrop-filter: blur(10px);
}

.triage-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  background: #ffffff;
  border-radius: 14px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
  transition: transform 0.15s;
}

.triage-entry:active {
  transform: scale(0.99);
}

.triage-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.triage-desc {
  font-size: 12px;
  color: #8e8e93;
}

.triage-arrow {
  font-size: 20px;
  color: #1a73e8;
  font-weight: 300;
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

.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.quick-item {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  cursor: pointer;
  transition: transform 0.15s;
}

.quick-item:active {
  transform: scale(0.97);
}

.quick-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.quick-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.quick-desc {
  font-size: 11px;
  color: #8e8e93;
  line-height: 1.4;
}

.footer-tip {
  text-align: center;
  font-size: 11px;
  color: #8e8e93;
  line-height: 1.6;
  margin-top: 24px;
  padding: 0 20px;
}
</style>
