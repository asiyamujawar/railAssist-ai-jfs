import { Link } from 'react-router-dom';
import { Clock, Star, ArrowRight } from 'lucide-react';
import type { Train } from '@/types';
import { formatINR, classStatusColor, classStatusLabel } from '@/lib/format';
import { cn } from '@/lib/cn';

interface TrainCardProps {
  train: Train;
  journeyDate?: string;
  selectedClass?: string;
}

export function TrainCard({ train, journeyDate, selectedClass }: TrainCardProps) {
  const lowestFare = Math.min(...train.classes.map((c) => c.fare));
  const searchParams = new URLSearchParams();
  if (journeyDate) searchParams.set('date', journeyDate);
  if (selectedClass) searchParams.set('class', selectedClass);

  return (
    <Link
      to={`/trains/${train.id}?${searchParams.toString()}`}
      className="card group block p-5 transition-all hover:shadow-hover"
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="rounded-md bg-navy-50 px-2 py-0.5 text-12 font-bold text-navy-600">
              {train.number}
            </span>
            <span className="rounded-md bg-accent-50 px-2 py-0.5 text-12 font-medium text-accent-600">
              {train.type}
            </span>
          </div>
          <h3 className="mt-2 text-16 font-semibold text-navy-600">{train.name}</h3>
          <div className="mt-1 flex items-center gap-3 text-12 text-navy-400">
            <span className="flex items-center gap-1">
              <Star className="h-3 w-3 fill-accent-500 text-accent-500" />
              {train.rating}
            </span>
            <span>{train.onTime}% on-time</span>
            <span>{train.distance} km</span>
          </div>
        </div>
        <div className="text-right">
          <p className="text-12 text-navy-400">Starting from</p>
          <p className="text-20 font-bold text-navy-600">{formatINR(lowestFare)}</p>
        </div>
      </div>

      <div className="mt-4 flex items-center gap-4">
        <div className="text-center">
          <p className="text-20 font-bold text-navy-600">{train.departure}</p>
          <p className="text-12 text-navy-400">{train.fromName}</p>
        </div>

        <div className="flex flex-1 flex-col items-center">
          <p className="text-12 text-navy-400">{train.duration}</p>
          <div className="relative my-1 h-px w-full bg-navy-100">
            <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
              <ArrowRight className="h-3.5 w-3.5 text-navy-300" />
            </div>
          </div>
          <p className="text-12 text-navy-300">{train.from} → {train.to}</p>
        </div>

        <div className="text-center">
          <p className="text-20 font-bold text-navy-600">{train.arrival}</p>
          <p className="text-12 text-navy-400">{train.toName}</p>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-2 border-t border-navy-100 pt-4">
        {train.classes.map((cls) => (
          <div
            key={cls.code}
            className={cn(
              'flex items-center gap-2 rounded-md border border-navy-100 px-3 py-1.5',
            )}
          >
            <span className="text-12 font-bold text-navy-600">{cls.code}</span>
            <span className="text-12 text-navy-400">{formatINR(cls.fare)}</span>
            <span className={cn('text-12 font-medium', classStatusColor(cls.status))}>
              {cls.available > 0 ? `${cls.available} ${classStatusLabel(cls.status)}` : classStatusLabel(cls.status)}
            </span>
          </div>
        ))}
      </div>

      <div className="mt-4 flex items-center gap-2 text-12 text-navy-400">
        <Clock className="h-3.5 w-3.5" />
        Runs on: {train.daysOfWeek.join(', ')}
      </div>
    </Link>
  );
}
