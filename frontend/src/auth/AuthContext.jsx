import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { session, setUnauthorizedHandler } from '../api/client'
import { authApi } from '../api/endpoints'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [current, setCurrent] = useState(() => session.load())
  const [notice, setNotice] = useState(null)
  const navigate = useNavigate()

  const logout = useCallback(
    (message) => {
      session.clear()
      setCurrent(null)
      setNotice(message ?? null)
      navigate('/login', { replace: true })
    },
    [navigate],
  )

  useEffect(() => {
    setUnauthorizedHandler(() => logout('Your session has expired. Please sign in again.'))
    return () => setUnauthorizedHandler(() => {})
  }, [logout])

  const login = useCallback(async (email, password) => {
    const res = await authApi.login(email.trim(), password)
    const s = { accessToken: res.accessToken, expiresAt: res.expiresAt, user: res.user }
    session.save(s)
    setCurrent(s)
    setNotice(null)
    return s
  }, [])

  const value = useMemo(
    () => ({ user: current?.user ?? null, isAuthenticated: !!current, login, logout, notice }),
    [current, login, logout, notice],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}

export function RequireAuth({ children }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}
