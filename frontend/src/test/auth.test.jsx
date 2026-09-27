import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { session } from '../api/client'
import { authApi } from '../api/endpoints'
import { loadOverview } from '../api/overview'
import { DEMO_USER, deferred, renderApp, signIn } from './utils'

vi.mock('../api/overview', () => ({ loadOverview: vi.fn() }))
vi.mock('../api/endpoints', async (orig) => ({ ...(await orig()), authApi: { login: vi.fn(), me: vi.fn() } }))

beforeEach(() => {
  vi.mocked(loadOverview).mockResolvedValue([])
  vi.mocked(authApi.login).mockReset()
})

describe('login', () => {
  it('redirects to login when not signed in', () => {
    renderApp('/')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('signs in, stores the token and opens the dashboard', async () => {
    const pending = deferred()
    vi.mocked(authApi.login).mockReturnValue(pending.promise)
    renderApp('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email'), 'demo@agrioptima.local')
    await user.type(screen.getByLabelText('Password'), 'demo-pass-123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByRole('button', { name: /Signing in/ })).toBeDisabled()
    pending.resolve({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600_000).toISOString(), user: DEMO_USER })

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(authApi.login).toHaveBeenCalledWith('demo@agrioptima.local', 'demo-pass-123')
    expect(session.load().accessToken).toBe('jwt-1')
    expect(screen.getByText('Demo Farmer')).toBeInTheDocument()
  })

  it('shows a clear message for wrong credentials', async () => {
    vi.mocked(authApi.login).mockRejectedValue({ isApiError: true, status: 401, title: 'Session expired', message: 'x' })
    renderApp('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email'), 'demo@agrioptima.local')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('The email or password is incorrect.')).toBeInTheDocument()
    expect(session.load()).toBeNull()
  })

  it('validates the form before calling the API', async () => {
    renderApp('/login')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }))
    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('signs out and returns to the login page', async () => {
    signIn()
    renderApp('/')
    await screen.findByRole('heading', { name: 'Dashboard' })
    await userEvent.setup().click(screen.getByRole('button', { name: /Sign out/ }))
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument())
    expect(session.load()).toBeNull()
  })
})
