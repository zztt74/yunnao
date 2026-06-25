<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 底部 Tab 导航
const tabs = [
  { path: '/patient', label: '首页' },
  { path: '/patient/triage', label: '问诊' },
  { path: '/patient/appointments', label: '挂号' },
  { path: '/patient/profile', label: '我的' },
]

const activeTab = computed(() => {
  const matched = tabs
    .filter((t) => route.path === t.path || route.path.startsWith(t.path + '/'))
    .sort((a, b) => b.path.length - a.path.length)
  return matched[0]?.path ?? '/patient'
})

function goToTab(path: string) {
  router.push(path)
}

function handleLogout() {
  auth.logout()
  router.push('/')
}

const pageTitle = computed(() => {
  const map: Record<string, string> = {
    '/patient': '智慧云脑',
    '/patient/triage': '智能分诊',
    '/patient/appointments': '我的挂号',
    '/patient/medical-records': '我的病历',
    '/patient/examinations': '检查检验',
    '/patient/prescriptions': '我的处方',
    '/patient/profile': '个人信息',
  }
  return map[route.path] ?? '智慧云脑'
})
</script>

<template>
  <div class="mobile-stage">
    <div class="mobile-container">
      <!-- 顶部导航栏 -->
      <header class="mobile-header">
        <span class="header-title">{{ pageTitle }}</span>
        <div class="header-right">
          <span class="user-name">{{ auth.userInfo?.username }}</span>
          <button class="logout-btn" @click="handleLogout">退出</button>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="mobile-content">
        <RouterView />
      </main>

      <!-- 底部 Tab 栏 -->
      <nav class="mobile-tabbar">
        <button
          v-for="tab in tabs"
          :key="tab.path"
          class="tab-item"
          :class="{ active: activeTab === tab.path }"
          @click="goToTab(tab.path)"
        >
          <span class="tab-label">{{ tab.label }}</span>
        </button>
      </nav>
    </div>
  </div>
</template>

<style scoped>
.mobile-stage {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

/* 移动端容器：居中、固定宽度 */
.mobile-container {
  position: relative;
  width: 375px;
  height: 100vh;
  max-height: 812px;
  background: #f5f5f7;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 0 20px rgb(0 0 0 / 8%);
}

/* 顶部导航栏 */
.mobile-header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 12px;
  color: #8e8e93;
}

.logout-btn {
  padding: 3px 10px;
  border: 1px solid #d0d0d0;
  background: #ffffff;
  border-radius: 12px;
  cursor: pointer;
  font-size: 11px;
  color: #8e8e93;
  transition: all 0.15s;
}

.logout-btn:hover {
  border-color: #1a73e8;
  color: #1a73e8;
}

/* 内容区 */
.mobile-content {
  flex: 1;
  overflow-y: auto;
  background: #f5f5f7;
  -webkit-overflow-scrolling: touch;
}

.mobile-content::-webkit-scrollbar {
  width: 0;
  display: none;
}

/* 底部 Tab 栏 */
.mobile-tabbar {
  height: 56px;
  display: flex;
  background: #ffffff;
  border-top: 1px solid #e5e5e7;
  flex-shrink: 0;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 12px;
  color: #8e8e93;
  transition: color 0.15s;
  padding: 0;
}

.tab-item.active {
  color: #1a73e8;
  font-weight: 500;
}

.tab-label {
  position: relative;
}

.tab-item.active .tab-label::after {
  content: '';
  position: absolute;
  bottom: -6px;
  left: 50%;
  transform: translateX(-50%);
  width: 16px;
  height: 2px;
  background: #1a73e8;
  border-radius: 1px;
}

/* 响应式 */
@media (max-width: 420px) {
  .mobile-stage {
    background: #ffffff;
  }
  .mobile-container {
    width: 100%;
    height: 100vh;
    max-height: none;
    box-shadow: none;
  }
}
</style>
