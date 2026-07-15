import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError } from './client'
import { authState, clearSession, setSession } from '../stores/auth'

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
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

  it('falls back to a generic message when the error body is not JSON', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>500</html>', { status: 500 }))

    await expect(apiFetch('/admin/tables')).rejects.toMatchObject(new ApiError(500, 'Request failed'))
  })

  it('returns undefined for a 204 No Content response', async () => {
    setSession({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(apiFetch('/admin/tables/1')).resolves.toBeUndefined()
  })

  it('refreshes the token on 401 and retries once with the new access token', async () => {
    setSession({
      accessToken: 'expired',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: 'fresh',
            refreshToken: 'refresh',
            tokenType: 'Bearer',
            accessExpiresIn: 900,
            refreshExpiresIn: 3600,
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }))

    await apiFetch('/admin/tables')

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock).toHaveBeenNthCalledWith(2, 'http://localhost:8080/auth/refresh', expect.any(Object))
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      'http://localhost:8080/admin/tables',
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer fresh' }) }),
    )
    expect(authState.accessToken).toBe('fresh')
  })

  it('clears the session and does not retry when refresh itself fails', async () => {
    setSession({
      accessToken: 'expired',
      refreshToken: 'stale-refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'invalid refresh token' }), { status: 401 }))

    await expect(apiFetch('/admin/tables')).rejects.toMatchObject(new ApiError(401, 'expired'))

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authState.accessToken).toBe('')
    expect(authState.message).toBe('Your session expired. Sign in again to continue.')
  })

  it('does not attempt refresh when retryOnUnauthorized is false', async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ message: 'bad credentials' }), { status: 401 }))

    await expect(apiFetch('/auth/login', { retryOnUnauthorized: false })).rejects.toMatchObject(
      new ApiError(401, 'bad credentials'),
    )

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
