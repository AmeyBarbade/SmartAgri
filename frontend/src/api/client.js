import axios from 'axios'

const TOKEN_KEY = 'agrioptima.session'

/** The prototype keeps the JWT in localStorage (no refresh tokens; the backend issues 12 h tokens). */
export const session = {
  load() {
    try {
      const raw = localStorage.getItem(TOKEN_KEY)
      if (!raw) return null
      const s = JSON.parse(raw)
      if (!s?.accessToken || (s.expiresAt && new Date(s.expiresAt) <= new Date())) {
        localStorage.removeItem(TOKEN_KEY)
        return null
      }
      return s
    } catch {
      return null
    }
  },
  save(s) {
    localStorage.setItem(TOKEN_KEY, JSON.stringify(s))
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
  },
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 45000,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const s = session.load()
  if (s && !config.url?.startsWith('/api/auth/login')) {
    config.headers.Authorization = `Bearer ${s.accessToken}`
  }
  return config
})

let onUnauthorized = () => {}
/** Called when a protected request is rejected with 401 (expired or invalid token). */
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

api.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status
    const isLogin = error.config?.url?.startsWith('/api/auth/login')
    if (status === 401 && !isLogin) onUnauthorized()
    return Promise.reject(toApiError(error))
  },
)

const PROBLEM_MESSAGES = {
  'ml-service-unavailable': {
    title: 'Optimisation service unavailable',
    message:
      'The fertilizer optimiser and yield model are not reachable. Start the ML service (port 8001) and try again.',
  },
  'ml-service-timeout': {
    title: 'Optimisation service timed out',
    message: 'The ML service did not answer in time. Try again in a moment.',
  },
  'ml-service-error': {
    title: 'Optimisation failed',
    message: 'The ML service returned an error for this field. Nothing was stored.',
  },
  'ml-service-invalid-response': {
    title: 'Optimisation result rejected',
    message:
      'The backend could not verify the plans returned by the optimiser, so no recommendation was stored.',
  },
}

/** Normalises Axios failures and RFC 7807 problem responses into one shape the UI can display. */
export function toApiError(error) {
  if (error?.isApiError) return error
  const res = error?.response
  if (!res) {
    return {
      isApiError: true,
      status: 0,
      kind: 'network',
      title: 'Server not reachable',
      message: 'Cannot reach the AgriOptima backend. Check that it is running on port 8080.',
      fieldErrors: {},
    }
  }
  const body = res.data && typeof res.data === 'object' ? res.data : {}
  const type = typeof body.type === 'string' ? body.type.split(':').pop() : undefined
  const known = type && PROBLEM_MESSAGES[type]
  const base = {
    isApiError: true,
    status: res.status,
    kind: type ?? `http-${res.status}`,
    fieldErrors: body.errors && typeof body.errors === 'object' ? body.errors : {},
  }
  if (known) return { ...base, ...known }
  if (res.status === 401) {
    return { ...base, title: 'Session expired', message: 'Please sign in again.' }
  }
  if (res.status === 404) {
    return { ...base, title: 'Not found', message: body.detail ?? 'The requested item does not exist.' }
  }
  if (res.status === 400) {
    return { ...base, title: body.title ?? 'Invalid request', message: body.detail ?? 'Please check the input.' }
  }
  return {
    ...base,
    title: body.title ?? 'Something went wrong',
    message: body.detail ?? `The server answered with status ${res.status}.`,
  }
}
