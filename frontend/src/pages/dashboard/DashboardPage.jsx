import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Ticket,
  MapPin,
  BellRing,
  AlertTriangle,
  Search,
  Plus,
  ArrowRight,
  Sparkles,
  Calendar,
  Clock,
  CheckCircle2,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import StatCard from '../../components/common/StatCard';
import StatusBadge from '../../components/common/StatusBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';
import EmptyState from '../../components/common/EmptyState';

const DashboardPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [bookings, setBookings] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [disruptions, setDisruptions] = useState([]);
  const [unreadNotifications, setUnreadNotifications] = useState(0);

  const fetchDashboardData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [bookingsRes, alertsRes, disruptionsRes, notificationsRes] = await Promise.allSettled([
        api.get('/api/bookings/my?size=5'),
        api.get('/api/seat-alerts/my'),
        api.get('/api/disruptions/my'),
        api.get('/api/notifications/unread-count'),
      ]);

      if (bookingsRes.status === 'fulfilled' && bookingsRes.value.data?.data) {
        setBookings(bookingsRes.value.data.data.content || []);
      }
      if (alertsRes.status === 'fulfilled' && alertsRes.value.data?.data) {
        setAlerts(alertsRes.value.data.data || []);
      }
      if (disruptionsRes.status === 'fulfilled' && disruptionsRes.value.data?.data) {
        setDisruptions(disruptionsRes.value.data.data || []);
      }
      if (notificationsRes.status === 'fulfilled' && notificationsRes.value.data?.data) {
        setUnreadNotifications(notificationsRes.value.data.data.unreadCount || 0);
      }
    } catch (err) {
      setError('Failed to load dashboard metrics. Please check server connectivity.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  if (loading) {
    return <LoadingSpinner label="Fetching your travel dashboard..." size="lg" className="min-h-[60vh]" />;
  }

  if (error) {
    return <ErrorState title="Dashboard Error" message={error} onRetry={fetchDashboardData} />;
  }

  const activeAlertsCount = alerts.filter((a) => a.active).length;

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Welcome Banner */}
      <div className="relative p-8 rounded-3xl bg-gradient-to-r from-blue-900 via-slate-900 to-indigo-900 text-white overflow-hidden shadow-xl">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/20 text-blue-300 text-xs font-semibold mb-3 border border-blue-400/30">
            <Sparkles className="w-3.5 h-3.5 text-blue-400" /> AI Disruption Concierge Active
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Welcome back, {user?.firstName || 'Traveler'}!
          </h1>
          <p className="mt-2 text-sm text-slate-300 leading-relaxed">
            Your travel itinerary is currently being monitored in the background. Any status disruptions or seat threshold updates will trigger immediate concierge recommendations.
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <Link
              to="/trains"
              className="px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold rounded-xl shadow-md transition-all flex items-center gap-2"
            >
              <Search className="w-4 h-4" /> Book New Train
            </Link>
            <Link
              to="/alerts"
              className="px-4 py-2.5 bg-white/10 hover:bg-white/20 text-white text-xs font-bold rounded-xl transition-all flex items-center gap-2 backdrop-blur-xs"
            >
              <Plus className="w-4 h-4" /> Set Seat Alert
            </Link>
          </div>
        </div>
      </div>

      {/* Metric Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Bookings"
          value={bookings.length}
          icon={Ticket}
          color="blue"
          description="Confirmed & historical bookings"
        />
        <StatCard
          title="Active Seat Alerts"
          value={activeAlertsCount}
          icon={BellRing}
          color="indigo"
          description="Subscriptions currently monitored"
        />
        <StatCard
          title="Disruption Events"
          value={disruptions.length}
          icon={AlertTriangle}
          color={disruptions.length > 0 ? 'amber' : 'emerald'}
          description={disruptions.length > 0 ? 'Requires attention' : 'No active disruptions'}
        />
        <StatCard
          title="Unread Alerts"
          value={unreadNotifications}
          icon={BellRing}
          color="rose"
          description="In-app notification updates"
        />
      </div>

      {/* Disruption Alert Banner (if disruptions exist) */}
      {disruptions.length > 0 && (
        <div className="p-6 bg-amber-50 rounded-3xl border border-amber-200 shadow-xs flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-2xl bg-amber-100 text-amber-700 flex items-center justify-center shrink-0">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-amber-950">Active Train Disruption Detected</h3>
                <StatusBadge status={disruptions[0].severity || 'MEDIUM'} />
              </div>
              <p className="text-sm text-amber-800 mt-1 leading-relaxed">
                {disruptions[0].description || `Schedule ${disruptions[0].type || 'DELAY'} detected for your journey.`}
              </p>
            </div>
          </div>
          <Link
            to="/disruptions"
            className="px-5 py-2.5 bg-amber-600 hover:bg-amber-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors shrink-0 flex items-center gap-2"
          >
            View Recommendations <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      )}

      {/* Content Grid: Recent Bookings & Active Alerts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Recent Bookings (2 Columns) */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-slate-900">Recent Bookings</h2>
            <Link to="/bookings" className="text-xs font-bold text-blue-600 hover:underline flex items-center gap-1">
              View All <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {bookings.length === 0 ? (
            <EmptyState
              title="No Bookings Found"
              message="You haven't booked any train journeys yet."
              actionButton={
                <button
                  onClick={() => navigate('/trains')}
                  className="px-4 py-2 bg-blue-600 text-white text-xs font-bold rounded-xl hover:bg-blue-700 transition-colors"
                >
                  Search Available Trains
                </button>
              }
            />
          ) : (
            <div className="space-y-3">
              {bookings.map((booking) => (
                <div
                  key={booking.id}
                  className="p-5 bg-white rounded-2xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-bold text-slate-900">
                        {booking.schedule?.train?.trainNumber || 'TR'} — {booking.schedule?.train?.trainName || 'Train'}
                      </span>
                      <StatusBadge status={booking.status} />
                    </div>
                    <p className="text-xs text-slate-500 flex items-center gap-2">
                      <span>{booking.schedule?.train?.originStation}</span>
                      <span>→</span>
                      <span>{booking.schedule?.train?.destinationStation}</span>
                    </p>
                    <div className="flex items-center gap-4 text-[11px] text-slate-400 mt-2">
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-slate-400" /> {booking.schedule?.scheduledDate}
                      </span>
                      <span className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-slate-400" /> {booking.schedule?.scheduledDeparture}
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 w-full sm:w-auto justify-between sm:justify-end border-t sm:border-t-0 pt-3 sm:pt-0 border-slate-100">
                    <div className="text-right">
                      <div className="text-xs text-slate-400">Booking Ref</div>
                      <div className="text-xs font-mono font-bold text-slate-900">{booking.bookingReference}</div>
                    </div>
                    <Link
                      to={`/bookings`}
                      className="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs rounded-lg transition-colors"
                    >
                      Details
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right Sidebar: Active Seat Alerts & Quick Actions */}
        <div className="space-y-6">
          <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <BellRing className="w-4 h-4 text-blue-600" /> Active Seat Alerts
              </h3>
              <Link to="/alerts" className="text-[11px] font-bold text-blue-600 hover:underline">
                Manage
              </Link>
            </div>

            {alerts.length === 0 ? (
              <p className="text-xs text-slate-500 py-4 text-center">No active seat alerts set.</p>
            ) : (
              <div className="space-y-3">
                {alerts.slice(0, 3).map((alert) => (
                  <div key={alert.id} className="p-3 bg-slate-50 rounded-xl border border-slate-200/60 text-xs space-y-1">
                    <div className="flex items-center justify-between font-bold text-slate-900">
                      <span>{alert.schedule?.train?.trainNumber}</span>
                      <span className="text-blue-600">Threshold: {alert.threshold} seats</span>
                    </div>
                    <div className="text-slate-500 text-[11px]">
                      Class: <span className="font-semibold">{alert.seatClass}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
