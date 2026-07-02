import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { pageEnvelope, successEnvelope } from './helpers/api-client-mock'

import { getDrugOptions } from '@/api/drug'

const mock = getApiClientMock()

const baseDrug = {
  id: 1,
  code: 'D-001',
  name: '阿莫西林',
  genericName: 'Amoxicillin',
  dosageForm: '片剂',
  strength: '500mg',
  unit: '片',
  category: '抗生素',
  status: 'ENABLED',
  dosageRule: { minDose: 250, maxDose: 1000, maxSingleDose: 500, frequency: 'TID' },
}

describe('drug API', () => {
  beforeEach(() => resetApiClientMock())

  it('returns only ENABLED/ACTIVE drugs mapped to DrugOption', async () => {
    mock.get.mockResolvedValueOnce(
      pageEnvelope([
        baseDrug,
        { ...baseDrug, id: 2, code: 'D-002', status: 'ACTIVE' },
        { ...baseDrug, id: 3, code: 'D-003', status: 'DISABLED' },
      ]),
    )
    const result = await getDrugOptions()
    expect(result).toHaveLength(2)
    expect(result[0].id).toBe(1)
    expect(result[0].code).toBe('D-001')
    expect(result[0].defaultDosage).toBe('250')
    expect(result[0].defaultFrequency).toBe('TID')
    expect(result[0].defaultUsage).toBe('片剂')
  })

  it('falls back to defaults when dosageRule is null', async () => {
    mock.get.mockResolvedValueOnce(
      pageEnvelope([{ ...baseDrug, dosageRule: null, dosageForm: null, strength: null, unit: null }]),
    )
    const result = await getDrugOptions()
    expect(result[0].defaultDosage).toBe('1')
    expect(result[0].defaultFrequency).toBe('BID')
    expect(result[0].defaultUsage).toBe('口服')
    expect(result[0].defaultDuration).toBe('3')
    expect(result[0].strength).toBe('')
    expect(result[0].unit).toBe('')
  })

  it('uses default dosage 1 when minDose is missing', async () => {
    mock.get.mockResolvedValueOnce(
      pageEnvelope([{ ...baseDrug, dosageRule: { minDose: null, frequency: null } }]),
    )
    const result = await getDrugOptions()
    expect(result[0].defaultDosage).toBe('1')
    expect(result[0].defaultFrequency).toBe('BID')
  })

  it('passes name filter to backend', async () => {
    mock.get.mockResolvedValueOnce(pageEnvelope([baseDrug]))
    await getDrugOptions('阿莫')
    expect(mock.get).toHaveBeenCalledWith('/drugs', {
      params: { page: 1, pageSize: 100, name: '阿莫' },
    })
  })

  it('omits name filter when empty string', async () => {
    mock.get.mockResolvedValueOnce(pageEnvelope([]))
    await getDrugOptions('')
    expect(mock.get).toHaveBeenCalledWith('/drugs', {
      params: { page: 1, pageSize: 100, name: undefined },
    })
  })
})
