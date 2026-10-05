import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { AlertTriangle, RefreshCw, ArrowLeft, CheckCircle2, Ticket, Sparkles, Star, Hotel, Car, ShieldAlert } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import StatusBadge from '../../components/common/StatusBadge';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';
import EmptyState from '../../components/common/EmptyState';
import ConfirmationDialog from '../../components/common/ConfirmationDialog';

const RebookingWorkflowPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();

  const [disruption, setDisruption] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Rebooking Modal / Action state
  const [selectedAlternative, setSelectedAlternative] = useState(null);
  const [selectedClass, setSelectedClass] = useState('AC_3_TIER');
  const [isRebookingModalOpen, setIsRebookingModalOpen] = useState(false);
  const [isRebooking, setIsRebooking] = useState(false);
  const [rebookingResult, setRebookingResult] = useState(null);

  const fetchDisruptionAndRecommendations = async () => {
    setLoading(true);
    setError(null);
    try {
      // 1. Fetch Disruption Details
      const disruptionRes = await api.get(`/api/disruptions/${id}`);
      if (disruptionRes.data && disruptionRes.data.data) {
        setDisruption(disruptionRes.data.data);
      }

      // 2. Generate/Fetch Ranked Alternatives
      const recRes = await api.post(`/api/disruptions/${id}/recommendations`);
      if (recRes.data && recRes.data.data) {
        setRecommendations(recRes.data.data);
      }
    } catch (err) {
      // Fallback to GET if POST recommendations returns existing
      try {
        const existingRes = await api.get(`/api/disruptions/${id}/recommendations`);
        if (existingRes.data && existingRes.data.data) {
          setRecommendations(existingRes.data.data);
        }
      } catch (e) {
        setError('Failed to load alternative train recommendations.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      fetchDisruptionAndRecommendations();
    }
  }, [id]);

  const handleOpenConfirm = (alt) => {
    setSelectedAlternative(alt);
    setIsRebookingModalOpen(true);
  };

  const handleExecuteRebooking = async () => {
    if (!selectedAlternative) return;

    setIsRebooking(true);
    try {
      const response = await api.post(`/api/disruptions/${id}/rebook`, {
        alternativeScheduleId: selectedAlternative.alternativeSchedule?.id || selectedAlternative.id,
        seatClass: selectedClass,
      });

      if (response.data && response.data.data) {
        setRebookingResult(response.data.data);
        toast.success('Autonomous rebooking executed successfully! Hotel & cab rescheduled.');
        setIsRebookingModalOpen(false);
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Rebooking execution failed. Selected alternative may be full.';
      toast.error(msg);
    } finally {
      setIsRebooking(false);
    }
  };

  if (loading) {
    return <LoadingSpinner label="Calculating ranked alternative trains & scoring..." size="lg" className="py-12" />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={fetchDisruptionAndRecommendations} />;
  }

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Back Link */}
      <div>
        <Link to="/disruptions" className="inline-flex items-center gap-1.5 text-xs font-bold text-blue-600 hover:underline mb-3">
          <ArrowLeft className="w-3.5 h-3.5" /> Back to Disruption Center
        </Link>
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">
              Disruption Rebooking Workflow #{id}
            </h1>
            <p className="text-sm text-slate-500 mt-1">Select an algorithmically ranked alternative to execute rebooking & travel sync.</p>
          </div>
          <SimulatedBadge text="AUTONOMOUS REBOOKING ENGINE" />
        </div>
      </div>

      {/* Disruption Alert Details Card */}
      {disruption && (
        <div className="p-6 bg-amber-50 rounded-3xl border border-amber-200 shadow-xs space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-5 h-5 text-amber-700" />
              <span className="text-sm font-bold text-amber-950">
                Disruption: {disruption.type || 'TRAIN_DELAY'} ({disruption.severity})
              </span>
            </div>
            <StatusBadge status={disruption.status} />
          </div>
          <p className="text-xs text-amber-850 leading-relaxed">
            {disruption.description || 'Schedule delayed. Alternative train options scored based on arrival time, fare, and available seats.'}
          </p>
        </div>
      )}

      {/* Rebooking Success Card */}
      {rebookingResult && (
        <div className="p-8 bg-emerald-50 rounded-3xl border border-emerald-200 shadow-lg space-y-6 animate-scale-up">
          <div className="flex items-center gap-3 text-emerald-800">
            <CheckCircle2 className="w-8 h-8 text-emerald-600 shrink-0" />
            <div>
              <h2 className="text-xl font-extrabold text-emerald-950">Rebooking Confirmed Successfully!</h2>
              <p className="text-xs text-emerald-700">Original booking updated to REPLACED. New booking is CONFIRMED.</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div className="p-4 bg-white rounded-2xl border border-emerald-100 space-y-1">
              <span className="text-slate-400 block font-semibold">New Booking Reference</span>
              <span className="text-base font-mono font-extrabold text-slate-900">
                {rebookingResult.newBooking?.bookingReference || 'CONFIRMED'}
              </span>
            </div>
            <div className="p-4 bg-white rounded-2xl border border-emerald-100 space-y-1">
              <span className="text-slate-400 block font-semibold">Travel Coordination Status</span>
              <div className="flex items-center gap-3 pt-1">
                <span className="inline-flex items-center gap-1 font-bold text-purple-700">
                  <Hotel className="w-3.5 h-3.5" /> Hotel Rescheduled
                </span>
                <span className="inline-flex items-center gap-1 font-bold text-emerald-700">
                  <Car className="w-3.5 h-3.5" /> Cab Rescheduled
                </span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3 pt-2">
            <button
              onClick={() => navigate('/bookings')}
              className="px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl transition-colors"
            >
              View Updated Bookings
            </button>
            <button
              onClick={() => navigate(`/journeys/${disruption?.journeyId || 1}`)}
              className="px-5 py-2.5 bg-white hover:bg-slate-50 text-slate-700 font-bold text-xs rounded-xl border border-slate-200 transition-colors"
            >
              View Journey Timeline
            </button>
          </div>
        </div>
      )}

      {/* Recommendations List */}
      <div className="space-y-4">
        <div className="flex items-center justify-between px-2">
          <h2 className="text-base font-extrabold text-slate-900">Ranked Alternative Trains</h2>
          <span className="text-xs text-slate-400">Scored by Spring Boot Recommendation Algorithm</span>
        </div>

        {recommendations.length === 0 ? (
          <EmptyState
            title="No Alternative Trains Available"
            message="No alternative train schedules with available seats match this route and travel window."
          />
        ) : (
          recommendations.map((rec, idx) => {
            const altSchedule = rec.alternativeSchedule || rec;
            const train = altSchedule.train || rec.train;
            const score = rec.score ? Number(rec.score).toFixed(2) : '95.00';

            return (
              <div
                key={idx}
                className="p-6 bg-white rounded-3xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all space-y-4"
              >
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-600 font-extrabold text-sm flex items-center justify-center border border-blue-100 shrink-0">
                      #{idx + 1}
                    </div>
                    <div>
                      <h3 className="text-base font-bold text-slate-900">
                        {train?.trainNumber} — {train?.trainName}
                      </h3>
                      <p className="text-xs text-slate-500">{train?.operatorName || 'Indian Railways'}</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <div className="px-3 py-1.5 rounded-2xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-extrabold flex items-center gap-1.5">
                      <Star className="w-4 h-4 fill-amber-400 text-amber-500" /> Score: {score}
                    </div>
                  </div>
                </div>

                {/* Departure / Arrival Timing */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 bg-slate-50 p-4 rounded-2xl border border-slate-200/60 text-xs">
                  <div>
                    <span className="text-slate-400 block font-semibold uppercase">Departure</span>
                    <span className="font-extrabold text-slate-900 text-sm">{altSchedule.scheduledDeparture}</span>
                    <span className="text-slate-500 block">{train?.originStation}</span>
                  </div>

                  <div className="text-center sm:border-x border-slate-200/80 px-2">
                    <span className="text-slate-400 block font-semibold uppercase">Date</span>
                    <span className="font-bold text-slate-900 text-sm">{altSchedule.scheduledDate}</span>
                    <span className="text-slate-500 block">Platform {altSchedule.platform || '1'}</span>
                  </div>

                  <div className="text-right">
                    <span className="text-slate-400 block font-semibold uppercase">Arrival</span>
                    <span className="font-extrabold text-slate-900 text-sm">{altSchedule.scheduledArrival}</span>
                    <span className="text-slate-500 block">{train?.destinationStation}</span>
                  </div>
                </div>

                {/* Score Rationale Reasons */}
                {rec.reasons && (
                  <p className="text-xs text-blue-800 bg-blue-50/80 p-3 rounded-xl border border-blue-100">
                    <span className="font-bold">Recommendation Reasons:</span> {rec.reasons}
                  </p>
                )}

                {/* Action */}
                <div className="flex items-center justify-between pt-2">
                  <div>
                    <span className="text-xs text-slate-400 block">Fare</span>
                    <span className="text-base font-extrabold text-slate-900">₹{altSchedule.baseFare || 1200}</span>
                  </div>

                  <button
                    onClick={() => handleOpenConfirm(rec)}
                    disabled={!!rebookingResult}
                    className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-md shadow-blue-600/20 transition-all flex items-center gap-2 disabled:opacity-50"
                  >
                    <RefreshCw className="w-4 h-4" /> Rebook onto this Train
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Confirmation Dialog Modal */}
      <ConfirmationDialog
        isOpen={isRebookingModalOpen}
        title="Confirm Autonomous Rebooking"
        message={`Are you sure you want to rebook onto train ${selectedAlternative?.alternativeSchedule?.train?.trainNumber || ''}? Your original booking will be updated to REPLACED and your linked hotel and cab reservations will be automatically rescheduled.`}
        confirmText="Confirm Rebooking"
        cancelText="Cancel"
        isLoading={isRebooking}
        onConfirm={handleExecuteRebooking}
        onCancel={() => setIsRebookingModalOpen(false)}
      />
    </div>
  );
};

export default RebookingWorkflowPage;
