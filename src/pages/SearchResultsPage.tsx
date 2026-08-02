import { useState, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { SlidersHorizontal, ArrowUpDown, Train as TrainIcon, X } from 'lucide-react';
import trainsData from '@/data/trains.json';
import type { Train } from '@/types';
import { TrainCard } from '@/components/TrainCard';
import { PageHeader } from '@/components/PageHeader';
import { cn } from '@/lib/cn';

type SortKey = 'departure' | 'duration' | 'fare' | 'rating';

export function SearchResultsPage() {
  const [searchParams] = useSearchParams();
  const fromCode = searchParams.get('from') || 'NDLS';
  const toCode = searchParams.get('to') || 'BCT';
  const date = searchParams.get('date') || '2026-08-05';
  const travelClass = searchParams.get('class') || '2A';

  const fromStation = trainsData.stations.find((s) => s.code === fromCode);
  const toStation = trainsData.stations.find((s) => s.code === toCode);

  const [sortBy, setSortBy] = useState<SortKey>('departure');
  const [filterType, setFilterType] = useState<string[]>([]);
  const [filterTime, setFilterTime] = useState<string[]>([]);
  const [maxFare, setMaxFare] = useState(6000);
  const [showFilters, setShowFilters] = useState(false);

  const allTrains = trainsData.trains as Train[];

  const results = useMemo(() => {
    let filtered = allTrains.filter(
      (t) => t.from === fromCode && t.to === toCode,
    );

    if (filterType.length > 0) {
      filtered = filtered.filter((t) => filterType.includes(t.type));
    }

    if (filterTime.length > 0) {
      filtered = filtered.filter((t) => {
        const hour = parseInt(t.departure.split(':')[0]);
        return filterTime.some((slot) => {
          if (slot === 'morning') return hour >= 6 && hour < 12;
          if (slot === 'afternoon') return hour >= 12 && hour < 17;
          if (slot === 'evening') return hour >= 17 && hour < 21;
          if (slot === 'night') return hour >= 21 || hour < 6;
          return false;
        });
      });
    }

    filtered = filtered.filter((t) => {
      const lowest = Math.min(...t.classes.map((c) => c.fare));
      return lowest <= maxFare;
    });

    filtered.sort((a, b) => {
      if (sortBy === 'departure') return a.departure.localeCompare(b.departure);
      if (sortBy === 'fare') return Math.min(...a.classes.map((c) => c.fare)) - Math.min(...b.classes.map((c) => c.fare));
      if (sortBy === 'rating') return b.rating - a.rating;
      if (sortBy === 'duration') {
        const parseDur = (d: string) => {
          const [h, m] = d.match(/(\d+)h (\d+)m/)?.slice(1).map(Number) || [0, 0];
          return h * 60 + m;
        };
        return parseDur(a.duration) - parseDur(b.duration);
      }
      return 0;
    });

    return filtered;
  }, [allTrains, fromCode, toCode, filterType, filterTime, maxFare, sortBy]);

  const toggleType = (type: string) => {
    setFilterType((prev) => prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]);
  };

  const toggleTime = (slot: string) => {
    setFilterTime((prev) => prev.includes(slot) ? prev.filter((t) => t !== slot) : [...prev, slot]);
  };

  const clearFilters = () => {
    setFilterType([]);
    setFilterTime([]);
    setMaxFare(6000);
  };

  const trainTypes = [...new Set(allTrains.map((t) => t.type))];

  return (
    <div>
      <PageHeader
        title="Search Results"
        subtitle={`${fromStation?.name} → ${toStation?.name} · ${date} · ${travelClass}`}
      >
        <Link to="/" className="btn-outline">
          <X className="h-4 w-4" />
          New Search
        </Link>
      </PageHeader>

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="flex flex-col gap-6 lg:flex-row">
          {/* Filters Sidebar */}
          <aside className={cn('lg:w-64 lg:flex-shrink-0', showFilters ? 'block' : 'hidden lg:block')}>
            <div className="card sticky top-20 p-5">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <SlidersHorizontal className="h-4 w-4 text-navy-400" />
                  <h3 className="text-16 font-semibold text-navy-600">Filters</h3>
                </div>
                <button onClick={clearFilters} className="text-12 font-medium text-accent-600 hover:text-accent-700">
                  Clear all
                </button>
              </div>

              <div className="mt-6">
                <h4 className="label-text mb-3">Departure Time</h4>
                <div className="space-y-2">
                  {[
                    { key: 'morning', label: 'Morning (6 AM – 12 PM)' },
                    { key: 'afternoon', label: 'Afternoon (12 – 5 PM)' },
                    { key: 'evening', label: 'Evening (5 – 9 PM)' },
                    { key: 'night', label: 'Night (9 PM – 6 AM)' },
                  ].map((slot) => (
                    <label key={slot.key} className="flex cursor-pointer items-center gap-2 text-14 text-navy-500">
                      <input
                        type="checkbox"
                        checked={filterTime.includes(slot.key)}
                        onChange={() => toggleTime(slot.key)}
                        className="h-4 w-4 rounded border-navy-200 text-accent-500 focus:ring-accent-100"
                      />
                      {slot.label}
                    </label>
                  ))}
                </div>
              </div>

              <div className="mt-6">
                <h4 className="label-text mb-3">Train Type</h4>
                <div className="space-y-2">
                  {trainTypes.map((type) => (
                    <label key={type} className="flex cursor-pointer items-center gap-2 text-14 text-navy-500">
                      <input
                        type="checkbox"
                        checked={filterType.includes(type)}
                        onChange={() => toggleType(type)}
                        className="h-4 w-4 rounded border-navy-200 text-accent-500 focus:ring-accent-100"
                      />
                      {type}
                    </label>
                  ))}
                </div>
              </div>

              <div className="mt-6">
                <h4 className="label-text mb-3">Max Fare</h4>
                <input
                  type="range"
                  min={500}
                  max={6000}
                  step={500}
                  value={maxFare}
                  onChange={(e) => setMaxFare(Number(e.target.value))}
                  className="w-full accent-accent-500"
                />
                <div className="mt-1 flex justify-between text-12 text-navy-400">
                  <span>₹500</span>
                  <span className="font-semibold text-navy-600">₹{maxFare.toLocaleString('en-IN')}</span>
                </div>
              </div>
            </div>
          </aside>

          {/* Results */}
          <div className="flex-1">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <button
                  onClick={() => setShowFilters(!showFilters)}
                  className="btn-ghost lg:hidden"
                >
                  <SlidersHorizontal className="h-4 w-4" />
                  {showFilters ? 'Hide Filters' : 'Filters'}
                </button>
                <p className="text-14 text-navy-400">
                  <span className="font-semibold text-navy-600">{results.length}</span> trains found
                </p>
              </div>

              <div className="flex items-center gap-2">
                <ArrowUpDown className="h-4 w-4 text-navy-400" />
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as SortKey)}
                  className="rounded-btn border border-navy-200 bg-white px-3 py-2 text-14 font-medium text-navy-600 focus:border-accent-500 focus:outline-none"
                >
                  <option value="departure">Sort: Departure</option>
                  <option value="duration">Sort: Duration</option>
                  <option value="fare">Sort: Lowest Fare</option>
                  <option value="rating">Sort: Rating</option>
                </select>
              </div>
            </div>

            {results.length > 0 ? (
              <div className="space-y-4">
                {results.map((train) => (
                  <TrainCard key={train.id} train={train} journeyDate={date} selectedClass={travelClass} />
                ))}
              </div>
            ) : (
              <div className="card flex flex-col items-center justify-center p-16 text-center">
                <TrainIcon className="h-12 w-12 text-navy-200" />
                <h3 className="mt-4 text-20 font-semibold text-navy-600">No trains found</h3>
                <p className="mt-2 text-14 text-navy-400">
                  No direct trains match your filters. Try adjusting your search or filters.
                </p>
                <button onClick={clearFilters} className="btn-accent mt-6">
                  Clear Filters
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
