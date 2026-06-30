import type {
  DoctorProfile,
  DoctorProfileUpdateRequest,
} from '@/types/doctor'
import type { ScheduleResponse } from '@/types/appointment'
import type { PageResponse } from '@/types/api'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import { useAuthStore } from '@/stores/auth'

interface BackendDoctorResponse {
  id: number
  userId: number
  departmentId: number
  departmentName: string
  name: string
  title: string
  specialty: string | null
  status: string
  education?: string | null
  experienceYears?: number | null
  introduction: string | null
  createdAt: string
  updatedAt: string
}

function titleText(title: string): string {
  const map: Record<string, string> = {
    CHIEF: '主任医师',
    DEPUTY_CHIEF: '副主任医师',
    ATTENDING: '主治医师',
    RESIDENT: '住院医师',
  }
  return map[title] ?? title
}

function toDoctorProfile(doctor: BackendDoctorResponse): DoctorProfile {
  return {
    doctorId: doctor.id,
    doctorName: doctor.name,
    title: titleText(doctor.title),
    departmentId: doctor.departmentId,
    departmentName: doctor.departmentName,
    gender: 'MALE',
    phone: '',
    email: '',
    specialty: doctor.specialty ?? '',
    introduction: doctor.introduction ?? '',
    status: doctor.status === 'ENABLED' ? 'ACTIVE' : 'DISABLED',
    createdAt: doctor.createdAt,
    updatedAt: doctor.updatedAt,
  }
}

export async function getCurrentDoctor(): Promise<BackendDoctorResponse> {
  const auth = useAuthStore()
  const userId = auth.userInfo?.userId
  if (!userId) {
    throw new Error('当前医生未登录')
  }

  const res = await apiClient.get('/doctors', {
    params: { page: 1, pageSize: 100 },
  })
  const page = parseApiResponse<PageResponse<BackendDoctorResponse>>(res.data)
  const doctor = page.items.find((item) => item.userId === userId)
  if (!doctor) {
    throw new Error('当前登录账号没有关联医生档案')
  }
  return doctor
}

export async function getDoctorProfile(): Promise<DoctorProfile> {
  return toDoctorProfile(await getCurrentDoctor())
}

export async function updateDoctorProfile(
  _payload: DoctorProfileUpdateRequest,
): Promise<DoctorProfile> {
  throw new Error('后端尚未提供医生本人资料更新接口，已停止使用本地假数据。')
}

export async function getDoctorSchedules(): Promise<ScheduleResponse[]> {
  const doctor = await getCurrentDoctor()
  const res = await apiClient.get(`/schedules/doctor/${doctor.id}`, {
    params: { page: 1, size: 100 },
  })
  const page = parseApiResponse<PageResponse<ScheduleResponse>>(res.data)
  return page.items
}

export async function getDoctorTodaySchedules(): Promise<ScheduleResponse[]> {
  const today = new Date().toISOString().slice(0, 10)
  const schedules = await getDoctorSchedules()
  return schedules.filter((s) => s.scheduleDate === today)
}
