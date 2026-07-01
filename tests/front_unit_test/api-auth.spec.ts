import { beforeEach, describe, expect, it } from 'vitest'

import './helpers/mock-setup'
import { getApiClientMock, resetApiClientMock } from './helpers/mock-setup'
import { successEnvelope } from './helpers/api-client-mock'

import { changePassword, login, logout } from '@/api/auth'

const mock = getApiClientMock()

describe('auth API', () => {
  beforeEach(() => resetApiClientMock())

  describe('login', () => {
    it('posts to /auth/login and returns parsed response', async () => {
      const payload = { username: 'patient', password: 'secret123' }
      const response = {
        accessToken: 'token',
        tokenType: 'Bearer',
        userId: 1,
        username: 'patient',
        roles: ['PATIENT'],
        mustChangePassword: false,
        expiresIn: 7200,
      }
      mock.post.mockResolvedValueOnce(successEnvelope(response))
      const result = await login(payload)
      expect(result).toEqual(response)
      expect(mock.post).toHaveBeenCalledWith('/auth/login', payload)
    })
  })

  describe('logout', () => {
    it('posts to /auth/logout and resolves', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(null))
      await expect(logout()).resolves.toBeUndefined()
      expect(mock.post).toHaveBeenCalledWith('/auth/logout')
    })
  })

  describe('changePassword', () => {
    it('posts to /auth/change-password', async () => {
      mock.post.mockResolvedValueOnce(successEnvelope(null))
      const payload = { oldPassword: 'old123', newPassword: 'new456' }
      await expect(changePassword(payload)).resolves.toBeUndefined()
      expect(mock.post).toHaveBeenCalledWith('/auth/change-password', payload)
    })

    it('propagates error envelope', async () => {
      mock.post.mockRejectedValueOnce(new Error('INVALID_PASSWORD'))
      await expect(changePassword({ oldPassword: 'x', newPassword: 'y' })).rejects.toThrow(
        'INVALID_PASSWORD',
      )
    })
  })
})
