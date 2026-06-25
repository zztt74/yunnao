import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import type {
  LoginRequest,
  LoginResponse,
  ChangePasswordRequest,
} from '@/types/auth'

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const res = await apiClient.post('/auth/login', payload)
  return parseApiResponse(res.data)
}

export async function logout(): Promise<void> {
  const res = await apiClient.post('/auth/logout')
  parseApiResponse(res.data)
}

export async function changePassword(payload: ChangePasswordRequest): Promise<void> {
  const res = await apiClient.post('/auth/change-password', payload)
  parseApiResponse(res.data)
}
