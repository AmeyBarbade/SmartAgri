// Display formatting only. Every number shown in the UI comes from the backend unchanged.

const num = (v) => (v === null || v === undefined || v === '' ? null : Number(v))

export function fmt(v, digits = 2) {
  const n = num(v)
  if (n === null || Number.isNaN(n)) return '—'
  return n.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: digits })
}

export function fmtFixed(v, digits = 1) {
  const n = num(v)
  if (n === null || Number.isNaN(n)) return '—'
  return n.toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

export function inr(v, digits = 0) {
  const n = num(v)
  if (n === null || Number.isNaN(n)) return '—'
  const s = '₹' + Math.abs(n).toLocaleString('en-IN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
  return n < 0 ? `−${s}` : s
}

export function fmtDate(v) {
  if (!v) return '—'
  const d = new Date(v.length === 10 ? `${v}T00:00:00` : v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function fmtDateTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  return d.toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function titleCase(code) {
  if (!code) return '—'
  return code
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

export const STRATEGY_LABELS = {
  LOWEST_COST: 'Lowest cost',
  MIN_EXCESS: 'Minimum excess',
  BALANCED: 'Balanced',
}

export const strategyLabel = (s) => STRATEGY_LABELS[s] ?? titleCase(s)
