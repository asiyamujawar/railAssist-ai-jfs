import React, { useState, useEffect } from 'react';
import { BellRing, Plus, ShieldAlert, CheckCircle2, XCircle, AlertCircle } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';
import EmptyState from '../../components/common/EmptyState';

const SeatAlertsPage = () => {
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [scheduleId, setScheduleId] = useState('1');
  const [seatClass, setSeatClass] = useState('AC_3_TIER');
  const [threshold, setThreshold] = useState(10);

  const toast = useToast();

  const fetchAlerts = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/api/seat-alerts/my');
      if (response.data && response.data.data) {
        setAlerts(response.data.data);
      }
    } catch (err) {
      setError('Failed to fetch your seat alert subscriptions.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAlerts();
  }, []);

  const handleCreateAlert = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const response = await api.post('/api/seat-alerts', {
        scheduleId: Number(scheduleId),
        seatClass,
        threshold: Number(threshold),
      });

      if (response.data && response.data.data) {
        toast.success('Smart Seat Alert subscription created successfully!');
        fetchAlerts();
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create seat alert subscription.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeactivate = async (id) => {
    try {
      const response = await api.patch(`/api/seat-alerts/${id}/deactivate`);
      if (response.data && response.data.data) {
        toast.info('Seat alert deactivated.');
        fetchAlerts();
      }
    } catch (err) {
      toast.error('Failed to deactivate seat alert.');
    }
  };

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Smart Seat Alert Subscriptions</h1>
            <SimulatedBadge text="SIMULATED INVENTORY MONITOR" />
          </div>
          <p className="text-sm text-slate-500 mt-1">Receive automated alerts when seat availability for a schedule reaches your threshold.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Subscription Form */}
        <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Plus className="w-4 h-4 text-blue-600" /> Create Seat Alert
          </h2>

          <form onSubmit={handleCreateAlert} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Schedule ID</label>
              <input
                type="number"
                required
                value={scheduleId}
                onChange={(e) => setScheduleId(e.target.value)}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Seat Class</label>
              <select
                value={seatClass}
                onChange={(e) => setSeatClass(e.target.value)}
                className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="AC_3_TIER">AC 3 Tier (3A)</option>
                <option value="AC_2_TIER">AC 2 Tier (2A)</option>
                <option value="AC_FIRST_CLASS">AC First Class (1A)</option>
                <option value="SLEEPER">Sleeper (SL)</option>
                <option value="EXECUTIVE_CHAIR_CAR">Executive Chair Car (EC)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Alert Seat Threshold</label>
              <input
                type="number"
                min="1"
                required
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <span className="text-[11px] text-slate-400 mt-1 block">Alert fires when available seats drop to or cross this count.</span>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-md transition-all flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {isSubmitting ? (
                <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  <BellRing className="w-4 h-4" /> Subscribe to Alert
                </>
              )}
            </button>
          </form>
        </div>

        {/* Subscriptions List */}
        <div className="lg:col-span-2 space-y-4">
          <h2 className="text-base font-bold text-slate-900">Your Active Subscriptions</h2>

          {loading ? (
            <LoadingSpinner label="Fetching your seat alerts..." size="md" />
          ) : error ? (
            <ErrorState message={error} />
          ) : alerts.length === 0 ? (
            <EmptyState title="No Alert Subscriptions" message="You have no active smart seat alert subscriptions." />
          ) : (
            <div className="space-y-3">
              {alerts.map((a) => (
                <div key={a.id} className="p-5 bg-white rounded-3xl border border-slate-200/80 shadow-xs flex items-center justify-between gap-4">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-slate-900">
                        {a.schedule?.train?.trainNumber || `Schedule #${a.scheduleId}`} — {a.seatClass}
                      </span>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${a.active ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-slate-100 text-slate-600 border-slate-200'}`}>
                        {a.active ? 'ACTIVE' : 'DEACTIVATED'}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500">
                      Threshold Trigger: <span className="font-bold text-slate-800">{a.threshold} seats</span>
                    </p>
                  </div>

                  {a.active && (
                    <button
                      onClick={() => handleDeactivate(a.id)}
                      className="px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold rounded-lg transition-colors"
                    >
                      Deactivate
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SeatAlertsPage;
