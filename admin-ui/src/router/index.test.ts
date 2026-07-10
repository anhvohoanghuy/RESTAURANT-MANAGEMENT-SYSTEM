import { describe, expect, it } from 'vitest'
import { router } from './index'
import { clearSession, setSession } from '../stores/auth'

describe('router guard', () => {
  it('redirects protected routes to login when logged out', async () => {
    clearSession()

    await router.push('/inventory')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows protected routes when logged in', async () => {
    setSession({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })

    await router.push('/inventory')

    expect(router.currentRoute.value.name).toBe('inventory')
  })
})
