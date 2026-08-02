import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, Hotel, MapPin, Bed, Check, Clock, Sparkles } from 'lucide-react';
import hotelsData from '@/data/hotels.json';
import bookingsData from '@/data/bookings.json';
import type { Hotel as HotelType, Booking } from '@/types';
import { PageHeader } from '@/components/PageHeader';
import { formatINR } from '@/lib/format';
import { cn } from '@/lib/cn';

export function HotelPage() {
  const { id } = useParams();
  const hotel = (hotelsData.hotels as HotelType[]).find((h) => h.id === id);
  const booking = bookingsData.bookings.find((b) => b.id === hotel?.bookingId) as Booking | undefined;

  if (!hotel) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 lg:px-8">
        <p className="text-16 text-navy-400">Hotel booking not found.</p>
        <Link to="/dashboard" className="btn-primary mt-4">Back to Dashboard</Link>
      </div>
    );
  }

  return (
    <div>
      <PageHeader title={hotel.name} subtitle={`${hotel.city} · ${hotel.nights} nights`}>
        <Link to="/dashboard" className="btn-outline">
          <ArrowLeft className="h-4 w-4" />
          Back to Dashboard
        </Link>
      </PageHeader>

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <div className="card p-6">
              <h3 className="text-24 font-bold text-navy-600">{hotel.name}</h3>
              <p className="mt-1 flex items-center gap-2 text-14 text-navy-400">
                <MapPin className="h-3.5 w-3.5" />
                {hotel.address}
              </p>
              <div className="mt-4 inline-flex">
                <span className="rounded-md bg-accent-50 px-3 py-1 text-12 font-semibold text-accent-600">
                  {hotel.status}
                </span>
              </div>
            </div>

            {/* Booking Details */}
            <div className="card p-6">
              <h3 className="text-16 font-semibold text-navy-600">Booking Details</h3>
              <div className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div>
                  <p className="label-text mb-1">Check-in</p>
                  <p className="text-14 font-medium text-navy-600">{hotel.checkIn}</p>
                </div>
                <div>
                  <p className="label-text mb-1">Check-out</p>
                  <p className="text-14 font-medium text-navy-600">{hotel.checkOut}</p>
                </div>
                <div>
                  <p className="label-text mb-1">Nights</p>
                  <p className="text-14 font-medium text-navy-600">{hotel.nights}</p>
                </div>
                <div>
                  <p className="label-text mb-1">Guests</p>
                  <p className="text-14 font-medium text-navy-600">{hotel.guests}</p>
                </div>
              </div>
              <div className="mt-4 border-t border-navy-100 pt-4">
                <p className="label-text mb-1">Room Type</p>
                <p className="flex items-center gap-2 text-14 font-medium text-navy-600">
                  <Bed className="h-4 w-4 text-navy-400" />
                  {hotel.roomType}
                </p>
              </div>
              <div className="mt-4 flex items-center justify-between border-t border-navy-100 pt-4">
                <span className="text-14 text-navy-400">Total ({hotel.nights} nights)</span>
                <span className="text-20 font-bold text-navy-600">{formatINR(hotel.price)}</span>
              </div>
            </div>

            {/* Before/After Comparison */}
            <div className="card p-6">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-accent-600" />
                <h3 className="text-16 font-semibold text-navy-600">AI-Updated Check-in</h3>
              </div>
              <p className="mt-1 text-14 text-navy-400">
                Check-in adjusted automatically when your train was disrupted.
              </p>

              <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                {/* Before */}
                <div className="rounded-card border border-navy-100 p-5">
                  <div className="flex items-center gap-2">
                    <Clock className="h-4 w-4 text-navy-300" />
                    <span className="label-text">Before</span>
                  </div>
                  <p className="mt-3 text-32 font-bold text-navy-300 line-through">{hotel.before.checkInTime}</p>
                  <p className="mt-2 text-14 text-navy-400">{hotel.before.note}</p>
                </div>

                {/* After */}
                <div className="rounded-card border border-accent-200 bg-accent-50 p-5">
                  <div className="flex items-center gap-2">
                    <Check className="h-4 w-4 text-accent-600" />
                    <span className="label-text text-accent-600">After</span>
                  </div>
                  <p className="mt-3 text-32 font-bold text-accent-600">{hotel.after.checkInTime}</p>
                  <p className="mt-2 text-14 text-navy-500">{hotel.after.note}</p>
                </div>
              </div>

              <div className="mt-4 flex items-center gap-2 rounded-btn bg-navy-50 p-3 text-12 text-navy-400">
                <Sparkles className="h-3.5 w-3.5 text-accent-500" />
                Updated automatically when disruption was detected — no calls needed.
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
