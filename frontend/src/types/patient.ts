export type Gender = 'MALE' | 'FEMALE'

export type PatientStatus = 'ACTIVE' | 'INACTIVE'

export interface PatientRegisterRequest {
  username: string
  password: string
  name: string
  gender: Gender
  birthDate: string
  phone: string
}

export interface PatientUpdateRequest {
  name: string
  gender: Gender
  birthDate: string
  phone: string
}

export interface PatientProfileUpdateRequest {
  address?: string
  emergencyContact?: string
  emergencyPhone?: string
  allergies?: string
  medicalHistory?: string
}

export interface PatientResponse {
  id: number
  userId: number
  name: string
  gender: Gender
  birthDate: string
  phone: string
  status: PatientStatus
  createdAt: string
  updatedAt: string
}

export interface PatientProfileResponse {
  id: number
  patientId: number
  address: string
  emergencyContact: string
  emergencyPhone: string
  allergies: string
  medicalHistory: string
  createdAt: string
  updatedAt: string
}
