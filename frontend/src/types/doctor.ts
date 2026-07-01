// 医生类型定义
// 设计来源：product/11_功能需求.md §4 科室与医生管理、§2 用户认证与权限管理

export type DoctorStatus = 'ACTIVE' | 'DISABLED'

// 医生个人信息（§4.3：职称、擅长方向和简介；§2.3：密码修改适用于所有角色）
export interface DoctorProfile {
  doctorId: number
  doctorName: string
  title: string
  departmentId: number
  departmentName: string
  gender: 'MALE' | 'FEMALE'
  phone: string
  email: string
  // 擅长方向（§4.3）
  specialty: string
  // 个人简介（§4.3）
  introduction: string
  status: DoctorStatus
  createdAt: string
  updatedAt: string
}

// 医生可编辑的个人字段（与 PUT /api/doctors/me/profile 契约保持一致）
// 仅允许 specialty、introduction；电话/邮箱当前不在契约更新范围内
export interface DoctorProfileUpdateRequest {
  specialty: string
  introduction: string
}
