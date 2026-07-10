import { reactive } from 'vue'
import type { AuthResponse } from '../api/auth'

const STORAGE_KEY = 'feat1.admin.session'

export type AuthSession = {
  accessToken: string
  refreshToken: string
  tokenType: string
}

export const authState = reactive({
  accessToken: '',
  refreshToken: '',
  tokenType: 'Bearer',
  message: '',
})

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
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }
}

export function setSession(response: AuthResponse) {
  authState.accessToken = response.accessToken
  authState.refreshToken = response.refreshToken
  authState.tokenType = response.tokenType || 'Bearer'
  authState.message = ''
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
  localStorage.removeItem(STORAGE_KEY)
}

export function isAuthenticated() {
  return Boolean(authState.accessToken)
}
