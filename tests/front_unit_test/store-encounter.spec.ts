// @vitest-environment happy-dom
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useEncounterStore } from '@/stores/encounter'
import { backendEncounterFixture } from './helpers/fixtures'

const encounterFixture = backendEncounterFixture()

describe('encounter store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('initial state', () => {
    it('starts with no active encounter and empty notes', () => {
      const store = useEncounterStore()
      expect(store.activeEncounter).toBeNull()
      expect(store.consultationNotes).toEqual({
        chiefComplaint: '',
        presentIllness: '',
        pastHistory: '',
        physicalExam: '',
      })
    })
  })

  describe('setActiveEncounter', () => {
    it('sets the active encounter', () => {
      const store = useEncounterStore()
      store.setActiveEncounter(encounterFixture)
      expect(store.activeEncounter).toEqual(encounterFixture)
    })

    it('clears the active encounter when null is passed', () => {
      const store = useEncounterStore()
      store.setActiveEncounter(encounterFixture)
      store.setActiveEncounter(null)
      expect(store.activeEncounter).toBeNull()
    })
  })

  describe('setConsultationNotes', () => {
    it('merges partial notes without clearing existing values', () => {
      const store = useEncounterStore()
      store.setConsultationNotes({ chiefComplaint: '咳嗽' })
      expect(store.consultationNotes.chiefComplaint).toBe('咳嗽')
      expect(store.consultationNotes.presentIllness).toBe('')

      store.setConsultationNotes({ presentIllness: '3 天', physicalExam: '双肺呼吸音清' })
      expect(store.consultationNotes).toEqual({
        chiefComplaint: '咳嗽',
        presentIllness: '3 天',
        pastHistory: '',
        physicalExam: '双肺呼吸音清',
      })
    })
  })

  describe('reset', () => {
    it('clears the active encounter and notes', () => {
      const store = useEncounterStore()
      store.setActiveEncounter(encounterFixture)
      store.setConsultationNotes({ chiefComplaint: '咳嗽', presentIllness: '3 天' })
      store.reset()
      expect(store.activeEncounter).toBeNull()
      expect(store.consultationNotes).toEqual({
        chiefComplaint: '',
        presentIllness: '',
        pastHistory: '',
        physicalExam: '',
      })
    })
  })
})
