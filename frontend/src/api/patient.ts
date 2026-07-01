import type { AppointmentResponse } from '@/types/appointment'
import type { PageResponse } from '@/types/api'
import type { ExaminationResponse } from '@/types/examination'
import type { MedicalRecord } from '@/types/medical-record'
import type {
  PatientDetailResponse,
  PatientProfileResponse,
  PatientProfileUpdateRequest,
  PatientRegisterRequest,
  PatientResponse,
  PatientTimelineEntry,
  PatientUpdateRequest,
} from '@/types/patient'
import type { PrescriptionResponse } from '@/types/prescription'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'

export async function registerPatient(
  payload: PatientRegisterRequest,
): Promise<PatientResponse> {
  const res = await apiClient.post('/patients/register', payload)
  return parseApiResponse(res.data)
}

export async function getPatientProfile(): Promise<PatientProfileResponse> {
  const patient = await getPatientInfo()
  const res = await apiClient.get(`/patients/${patient.id}/profile`)
  return parseApiResponse(res.data)
}

export async function updatePatientProfile(
  payload: PatientProfileUpdateRequest,
): Promise<PatientProfileResponse> {
  const patient = await getPatientInfo()
  const res = await apiClient.put(`/patients/${patient.id}/profile`, payload)
  return parseApiResponse(res.data)
}

export async function getPatientInfo(): Promise<PatientResponse> {
  const res = await apiClient.get('/patients/me')
  return parseApiResponse(res.data)
}

export async function updatePatientInfo(
  payload: PatientUpdateRequest,
): Promise<PatientResponse> {
  const patient = await getPatientInfo()
  const res = await apiClient.put(`/patients/${patient.id}`, payload)
  return parseApiResponse(res.data)
}

export async function getPatientDetail(
  patientId: number,
): Promise<PatientDetailResponse> {
  const [patientRes, profileRes] = await Promise.all([
    apiClient.get(`/patients/${patientId}`),
    apiClient.get(`/patients/${patientId}/profile`),
  ])
  const patient = parseApiResponse<PatientResponse>(patientRes.data)
  const profile = parseApiResponse<PatientProfileResponse>(profileRes.data)
  return {
    id: patient.id,
    name: patient.name,
    gender: patient.gender,
    birthDate: patient.birthDate,
    age: calculateAge(patient.birthDate),
    phone: patient.phone,
    allergies: profile.allergies || '无',
    medicalHistory: profile.medicalHistory || '无',
    address: profile.address || '',
    emergencyContact: profile.emergencyContact || '',
    emergencyPhone: profile.emergencyPhone || '',
    createdAt: patient.createdAt,
  }
}

export async function getPatientTimeline(
  patientId: number,
): Promise<PatientTimelineEntry[]> {
  const [appointments, examinations, medicalRecords, prescriptions] = await Promise.all([
    apiClient.get(`/appointments/patient/${patientId}`).then((res) =>
      parsePageOrArray<AppointmentResponse>(res.data)),
    apiClient.get(`/examinations/patient/${patientId}`).then((res) =>
      parseApiResponse<PageResponse<ExaminationResponse>>(res.data).items),
    apiClient.get(`/medical-records/patient/${patientId}`).then((res) =>
      parseApiResponse<PageResponse<MedicalRecord>>(res.data).items),
    apiClient.get(`/prescriptions/patient/${patientId}`).then((res) =>
      parseApiResponse<PageResponse<PrescriptionResponse>>(res.data).items),
  ])

  return [
    ...appointments.map((appointment): PatientTimelineEntry => ({
      id: appointment.id,
      type: 'APPOINTMENT',
      title: `${appointment.departmentName} 挂号`,
      description: `${appointment.doctorName}，状态：${appointment.status}`,
      occurredAt: appointment.bookedAt,
      statusLabel: appointment.status,
    })),
    ...examinations.map((exam): PatientTimelineEntry => ({
      id: exam.id,
      type: 'EXAMINATION',
      title: exam.itemName,
      description: exam.purpose || exam.status,
      occurredAt: exam.orderedAt,
      encounterId: exam.encounterId,
      resourceId: exam.id,
      statusLabel: exam.status,
    })),
    ...medicalRecords.map((record): PatientTimelineEntry => ({
      id: record.id,
      type: 'MEDICAL_RECORD',
      title: '电子病历',
      description: record.chiefComplaint || record.status,
      occurredAt: record.confirmedAt || record.createdAt,
      encounterId: record.encounterId,
      resourceId: record.id,
      statusLabel: record.status,
    })),
    ...prescriptions.map((prescription): PatientTimelineEntry => ({
      id: prescription.id,
      type: 'PRESCRIPTION',
      title: '处方',
      description: prescription.items.map((item) => item.drugName).join('、') || prescription.status,
      occurredAt: prescription.confirmedAt || prescription.createdAt,
      encounterId: prescription.encounterId,
      resourceId: prescription.id,
      statusLabel: prescription.status,
    })),
  ].sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
}

function calculateAge(birthDate: string): number {
  const birth = new Date(birthDate)
  if (Number.isNaN(birth.getTime())) return 0
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const monthDelta = today.getMonth() - birth.getMonth()
  if (monthDelta < 0 || (monthDelta === 0 && today.getDate() < birth.getDate())) {
    age -= 1
  }
  return Math.max(0, age)
}

function parsePageOrArray<T>(response: unknown): T[] {
  const data = parseApiResponse<T[] | PageResponse<T>>(response as never)
  return Array.isArray(data) ? data : data.items
}
