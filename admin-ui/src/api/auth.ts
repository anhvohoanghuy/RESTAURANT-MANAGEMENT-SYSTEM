import { apiFetch } from './client'

export type AuthResponse = {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessExpiresIn: number
  refreshExpiresIn: number
}

export type LoginRequest = {
  username: string
  password: string
}

export function login(request: LoginRequest) {
  return apiFetch<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
    retryOnUnauthorized: false,
  })
}

export function logout(refreshToken: string) {
  return apiFetch<void>('/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
    retryOnUnauthorized: false,
  })
}
