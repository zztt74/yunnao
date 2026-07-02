import type { EncounterResponse } from '@/types/encounter'
import type { PageResponse } from '@/types/api'
import type {
  MedicalRecord,
  MedicalRecordAiRequest,
  MedicalRecordAiResponse,
  MedicalRecordSaveRequest,
  MedicalRecordStatus,
} from '@/types/medical-record'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'
import { getPatientInfo } from '@/api/patient'

interface BackendMedicalRecord {
  id: number
  encounterId: number
  patientId: number
  doctorId: number
  content: string
  source: string
  status: MedicalRecordStatus
  createdBy?: number | null
  confirmedBy?: number | null
  confirmedAt?: string | null
  createdAt: string
  updatedAt: string
}

interface StructuredContent {
  chiefComplaint?: string
  presentIllness?: string
  pastHistory?: string
  physicalExam?: string
  preliminaryDiagnosis?: string
  treatmentAdvice?: string
}

export async function getMyMedicalRecords(params?: {
  fromDate?: string
  toDate?: string
}): Promise<MedicalRecord[]> {
  const patient = await getPatientInfo()
  const res = await apiClient.get(`/medical-records/patient/${patient.id}`)
  let list = parseApiResponse<PageResponse<BackendMedicalRecord>>(res.data).items
    .map((record) => mapMedicalRecord(record))
    .filter((record) => record.status === 'CONFIRMED')
  if (params?.fromDate) {
    list = list.filter((record) => record.encounterDate >= params.fromDate!)
  }
  if (params?.toDate) {
    list = list.filter((record) => record.encounterDate <= params.toDate!)
  }
  return list.sort((a, b) => new Date(b.encounterDate).getTime() - new Date(a.encounterDate).getTime())
}

export async function getMedicalRecordById(id: number): Promise<MedicalRecord> {
  const res = await apiClient.get(`/medical-records/${id}`)
  return mapMedicalRecord(parseApiResponse<BackendMedicalRecord>(res.data))
}

export async function getEncounterMedicalRecord(encounterId: number): Promise<MedicalRecord | null> {
  const res = await apiClient.get(`/medical-records/encounter/${encounterId}`)
  const records = parseApiResponse<BackendMedicalRecord[]>(res.data)
  return records.length > 0 ? mapMedicalRecord(records[0]) : null
}

export async function generateMedicalRecordDraft(
  payload: MedicalRecordAiRequest,
): Promise<MedicalRecordAiResponse> {
  const res = await apiClient.post('/medical-records/ai-generate', {
    encounterId: payload.encounterId,
    chiefComplaint: payload.chiefComplaint,
    presentIllness: payload.presentIllness,
    pastHistory: payload.pastHistory,
    physicalExamination: payload.physicalExam,
    preliminaryDiagnoses: payload.diagnoses,
    consultationTranscript: payload.consultationTranscript,
  })
  const record = parseApiResponse<BackendMedicalRecord>(res.data)
  const content = parseContent(record.content)
  return {
    encounterId: record.encounterId,
    chiefComplaint: content.chiefComplaint || payload.chiefComplaint,
    presentIllness: content.presentIllness || payload.presentIllness || '',
    pastHistory: content.pastHistory || payload.pastHistory || '',
    physicalExam: content.physicalExam || payload.physicalExam || '',
    preliminaryDiagnosis: content.preliminaryDiagnosis || payload.diagnoses?.join('、') || '',
    treatmentAdvice: content.treatmentAdvice || '',
    aiStatus: 'SUCCESS',
  }
}

export async function saveMedicalRecord(
  encounter: EncounterResponse,
  payload: MedicalRecordSaveRequest,
): Promise<MedicalRecord> {
  const existing = await getEncounterMedicalRecord(encounter.id)
  const content = serializeContent(payload)
  const res = existing
    ? await apiClient.put(`/medical-records/${existing.id}`, { content })
    : await apiClient.post('/medical-records', { encounterId: encounter.id, content })
  return mapMedicalRecord(parseApiResponse<BackendMedicalRecord>(res.data), encounter)
}

export async function confirmMedicalRecord(
  encounter: EncounterResponse,
  payload: MedicalRecordSaveRequest,
): Promise<MedicalRecord> {
  const saved = await saveMedicalRecord(encounter, { ...payload, status: 'DRAFT' })
  const res = await apiClient.post(`/medical-records/${saved.id}/confirm`)
  return mapMedicalRecord(parseApiResponse<BackendMedicalRecord>(res.data), encounter)
}

function mapMedicalRecord(record: BackendMedicalRecord, encounter?: EncounterResponse): MedicalRecord {
  const content = parseContent(record.content)
  return {
    id: record.id,
    encounterId: record.encounterId,
    patientId: record.patientId,
    doctorId: record.doctorId,
    doctorName: encounter?.doctorName ?? '',
    departmentName: encounter?.departmentName ?? '',
    chiefComplaint: content.chiefComplaint || record.content,
    presentIllness: content.presentIllness || '',
    pastHistory: content.pastHistory || '',
    physicalExam: content.physicalExam || '',
    preliminaryDiagnosis: content.preliminaryDiagnosis || '',
    treatmentAdvice: content.treatmentAdvice || '',
    status: record.status,
    diagnoses: [],
    encounterDate: record.createdAt,
    confirmedAt: record.confirmedAt ?? null,
    createdAt: record.createdAt,
    updatedAt: record.updatedAt,
  }
}

function serializeContent(payload: MedicalRecordSaveRequest): string {
  return JSON.stringify({
    chiefComplaint: payload.chiefComplaint,
    presentIllness: payload.presentIllness,
    pastHistory: payload.pastHistory ?? '',
    physicalExam: payload.physicalExam ?? '',
    preliminaryDiagnosis: payload.preliminaryDiagnosis ?? '',
    treatmentAdvice: payload.treatmentAdvice ?? '',
  })
}

function parseContent(content: string): StructuredContent {
  try {
    const parsed = JSON.parse(content) as StructuredContent
    if (parsed && typeof parsed === 'object') return parsed
  } catch {
    // Existing records may be plain text from earlier contracts.
  }
  return { chiefComplaint: content }
}
