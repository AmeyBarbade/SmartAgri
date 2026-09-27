import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { session } from '../api/client'
import App from '../App'

export const DEMO_USER = { id: 1, fullName: 'Demo Farmer', email: 'demo@agrioptima.local', role: 'FARMER' }

export function signIn() {
  session.save({ accessToken: 'test-token', expiresAt: new Date(Date.now() + 3600_000).toISOString(), user: DEMO_USER })
}

export function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

/** A promise that the test resolves or rejects by hand, to observe loading states. */
export function deferred() {
  let resolve, reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
