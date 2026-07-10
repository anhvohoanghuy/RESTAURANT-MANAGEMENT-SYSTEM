import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from './client'
import { clearSession, setSession } from '../stores/auth'

const fetchMock = vi.fn()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  localStorage.clear()
  clearSession()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiFetch', () => {
  it('attaches bearer token', async () => {
    setSession({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }))

    await apiFetch('/admin/tables')

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/admin/tables',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer access' }),
      }),
    )
  })

  it('throws ApiError with backend code', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'FORBIDDEN', message: 'Access is denied' }), { status: 403 }),
    )

    await expect(apiFetch('/admin/tables')).rejects.toMatchObject(
      new ApiError(403, 'Access is denied', 'FORBIDDEN'),
    )
  })
})
