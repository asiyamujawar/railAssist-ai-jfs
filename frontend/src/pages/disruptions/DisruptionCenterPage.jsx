import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AlertTriangle, RefreshCw, ArrowRight, ShieldAlert, Sparkles, CheckCircle2 } from 'lucide-react';
import api from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';
import EmptyState from '../../components/common/EmptyState';

const DisruptionCenterPage = () => {
  const [disruptions, setDisruptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchDisruptions = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/api/disruptions/my');
      if (response.data && response.data.data) {
        setDisruptions(response.data.data);
      }
    } catch (err) {
      setError('Failed to fetch disruption alerts.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDisruptions();
  }, []);

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Train Disruption Center</h1>
            <SimulatedBadge text="AUTONOMOUS MONITOR ACTIVE" />
          </div>
          <p className="text-sm text-slate-500 mt-1">Real-time detection of delays and cancellations with algorithmically ranked alternative recommendations.</p>
        </div>
      </div>

      {loading ? (
        <LoadingSpinner label="Polling disruption events..." size="lg" className="py-12" />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchDisruptions} />
      ) : disruptions.length === 0 ? (
        <EmptyState
          icon={CheckCircle2}
          title="All Journeys Operating Normally"
          message="No active disruptions or delays detected on your booked train schedules."
        />
      ) : (
        <div className="space-y-4">
          {disruptions.map((disruption) => (
            <div
              key={disruption.id}
              className="p-6 bg-white rounded-3xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all space-y-4"
            >
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-2xl bg-amber-100 text-amber-700 flex items-center justify-center shrink-0">
                    <AlertTriangle className="w-6 h-6" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-base font-bold text-slate-900">
                        Disruption Event #{disruption.id} — {disruption.type || 'SCHEDULE_DELAY'}
                      </h3>
                      <StatusBadge status={disruption.severity || 'MEDIUM'} />
                    </div>
                    <p className="text-xs text-slate-500 mt-0.5">
                      Journey #{disruption.journeyId} | Status: <span className="font-bold">{disruption.status}</span>
                    </p>
                  </div>
                </div>

                <Link
                  to={`/disruptions/${disruption.id}/rebook`}
                  className="px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-md shadow-blue-600/20 transition-all flex items-center gap-2 shrink-0"
                >
                  Inspect Recommendations & Rebook <ArrowRight className="w-4 h-4" />
                </Link>
              </div>

              <p className="text-xs text-slate-600 leading-relaxed bg-slate-50 p-4 rounded-2xl border border-slate-100">
                {disruption.description || 'Schedule disruption detected by the autonomous monitoring scheduler.'}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default DisruptionCenterPage;
