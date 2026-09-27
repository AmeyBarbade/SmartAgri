import { useCallback, useEffect, useState } from 'react'
import { toApiError } from './api/client'

/** Runs an async loader on mount / when deps change. Returns { data, error, loading, reload, setData }. */
export function useAsync(loader, deps) {
  const [state, setState] = useState({ data: undefined, error: null, loading: true })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let active = true
    setState((s) => ({ ...s, loading: true, error: null }))
    loader()
      .then((data) => active && setState({ data, error: null, loading: false }))
      .catch((e) => active && setState({ data: undefined, error: toApiError(e), loading: false }))
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  const setData = useCallback((data) => setState((s) => ({ ...s, data })), [])
  return { ...state, reload, setData }
}
