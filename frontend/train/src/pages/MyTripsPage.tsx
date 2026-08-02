import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Train, Users, Calendar, AlertTriangle, Sparkles } from 'lucide-react';
import bookingsData from '@/data/bookings.json';
import type { Booking } from '@/types';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { formatINR } from '@/lib/format';
import { cn } from '@/lib/cn';

type Tab = 'upcoming' | 'past';

export function MyTripsPage() {
  const [tab, setTab] = useState<Tab>('upcoming');
  const bookings = bookingsData.bookings as Booking[];
  const filtered = bookings.filter((b) => b.tripType === tab);

  return (
    <div>
      <PageHeader title="My Trips" subtitle="Manage your upcoming and past journeys" />

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="mb-6 flex gap-1 rounded-btn bg-navy-50 p-1">
          {(['upcoming', 'past'] as Tab[]).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={cn(
                'flex-1 rounded-btn px-4 py-2.5 text-14 font-semibold capitalize transition-all',
                tab === t ? 'bg-white text-navy-600 shadow-card' : 'text-navy-400 hover:text-navy-500',
              )}
            >
              {t} Trips
              <span className="ml-2 text-12 text-navy-300">
                {bookings.filter((b) => b.tripType === t).length}
              </span>
            </button>
          ))}
        </div>

        {filtered.length > 0 ? (
          <div className="space-y-4">
            {filtered.map((booking) => (
              <Link
                key={booking.id}
                to={booking.tripType === 'upcoming' ? '/dashboard' : `/trips/${booking.id}`}
                className="card group block p-5 transition-all hover:shadow-hover"
              >
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-md bg-navy-50 px-2 py-0.5 text-12 font-bold text-navy-600">
                        {booking.trainNumber}
                      </span>
                      <span className="rounded-md bg-accent-50 px-2 py-0.5 text-12 font-medium text-accent-600">
                        {booking.trainType}
                      </span>
                      <StatusBadge status={booking.status} />
                    </div>
                    <h3 className="mt-2 text-16 font-semibold text-navy-600">{booking.trainName}</h3>

                    <div className="mt-4 flex items-center gap-4">
                      <div className="text-center">
                        <p className="text-20 font-bold text-navy-600">{booking.departure}</p>
                        <p className="text-12 text-navy-400">{booking.from}</p>
                      </div>
                      <div className="flex flex-1 flex-col items-center">
                        <p className="text-12 text-navy-400">{booking.duration}</p>
                        <div className="relative my-1 h-px w-full bg-navy-100">
                          <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
                            <Train className="h-3.5 w-3.5 text-navy-300" />
                          </div>
                        </div>
                        <p className="text-12 text-navy-300">{booking.fromCode} → {booking.toCode}</p>
                      </div>
                      <div className="text-center">
                        <p className="text-20 font-bold text-navy-600">{booking.arrival}</p>
                        <p className="text-12 text-navy-400">{booking.to}</p>
                      </div>
                    </div>
                  </div>

                  <div className="flex flex-col items-end gap-3 sm:items-end">
                    <div className="text-right">
                      <p className="text-12 text-navy-400">PNR</p>
                      <p className="text-16 font-bold text-navy-600">{booking.pnr}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-12 text-navy-400">Total Fare</p>
                      <p className="text-16 font-bold text-navy-600">{formatINR(booking.totalFare)}</p>
                    </div>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap items-center gap-4 border-t border-navy-100 pt-4 text-12 text-navy-400">
                  <span className="flex items-center gap-1.5">
                    <Calendar className="h-3.5 w-3.5" />
                    {booking.journeyDate}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <Users className="h-3.5 w-3.5" />
                    {booking.passengers.length} passengers
                  </span>
                  <span className="flex items-center gap-1.5">
                    <Train className="h-3.5 w-3.5" />
                    {booking.className}
                  </span>
                </div>

                {booking.hasDisruption && booking.disruption && (
                  <div className={cn(
                    'mt-4 rounded-btn border p-4',
                    booking.status === 'Rebooked' ? 'border-accent-200 bg-accent-50' : 'border-status-delayed/20 bg-status-delayed/5',
                  )}>
                    <div className="flex items-start gap-3">
                      {booking.status === 'Rebooked' ? (
                        <Sparkles className="h-4 w-4 flex-shrink-0 text-accent-600" />
                      ) : (
                        <AlertTriangle className="h-4 w-4 flex-shrink-0 text-status-delayed" />
                      )}
                      <div className="flex-1">
                        <p className={cn('text-14 font-semibold', booking.status === 'Rebooked' ? 'text-accent-600' : 'text-status-delayed')}>
                          {booking.status === 'Rebooked' ? 'Rebooked automatically' : 'Disruption detected'}
                        </p>
                        <p className="mt-0.5 text-12 text-navy-400">{booking.disruption.reason}</p>
                        {booking.disruption.alternativeTrain && (
                          <p className="mt-2 text-12 text-navy-500">
                            Alternative: {booking.disruption.alternativeTrain.trainName} ({booking.disruption.alternativeTrain.trainNumber})
                            {booking.disruption.alternativeTrain.arrivesEarlier && ` — arrives ${Math.floor(booking.disruption.alternativeTrain.savesMinutes / 60)}h ${booking.disruption.alternativeTrain.savesMinutes % 60}m earlier`}
                          </p>
                        )}
                      </div>
                    </div>
                  </div>
                )}

                <div className="mt-4 flex items-center justify-end gap-1 text-14 font-medium text-accent-600 group-hover:gap-2 transition-all">
                  {booking.tripType === 'upcoming' ? 'View Live Dashboard' : 'View Trip'}
                  <ArrowRight className="h-4 w-4" />
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <div className="card flex flex-col items-center justify-center p-16 text-center">
            <Train className="h-12 w-12 text-navy-200" />
            <h3 className="mt-4 text-20 font-semibold text-navy-600">No {tab} trips</h3>
            <p className="mt-2 text-14 text-navy-400">
              {tab === 'upcoming' ? "You don't have any upcoming trips. Search for trains to book your next journey." : "You haven't completed any trips yet."}
            </p>
            <Link to="/search" className="btn-accent mt-6">
              Search Trains
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
