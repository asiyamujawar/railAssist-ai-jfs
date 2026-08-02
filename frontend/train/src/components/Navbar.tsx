import { Link, useLocation } from 'react-router-dom';
import { TrainFront, Home, Search, Ticket, LayoutDashboard, Bell, User, Menu, X } from 'lucide-react';
import { useState } from 'react';
import { cn } from '@/lib/cn';

const navItems = [
  { label: 'Home', path: '/', icon: Home },
  { label: 'Search Trains', path: '/search', icon: Search },
  { label: 'My Trips', path: '/trips', icon: Ticket },
  { label: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
  { label: 'Notifications', path: '/notifications', icon: Bell },
];

export function Navbar() {
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 border-b border-navy-100 bg-white/95 backdrop-blur-sm">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 lg:px-8">
        <Link to="/" className="flex items-center gap-2.5" onClick={() => setMobileOpen(false)}>
          <div className="flex h-9 w-9 items-center justify-center rounded-btn bg-navy-600">
            <TrainFront className="h-5 w-5 text-white" strokeWidth={2} />
          </div>
          <span className="text-20 font-bold text-navy-600">TrainMate</span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path);
            return (
              <Link
                key={item.path}
                to={item.path}
                className={cn(
                  'flex items-center gap-2 rounded-btn px-3 py-2 text-14 font-medium transition-colors',
                  active ? 'bg-navy-600 text-white' : 'text-navy-500 hover:bg-navy-50 hover:text-navy-600',
                )}
              >
                <Icon className="h-4 w-4" strokeWidth={2} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="hidden md:block">
          <Link
            to="/profile"
            className="flex items-center gap-2 rounded-btn border border-navy-100 px-3 py-1.5 text-14 font-medium text-navy-600 transition-colors hover:bg-navy-50"
          >
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-accent-500 text-12 font-bold text-white">
              AM
            </div>
            <span>Arjun M.</span>
          </Link>
        </div>

        <button
          className="flex h-9 w-9 items-center justify-center rounded-btn text-navy-600 md:hidden"
          onClick={() => setMobileOpen(!mobileOpen)}
        >
          {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>

      {mobileOpen && (
        <nav className="border-t border-navy-100 bg-white px-4 py-3 md:hidden">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path);
            return (
              <Link
                key={item.path}
                to={item.path}
                onClick={() => setMobileOpen(false)}
                className={cn(
                  'flex items-center gap-3 rounded-btn px-3 py-2.5 text-14 font-medium',
                  active ? 'bg-navy-600 text-white' : 'text-navy-500 hover:bg-navy-50',
                )}
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
          <Link
            to="/profile"
            onClick={() => setMobileOpen(false)}
            className="mt-1 flex items-center gap-3 rounded-btn px-3 py-2.5 text-14 font-medium text-navy-500 hover:bg-navy-50"
          >
            <User className="h-4 w-4" />
            Profile
          </Link>
        </nav>
      )}
    </header>
  );
}
