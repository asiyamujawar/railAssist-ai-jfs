import { useParams, useSearchParams, Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { ArrowLeft, Clock, Star, MapPin, Users, Calendar, ArrowRight, Train as TrainIcon } from 'lucide-react';
import trainsData from '@/data/trains.json';
import type { Train, ScheduleStop } from '@/types';
import { formatINR, classStatusColor, classStatusLabel, classStatusBg } from '@/lib/format';
import { cn } from '@/lib/cn';
import { PageHeader } from '@/components/PageHeader';

export function TrainDetailsPage() {
  const { trainId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const date = searchParams.get('date') || '2026-08-05';
  const initialClass = searchParams.get('class') || '2A';

  const train = (trainsData.trains as Train[]).find((t) => t.id === trainId);
  const schedule = (trainsData.trainSchedules as Record<string, ScheduleStop[]>)?.[trainId || ''] || [];

  const [selectedClass, setSelectedClass] = useState(
    train?.classes.find((c) => c.code === initialClass)?.code || train?.classes[0].code || '',
  );

  if (!train) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 lg:px-8">
        <p className="text-16 text-navy-400">Train not found.</p>
        <Link to="/search" className="btn-primary mt-4">Back to Search</Link>
      </div>
    );
  }

  const selectedClassObj = train.classes.find((c) => c.code === selectedClass) || train.classes[0];

  const handleBook = () => {
    const params = new URLSearchParams({ date, class: selectedClass });
    navigate(`/book/${train.id}?${params.toString()}`);
  };

  return (
    <div>
      <PageHeader title={train.name} subtitle={`${train.number} · ${train.type}`}>
        <Link to="/search" className="btn-outline">
          <ArrowLeft className="h-4 w-4" />
          Back to Results
        </Link>
      </PageHeader>

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Left: Schedule + Class selection */}
          <div className="space-y-6 lg:col-span-2">
            {/* Route Summary */}
            <div className="card p-6">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="rounded-md bg-navy-50 px-2 py-0.5 text-12 font-bold text-navy-600">{train.number}</span>
                  <span className="rounded-md bg-accent-50 px-2 py-0.5 text-12 font-medium text-accent-600">{train.type}</span>
                </div>
                <div className="flex items-center gap-3 text-12 text-navy-400">
                  <span className="flex items-center gap-1">
                    <Star className="h-3.5 w-3.5 fill-accent-500 text-accent-500" />
                    {train.rating}
                  </span>
                  <span>{train.onTime}% on-time</span>
                </div>
              </div>

              <div className="mt-6 flex items-center gap-4">
                <div className="text-center">
                  <p className="text-24 font-bold text-navy-600">{train.departure}</p>
                  <p className="text-12 text-navy-400">{train.fromName}</p>
                  <p className="text-12 text-navy-300">{train.from}</p>
                </div>
                <div className="flex flex-1 flex-col items-center">
                  <p className="text-12 text-navy-400">{train.duration}</p>
                  <div className="relative my-1 h-px w-full bg-navy-100">
                    <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-white p-1">
                      <TrainIcon className="h-3.5 w-3.5 text-navy-300" />
                    </div>
                  </div>
                  <p className="text-12 text-navy-300">{train.distance} km</p>
                </div>
                <div className="text-center">
                  <p className="text-24 font-bold text-navy-600">{train.arrival}</p>
                  <p className="text-12 text-navy-400">{train.toName}</p>
                  <p className="text-12 text-navy-300">{train.to}</p>
                </div>
              </div>

              <div className="mt-6 flex items-center gap-2 border-t border-navy-100 pt-4 text-12 text-navy-400">
                <Calendar className="h-3.5 w-3.5" />
                Runs on: {train.daysOfWeek.join(', ')}
              </div>
            </div>

            {/* Class-wise fare and availability */}
            <div className="card p-6">
              <h3 className="text-16 font-semibold text-navy-600">Class & Availability</h3>
              <div className="mt-4 space-y-3">
                {train.classes.map((cls) => (
                  <button
                    key={cls.code}
                    onClick={() => setSelectedClass(cls.code)}
                    className={cn(
                      'flex w-full items-center justify-between rounded-btn border p-4 transition-all',
                      selectedClass === cls.code
                        ? 'border-accent-500 bg-accent-50'
                        : 'border-navy-100 hover:border-navy-200 hover:bg-navy-50',
                    )}
                  >
                    <div className="flex items-center gap-4">
                      <div className="flex h-10 w-10 items-center justify-center rounded-btn bg-navy-50 text-12 font-bold text-navy-600">
                        {cls.code}
                      </div>
                      <div className="text-left">
                        <p className="text-14 font-semibold text-navy-600">{cls.name}</p>
                        <div className="mt-0.5 flex items-center gap-2">
                          <span className={cn('h-2 w-2 rounded-full', classStatusBg(cls.status))} />
                          <span className={cn('text-12 font-medium', classStatusColor(cls.status))}>
                            {cls.available > 0 ? `${cls.available} seats · ${classStatusLabel(cls.status)}` : classStatusLabel(cls.status)}
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-20 font-bold text-navy-600">{formatINR(cls.fare)}</p>
                      <p className="text-12 text-navy-400">per passenger</p>
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* Schedule / Stops */}
            {schedule.length > 0 && (
              <div className="card p-6">
                <h3 className="text-16 font-semibold text-navy-600">Route & Schedule</h3>
                <div className="mt-6 space-y-0">
                  {schedule.map((stop, idx) => (
                    <div key={idx} className="flex gap-4">
                      <div className="flex flex-col items-center">
                        <div className={cn(
                          'flex h-4 w-4 items-center justify-center rounded-full border-2',
                          idx === 0 ? 'border-status-ontime bg-status-ontime' :
                          idx === schedule.length - 1 ? 'border-accent-500 bg-accent-500' :
                          'border-navy-200 bg-white',
                        )} />
                        {idx < schedule.length - 1 && <div className="w-0.5 flex-1 bg-navy-100" style={{ minHeight: '48px' }} />}
                      </div>
                      <div className="flex-1 pb-6">
                        <div className="flex items-start justify-between">
                          <div>
                            <p className="text-14 font-semibold text-navy-600">{stop.station}</p>
                            <p className="text-12 text-navy-300">{stop.code} · {stop.distance} km</p>
                          </div>
                          <div className="text-right">
                            {stop.arrival !== '—' && (
                              <p className="text-14 font-medium text-navy-600">Arr: {stop.arrival}</p>
                            )}
                            {stop.departure !== '—' && (
                              <p className="text-14 font-medium text-navy-600">Dep: {stop.departure}</p>
                            )}
                            <p className="text-12 text-navy-300">Day {stop.day}</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Right: Booking sidebar */}
          <div className="lg:col-span-1">
            <div className="card sticky top-20 p-6">
              <h3 className="text-16 font-semibold text-navy-600">Fare Summary</h3>
              <div className="mt-4 space-y-3">
                <div className="flex items-center justify-between text-14">
                  <span className="text-navy-400">Train</span>
                  <span className="font-medium text-navy-600">{train.number}</span>
                </div>
                <div className="flex items-center justify-between text-14">
                  <span className="text-navy-400">Class</span>
                  <span className="font-medium text-navy-600">{selectedClassObj.name}</span>
                </div>
                <div className="flex items-center justify-between text-14">
                  <span className="text-navy-400">Date</span>
                  <span className="font-medium text-navy-600">{date}</span>
                </div>
                <div className="flex items-center justify-between text-14">
                  <span className="text-navy-400">Availability</span>
                  <span className={cn('font-medium', classStatusColor(selectedClassObj.status))}>
                    {selectedClassObj.available > 0 ? `${selectedClassObj.available} seats` : classStatusLabel(selectedClassObj.status)}
                  </span>
                </div>

                <div className="border-t border-navy-100 pt-3">
                  <div className="flex items-center justify-between">
                    <span className="text-14 text-navy-400">Per passenger</span>
                    <span className="text-16 font-bold text-navy-600">{formatINR(selectedClassObj.fare)}</span>
                  </div>
                  <p className="mt-1 text-12 text-navy-300">Final fare calculated at checkout</p>
                </div>
              </div>

              <button
                onClick={handleBook}
                disabled={selectedClassObj.status === 'waitlist'}
                className={cn(
                  'mt-6 w-full',
                  selectedClassObj.status === 'waitlist' ? 'btn-outline opacity-50 cursor-not-allowed' : 'btn-accent',
                )}
              >
                {selectedClassObj.status === 'waitlist' ? 'Join Waitlist' : 'Book Now'}
                <ArrowRight className="h-4 w-4" />
              </button>

              <div className="mt-4 space-y-2 text-12 text-navy-400">
                <div className="flex items-center gap-2">
                  <Clock className="h-3.5 w-3.5" />
                  Free cancellation up to 4 hours before departure
                </div>
                <div className="flex items-center gap-2">
                  <Users className="h-3.5 w-3.5" />
                  Up to 6 passengers per booking
                </div>
                <div className="flex items-center gap-2">
                  <MapPin className="h-3.5 w-3.5" />
                  Live disruption monitoring included
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
