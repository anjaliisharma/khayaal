import { BarChart3, Home, LogOut, Package, Settings, UserCog, Users } from 'lucide-react';
import type { PropsWithChildren } from 'react';
import { NavLink } from 'react-router-dom';
import { cn } from '../lib/utils';
import { ThemeToggle } from './ThemeToggle';
import { useAuth } from '../auth/authContext';
import { OfflineBanner } from './OfflineBanner';

const nav = [
  { to: '/', label: 'Today', icon: Home },
  { to: '/people', label: 'Family', icon: Users },
  { to: '/stock', label: 'Stock', icon: Package },
  { to: '/adherence', label: 'Adherence', icon: BarChart3 },
  { to: '/household', label: 'Household', icon: UserCog },
  { to: '/settings', label: 'Settings', icon: Settings }
];
function Navigation({ mobile = false }: { mobile?: boolean }) {
  return (
    <nav
      aria-label={mobile ? 'Primary mobile navigation' : 'Primary navigation'}
      className={cn(mobile ? 'grid grid-cols-6' : 'space-y-2')}
    >
      {nav.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          className={({ isActive }) =>
            cn(
              'flex min-h-12 items-center gap-3 rounded-xl px-3 text-sm font-medium',
              mobile && 'flex-col justify-center gap-1 px-1 py-2 text-xs',
              isActive
                ? 'bg-teal-100 text-teal-900 dark:bg-teal-900 dark:text-teal-50'
                : 'text-muted hover:bg-slate-100 dark:hover:bg-slate-800'
            )
          }
        >
          <Icon className="h-5 w-5" aria-hidden="true" />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
export function AppShell({ children }: PropsWithChildren) {
  const auth = useAuth();
  return (
    <div className="min-h-screen md:grid md:grid-cols-[16rem_1fr]">
      <aside className="hidden border-r bg-surface p-5 md:block">
        <div className="mb-8 flex items-center gap-3">
          <img src="/khayaal-icon.svg" alt="" className="h-10 w-10" />
          <div>
            <p className="text-xl font-bold">Khayaal</p>
            <p className="text-xs text-muted">Care, remembered</p>
          </div>
        </div>
        <Navigation />
      </aside>
      <div className="flex min-h-screen flex-col">
        <OfflineBanner />
        <header className="flex min-h-16 items-center justify-between border-b bg-surface px-5">
          <div className="flex items-center gap-3 md:hidden">
            <img src="/khayaal-icon.svg" alt="" className="h-9 w-9" />
            <span className="text-xl font-bold">Khayaal</span>
          </div>
          <span className="hidden text-sm text-muted md:block">
            Family medicine and refill tracker
          </span>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <button
              aria-label="Sign out"
              className="grid min-h-12 min-w-12 place-items-center rounded-xl"
              onClick={() => void auth.logout()}
            >
              <LogOut className="h-5 w-5" />
            </button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-5 py-8 pb-28 md:pb-8">{children}</main>
        <footer className="border-t bg-surface px-5 py-5 text-center text-sm text-muted">
          Khayaal helps you remember and track. It is not medical advice. Follow your doctor's and
          pharmacist's instructions.
        </footer>
        <div className="fixed inset-x-0 bottom-0 border-t bg-surface md:hidden">
          <Navigation mobile />
        </div>
      </div>
    </div>
  );
}
