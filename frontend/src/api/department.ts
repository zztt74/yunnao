import { apiClient } from '@/api/client'
import { parseApiResponse } from '@/api/response'

export interface DepartmentResponse {
  id: number
  code: string
  name: string
  parentId?: number | null
  level: number
  sortOrder: number
  status: string
  description?: string | null
  createdAt: string
  updatedAt: string
  children?: DepartmentResponse[] | null
}

export async function getDepartments(): Promise<DepartmentResponse[]> {
  const res = await apiClient.get('/departments')
  return parseApiResponse(res.data)
}
