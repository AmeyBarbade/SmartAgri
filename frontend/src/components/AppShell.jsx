import { History, LayoutDashboard, LogOut, Map, Sprout } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/farms', label: 'Farms & fields', icon: Map },
  { to: '/history', label: 'History', icon: History },
]

export function Logo() {
  return (
    <span className="inline-flex items-center gap-2 font-semibold tracking-tight text-ink">
      <span className="grid size-6 place-items-center rounded bg-accent text-white">
        <Sprout className="size-4" aria-hidden />
      </span>
      AgriOptima
    </span>
  )
}

export default function AppShell() {
  const { user, logout } = useAuth()
  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-10 flex h-13 items-center justify-between border-b border-line bg-surface px-4 sm:px-5">
        <Logo />
        <div className="flex items-center gap-4 text-sm">
          <span className="hidden text-muted sm:inline">{user?.fullName ?? user?.email}</span>
          <button
            onClick={() => logout()}
            className="inline-flex items-center gap-1.5 text-muted hover:text-ink"
            title="Sign out"
          >
            <LogOut className="size-4" aria-hidden />
            <span>Sign out</span>
          </button>
        </div>
      </header>
      <div className="flex flex-1 flex-col md:flex-row">
        <nav className="border-b border-line bg-surface md:w-52 md:shrink-0 md:border-r md:border-b-0">
          <ul className="flex gap-1 overflow-x-auto px-3 py-2 md:flex-col md:py-4">
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={end}
                  className={({ isActive }) =>
                    `flex items-center gap-2.5 whitespace-nowrap rounded-md px-2.5 py-1.5 text-sm ${
                      isActive ? 'bg-accent-soft font-medium text-accent' : 'text-ink-2 hover:bg-canvas'
                    }`
                  }
                >
                  <Icon className="size-4" aria-hidden />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-8 lg:px-10">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
