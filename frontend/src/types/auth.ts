export type UserRole = 'PATIENT' | 'DOCTOR' | 'ADMIN'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  userId: number
  username: string
  roles: UserRole[]
  mustChangePassword: boolean
  expiresIn: number
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface UserInfo {
  userId: number
  username: string
  roles: UserRole[]
  mustChangePassword: boolean
}
