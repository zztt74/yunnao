import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import type {
  PatientRegisterRequest,
  PatientResponse,
  PatientProfileResponse,
  PatientProfileUpdateRequest,
} from '@/types/patient'

export async function registerPatient(payload: PatientRegisterRequest): Promise<PatientResponse> {
  const res = await apiClient.post('/patients/register', payload)
  return parseApiResponse(res.data)
}

export async function getPatientProfile(): Promise<PatientProfileResponse> {
  const res = await apiClient.get('/patients/me/profile')
  return parseApiResponse(res.data)
}

export async function updatePatientProfile(
  payload: PatientProfileUpdateRequest,
): Promise<PatientProfileResponse> {
  const res = await apiClient.put('/patients/me/profile', payload)
  return parseApiResponse(res.data)
}

export async function getPatientInfo(): Promise<PatientResponse> {
  const res = await apiClient.get('/patients/me')
  return parseApiResponse(res.data)
}
