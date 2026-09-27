import { Globe, History, LayoutDashboard, LogOut, Map, Sprout } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/map', label: 'GIS Satellite Map', icon: Globe },
  { to: '/farms', label: 'Farms & fields', icon: Map },
  { to: '/history', label: 'History', icon: History },
]

export function Logo() {
  return (
    <div className="inline-flex items-center gap-2.5 tracking-tight text-ink">
      <span className="grid size-7 place-items-center rounded-lg bg-emerald-700 text-white shadow-xs">
        <Sprout className="size-4.5" aria-hidden />
      </span>
      <span className="font-bold text-base leading-tight text-slate-900">AgriOptima</span>
    </div>
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
        <nav className="flex flex-col justify-between border-b border-slate-200/90 bg-[#f8fafc] md:w-56 md:shrink-0 md:border-r md:border-b-0 md:border-slate-200/90 shadow-[1px_0_6px_rgba(0,0,0,0.03)]">
          <ul className="flex gap-1 overflow-x-auto px-3 py-2.5 md:flex-col md:gap-1.5 md:py-4">
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  end={end}
                  className={({ isActive }) =>
                    `flex items-center gap-2.5 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                      isActive
                        ? 'bg-emerald-700 font-semibold text-white shadow-sm'
                        : 'text-slate-600 hover:bg-slate-200/60 hover:text-slate-900'
                    }`
                  }
                >
                  <Icon className="size-4 shrink-0" aria-hidden />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
          <div className="hidden border-t border-slate-200/80 p-4 md:block bg-slate-100/50">
            <p className="text-[11px] font-bold uppercase tracking-wider text-slate-500">PSAI01 · AgriOptima</p>
            <p className="text-xs text-slate-500 mt-0.5">Sustainable Fertilizer Optimizer</p>
          </div>
        </nav>
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-8 lg:px-10">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
      <footer className="border-t border-slate-200/80 bg-white py-3 px-6 text-center text-xs text-slate-500">
        <p>
          PSAI01: Sustainable Fertilizer Usage Optimizer &nbsp;·&nbsp;
          ICAR STCR & IPNS Agronomic Engine
        </p>
      </footer>
    </div>
  )
}
