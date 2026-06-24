<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

function goBack() {
  if (auth.isAuthenticated) {
    if (auth.isPatient) router.push('/patient')
    else if (auth.isDoctor) router.push('/doctor')
    else if (auth.isAdmin) router.push('/admin')
    else router.push('/')
  } else {
    router.push('/')
  }
}
</script>

<template>
  <div class="forbidden-page">
    <div class="forbidden-card">
      <div class="forbidden-code">403</div>
      <h1 class="forbidden-title">无访问权限</h1>
      <p class="forbidden-desc">抱歉，您没有权限访问此页面。</p>
      <p class="forbidden-tip">如需访问，请联系系统管理员或切换具有相应权限的账号。</p>
      <button class="back-btn" @click="goBack">返回首页</button>
    </div>
  </div>
</template>

<style scoped>
.forbidden-page {
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8edf5 100%);
  padding: 20px;
}

.forbidden-card {
  max-width: 480px;
  width: 100%;
  background: #ffffff;
  border-radius: 16px;
  padding: 48px 40px;
  text-align: center;
  box-shadow: 0 12px 40px rgb(0 0 0 / 8%);
}

.forbidden-code {
  font-size: 80px;
  font-weight: 700;
  line-height: 1;
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 20px;
  letter-spacing: 0.05em;
}

.forbidden-title {
  margin: 0 0 12px;
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
}

.forbidden-desc {
  margin: 0 0 8px;
  font-size: 15px;
  color: #475569;
  line-height: 1.6;
}

.forbidden-tip {
  margin: 0 0 28px;
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.6;
}

.back-btn {
  padding: 12px 36px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.back-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgb(79 172 254 / 35%);
}
</style>
