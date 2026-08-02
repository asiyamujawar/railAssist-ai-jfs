import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MapPin, Calendar, Users, ArrowRight, ArrowLeftRight, Sparkles, Ticket, Activity, Search } from 'lucide-react';
import trainsData from '@/data/trains.json';

const stations = trainsData.stations;
const classOptions = [
  { code: '1A', label: 'AC First Class' },
  { code: '2A', label: 'AC 2 Tier' },
  { code: '3A', label: 'AC 3 Tier' },
  { code: 'SL', label: 'Sleeper' },
  { code: 'CC', label: 'AC Chair Car' },
  { code: '2S', label: 'Second Sitting' },
];

export function HomePage() {
  const navigate = useNavigate();
  const [from, setFrom] = useState('NDLS');
  const [to, setTo] = useState('BCT');
  const [date, setDate] = useState('2026-08-05');
  const [travelClass, setTravelClass] = useState('2A');

  const swapStations = () => {
    const temp = from;
    setFrom(to);
    setTo(temp);
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const params = new URLSearchParams({ from, to, date, class: travelClass });
    navigate(`/search?${params.toString()}`);
  };

  return (
    <div>
      {/* Hero Section */}
      <section className="relative overflow-hidden bg-navy-600">
        <div className="relative mx-auto max-w-7xl px-4 py-16 lg:px-8 lg:py-20">
          <div className="max-w-2xl">
            <div className="inline-flex items-center gap-2 rounded-full border border-accent-400/30 bg-accent-500/10 px-3 py-1 text-12 font-medium text-accent-200">
              <Sparkles className="h-3.5 w-3.5" />
              AI-powered disruption concierge
            </div>
            <h1 className="mt-4 text-40 font-bold leading-tight text-white">
              Travel by train.<br />We handle the disruptions.
            </h1>
            <p className="mt-4 text-16 text-navy-200">
              Search and book trains across India. When delays or cancellations hit, TrainMate automatically finds alternatives, rebooks your seat, and updates your hotel and cab — before you even notice.
            </p>
          </div>

          {/* Search Card */}
          <form onSubmit={handleSearch} className="mt-8 rounded-card bg-white p-6 shadow-hover lg:mt-10">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-12">
              <div className="md:col-span-4">
                <label className="label-text mb-1.5 block">From</label>
                <div className="relative">
                  <MapPin className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                  <select
                    value={from}
                    onChange={(e) => setFrom(e.target.value)}
                    className="input-field appearance-none pl-10"
                  >
                    {stations.map((s) => (
                      <option key={s.code} value={s.code}>{s.name} ({s.code})</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex items-end justify-center md:col-span-1">
                <button
                  type="button"
                  onClick={swapStations}
                  className="flex h-10 w-10 items-center justify-center rounded-btn border border-navy-100 text-navy-400 transition-colors hover:bg-navy-50 hover:text-navy-600"
                >
                  <ArrowLeftRight className="h-4 w-4" />
                </button>
              </div>

              <div className="md:col-span-4">
                <label className="label-text mb-1.5 block">To</label>
                <div className="relative">
                  <MapPin className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                  <select
                    value={to}
                    onChange={(e) => setTo(e.target.value)}
                    className="input-field appearance-none pl-10"
                  >
                    {stations.map((s) => (
                      <option key={s.code} value={s.code}>{s.name} ({s.code})</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="md:col-span-3">
                <label className="label-text mb-1.5 block">Journey Date</label>
                <div className="relative">
                  <Calendar className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                  <input
                    type="date"
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    className="input-field pl-10"
                  />
                </div>
              </div>
            </div>

            <div className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
              <div className="flex-1 sm:max-w-xs">
                <label className="label-text mb-1.5 block">Travel Class</label>
                <div className="relative">
                  <Users className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                  <select
                    value={travelClass}
                    onChange={(e) => setTravelClass(e.target.value)}
                    className="input-field appearance-none pl-10"
                  >
                    {classOptions.map((c) => (
                      <option key={c.code} value={c.code}>{c.label}</option>
                    ))}
                  </select>
                </div>
              </div>

              <button type="submit" className="btn-accent w-full sm:w-auto">
                <Search className="h-4 w-4" />
                Search Trains
              </button>
            </div>
          </form>
        </div>
      </section>

      {/* Quick Links */}
      <section className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <QuickLink
            to="/dashboard"
            icon={Ticket}
            title="PNR Status"
            description="Check your booking status and live updates"
          />
          <QuickLink
            to="/dashboard"
            icon={Activity}
            title="Live Journey"
            description="Track your current trip in real-time"
          />
          <QuickLink
            to="/trips"
            icon={Ticket}
            title="My Trips"
            description="View upcoming and past bookings"
          />
        </div>
      </section>

      {/* AI Disruption Feature Callout */}
      <section className="mx-auto max-w-7xl px-4 pb-16 lg:px-8">
        <div className="card overflow-hidden">
          <div className="grid grid-cols-1 lg:grid-cols-2">
            <div className="p-8 lg:p-12">
              <div className="inline-flex items-center gap-2 rounded-full bg-accent-50 px-3 py-1 text-12 font-medium text-accent-600">
                <Sparkles className="h-3.5 w-3.5" />
                TrainMate AI Concierge
              </div>
              <h2 className="mt-4 text-32 font-bold text-navy-600">
                Your trip doesn't end when a train gets delayed.
              </h2>
              <p className="mt-4 text-16 text-navy-400">
                When we detect a disruption on your route, we instantly find the next best train with available seats, rebook your ticket, and coordinate your hotel check-in and cab pickup — all automatically. You just travel.
              </p>

              <div className="mt-8 space-y-4">
                <FeaturePoint
                  step="01"
                  title="Delay detected"
                  description="We monitor signal status, rake movements, and weather patterns in real time."
                />
                <FeaturePoint
                  step="02"
                  title="Alternative found"
                  description="AI compares every train on your route for the earliest arrival with seats available."
                />
                <FeaturePoint
                  step="03"
                  title="Everything rebooked"
                  description="Seat, hotel check-in time, and cab pickup — all updated to match your new arrival."
                />
              </div>

              <Link to="/dashboard" className="mt-8 inline-flex items-center gap-2 text-14 font-semibold text-accent-600 hover:text-accent-700">
                See it in action
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>

            <div className="relative bg-navy-600 p-8 lg:p-12">
              <div className="relative">
                <div className="rounded-card border border-white/10 bg-white/5 p-5 backdrop-blur-sm">
                  <div className="flex items-center gap-2">
                    <div className="h-2 w-2 rounded-full bg-status-delayed" />
                    <span className="text-12 font-semibold uppercase tracking-wide text-status-delayed">Delay Detected</span>
                  </div>
                  <p className="mt-3 text-14 text-navy-100">Rajdhani Express (12951)</p>
                  <p className="text-12 text-navy-300">Signal failure near Ratlam Jn</p>
                  <div className="mt-3 flex items-center gap-2 text-12 text-status-delayed">
                    <span className="font-bold">+2h 40m</span>
                    <span className="text-navy-300">expected delay</span>
                  </div>
                </div>

                <div className="mt-4 flex justify-center">
                  <div className="text-accent-300">
                    <ArrowRight className="h-5 w-5 rotate-90" />
                  </div>
                </div>

                <div className="rounded-card border border-accent-400/30 bg-accent-500/10 p-5">
                  <div className="flex items-center gap-2">
                    <Sparkles className="h-3.5 w-3.5 text-accent-300" />
                    <span className="text-12 font-semibold uppercase tracking-wide text-accent-300">AI Recommendation</span>
                  </div>
                  <p className="mt-3 text-14 font-semibold text-white">Garib Rath Express (12909)</p>
                  <p className="text-12 text-navy-200">Departs 13:55 — arrives 1h 55m earlier</p>
                  <div className="mt-3 flex items-center justify-between">
                    <div>
                      <p className="text-12 text-navy-300">AC 3 Tier</p>
                      <p className="text-16 font-bold text-white">₹1,295</p>
                    </div>
                    <div className="rounded-md bg-accent-500 px-3 py-1.5 text-12 font-bold text-white">
                      68 seats left
                    </div>
                  </div>
                </div>

                <div className="mt-4 flex items-center gap-2 text-12 text-navy-200">
                  <div className="h-px flex-1 bg-white/10" />
                  <span>Auto-rebooked in seconds</span>
                  <div className="h-px flex-1 bg-white/10" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}

function QuickLink({ to, icon: Icon, title, description }: { to: string; icon: typeof Ticket; title: string; description: string }) {
  return (
    <Link to={to} className="card group flex items-center gap-4 p-5 transition-all hover:shadow-hover">
      <div className="flex h-12 w-12 items-center justify-center rounded-btn bg-navy-50 text-navy-600 transition-colors group-hover:bg-accent-50 group-hover:text-accent-600">
        <Icon className="h-5 w-5" strokeWidth={2} />
      </div>
      <div className="flex-1">
        <h3 className="text-16 font-semibold text-navy-600">{title}</h3>
        <p className="text-12 text-navy-400">{description}</p>
      </div>
      <ArrowRight className="h-4 w-4 text-navy-300 transition-transform group-hover:translate-x-1" />
    </Link>
  );
}

function FeaturePoint({ step, title, description }: { step: string; title: string; description: string }) {
  return (
    <div className="flex gap-4">
      <span className="text-14 font-bold text-accent-500">{step}</span>
      <div>
        <h4 className="text-16 font-semibold text-navy-600">{title}</h4>
        <p className="text-14 text-navy-400">{description}</p>
      </div>
    </div>
  );
}


