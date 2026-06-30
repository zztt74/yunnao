import type { PageResponse } from '@/types/api'
import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'

export interface DrugOption {
  id: number
  code: string
  name: string
  strength: string
  unit: string
  defaultDosage: string
  defaultFrequency: string
  defaultUsage: string
  defaultDuration: string
}

interface BackendDrugResponse {
  id: number
  code: string
  name: string
  genericName?: string | null
  dosageForm?: string | null
  strength?: string | null
  unit?: string | null
  category?: string | null
  status: string
  dosageRule?: {
    minDose?: number | null
    maxDose?: number | null
    maxSingleDose?: number | null
    frequency?: string | null
  } | null
}

export async function getDrugOptions(name?: string): Promise<DrugOption[]> {
  const res = await apiClient.get('/drugs', {
    params: { page: 1, pageSize: 100, name: name || undefined },
  })
  return parseApiResponse<PageResponse<BackendDrugResponse>>(res.data).items
    .filter((drug) => drug.status === 'ENABLED' || drug.status === 'ACTIVE')
    .map((drug) => ({
      id: drug.id,
      code: drug.code,
      name: drug.name,
      strength: drug.strength ?? '',
      unit: drug.unit ?? '',
      defaultDosage: drug.dosageRule?.minDose ? String(drug.dosageRule.minDose) : '1',
      defaultFrequency: drug.dosageRule?.frequency ?? 'BID',
      defaultUsage: drug.dosageForm ?? '口服',
      defaultDuration: '3',
    }))
}
