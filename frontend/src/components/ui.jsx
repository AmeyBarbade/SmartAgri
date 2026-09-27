import { AlertTriangle, Loader2, XCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

const BUTTON = {
  primary: 'bg-accent text-white hover:bg-accent-hover border border-accent',
  secondary: 'bg-surface text-ink border border-line-strong hover:bg-canvas',
  tertiary: 'text-accent hover:text-accent-hover hover:underline underline-offset-2 px-0',
}

export function Button({ variant = 'secondary', size = 'md', loading, className = '', children, ...props }) {
  const sizing = variant === 'tertiary' ? 'h-9' : size === 'lg' ? 'h-10 px-4' : 'h-9 px-3'
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-md text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${sizing} ${BUTTON[variant]} ${className}`}
      disabled={loading || props.disabled}
      {...props}
    >
      {loading && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  )
}

export function ButtonLink({ variant = 'secondary', className = '', children, ...props }) {
  const sizing = variant === 'tertiary' ? '' : 'h-9 px-3'
  return (
    <Link
      className={`inline-flex items-center justify-center gap-2 rounded-md text-sm font-medium transition-colors ${sizing} ${BUTTON[variant]} ${className}`}
      {...props}
    >
      {children}
    </Link>
  )
}

export function PageHeader({ title, meta, actions, back }) {
  return (
    <div className="mb-6 flex flex-col gap-3 border-b border-line pb-5 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {back}
        <h1 className="text-xl font-semibold tracking-tight text-ink">{title}</h1>
        {meta && <div className="mt-1 text-sm text-muted">{meta}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
    </div>
  )
}

export function Section({ title, description, actions, children, className = '' }) {
  return (
    <section className={`mb-8 ${className}`}>
      {(title || actions) && (
        <div className="mb-3 flex items-end justify-between gap-4">
          <div>
            {title && <h2 className="text-[15px] font-semibold text-ink">{title}</h2>}
            {description && <p className="mt-0.5 text-[13px] text-muted">{description}</p>}
          </div>
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

export function Panel({ className = '', children }) {
  return <div className={`rounded-lg border border-line bg-surface ${className}`}>{children}</div>
}

export function Label({ htmlFor, children, hint }) {
  return (
    <label htmlFor={htmlFor} className="mb-1 block text-[13px] font-medium text-ink-2">
      {children}
      {hint && <span className="ml-1 font-normal text-faint">{hint}</span>}
    </label>
  )
}

export function FieldError({ children }) {
  if (!children) return null
  return <p className="mt-1 text-xs text-danger">{children}</p>
}

export function Loading({ label = 'Loading…' }) {
  return (
    <div className="flex items-center gap-2 py-10 text-sm text-muted" role="status">
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {label}
    </div>
  )
}

export function Skeleton({ className = '' }) {
  return <div className={`animate-pulse rounded bg-line/60 ${className}`} />
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="rounded-lg border border-dashed border-line-strong px-6 py-8 text-center">
      <p className="font-medium text-ink">{title}</p>
      {children && <p className="mx-auto mt-1 max-w-md text-[13px] text-muted">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Notice({ tone = 'warn', title, children, action }) {
  const tones = {
    warn: 'border-warn-line bg-warn-soft text-warn',
    danger: 'border-danger-line bg-danger-soft text-danger',
  }
  const Icon = tone === 'danger' ? XCircle : AlertTriangle
  return (
    <div className={`flex gap-3 rounded-lg border px-4 py-3 ${tones[tone]}`} role="alert">
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1 text-[13px]">
        {title && <p className="font-medium">{title}</p>}
        {children && <div className="text-ink-2">{children}</div>}
        {action && <div className="mt-2">{action}</div>}
      </div>
    </div>
  )
}

export function ErrorNotice({ error, onRetry }) {
  if (!error) return null
  return (
    <Notice
      tone="danger"
      title={error.title ?? 'Something went wrong'}
      action={
        onRetry && (
          <Button variant="secondary" onClick={onRetry}>
            Try again
          </Button>
        )
      }
    >
      {error.message}
    </Notice>
  )
}

/** Small uppercase label + value, used in summary strips. */
export function Stat({ label, children, sub }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 font-medium break-words text-ink">{children}</dd>
      {sub && <dd className="text-xs text-muted">{sub}</dd>}
    </div>
  )
}

export function StatusDot({ tone = 'ok', children }) {
  const colors = { ok: 'bg-accent', warn: 'bg-[#c08a2a]', off: 'bg-line-strong', danger: 'bg-danger' }
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={`size-1.5 rounded-full ${colors[tone]}`} aria-hidden />
      {children}
    </span>
  )
}
