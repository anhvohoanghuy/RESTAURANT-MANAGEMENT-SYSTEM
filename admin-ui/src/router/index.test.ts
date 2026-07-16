import { afterEach, describe, expect, it } from 'vitest'
import { router } from './index'
import { clearSession, setSession } from '../stores/auth'

function fakeJwt(claims: Record<string, unknown>): string {
  return `x.${btoa(JSON.stringify(claims))}.y`
}

afterEach(() => {
  clearSession()
})

describe('router guard', () => {
  it('redirects protected routes to login when logged out', async () => {
    clearSession()

    await router.push('/inventory')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('preserves the originally requested path as a redirect query param', async () => {
    clearSession()

    await router.push('/payments')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/payments')
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

  it('redirects an already-authenticated user away from /login to overview', async () => {
    setSession({
      accessToken: 'access',
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('overview')
  })

  it('redirects a STAFF deep-link to the recipe route back to menu', async () => {
    setSession({
      accessToken: fakeJwt({ roles: ['STAFF'] }),
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })

    await router.push('/menu/dishes/some-id/recipe')

    expect(router.currentRoute.value.name).toBe('menu')
  })

  it('allows an ADMIN to reach the recipe route', async () => {
    setSession({
      accessToken: fakeJwt({ roles: ['ADMIN'] }),
      refreshToken: 'refresh',
      tokenType: 'Bearer',
      accessExpiresIn: 1,
      refreshExpiresIn: 1,
    })

    await router.push('/menu/dishes/some-id/recipe')

    expect(router.currentRoute.value.name).toBe('recipe')
  })
})
