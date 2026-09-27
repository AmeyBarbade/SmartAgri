import { AxiosError } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, session, setUnauthorizedHandler, toApiError } from '../api/client'

let seen
const originalAdapter = api.defaults.adapter

function respondWith(status, data) {
  api.defaults.adapter = async (config) => {
    seen = config
    const response = { status, data, headers: {}, config, statusText: '' }
    if (status >= 400) throw new AxiosError('failed', 'ERR_BAD_RESPONSE', config, null, response)
    return response
  }
}

beforeEach(() => {
  seen = undefined
})
afterEach(() => {
  api.defaults.adapter = originalAdapter
  setUnauthorizedHandler(() => {})
})

describe('api client', () => {
  it('sends the stored JWT as a bearer token on protected requests', async () => {
    session.save({ accessToken: 'abc.def.ghi', expiresAt: new Date(Date.now() + 60_000).toISOString() })
    respondWith(200, [])
    await api.get('/api/farms')
    expect(seen.headers.Authorization).toBe('Bearer abc.def.ghi')
  })

  it('sends no token when signed out, and drops an expired session', async () => {
    session.save({ accessToken: 'old', expiresAt: new Date(Date.now() - 1000).toISOString() })
    respondWith(200, [])
    await api.get('/api/farms')
    expect(seen.headers.Authorization).toBeUndefined()
    expect(session.load()).toBeNull()
  })

  it('calls the unauthorized handler on 401 from a protected endpoint', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    session.save({ accessToken: 'bad', expiresAt: new Date(Date.now() + 60_000).toISOString() })
    respondWith(401, { type: 'urn:agrioptima:problem:unauthorized', title: 'Unauthorized' })
    await expect(api.get('/api/farms')).rejects.toMatchObject({ status: 401, title: 'Session expired' })
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not treat a failed login as an expired session', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    respondWith(401, { title: 'Unauthorized', detail: 'Invalid email or password' })
    await expect(api.post('/api/auth/login', {})).rejects.toMatchObject({ status: 401 })
    expect(handler).not.toHaveBeenCalled()
  })

  it('maps ML-service problems and validation errors to readable messages', async () => {
    respondWith(503, { type: 'urn:agrioptima:problem:ml-service-unavailable', title: 'ML service unavailable', status: 503 })
    await expect(api.post('/api/fields/1/recommendations')).rejects.toMatchObject({
      title: 'Optimisation service unavailable',
      message: expect.stringContaining('ML service'),
    })

    respondWith(400, { type: 'urn:agrioptima:problem:validation', title: 'Validation failed', detail: 'Request has invalid fields', errors: { ph: 'must be less than or equal to 11.0' } })
    await expect(api.post('/api/fields/1/soil-records', {})).rejects.toMatchObject({
      status: 400,
      fieldErrors: { ph: 'must be less than or equal to 11.0' },
    })
  })

  it('reports an unreachable backend without a raw error', () => {
    const err = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'))
    expect(err).toMatchObject({ status: 0, kind: 'network', title: 'Server not reachable' })
  })
})
