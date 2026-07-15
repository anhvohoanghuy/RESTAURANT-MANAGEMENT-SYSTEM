import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { menuApi, sessionsApi } from './modules'
import { clearSession, setSession } from '../stores/auth'

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

describe('sessionsApi', () => {
  it('revokeOthers sends the live refreshToken in the request body', async () => {
    setSession({
      accessToken: 'access',
      refreshToken: 'rt-123',
      tokenType: 'Bearer',
      accessExpiresIn: 900,
      refreshExpiresIn: 3600,
    })
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await sessionsApi.revokeOthers()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(String(url).endsWith('/auth/sessions/revoke-others')).toBe(true)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toMatchObject({ refreshToken: 'rt-123' })
  })

  it('revoke issues a DELETE to /auth/sessions/{id}', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await sessionsApi.revoke('s-1')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(String(url).endsWith('/auth/sessions/s-1')).toBe(true)
    expect(init.method).toBe('DELETE')
  })
})

describe('menuApi.updateDish', () => {
  it('issues a PUT to /admin/menu/dishes/{id} with the JSON-stringified body', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 'd-1', categoryId: 'c-1', name: 'Pho', description: '', basePrice: 50000, status: 'ACTIVE', sortOrder: 0 }), {
        status: 200,
      }),
    )

    const body = { categoryId: 'c-1', name: 'Pho', basePrice: 50000 }
    await menuApi.updateDish('d-1', body)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(String(url).endsWith('/admin/menu/dishes/d-1')).toBe(true)
    expect(init.method).toBe('PUT')
    expect(init.body).toBe(JSON.stringify(body))
  })
})
