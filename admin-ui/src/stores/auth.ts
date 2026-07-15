import { computed, reactive } from 'vue'
import type { AuthResponse } from '../api/auth'

const STORAGE_KEY = 'feat1.admin.session'

export type AuthSession = {
  accessToken: string
  refreshToken: string
  tokenType: string
}

// Source: hand-rolled, verified against auth/application/auth_service/jwt/JwtProvider.java
// (claim("roles", roles) where roles = List<String> of RoleEnum names — "ADMIN"/"USER"/"STAFF", no ROLE_ prefix)
export function decodeRoles(accessToken: string): string[] {
  try {
    const payload = accessToken.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const json = JSON.parse(atob(padded)) as { roles?: string[] }
    return Array.isArray(json.roles) ? json.roles : []
  } catch {
    return []
  }
}

export const authState = reactive({
  accessToken: '',
  refreshToken: '',
  tokenType: 'Bearer',
  message: '',
  roles: [] as string[],
})

export const isAdmin = computed(() => authState.roles.includes('ADMIN'))

export function restoreSession() {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return
  }
  try {
    const session = JSON.parse(raw) as AuthSession
    authState.accessToken = session.accessToken
    authState.refreshToken = session.refreshToken
    authState.tokenType = session.tokenType || 'Bearer'
    authState.roles = decodeRoles(session.accessToken)
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }
}

export function setSession(response: AuthResponse) {
  authState.accessToken = response.accessToken
  authState.refreshToken = response.refreshToken
  authState.tokenType = response.tokenType || 'Bearer'
  authState.message = ''
  authState.roles = decodeRoles(response.accessToken)
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      accessToken: authState.accessToken,
      refreshToken: authState.refreshToken,
      tokenType: authState.tokenType,
    }),
  )
}

export function clearSession(message = '') {
  authState.accessToken = ''
  authState.refreshToken = ''
  authState.tokenType = 'Bearer'
  authState.message = message
  authState.roles = []
  localStorage.removeItem(STORAGE_KEY)
}

export function isAuthenticated() {
  return Boolean(authState.accessToken)
}
