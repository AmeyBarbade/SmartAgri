import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../components/AppShell'
import { Button, ErrorNotice, FieldError, Label, Notice } from '../components/ui'

// Shown in development builds only; the account is created by scripts/demo_recommendation.py --seed-demo-user.
const DEMO = import.meta.env.DEV ? { email: 'demo@agrioptima.local', password: 'demo-pass-123' } : null

export default function LoginPage() {
  const { login, isAuthenticated, notice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [touched, setTouched] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) return <Navigate to="/" replace />

  const emailInvalid = touched && !/^\S+@\S+\.\S+$/.test(email.trim())
  const passwordMissing = touched && !password

  async function onSubmit(e) {
    e.preventDefault()
    setTouched(true)
    if (!/^\S+@\S+\.\S+$/.test(email.trim()) || !password) return
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
      navigate(location.state?.from ?? '/', { replace: true })
    } catch (err) {
      setError(
        err.status === 401 || err.status === 400
          ? { title: 'Sign-in failed', message: 'The email or password is incorrect.' }
          : err,
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <Logo />
          <p className="mt-3 text-sm text-muted">Fertilizer planning for your fields, based on soil tests and crop stage.</p>
        </div>
        <form onSubmit={onSubmit} noValidate className="rounded-lg border border-line bg-surface p-6">
          <h1 className="mb-5 text-base font-semibold">Sign in</h1>
          <div className="space-y-4">
            {notice && <Notice tone="warn">{notice}</Notice>}
            <ErrorNotice error={error} />
            <div>
              <Label htmlFor="email">Email</Label>
              <input
                id="email"
                type="email"
                autoComplete="username"
                className="input"
                value={email}
                aria-invalid={emailInvalid}
                onChange={(e) => setEmail(e.target.value)}
              />
              <FieldError>{emailInvalid && 'Enter a valid email address.'}</FieldError>
            </div>
            <div>
              <Label htmlFor="password">Password</Label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                className="input"
                value={password}
                aria-invalid={passwordMissing}
                onChange={(e) => setPassword(e.target.value)}
              />
              <FieldError>{passwordMissing && 'Enter your password.'}</FieldError>
            </div>
            <Button type="submit" variant="primary" className="w-full" loading={submitting}>
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </div>
        </form>
        {DEMO && (
          <p className="mt-4 text-center text-xs text-faint">
            Demo account:{' '}
            <button
              type="button"
              className="text-muted underline underline-offset-2 hover:text-ink"
              onClick={() => {
                setEmail(DEMO.email)
                setPassword(DEMO.password)
              }}
            >
              {DEMO.email}
            </button>
          </p>
        )}
      </div>
    </div>
  )
}
