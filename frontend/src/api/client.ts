import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15_000,
  headers: {
    Accept: 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('cloud-brain.access-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      const auth = useAuthStore()
      auth.clearSession()
      if (router.currentRoute.value.path !== '/') {
        router.push('/')
      }
    } else if (status === 403) {
      router.push('/forbidden')
    }
    return Promise.reject(error)
  },
)
