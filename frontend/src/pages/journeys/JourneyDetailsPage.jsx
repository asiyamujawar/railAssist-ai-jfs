import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { MapPin, Calendar, Clock, Hotel, Car, ShieldCheck, RefreshCw, AlertTriangle, CheckCircle2, ArrowLeft } from 'lucide-react';
import api from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';

const JourneyDetailsPage = () => {
  const { id } = useParams();
  const [timelineEvents, setTimelineEvents] = useState([]);
  const [hotels, setHotels] = useState([]);
  const [cabs, setCabs] = useState([]);
  const [rebookingHistory, setRebookingHistory] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchJourneyData = async () => {
      setLoading(true);
      setError(null);
      try {
        const [timelineRes, hotelsRes, cabsRes, historyRes] = await Promise.allSettled([
          api.get(`/api/journeys/${id}/timeline`),
          api.get(`/api/hotels/journey/${id}`),
          api.get(`/api/cabs/journey/${id}`),
          api.get(`/api/journeys/${id}/rebooking-history`),
        ]);

        if (timelineRes.status === 'fulfilled' && timelineRes.value.data?.data) {
          setTimelineEvents(timelineRes.value.data.data.events || []);
        }
        if (hotelsRes.status === 'fulfilled' && hotelsRes.value.data?.data) {
          setHotels(hotelsRes.value.data.data || []);
        }
        if (cabsRes.status === 'fulfilled' && cabsRes.value.data?.data) {
          setCabs(cabsRes.value.data.data || []);
        }
        if (historyRes.status === 'fulfilled' && historyRes.value.data?.data) {
          setRebookingHistory(historyRes.value.data.data || []);
        }
      } catch (err) {
        setError('Failed to load journey audit timeline details.');
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      fetchJourneyData();
    }
  }, [id]);

  if (loading) {
    return <LoadingSpinner label="Compiling journey timeline audit..." size="lg" className="py-12" />;
  }

  if (error) {
    return <ErrorState message={error} />;
  }

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div>
        <Link to="/journeys" className="inline-flex items-center gap-1.5 text-xs font-bold text-blue-600 hover:underline mb-3">
          <ArrowLeft className="w-3.5 h-3.5" /> Back to My Journeys
        </Link>
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Journey Timeline Audit #{id}</h1>
            <p className="text-sm text-slate-500 mt-1">Unified chronological audit trail of all train, hotel, cab & disruption events.</p>
          </div>
          <SimulatedBadge text="SIMULATED SERVICES LINKED" />
        </div>
      </div>

      {/* Main Grid: Timeline + Services */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Timeline Column (2 cols) */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
            <h2 className="text-lg font-bold text-slate-900 border-b border-slate-100 pb-4">
              Chronological Audit Trail
            </h2>

            {timelineEvents.length === 0 ? (
              <p className="text-xs text-slate-500 py-6 text-center">No timeline events recorded for this journey yet.</p>
            ) : (
              <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-slate-200">
                {timelineEvents.map((evt, idx) => (
                  <div key={idx} className="relative flex items-start gap-4">
                    <div className="absolute -left-6 top-0.5 w-5 h-5 rounded-full bg-blue-600 border-4 border-white shadow-xs" />
                    <div className="flex-1 p-4 bg-slate-50 rounded-2xl border border-slate-200/60 text-xs space-y-1">
                      <div className="flex items-center justify-between font-bold text-slate-900">
                        <span>{evt.title || evt.eventType}</span>
                        <span className="text-[11px] font-normal text-slate-400">
                          {evt.timestamp ? new Date(evt.timestamp).toLocaleString() : ''}
                        </span>
                      </div>
                      <p className="text-slate-600 text-xs">{evt.description || evt.details}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Services & Coordination Column (1 col) */}
        <div className="space-y-6">
          {/* Linked Hotels */}
          <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Hotel className="w-4 h-4 text-purple-600" /> Hotel Booking
              </h3>
              <SimulatedBadge text="SIMULATED" />
            </div>

            {hotels.length === 0 ? (
              <div className="text-center py-4 space-y-2">
                <p className="text-xs text-slate-500">No hotel attached to this journey.</p>
                <Link to="/hotels" className="text-xs font-bold text-blue-600 hover:underline block">
                  + Add Hotel Reservation
                </Link>
              </div>
            ) : (
              hotels.map((h) => (
                <div key={h.id} className="p-3 bg-purple-50/50 rounded-2xl border border-purple-100 text-xs space-y-1">
                  <div className="font-bold text-slate-900">{h.hotelName}</div>
                  <div className="text-slate-500">{h.city}</div>
                  <div className="text-[11px] text-slate-400">Check-in: {h.checkInDate}</div>
                </div>
              ))
            )}
          </div>

          {/* Linked Cabs */}
          <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                <Car className="w-4 h-4 text-emerald-600" /> Cab Reservation
              </h3>
              <SimulatedBadge text="SIMULATED" />
            </div>

            {cabs.length === 0 ? (
              <div className="text-center py-4 space-y-2">
                <p className="text-xs text-slate-500">No cab attached to this journey.</p>
                <Link to="/cabs" className="text-xs font-bold text-blue-600 hover:underline block">
                  + Reserve Pickup Cab
                </Link>
              </div>
            ) : (
              cabs.map((c) => (
                <div key={c.id} className="p-3 bg-emerald-50/50 rounded-2xl border border-emerald-100 text-xs space-y-1">
                  <div className="font-bold text-slate-900">{c.pickupLocation} → {c.destination}</div>
                  <div className="text-slate-500">Pickup: {c.scheduledPickupTime}</div>
                  <div className="text-[11px] text-slate-400">Cab Type: {c.cabType}</div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default JourneyDetailsPage;
