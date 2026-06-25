// 排班状态
export type ScheduleStatus = 'AVAILABLE' | 'FULL' | 'CANCELLED' | 'COMPLETED'

// 挂号状态
export type AppointmentStatus =
  | 'BOOKED'
  | 'CHECKED_IN'
  | 'IN_PROGRESS'
  | 'WAITING_EXAM'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'

// 取消来源
export type CancellationSource = 'PATIENT' | 'SCHEDULE' | 'ADMIN'

// 排班响应（与契约 ScheduleResponse 对齐）
export interface ScheduleResponse {
  id: number
  doctorId: number
  doctorName: string
  departmentId: number
  departmentName: string
  scheduleDate: string // YYYY-MM-DD
  startTime: string // ISO 8601
  endTime: string
  maxAppointments: number
  bookedCount: number
  remainingCount: number
  status: ScheduleStatus
  cancelledAt?: string | null
  cancelReason?: string | null
  createdAt: string
  updatedAt: string
}

// 挂号响应（与契约 AppointmentResponse 对齐）
export interface AppointmentResponse {
  id: number
  patientId: number
  patientName: string
  scheduleId: number
  doctorId: number
  doctorName: string
  departmentId: number
  departmentName: string
  appointmentNumber: string
  status: AppointmentStatus
  bookedAt: string
  checkInTime?: string | null
  cancellationReason?: string | null
  cancellationSource?: CancellationSource | null
  cancelledAt?: string | null
  createdAt: string
  updatedAt: string
}

// 创建挂号请求
export interface AppointmentCreateRequest {
  patientId: number
  scheduleId: number
}

// 取消挂号请求
export interface AppointmentCancelRequest {
  reason: string
}
