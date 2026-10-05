import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  TrainTrack,
  Search,
  BellRing,
  AlertTriangle,
  RefreshCw,
  Hotel,
  Car,
  ShieldCheck,
  ArrowRight,
  CheckCircle2,
  Sparkles,
} from 'lucide-react';
import SimulatedBadge from '../components/common/SimulatedBadge';

const LandingPage = () => {
  const navigate = useNavigate();

  const features = [
    {
      icon: Search,
      title: 'Intelligent Train Search',
      description: 'Search schedules, check real-time seat availability across classes, and compare fares effortlessly.',
      color: 'blue',
    },
    {
      icon: BellRing,
      title: 'Smart Seat Alerts',
      description: 'Set custom seat count thresholds and get instant in-app alerts as soon as availability opens up.',
      color: 'indigo',
    },
    {
      icon: AlertTriangle,
      title: 'Real-time Disruption Detection',
      description: 'Autonomous background monitoring continuously checks status for delays, cancellations, and changes.',
      color: 'amber',
    },
    {
      icon: RefreshCw,
      title: 'Autonomous Rebooking Engine',
      description: 'Receive algorithmically ranked alternative trains based on arrival delta, fare, and seat availability.',
      color: 'emerald',
    },
    {
      icon: Hotel,
      title: 'Hotel & Cab Rescheduling',
      description: 'Linked hotel reservations and pickup cabs auto-coordinate whenever your train schedule shifts.',
      color: 'purple',
    },
    {
      icon: ShieldCheck,
      title: 'Unified Journey Audit Timeline',
      description: 'Keep a complete chronological audit trail of all bookings, alerts, disruptions, and notifications.',
      color: 'sky',
    },
  ];

  const steps = [
    { step: '01', title: 'Book Your Train', desc: 'Search available schedules and reserve your seat with instant confirmation.' },
    { step: '02', title: 'Background Monitoring', desc: 'Our scheduler polls train status continuously to detect delays or disruptions.' },
    { step: '03', title: 'Smart Rebooking & Travel Sync', desc: 'Get ranked alternatives and auto-reschedule linked hotels and cabs seamlessly.' },
  ];

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      {/* Public Header Navbar */}
      <header className="sticky top-0 z-50 bg-white/90 backdrop-blur-md border-b border-slate-200/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-20 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-blue-600 flex items-center justify-center text-white shadow-lg shadow-blue-600/30">
              <TrainTrack className="w-6 h-6" />
            </div>
            <div>
              <span className="text-xl font-bold text-slate-900 tracking-tight">TrainConcierge</span>
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold text-slate-500">Autonomous Travel Concierge</span>
              </div>
            </div>
          </div>

          <nav className="hidden md:flex items-center gap-8 text-sm font-semibold text-slate-600">
            <a href="#features" className="hover:text-blue-600 transition-colors">Features</a>
            <a href="#how-it-works" className="hover:text-blue-600 transition-colors">How It Works</a>
            <a href="#simulated-notice" className="hover:text-blue-600 transition-colors">Prototype Notice</a>
          </nav>

          <div className="flex items-center gap-3">
            <Link
              to="/login"
              className="px-5 py-2.5 text-sm font-semibold text-slate-700 hover:text-slate-900 hover:bg-slate-100 rounded-xl transition-all"
            >
              Sign In
            </Link>
            <Link
              to="/register"
              className="px-5 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl shadow-md shadow-blue-600/20 transition-all flex items-center gap-2"
            >
              Get Started <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <section className="relative pt-16 pb-24 lg:pt-24 lg:pb-32 overflow-hidden bg-gradient-to-b from-blue-50/50 via-slate-50 to-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center">
          <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-blue-100/80 text-blue-800 text-xs font-bold mb-6 border border-blue-200">
            <Sparkles className="w-4 h-4 text-blue-600" /> AI-Powered Autonomous Disruption Management
          </div>

          <h1 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold text-slate-900 tracking-tight max-w-4xl mx-auto leading-tight">
            Your Journey. <br />
            <span className="bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">Always on Track.</span>
          </h1>

          <p className="mt-6 text-lg sm:text-xl text-slate-600 max-w-2xl mx-auto leading-relaxed">
            Automatically detect train delays and cancellations, discover ranked alternative trains, rebook seamlessly, and synchronize your hotel and cab reservations — zero stress required.
          </p>

          <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
            <button
              onClick={() => navigate('/trains')}
              className="w-full sm:w-auto px-8 py-4 text-base font-bold text-white bg-blue-600 hover:bg-blue-700 rounded-2xl shadow-xl shadow-blue-600/30 transition-all flex items-center justify-center gap-3"
            >
              <Search className="w-5 h-5" /> Find & Book Trains
            </button>
            <a
              href="#features"
              className="w-full sm:w-auto px-8 py-4 text-base font-bold text-slate-700 bg-white hover:bg-slate-100 border border-slate-200/80 rounded-2xl transition-all shadow-xs text-center"
            >
              Explore Features
            </a>
          </div>

          {/* Quick Metrics Bar */}
          <div className="mt-16 grid grid-cols-2 md:grid-cols-4 gap-4 max-w-4xl mx-auto p-6 bg-white/80 backdrop-blur-md rounded-3xl border border-slate-200/80 shadow-lg">
            <div>
              <div className="text-2xl font-extrabold text-slate-900">24 / 7</div>
              <div className="text-xs font-medium text-slate-500 mt-1">Autonomous Monitoring</div>
            </div>
            <div>
              <div className="text-2xl font-extrabold text-blue-600">Smart Scoring</div>
              <div className="text-xs font-medium text-slate-500 mt-1">Ranked Alternatives</div>
            </div>
            <div>
              <div className="text-2xl font-extrabold text-slate-900">Auto Sync</div>
              <div className="text-xs font-medium text-slate-500 mt-1">Hotel & Cab Reschedule</div>
            </div>
            <div>
              <div className="text-2xl font-extrabold text-emerald-600">Seat Alerts</div>
              <div className="text-xs font-medium text-slate-500 mt-1">Threshold Notifications</div>
            </div>
          </div>
        </div>
      </section>

      {/* Features Grid */}
      <section id="features" className="py-20 bg-white border-y border-slate-200/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-16">
            <h2 className="text-3xl font-extrabold text-slate-900 tracking-tight">Built for Uninterrupted Travel</h2>
            <p className="mt-3 text-base text-slate-600">
              TrainConcierge brings enterprise-grade concierge automation to train travel.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {features.map((f, idx) => {
              const Icon = f.icon;
              return (
                <div
                  key={idx}
                  className="p-8 bg-slate-50/70 hover:bg-slate-50 rounded-3xl border border-slate-200/70 shadow-xs hover:shadow-md transition-all group"
                >
                  <div className="w-12 h-12 rounded-2xl bg-blue-600/10 text-blue-600 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform">
                    <Icon className="w-6 h-6" />
                  </div>
                  <h3 className="text-lg font-bold text-slate-900 mb-2">{f.title}</h3>
                  <p className="text-sm text-slate-600 leading-relaxed">{f.description}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section id="how-it-works" className="py-20 bg-slate-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-16">
            <h2 className="text-3xl font-extrabold text-slate-900 tracking-tight">How TrainConcierge Works</h2>
            <p className="mt-3 text-base text-slate-600">
              Three simple steps from booking to autonomous disruption resolution.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 relative">
            {steps.map((s, idx) => (
              <div key={idx} className="p-8 bg-white rounded-3xl border border-slate-200/80 shadow-xs relative">
                <span className="text-4xl font-extrabold text-blue-600/20 mb-4 block">{s.step}</span>
                <h3 className="text-lg font-bold text-slate-900 mb-2">{s.title}</h3>
                <p className="text-sm text-slate-600 leading-relaxed">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Simulated Transparency Notice */}
      <section id="simulated-notice" className="py-12 bg-amber-50/70 border-t border-amber-200/80">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
          <div className="inline-flex items-center gap-2 mb-3">
            <SimulatedBadge text="PROTOTYPE & SIMULATION DISCLOSURE" />
          </div>
          <h3 className="text-lg font-bold text-amber-950">Simulated Service Operations</h3>
          <p className="mt-2 text-sm text-amber-850 leading-relaxed max-w-3xl mx-auto">
            TrainConcierge operates as a prototype demonstration backend system. External railway operations, IRCTC status updates, hotel room reservations, and cab dispatching behaviors are simulated internally within the application database. No real third-party financial or travel providers are contacted.
          </p>
        </div>
      </section>

      {/* Footer */}
      <footer className="mt-auto bg-slate-900 text-slate-400 py-12 border-t border-slate-800">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-blue-600 flex items-center justify-center text-white">
              <TrainTrack className="w-5 h-5" />
            </div>
            <span className="text-base font-bold text-white">TrainConcierge</span>
          </div>
          <p className="text-xs text-slate-500">
            © 2026 TrainConcierge. AI-Powered Autonomous Train Disruption Concierge System.
          </p>
        </div>
      </footer>
    </div>
  );
};

export default LandingPage;
