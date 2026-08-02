import { Link } from 'react-router-dom';
import { Mail, Phone, Award, Calendar, Ticket, TrendingUp, LogOut, ChevronRight } from 'lucide-react';
import usersData from '@/data/users.json';
import bookingsData from '@/data/bookings.json';
import type { User } from '@/types';
import { PageHeader } from '@/components/PageHeader';
import { formatINR } from '@/lib/format';

export function ProfilePage() {
  const user = usersData.user as User;
  const bookings = bookingsData.bookings;
  const totalSpent = bookings.reduce((sum, b) => sum + b.totalFare, 0);

  return (
    <div>
      <PageHeader title="Profile" subtitle="Your account and travel history" />

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Profile Card */}
          <div className="lg:col-span-1">
            <div className="card p-6 text-center">
              <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-accent-500 text-24 font-bold text-white">
                {user.avatar}
              </div>
              <h3 className="mt-4 text-20 font-bold text-navy-600">{user.name}</h3>
              <p className="text-14 text-navy-400">{user.email}</p>
              <div className="mt-3 inline-flex items-center gap-1.5 rounded-full bg-accent-50 px-3 py-1 text-12 font-semibold text-accent-600">
                <Award className="h-3.5 w-3.5" />
                {user.tier} Member
              </div>

              <div className="mt-6 space-y-3 border-t border-navy-100 pt-6 text-left">
                <div className="flex items-center gap-3 text-14">
                  <Mail className="h-4 w-4 text-navy-400" />
                  <span className="text-navy-500">{user.email}</span>
                </div>
                <div className="flex items-center gap-3 text-14">
                  <Phone className="h-4 w-4 text-navy-400" />
                  <span className="text-navy-500">{user.phone}</span>
                </div>
                <div className="flex items-center gap-3 text-14">
                  <Calendar className="h-4 w-4 text-navy-400" />
                  <span className="text-navy-500">Member since {user.memberSince}</span>
                </div>
              </div>

              <div className="mt-6 space-y-2 border-t border-navy-100 pt-4">
                <Link to="/login" className="flex w-full items-center justify-between rounded-btn px-3 py-2.5 text-14 text-status-cancelled hover:bg-status-cancelled/5">
                  <span className="flex items-center gap-2">
                    <LogOut className="h-4 w-4" />
                    Sign Out
                  </span>
                  <ChevronRight className="h-4 w-4" />
                </Link>
              </div>
            </div>
          </div>

          {/* Stats & Saved Passengers */}
          <div className="space-y-6 lg:col-span-2">
            {/* Stats Grid */}
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div className="card p-5">
                <div className="flex h-10 w-10 items-center justify-center rounded-btn bg-navy-50">
                  <Ticket className="h-5 w-5 text-navy-600" />
                </div>
                <p className="mt-3 text-32 font-bold text-navy-600">{user.tripsTaken}</p>
                <p className="text-12 text-navy-400">Trips Taken</p>
              </div>
              <div className="card p-5">
                <div className="flex h-10 w-10 items-center justify-center rounded-btn bg-accent-50">
                  <TrendingUp className="h-5 w-5 text-accent-600" />
                </div>
                <p className="mt-3 text-32 font-bold text-navy-600">{formatINR(totalSpent)}</p>
                <p className="text-12 text-navy-400">Total Spent</p>
              </div>
              <div className="card p-5">
                <div className="flex h-10 w-10 items-center justify-center rounded-btn bg-status-ontime/10">
                  <Award className="h-5 w-5 text-status-ontime" />
                </div>
                <p className="mt-3 text-32 font-bold text-navy-600">{user.tier}</p>
                <p className="text-12 text-navy-400">Membership Tier</p>
              </div>
              <div className="card p-5">
                <div className="flex h-10 w-10 items-center justify-center rounded-btn bg-status-delayed/10">
                  <Calendar className="h-5 w-5 text-status-delayed" />
                </div>
                <p className="mt-3 text-32 font-bold text-navy-600">{bookings.filter((b) => b.hasDisruption).length}</p>
                <p className="text-12 text-navy-400">Disruptions Handled</p>
              </div>
            </div>

            {/* Saved Passengers */}
            <div className="card p-6">
              <h3 className="text-16 font-semibold text-navy-600">Saved Passengers</h3>

              <div className="mt-4 space-y-3">
                {usersData.passengers.map((p) => (
                  <div key={p.id} className="flex items-center justify-between rounded-btn border border-navy-100 p-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-navy-50 text-14 font-bold text-navy-600">
                        {p.name.split(' ').map((n) => n[0]).join('')}
                      </div>
                      <div>
                        <p className="text-14 font-medium text-navy-600">{p.name}</p>
                        <p className="text-12 text-navy-400">{p.age} yrs · {p.gender} · Prefers {p.berthPreference}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Recent Bookings Summary */}
            <div className="card p-6">
              <div className="flex items-center justify-between">
                <h3 className="text-16 font-semibold text-navy-600">Recent Bookings</h3>
                <Link to="/trips" className="text-14 font-medium text-accent-600 hover:text-accent-700">
                  View all
                </Link>
              </div>
              <div className="mt-4 space-y-3">
                {bookings.slice(0, 3).map((b) => (
                  <Link
                    key={b.id}
                    to={b.tripType === 'upcoming' ? '/dashboard' : `/trips/${b.id}`}
                    className="flex items-center justify-between rounded-btn border border-navy-100 p-4 transition-colors hover:bg-navy-50"
                  >
                    <div>
                      <p className="text-14 font-medium text-navy-600">{b.trainName} ({b.trainNumber})</p>
                      <p className="text-12 text-navy-400">{b.from} → {b.to} · {b.journeyDate}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-14 font-bold text-navy-600">{formatINR(b.totalFare)}</p>
                      <p className="text-12 text-navy-400">PNR: {b.pnr}</p>
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
