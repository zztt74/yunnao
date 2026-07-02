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
    // F-HW-05：从后端 4xx/5xx 响应中提取 message，覆写 axios 默认的
    // "Request failed with status code 400" 之类的英文提示，便于直接展示。
    // 后端 ApiResponse 约定的字段是 code/message/traceId/data。
    const body = error?.response?.data
    if (body && typeof body === 'object' && typeof body.message === 'string' && body.message) {
      // 只在 axios 默认 message 是无意义英文时替换，避免覆盖已有更有用的描述
      const rawMsg = typeof error.message === 'string' ? error.message : ''
      if (/^Request failed with status code \d+$/.test(rawMsg) || !rawMsg) {
        error.message = body.message
      }
    }
    return Promise.reject(error)
  },
)
