import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, Car, Phone, Star, MapPin, Check, Clock, Sparkles, User } from 'lucide-react';
import cabsData from '@/data/cabs.json';
import bookingsData from '@/data/bookings.json';
import type { Cab as CabType, Booking } from '@/types';
import { PageHeader } from '@/components/PageHeader';
import { formatINR } from '@/lib/format';

export function CabPage() {
  const { id } = useParams();
  const cab = (cabsData.cabs as CabType[]).find((c) => c.id === id);
  const booking = bookingsData.bookings.find((b) => b.id === cab?.bookingId) as Booking | undefined;

  if (!cab) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 lg:px-8">
        <p className="text-16 text-navy-400">Cab booking not found.</p>
        <Link to="/dashboard" className="btn-primary mt-4">Back to Dashboard</Link>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title="Cab Booking" subtitle={`${cab.vehicle.model} · ${cab.vehicle.plate}`}>
        <Link to="/dashboard" className="btn-outline">
          <ArrowLeft className="h-4 w-4" />
          Back to Dashboard
        </Link>
      </PageHeader>

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            {/* Vehicle & Driver Card */}
            <div className="card p-6">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="text-24 font-bold text-navy-600">{cab.vehicle.model}</h3>
                  <p className="mt-1 text-14 text-navy-400">{cab.vehicle.color} · {cab.vehicle.type}</p>
                </div>
                <div className="rounded-btn bg-navy-50 px-4 py-2 text-center">
                  <p className="text-12 text-navy-400">Plate</p>
                  <p className="text-14 font-bold text-navy-600">{cab.vehicle.plate}</p>
                </div>
              </div>

                <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div className="rounded-btn border border-navy-100 p-4">
                    <div className="flex items-center gap-2">
                      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-accent-50">
                        <User className="h-5 w-5 text-accent-600" />
                      </div>
                      <div>
                        <p className="label-text">Driver</p>
                        <p className="text-14 font-semibold text-navy-600">{cab.driver.name}</p>
                      </div>
                    </div>
                    <div className="mt-3 flex items-center gap-3 text-12 text-navy-400">
                      <span className="flex items-center gap-1">
                        <Star className="h-3 w-3 fill-accent-500 text-accent-500" />
                        {cab.driver.rating}
                      </span>
                      <span>{cab.driver.trips} trips</span>
                    </div>
                    <a href={`tel:${cab.driver.phone}`} className="mt-3 flex items-center gap-2 text-14 font-medium text-accent-600">
                      <Phone className="h-3.5 w-3.5" />
                      {cab.driver.phone}
                    </a>
                  </div>

                  <div className="rounded-btn border border-navy-100 p-4">
                    <p className="label-text mb-2">Trip</p>
                    <div className="space-y-2 text-14">
                      <div className="flex items-start gap-2">
                        <MapPin className="mt-0.5 h-3.5 w-3.5 flex-shrink-0 text-navy-400" />
                        <div>
                          <p className="text-12 text-navy-400">Pickup</p>
                          <p className="font-medium text-navy-600">{cab.pickup}</p>
                        </div>
                      </div>
                      <div className="flex items-start gap-2">
                        <MapPin className="mt-0.5 h-3.5 w-3.5 flex-shrink-0 text-accent-500" />
                        <div>
                          <p className="text-12 text-navy-400">Destination</p>
                          <p className="font-medium text-navy-600">{cab.destination}</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-4 flex items-center justify-between border-t border-navy-100 pt-4">
                  <span className="text-14 text-navy-400">Estimated Fare</span>
                  <span className="text-20 font-bold text-navy-600">{formatINR(cab.estimatedFare)}</span>
                </div>
            </div>

            {/* Before/After Comparison */}
            <div className="card p-6">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-accent-600" />
                <h3 className="text-16 font-semibold text-navy-600">AI-Updated Pickup</h3>
              </div>
              <p className="mt-1 text-14 text-navy-400">
                Pickup rescheduled automatically when your train was rebooked.
              </p>

              <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="rounded-card border border-navy-100 p-5">
                  <div className="flex items-center gap-2">
                    <Clock className="h-4 w-4 text-navy-300" />
                    <span className="label-text">Before</span>
                  </div>
                  <p className="mt-3 text-32 font-bold text-navy-300 line-through">{cab.before.pickupTime}</p>
                  <p className="mt-2 text-14 text-navy-400">{cab.before.note}</p>
                </div>

                <div className="rounded-card border border-accent-200 bg-accent-50 p-5">
                  <div className="flex items-center gap-2">
                    <Check className="h-4 w-4 text-accent-600" />
                    <span className="label-text text-accent-600">After</span>
                  </div>
                  <p className="mt-3 text-32 font-bold text-accent-600">{cab.after.pickupTime}</p>
                  <p className="mt-2 text-14 text-navy-500">{cab.after.note}</p>
                </div>
              </div>

              <div className="mt-4 flex items-center gap-2 rounded-btn bg-navy-50 p-3 text-12 text-navy-400">
                <Sparkles className="h-3.5 w-3.5 text-accent-500" />
                Driver notified automatically — no extra charge for rescheduling.
              </div>
            </div>
          </div>

          {/* Sidebar */}
          <div className="lg:col-span-1">
            <div className="card sticky top-20 p-6">
              <h3 className="text-14 font-semibold text-navy-600">Connected Journey</h3>
              {booking && (
                <div className="mt-4 space-y-3 text-14">
                  <div className="flex justify-between">
                    <span className="text-navy-400">Train</span>
                    <span className="font-medium text-navy-600">{booking.trainName}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-navy-400">Route</span>
                    <span className="font-medium text-navy-600">{booking.from} → {booking.to}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-navy-400">Date</span>
                    <span className="font-medium text-navy-600">{booking.journeyDate}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-navy-400">PNR</span>
                    <span className="font-medium text-navy-600">{booking.pnr}</span>
                  </div>
                </div>
              )}

              <div className="mt-6 border-t border-navy-100 pt-4">
                <Link to="/dashboard" className="btn-primary w-full">
                  <ArrowLeft className="h-4 w-4" />
                  Back to Dashboard
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
