import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { successEnvelope } from './helpers/api-client-mock'

import { getDepartments } from '@/api/department'

const mock = getApiClientMock()

const departmentFixture = {
  id: 1,
  code: 'DEPT_INTERNAL',
  name: '内科',
  parentId: null,
  level: 1,
  sortOrder: 1,
  status: 'ENABLED',
  description: '内科系统',
  createdAt: '2026-06-28T10:00:00',
  updatedAt: '2026-06-28T10:00:00',
  children: [],
}

describe('department API', () => {
  beforeEach(() => resetApiClientMock())

  it('returns parsed departments', async () => {
    mock.get.mockResolvedValueOnce(successEnvelope([departmentFixture]))
    const result = await getDepartments()
    expect(result).toEqual([departmentFixture])
    expect(mock.get).toHaveBeenCalledWith('/departments')
  })

  it('returns empty array when backend has no departments', async () => {
    mock.get.mockResolvedValueOnce(successEnvelope([]))
    const result = await getDepartments()
    expect(result).toEqual([])
  })
})
