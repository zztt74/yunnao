import type {
  EncounterResponse,
  EncounterStartRequest,
  EncounterCancelRequest,
  EncounterDiagnosisRequest,
  EncounterDiagnosisResponse,
  AiDiagnosisRequest,
  AiDiagnosisResponse,
} from '@/types/encounter'
import type { AppointmentResponse } from '@/types/appointment'
import type { PageResponse } from '@/types/api'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import { getCurrentDoctor } from '@/api/doctor'

async function currentDoctorId(doctorId?: number): Promise<number> {
  return doctorId ?? (await getCurrentDoctor()).id
}

export async function getDoctorPendingQueue(doctorId?: number): Promise<AppointmentResponse[]> {
  const id = await currentDoctorId(doctorId)
  const res = await apiClient.get(`/appointments/doctor/${id}/pending`)
  return parseApiResponse<AppointmentResponse[]>(res.data)
}

export async function getDoctorActiveAppointments(doctorId?: number): Promise<AppointmentResponse[]> {
  const id = await currentDoctorId(doctorId)
  const res = await apiClient.get(`/appointments/doctor/${id}`, {
    params: { page: 1, size: 100 },
  })
  const page = parseApiResponse<PageResponse<AppointmentResponse>>(res.data)
  return page.items.filter((item) =>
    item.status === 'IN_PROGRESS' || item.status === 'WAITING_EXAM')
}

export async function getDoctorTodayAppointments(doctorId?: number): Promise<AppointmentResponse[]> {
  const id = await currentDoctorId(doctorId)
  const today = new Date().toISOString().slice(0, 10)
  const res = await apiClient.get(`/appointments/doctor/${id}`, {
    params: { page: 1, size: 100 },
  })
  const page = parseApiResponse<PageResponse<AppointmentResponse>>(res.data)
  return page.items.filter((item) => item.bookedAt?.slice(0, 10) === today)
}

export async function getDoctorEncounters(doctorId?: number): Promise<EncounterResponse[]> {
  const id = await currentDoctorId(doctorId)
  const res = await apiClient.get(`/encounters/doctor/${id}`, {
    params: { page: 1, size: 100 },
  })
  const page = parseApiResponse<PageResponse<EncounterResponse>>(res.data)
  return page.items
}

export async function startPatientEncounter(
  payload: EncounterStartRequest,
): Promise<EncounterResponse> {
  const res = await apiClient.post('/encounters/start', payload)
  return parseApiResponse<EncounterResponse>(res.data)
}

export async function waitForExam(encounterId: number): Promise<EncounterResponse> {
  const res = await apiClient.post(`/encounters/${encounterId}/wait-exam`)
  return parseApiResponse<EncounterResponse>(res.data)
}

export async function resumeEncounter(encounterId: number): Promise<EncounterResponse> {
  const res = await apiClient.post(`/encounters/${encounterId}/resume`)
  return parseApiResponse<EncounterResponse>(res.data)
}

export async function completeEncounter(encounterId: number): Promise<EncounterResponse> {
  const res = await apiClient.post(`/encounters/${encounterId}/complete`)
  return parseApiResponse<EncounterResponse>(res.data)
}

export async function cancelEncounter(
  encounterId: number,
  payload?: EncounterCancelRequest,
): Promise<EncounterResponse> {
  const res = await apiClient.post(`/encounters/${encounterId}/cancel`, payload ?? {})
  return parseApiResponse<EncounterResponse>(res.data)
}

export async function getEncounterById(id: number): Promise<EncounterResponse> {
  const res = await apiClient.get(`/encounters/${id}`)
  return parseApiResponse<EncounterResponse>(res.data)
}

export async function getEncounterByAppointmentId(appointmentId: number): Promise<EncounterResponse | null> {
  try {
    const res = await apiClient.get(`/encounters/appointment/${appointmentId}`)
    return parseApiResponse<EncounterResponse>(res.data)
  } catch {
    return null
  }
}

export async function getEncounterDiagnoses(encounterId: number): Promise<EncounterDiagnosisResponse[]> {
  const res = await apiClient.get(`/encounters/${encounterId}/diagnoses`)
  return parseApiResponse<EncounterDiagnosisResponse[]>(res.data)
}

export async function addAIDiagnosis(
  encounterId: number,
  payload: EncounterDiagnosisRequest,
): Promise<EncounterDiagnosisResponse> {
  const res = await apiClient.post(`/encounters/${encounterId}/diagnoses/ai`, payload)
  return parseApiResponse<EncounterDiagnosisResponse>(res.data)
}

export async function addDoctorDiagnosis(
  encounterId: number,
  payload: EncounterDiagnosisRequest,
): Promise<EncounterDiagnosisResponse> {
  const res = await apiClient.post(`/encounters/${encounterId}/diagnoses/doctor`, payload)
  return parseApiResponse<EncounterDiagnosisResponse>(res.data)
}

export async function assistDiagnosis(payload: AiDiagnosisRequest): Promise<AiDiagnosisResponse> {
  const res = await apiClient.post('/ai/assist-diagnosis', payload)
  return res.data as AiDiagnosisResponse
}

export async function getDoctorAppointmentById(id: number): Promise<AppointmentResponse> {
  const res = await apiClient.get(`/appointments/${id}`)
  return parseApiResponse<AppointmentResponse>(res.data)
}
