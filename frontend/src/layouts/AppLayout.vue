<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const collapse = ref(false)

interface MenuItem {
  path: string
  label: string
}

const patientMenus: MenuItem[] = [
  { path: '/patient', label: '首页' },
  { path: '/patient/triage', label: '智能分诊' },
  { path: '/patient/appointments', label: '我的挂号' },
  { path: '/patient/medical-records', label: '我的病历' },
  { path: '/patient/examinations', label: '检查检验' },
  { path: '/patient/prescriptions', label: '我的处方' },
  { path: '/patient/profile', label: '个人信息' },
  { path: '/patient/change-password', label: '修改密码' },
]

const doctorMenus: MenuItem[] = [
  { path: '/doctor', label: '工作台' },
  { path: '/doctor/schedules', label: '我的排班' },
  { path: '/doctor/queue', label: '待诊队列' },
  { path: '/doctor/encounters', label: '接诊历史' },
  { path: '/doctor/profile', label: '个人信息' },
  { path: '/doctor/change-password', label: '修改密码' },
]

const adminMenus: MenuItem[] = [
  { path: '/admin', label: '首页概览' },
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/departments', label: '科室管理' },
  { path: '/admin/doctors', label: '医生管理' },
  { path: '/admin/patients', label: '患者管理' },
  { path: '/admin/schedules', label: '排班管理' },
  { path: '/admin/appointments', label: '挂号管理' },
  { path: '/admin/triage', label: '分诊记录' },
  { path: '/admin/devices', label: '设备管理' },
  { path: '/admin/statistics/dashboard', label: '统计驾驶舱' },
  { path: '/admin/logs/login', label: '登录日志' },
  { path: '/admin/logs/operation', label: '操作日志' },
  { path: '/admin/logs/ai-invocation', label: 'AI 调用记录' },
  { path: '/admin/change-password', label: '修改密码' },
]

const menus = computed<MenuItem[]>(() => {
  if (auth.isAdmin) return adminMenus
  if (auth.isDoctor) return doctorMenus
  return patientMenus
})

const pageTitle = computed(() => {
  const active = menus.value.find((m) => route.path.startsWith(m.path))
  return active?.label ?? ''
})

function handleLogout() {
  auth.logout()
  router.push('/')
}

function isActive(path: string): boolean {
  if (path === '/patient' || path === '/doctor' || path === '/admin') {
    return route.path === path
  }
  return route.path.startsWith(path)
}
</script>

<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-left">
        <div class="header-logo">云脑</div>
        <span class="header-brand">智慧云脑诊疗平台</span>
        <button class="collapse-btn" @click="collapse = !collapse">
          {{ collapse ? '»' : '«' }}
        </button>
      </div>
      <div class="header-right">
        <span class="user-name">{{ auth.userInfo?.username }}</span>
        <button class="logout-btn" @click="handleLogout">退出</button>
      </div>
    </header>

    <div class="app-body">
      <aside class="app-sidebar" :class="{ collapsed: collapse }">
        <nav class="sidebar-menu">
          <RouterLink
            v-for="item in menus"
            :key="item.path"
            :to="item.path"
            class="menu-item"
            :class="{ active: isActive(item.path) }"
          >
            <span class="menu-label">{{ item.label }}</span>
          </RouterLink>
        </nav>
      </aside>

      <main class="app-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

.app-header {
  height: 56px;
  background: #6cb6ff;
  border-bottom: 1px solid rgb(255 255 255 / 20%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-logo {
  width: 32px;
  height: 32px;
  background: #4a90d9;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  color: #ffffff;
  flex-shrink: 0;
}

.header-brand {
  font-size: 15px;
  font-weight: 600;
  color: #ffffff;
  margin-right: 6px;
}

.collapse-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: rgb(255 255 255 / 8%);
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: rgb(255 255 255 / 68%);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.collapse-btn:hover {
  background: rgb(255 255 255 / 14%);
  color: #ffffff;
}

.page-title {
  font-size: 14px;
  font-weight: 500;
  color: rgb(255 255 255 / 82%);
  padding-left: 10px;
  border-left: 1px solid rgb(255 255 255 / 15%);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 13px;
  color: rgb(255 255 255 / 82%);
  font-weight: 500;
}

.logout-btn {
  padding: 5px 12px;
  border: 1px solid rgb(255 255 255 / 18%);
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: rgb(255 255 255 / 68%);
  transition: all 0.15s;
}

.logout-btn:hover {
  border-color: #4a90d9;
  color: #4a90d9;
}

.app-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.app-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #ffffff;
  color: #1a2b4a;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  overflow: hidden;
  border-right: 1px solid #000000;
}

.app-sidebar.collapsed {
  width: 0;
  border-right: none;
}

.sidebar-menu {
  flex: 1;
  padding: 0;
  overflow-y: auto;
  overflow-x: hidden;
  width: 220px;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  color: #4a5568;
  text-decoration: none;
  font-size: 13px;
  transition: all 0.15s ease;
  border-left: 3px solid transparent;
  white-space: nowrap;
  overflow: hidden;
  min-height: 20px;
}

.menu-item:hover {
  color: #1a73e8;
  background: #f0f7ff;
}

.menu-item.active {
  color: #1a73e8;
  background: #e3f0ff;
  border-left-color: #1a73e8;
  font-weight: 500;
}

.menu-label {
  flex: 1;
}

.app-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}
</style>
