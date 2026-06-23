import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type UserRole = 'PATIENT' | 'DOCTOR' | 'ADMIN'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(sessionStorage.getItem('cloud-brain.access-token'))
  const role = ref<UserRole | null>(
    sessionStorage.getItem('cloud-brain.role') as UserRole | null,
  )

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function establishSession(token: string, nextRole: UserRole) {
    accessToken.value = token
    role.value = nextRole
    sessionStorage.setItem('cloud-brain.access-token', token)
    sessionStorage.setItem('cloud-brain.role', nextRole)
  }

  function clearSession() {
    accessToken.value = null
    role.value = null
    sessionStorage.removeItem('cloud-brain.access-token')
    sessionStorage.removeItem('cloud-brain.role')
  }

  return {
    accessToken,
    role,
    isAuthenticated,
    establishSession,
    clearSession,
  }
})
