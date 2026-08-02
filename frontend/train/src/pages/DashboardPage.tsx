import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Train, AlertTriangle, Sparkles, Clock, ArrowRight, Hotel as HotelIcon, Car,
  Bell, TrendingUp, CheckCircle2,
} from 'lucide-react';
import {
  ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid, BarChart, Bar,
} from 'recharts';
import bookingsData from '@/data/bookings.json';
import trainsData from '@/data/trains.json';
import hotelsData from '@/data/hotels.json';
import cabsData from '@/data/cabs.json';
import notificationsData from '@/data/notifications.json';
import type { Booking, NotificationItem, Train as TrainType } from '@/types';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { formatINR, severityColor, classStatusColor, classStatusLabel, classStatusBg } from '@/lib/format';
import { cn } from '@/lib/cn';

const disruptionTrendData = [
  { month: 'Feb', disruptions: 3, rebooked: 2 },
  { month: 'Mar', disruptions: 5, rebooked: 4 },
  { month: 'Apr', disruptions: 2, rebooked: 2 },
  { month: 'May', disruptions: 6, rebooked: 5 },
  { month: 'Jun', disruptions: 4, rebooked: 3 },
  { month: 'Jul', disruptions: 7, rebooked: 6 },
];

const onTimeData = [
  { route: 'NDLS-BCT', onTime: 92 },
  { route: 'NDLS-HWH', onTime: 90 },
  { route: 'NDLS-SBC', onTime: 79 },
  { route: 'NDLS-LKO', onTime: 88 },
  { route: 'SC-MAS', onTime: 77 },
];

function computeNewArrival(arrival: string, delayMinutes: number): string {
  const [h, m] = arrival.split(':').map(Number);
  const total = h * 60 + m + delayMinutes;
  const newH = Math.floor((total % 1440) / 60);
  const newM = total % 60;
  const period = newH >= 12 ? 'PM' : 'AM';
  const displayH = newH % 12 || 12;
  return `${String(displayH).padStart(2, '0')}:${String(newM).padStart(2, '0')} ${period}`;
}

export function DashboardPage() {
  const { id } = useParams();
  const bookings = bookingsData.bookings as Booking[];
  const currentTrip = id
    ? bookings.find((b) => b.id === id)
    : bookings.find((b) => b.tripType === 'upcoming' && b.hasDisruption) || bookings.find((b) => b.tripType === 'upcoming');
  const hotel = hotelsData.hotels.find((h) => h.bookingId === currentTrip?.id);
  const cab = cabsData.cabs.find((c) => c.bookingId === currentTrip?.id);
  const notifications = (notificationsData.notifications as NotificationItem[]).filter((n) => n.bookingId === currentTrip?.id).slice(0, 4);

  const [rebookDecision, setRebookDecision] = useState<'none' | 'rebooked' | 'kept'>('none');

  const trainData = currentTrip
    ? (trainsData.trains as TrainType[]).find((t) => t.id === currentTrip.trainId)
    : null;

  if (!currentTrip) {
    return (
      <div>
        <PageHeader title="Live Dashboard" subtitle="Track your current journey in real-time" />
        <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
          <div className="card flex flex-col items-center justify-center p-16 text-center">
            <Train className="h-12 w-12 text-navy-200" />
            <h3 className="mt-4 text-20 font-semibold text-navy-600">No active journey</h3>
            <p className="mt-2 text-14 text-navy-400">You don't have any upcoming trips to track.</p>
            <Link to="/search" className="btn-accent mt-6">Search Trains</Link>
          </div>
        </div>
      </div>
    );
  }

  const newArrival = currentTrip.disruption?.delayMinutes
    ? computeNewArrival(currentTrip.arrival, currentTrip.disruption.delayMinutes)
    : '';

  return (
    <div>
      <PageHeader title="Live Dashboard" subtitle="Real-time monitoring of your current journey" />

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Main column */}
          <div className="space-y-6 lg:col-span-2">
            {/* Current Journey Card */}
            <div className="card p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="rounded-md bg-navy-50 px-2 py-0.5 text-12 font-bold text-navy-600">{currentTrip.trainNumber}</span>
                  <span className="rounded-md bg-accent-50 px-2 py-0.5 text-12 font-medium text-accent-600">{currentTrip.trainType}</span>
                </div>
                <StatusBadge status={currentTrip.status} />
              </div>
              <h3 className="mt-2 text-20 font-bold text-navy-600">{currentTrip.trainName}</h3>

              <div className="mt-6 flex items-center gap-4">
                <div className="text-center">
                  <p className="text-24 font-bold text-navy-600">{currentTrip.departure}</p>
                  <p className="text-12 text-navy-400">{currentTrip.from}</p>
                </div>
                <div className="flex flex-1 flex-col items-center">
                  <p className="text-12 text-navy-400">{currentTrip.duration}</p>
                  <div className="relative my-1 h-px w-full bg-navy-100">
                    <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
                      <Train className="h-3.5 w-3.5 text-navy-300" />
                    </div>
                  </div>
                  <p className="text-12 text-navy-300">{currentTrip.fromCode} → {currentTrip.toCode}</p>
                </div>
                <div className="text-center">
                  <p className="text-24 font-bold text-navy-600">{currentTrip.arrival}</p>
                  <p className="text-12 text-navy-400">{currentTrip.to}</p>
                </div>
              </div>

              <div className="mt-6 grid grid-cols-2 gap-4 border-t border-navy-100 pt-4 sm:grid-cols-4">
                <div>
                  <p className="label-text">PNR</p>
                  <p className="text-14 font-bold text-navy-600">{currentTrip.pnr}</p>
                </div>
                <div>
                  <p className="label-text">Date</p>
                  <p className="text-14 font-medium text-navy-600">{currentTrip.journeyDate}</p>
                </div>
                <div>
                  <p className="label-text">Class</p>
                  <p className="text-14 font-medium text-navy-600">{currentTrip.className}</p>
                </div>
                <div>
                  <p className="label-text">Passengers</p>
                  <p className="text-14 font-medium text-navy-600">{currentTrip.passengers.length}</p>
                </div>
              </div>
            </div>

            {/* Disruption Banner */}
            {currentTrip.hasDisruption && currentTrip.disruption && (
              <div className="card overflow-hidden border-status-delayed/30">
                <div className="flex items-center gap-2 bg-status-delayed/10 px-6 py-3">
                  <AlertTriangle className="h-4 w-4 text-status-delayed" />
                  <span className="text-14 font-semibold text-status-delayed">Disruption Detected</span>
                </div>
                <div className="p-6">
                  <p className="text-16 text-navy-600">{currentTrip.disruption.reason}</p>
                  {currentTrip.disruption.delayMinutes > 0 && (
                    <div className="mt-3 flex items-center gap-2 text-14">
                      <Clock className="h-4 w-4 text-status-delayed" />
                      <span className="text-status-delayed font-bold">+{currentTrip.disruption.delayMinutes} min delay</span>
                      <span className="text-navy-400">· New arrival: {newArrival}</span>
                    </div>
                  )}

                  {currentTrip.disruption.alternativeTrain && rebookDecision !== 'rebooked' && (
                    <div className="mt-6 rounded-card border border-accent-200 bg-accent-50 p-5">
                      <div className="flex items-center gap-2">
                        <Sparkles className="h-4 w-4 text-accent-600" />
                        <span className="text-14 font-semibold text-accent-600">AI Recommended Alternative</span>
                      </div>
                      <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p className="text-16 font-bold text-navy-600">
                            {currentTrip.disruption.alternativeTrain.trainName}
                          </p>
                          <p className="text-12 text-navy-400">
                            {currentTrip.disruption.alternativeTrain.trainNumber} · Departs {currentTrip.disruption.alternativeTrain.departure}
                          </p>
                          {currentTrip.disruption.alternativeTrain.arrivesEarlier && (
                            <p className="mt-1 text-12 font-medium text-status-ontime">
                              Arrives {Math.floor(currentTrip.disruption.alternativeTrain.savesMinutes / 60)}h {currentTrip.disruption.alternativeTrain.savesMinutes % 60}m earlier
                            </p>
                          )}
                        </div>
                        <div className="flex items-center gap-4">
                          <div className="text-right">
                            <p className="text-12 text-navy-400">{currentTrip.disruption.alternativeTrain.class}</p>
                            <p className="text-20 font-bold text-navy-600">{formatINR(currentTrip.disruption.alternativeTrain.fare)}</p>
                          </div>
                          <div className="rounded-md bg-accent-500 px-3 py-1.5 text-12 font-bold text-white">
                            {currentTrip.disruption.alternativeTrain.available} seats
                          </div>
                        </div>
                      </div>
                      <div className="mt-4 flex gap-3">
                        <button onClick={() => setRebookDecision('rebooked')} className="btn-accent flex-1">
                          <Sparkles className="h-4 w-4" />
                          Rebook Automatically
                        </button>
                        <button onClick={() => setRebookDecision('kept')} className="btn-outline flex-1">
                          Keep Current Train
                        </button>
                      </div>
                    </div>
                  )}

                  {rebookDecision === 'rebooked' && (
                    <div className="mt-4 flex items-center gap-2 rounded-btn bg-accent-50 p-4">
                      <CheckCircle2 className="h-5 w-5 text-accent-600" />
                      <p className="text-14 font-medium text-accent-600">
                        Rebooked on {currentTrip.disruption.alternativeTrain?.trainName} ({currentTrip.disruption.alternativeTrain?.trainNumber}). Hotel and cab updated automatically.
                      </p>
                    </div>
                  )}

                  {rebookDecision === 'kept' && (
                    <div className="mt-4 flex items-center gap-2 rounded-btn bg-navy-50 p-4">
                      <CheckCircle2 className="h-5 w-5 text-navy-400" />
                      <p className="text-14 font-medium text-navy-500">
                        Keeping current train. We'll continue monitoring for further changes.
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Seat Alert Widget — data-driven from train classes */}
            {trainData && (
              <div className="card p-6">
                <h3 className="text-16 font-semibold text-navy-600">Seat Availability</h3>
                <div className="mt-4 space-y-3">
                  {trainData.classes.map((cls) => (
                    <div key={cls.code} className="flex items-center justify-between rounded-btn border border-navy-100 p-4">
                      <div className="flex items-center gap-3">
                        <div className={cn('flex h-10 w-10 items-center justify-center rounded-btn', cls.status === 'available' ? 'bg-status-ontime/10' : cls.status === 'limited' ? 'bg-status-delayed/10' : 'bg-status-waitlist/10')}>
                          {cls.status === 'available' ? <CheckCircle2 className={cn('h-5 w-5', classStatusColor(cls.status))} /> : <AlertTriangle className={cn('h-5 w-5', classStatusColor(cls.status))} />}
                        </div>
                        <div>
                          <p className="text-14 font-medium text-navy-600">{cls.name}</p>
                          <p className="text-12 text-navy-400">{trainData.name} ({trainData.number})</p>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className={cn('h-2 w-2 rounded-full', classStatusBg(cls.status))} />
                        <span className={cn('text-12 font-semibold', classStatusColor(cls.status))}>
                          {cls.available > 0 ? `${cls.available} seats · ${classStatusLabel(cls.status)}` : classStatusLabel(cls.status)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Hotel & Cab Cards */}
            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
              {hotel && (
                <Link to={`/hotel/${hotel.id}`} className="card group block p-5 transition-all hover:shadow-hover">
                  <div className="flex items-center gap-2">
                    <HotelIcon className="h-4 w-4 text-navy-400" />
                    <span className="label-text">Hotel Status</span>
                  </div>
                  <h4 className="mt-2 text-16 font-semibold text-navy-600">{hotel.name}</h4>
                  <p className="text-12 text-navy-400">{hotel.city} · {hotel.nights} nights</p>
                  <div className="mt-3 flex items-center gap-2 rounded-btn bg-accent-50 px-3 py-2">
                    <CheckCircle2 className="h-3.5 w-3.5 text-accent-600" />
                    <span className="text-12 font-medium text-accent-600">Check-in updated to {hotel.updatedCheckInTime}</span>
                  </div>
                  <div className="mt-3 flex items-center justify-end gap-1 text-12 font-medium text-accent-600 group-hover:gap-2 transition-all">
                    View details <ArrowRight className="h-3.5 w-3.5" />
                  </div>
                </Link>
              )}

              {cab && (
                <Link to={`/cab/${cab.id}`} className="card group block p-5 transition-all hover:shadow-hover">
                  <div className="flex items-center gap-2">
                    <Car className="h-4 w-4 text-navy-400" />
                    <span className="label-text">Cab Status</span>
                  </div>
                  <h4 className="mt-2 text-16 font-semibold text-navy-600">{cab.vehicle.model}</h4>
                  <p className="text-12 text-navy-400">{cab.vehicle.plate} · {cab.driver.name}</p>
                  <div className="mt-3 flex items-center gap-2 rounded-btn bg-accent-50 px-3 py-2">
                    <CheckCircle2 className="h-3.5 w-3.5 text-accent-600" />
                    <span className="text-12 font-medium text-accent-600">Pickup at {cab.after.pickupTime}</span>
                  </div>
                  <div className="mt-3 flex items-center justify-end gap-1 text-12 font-medium text-accent-600 group-hover:gap-2 transition-all">
                    View details <ArrowRight className="h-3.5 w-3.5" />
                  </div>
                </Link>
              )}
            </div>
          </div>

          {/* Sidebar */}
          <div className="space-y-6 lg:col-span-1">
            {/* Notification Panel */}
            <div className="card p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Bell className="h-4 w-4 text-navy-400" />
                  <h3 className="text-16 font-semibold text-navy-600">Recent Alerts</h3>
                </div>
                <Link to="/notifications" className="text-12 font-medium text-accent-600 hover:text-accent-700">
                  View all
                </Link>
              </div>
              <div className="mt-4 space-y-3">
                {notifications.map((notif) => (
                  <div key={notif.id} className="flex gap-3">
                    <div className={cn('mt-1 h-2 w-2 flex-shrink-0 rounded-full', severityColor(notif.severity))} />
                    <div className="min-w-0 flex-1">
                      <p className="text-14 font-medium text-navy-600">{notif.title}</p>
                      <p className="text-12 text-navy-400">{notif.timestamp}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Analytics Chart */}
            <div className="card p-6">
              <div className="flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-navy-400" />
                <h3 className="text-16 font-semibold text-navy-600">Disruption History</h3>
              </div>
              <p className="mt-1 text-12 text-navy-400">Last 6 months</p>
              <div className="mt-4 h-40">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={disruptionTrendData} margin={{ top: 5, right: 5, bottom: 0, left: -25 }}>
                    <defs>
                      <linearGradient id="disruptGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#E8871E" stopOpacity={0.3} />
                        <stop offset="100%" stopColor="#E8871E" stopOpacity={0} />
                      </linearGradient>
                      <linearGradient id="rebookGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#0F9D8E" stopOpacity={0.3} />
                        <stop offset="100%" stopColor="#0F9D8E" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E8EEF6" vertical={false} />
                    <XAxis dataKey="month" tick={{ fontSize: 11, fill: '#6D8EBA' }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 11, fill: '#6D8EBA' }} axisLine={false} tickLine={false} />
                    <Tooltip
                      contentStyle={{ borderRadius: 8, border: '1px solid #C9D6E8', fontSize: 12 }}
                      labelStyle={{ color: '#0B2447', fontWeight: 600 }}
                    />
                    <Area type="monotone" dataKey="disruptions" stroke="#E8871E" strokeWidth={2} fill="url(#disruptGrad)" />
                    <Area type="monotone" dataKey="rebooked" stroke="#0F9D8E" strokeWidth={2} fill="url(#rebookGrad)" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
              <div className="mt-3 flex items-center gap-4 text-12">
                <span className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-status-delayed" />
                  <span className="text-navy-400">Disruptions</span>
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-accent-500" />
                  <span className="text-navy-400">Auto-rebooked</span>
                </span>
              </div>
            </div>

            {/* On-Time Performance */}
            <div className="card p-6">
              <h3 className="text-16 font-semibold text-navy-600">On-Time Performance</h3>
              <p className="mt-1 text-12 text-navy-400">By route (%)</p>
              <div className="mt-4 h-40">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={onTimeData} margin={{ top: 5, right: 5, bottom: 0, left: -25 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E8EEF6" vertical={false} />
                    <XAxis dataKey="route" tick={{ fontSize: 10, fill: '#6D8EBA' }} axisLine={false} tickLine={false} />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: '#6D8EBA' }} axisLine={false} tickLine={false} />
                    <Tooltip
                      contentStyle={{ borderRadius: 8, border: '1px solid #C9D6E8', fontSize: 12 }}
                      labelStyle={{ color: '#0B2447', fontWeight: 600 }}
                    />
                    <Bar dataKey="onTime" fill="#0F9D8E" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
