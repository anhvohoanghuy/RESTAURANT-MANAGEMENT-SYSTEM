import { authState, clearSession, setSession } from '../stores/auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  status: number
  code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

type RequestOptions = RequestInit & {
  retryOnUnauthorized?: boolean
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(authState.accessToken ? { Authorization: `Bearer ${authState.accessToken}` } : {}),
      ...options.headers,
    },
  })

  if (response.status === 401 && options.retryOnUnauthorized !== false && authState.refreshToken) {
    const refreshed = await refreshToken()
    if (refreshed) {
      return apiFetch<T>(path, { ...options, retryOnUnauthorized: false })
    }
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

async function refreshToken(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: authState.refreshToken }),
    })
    if (!response.ok) {
      clearSession('Your session expired. Sign in again to continue.')
      return false
    }
    setSession(await response.json())
    return true
  } catch {
    clearSession('Your session expired. Sign in again to continue.')
    return false
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json()
    return new ApiError(response.status, body.message ?? 'Request failed', body.code)
  } catch {
    return new ApiError(response.status, 'Request failed')
  }
}
