import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { login as apiLogin, logout as apiLogout } from '@/api/auth'
import type { LoginRequest, UserInfo, UserRole } from '@/types/auth'

const TOKEN_KEY = 'cloud-brain.access-token'
const USER_KEY = 'cloud-brain.user'

function readUserFromStorage(): UserInfo | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(sessionStorage.getItem(TOKEN_KEY))
  const userInfo = ref<UserInfo | null>(readUserFromStorage())

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  const isPatient = computed(() => userInfo.value?.roles?.includes('PATIENT') ?? false)
  const isDoctor = computed(() => userInfo.value?.roles?.includes('DOCTOR') ?? false)
  const isAdmin = computed(() => userInfo.value?.roles?.includes('ADMIN') ?? false)

  const primaryRole = computed<UserRole | null>(() => {
    const roles = userInfo.value?.roles
    if (!roles || roles.length === 0) return null
    if (roles.includes('ADMIN')) return 'ADMIN'
    if (roles.includes('DOCTOR')) return 'DOCTOR'
    return 'PATIENT'
  })

  // F-HW-03：保留后端 mustChangePassword 契约字段供审计/将来判断，
  // 但前端不再基于该字段做强制跳转（已在 router beforeEach 中移除该分支）。
  // 若后端后续真正删除该字段，可同步移除本计算属性。
  const mustChangePassword = computed(() => userInfo.value?.mustChangePassword ?? false)

  function hasRole(role: UserRole): boolean {
    return userInfo.value?.roles?.includes(role) ?? false
  }

  function establishSession(token: string, user: UserInfo) {
    accessToken.value = token
    userInfo.value = user
    sessionStorage.setItem(TOKEN_KEY, token)
    sessionStorage.setItem(USER_KEY, JSON.stringify(user))
  }

  function clearSession() {
    accessToken.value = null
    userInfo.value = null
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
  }

  async function login(payload: LoginRequest): Promise<void> {
    const res = await apiLogin(payload)
    const user: UserInfo = {
      userId: res.userId,
      username: res.username,
      roles: res.roles,
      mustChangePassword: res.mustChangePassword,
    }
    establishSession(res.accessToken, user)
  }

  async function logout(): Promise<void> {
    try {
      await apiLogout()
    } catch (e) {
      // ignore
    }
    clearSession()
    ElMessage.success('已退出登录')
  }

  return {
    accessToken,
    userInfo,
    isAuthenticated,
    isPatient,
    isDoctor,
    isAdmin,
    primaryRole,
    mustChangePassword,
    hasRole,
    establishSession,
    clearSession,
    login,
    logout,
  }
})
