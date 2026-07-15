import { beforeEach, describe, expect, it } from 'vitest'
import { authState, clearSession, isAuthenticated, restoreSession, setSession } from './auth'

const SAMPLE_AUTH_RESPONSE = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  accessExpiresIn: 900,
  refreshExpiresIn: 3600,
}

beforeEach(() => {
  localStorage.clear()
  clearSession()
})

describe('setSession', () => {
  it('populates auth state and persists to localStorage', () => {
    setSession(SAMPLE_AUTH_RESPONSE)

    expect(authState.accessToken).toBe('access-token')
    expect(authState.refreshToken).toBe('refresh-token')
    expect(authState.tokenType).toBe('Bearer')
    expect(authState.message).toBe('')
    expect(isAuthenticated()).toBe(true)

    const stored = JSON.parse(localStorage.getItem('feat1.admin.session') ?? '{}')
    expect(stored).toMatchObject({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
    })
  })

  it('defaults tokenType to Bearer when the backend omits it', () => {
    setSession({ ...SAMPLE_AUTH_RESPONSE, tokenType: '' })

    expect(authState.tokenType).toBe('Bearer')
  })
})

describe('clearSession', () => {
  it('resets auth state and removes the persisted session', () => {
    setSession(SAMPLE_AUTH_RESPONSE)

    clearSession('Your session expired. Sign in again to continue.')

    expect(authState.accessToken).toBe('')
    expect(authState.refreshToken).toBe('')
    expect(authState.message).toBe('Your session expired. Sign in again to continue.')
    expect(isAuthenticated()).toBe(false)
    expect(localStorage.getItem('feat1.admin.session')).toBeNull()
  })
})

describe('restoreSession', () => {
  it('rehydrates auth state from a previously persisted session', () => {
    localStorage.setItem(
      'feat1.admin.session',
      JSON.stringify({ accessToken: 'stored-access', refreshToken: 'stored-refresh', tokenType: 'Bearer' }),
    )

    restoreSession()

    expect(authState.accessToken).toBe('stored-access')
    expect(authState.refreshToken).toBe('stored-refresh')
    expect(isAuthenticated()).toBe(true)
  })

  it('does nothing when no session is persisted', () => {
    restoreSession()

    expect(isAuthenticated()).toBe(false)
  })

  it('clears the stored value and leaves state empty when JSON is corrupt', () => {
    localStorage.setItem('feat1.admin.session', '{not-json')

    restoreSession()

    expect(isAuthenticated()).toBe(false)
    expect(localStorage.getItem('feat1.admin.session')).toBeNull()
  })
})
