import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Search,
  Ticket,
  MapPin,
  BellRing,
  AlertOctagon,
  Hotel,
  Car,
  Bell,
  ShieldAlert,
  User,
  LogOut,
  TrainTrack,
  Sparkles,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

const AppSidebar = ({ isOpen, onClose }) => {
  const { user, isAdmin, logout } = useAuth();

  const navGroups = [
    {
      title: 'Main Navigation',
      items: [
        { label: 'Dashboard', icon: LayoutDashboard, path: '/dashboard' },
        { label: 'Search Trains', icon: Search, path: '/trains' },
        { label: 'My Bookings', icon: Ticket, path: '/bookings' },
        { label: 'My Journeys', icon: MapPin, path: '/journeys' },
        { label: 'Seat Alerts', icon: BellRing, path: '/alerts' },
        { label: 'Disruption Center', icon: AlertOctagon, path: '/disruptions' },
      ],
    },
    {
      title: 'Simulated Services',
      items: [
        { label: 'Hotel Bookings', icon: Hotel, path: '/hotels' },
        { label: 'Cab Bookings', icon: Car, path: '/cabs' },
        { label: 'Notifications', icon: Bell, path: '/notifications' },
      ],
    },
  ];

  if (isAdmin) {
    navGroups.push({
      title: 'Administration',
      items: [
        { label: 'Simulation Controls', icon: ShieldAlert, path: '/admin/simulation' },
      ],
    });
  }

  return (
    <>
      {/* Mobile Backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-slate-900/50 backdrop-blur-xs lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={`fixed top-0 left-0 z-40 h-screen w-64 bg-slate-900 text-slate-300 flex flex-col transition-transform duration-300 ease-in-out lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Brand Header */}
        <div className="flex items-center gap-3 px-6 py-5 border-b border-slate-800">
          <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-lg shadow-blue-600/30">
            <TrainTrack className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-base font-bold text-white tracking-wide leading-tight">TrainConcierge</h1>
            <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-amber-400">
              <Sparkles className="w-2.5 h-2.5" /> AI Disruption Concierge
            </span>
          </div>
        </div>

        {/* Navigation Links */}
        <div className="flex-1 overflow-y-auto px-4 py-6 space-y-6">
          {navGroups.map((group, idx) => (
            <div key={idx}>
              <h2 className="px-3 text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-2">
                {group.title}
              </h2>
              <div className="space-y-1">
                {group.items.map((item) => {
                  const Icon = item.icon;
                  return (
                    <NavLink
                      key={item.path}
                      to={item.path}
                      onClick={onClose}
                      className={({ isActive }) =>
                        `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all ${
                          isActive
                            ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20 font-semibold'
                            : 'hover:bg-slate-800/80 hover:text-white'
                        }`
                      }
                    >
                      <Icon className="w-4 h-4 shrink-0" />
                      <span>{item.label}</span>
                    </NavLink>
                  );
                })}
              </div>
            </div>
          ))}
        </div>

        {/* User Footer Profile */}
        <div className="p-4 border-t border-slate-800 bg-slate-950/40">
          <div className="flex items-center justify-between p-2 rounded-xl bg-slate-800/50">
            <div className="flex items-center gap-3 overflow-hidden">
              <div className="w-9 h-9 rounded-xl bg-blue-600/20 text-blue-400 font-bold flex items-center justify-center border border-blue-500/30">
                {user?.firstName ? user.firstName[0] : 'U'}
              </div>
              <div className="overflow-hidden">
                <p className="text-xs font-semibold text-white truncate">
                  {user?.firstName ? `${user.firstName} ${user.lastName || ''}` : 'User Account'}
                </p>
                <p className="text-[10px] text-slate-400 truncate">{user?.email}</p>
              </div>
            </div>
            <button
              onClick={logout}
              title="Logout"
              className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-slate-800 rounded-lg transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>
    </>
  );
};

export default AppSidebar;
