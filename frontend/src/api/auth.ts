import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import type {
  LoginRequest,
  LoginResponse,
  ChangePasswordRequest,
} from '@/types/auth'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  // MOCK：后端就绪前直接返回虚构登录结果（与 StageZeroHomeView 的演示一致）
  console.warn('[MOCK] /api/auth/login 后端未就绪，使用本地虚构演示数据')
  await delay(800)
  // 简易规则：根据账号前缀模拟角色
  const username = payload.username.toLowerCase()
  let roles: ('PATIENT' | 'DOCTOR' | 'ADMIN')[] = ['PATIENT']
  if (username === 'admin') roles = ['ADMIN']
  else if (username === 'doctor') roles = ['DOCTOR']
  // 校验密码长度
  if (!payload.password || payload.password.length < 6) {
    throw new Error('密码长度不少于 6 位')
  }
  return {
    accessToken: 'mock-token-' + Date.now(),
    tokenType: 'Bearer',
    userId: 1,
    username: payload.username,
    roles,
    mustChangePassword: false,
    expiresIn: 7200,
  }
  // 后端就绪后请替换为：
  // const res = await apiClient.post('/auth/login', payload)
  // return parseApiResponse(res.data)
}

export async function logout(): Promise<void> {
  // MOCK：后端就绪前直接返回
  console.warn('[MOCK] /api/auth/logout 后端未就绪，本地清理会话')
  await delay(200)
  return
  // 后端就绪后请替换为：
  // const res = await apiClient.post('/auth/logout')
  // parseApiResponse(res.data)
}

export async function changePassword(payload: ChangePasswordRequest): Promise<void> {
  // MOCK：后端就绪前演示用：仅校验原密码非空、新密码长度 ≥ 6 且与原密码不同
  console.warn('[MOCK] /api/auth/change-password 后端未就绪，使用本地虚构演示数据')
  await delay(600)
  if (!payload.oldPassword || payload.oldPassword.length < 1) {
    throw new Error('请输入原密码')
  }
  if (!payload.newPassword || payload.newPassword.length < 6) {
    throw new Error('新密码至少 6 位')
  }
  if (payload.oldPassword === payload.newPassword) {
    throw new Error('新密码不能与原密码相同')
  }
  // 校验通过
  return
  // 后端就绪后请替换为：
  // const res = await apiClient.post('/auth/change-password', payload)
  // parseApiResponse(res.data)
}
