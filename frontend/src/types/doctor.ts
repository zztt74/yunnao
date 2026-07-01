// 医生类型定义
// 设计来源：product/11_功能需求.md §4 科室与医生管理、§2 用户认证与权限管理

export type DoctorStatus = 'ACTIVE' | 'DISABLED'

// 医生个人信息（§4.3：职称、擅长方向和简介；§2.3：密码修改适用于所有角色）
// 注意：后端 DoctorProfileUpdateRequest 仅接受 specialty/education/experienceYears/introduction。
// phone/email 后端 DoctorProfileUpdateRequest 不支持，保留在展示字段中以承载历史只读数据，编辑表单不再提交。
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
  // 学历（§4.3，后端 B1 新增可编辑字段）
  education: string
  // 从业年限（§4.3，后端 B1 新增可编辑字段）
  experienceYears: number
  // 个人简介（§4.3）
  introduction: string
  status: DoctorStatus
  createdAt: string
  updatedAt: string
}

// 医生可编辑的个人字段（对齐后端 B1 PUT /api/doctors/me 的 DoctorProfileUpdateRequest）
// 系统管理字段（科室、职称、状态）不在内；phone/email 后端暂不支持，不在提交载荷中。
export interface DoctorProfileUpdateRequest {
  specialty: string
  education: string
  experienceYears: number
  introduction: string
}
