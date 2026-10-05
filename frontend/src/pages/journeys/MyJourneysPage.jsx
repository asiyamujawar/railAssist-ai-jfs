import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Calendar, ArrowRight, Clock, ShieldCheck, Ticket } from 'lucide-react';
import api from '../../services/api';
import StatusBadge from '../../components/common/StatusBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';
import EmptyState from '../../components/common/EmptyState';

const MyJourneysPage = () => {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchJourneys = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await api.get('/api/bookings/my?size=50');
        if (response.data && response.data.data) {
          setBookings(response.data.data.content || []);
        }
      } catch (err) {
        setError('Failed to fetch journey itineraries.');
      } finally {
        setLoading(false);
      }
    };

    fetchJourneys();
  }, []);

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">My Journeys</h1>
        <p className="text-sm text-slate-500 mt-1">Track end-to-end journey audit timelines, linked hotels, and cab coordination.</p>
      </div>

      {loading ? (
        <LoadingSpinner label="Loading journey itineraries..." size="lg" className="py-12" />
      ) : error ? (
        <ErrorState message={error} />
      ) : bookings.length === 0 ? (
        <EmptyState title="No Active Journeys" message="You currently have no active or completed travel itineraries." />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {bookings.map((b) => (
            <div
              key={b.id}
              className="p-6 bg-white rounded-3xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all space-y-4 flex flex-col justify-between"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-400">Journey ID #{b.journeyId || b.id}</span>
                  <StatusBadge status={b.status} />
                </div>

                <h3 className="text-lg font-extrabold text-slate-900">
                  {b.schedule?.train?.originStation} → {b.schedule?.train?.destinationStation}
                </h3>

                <div className="flex items-center gap-4 text-xs text-slate-500 bg-slate-50 p-3 rounded-2xl border border-slate-100">
                  <div className="flex items-center gap-1.5 font-semibold text-slate-700">
                    <Calendar className="w-4 h-4 text-blue-600" /> {b.schedule?.scheduledDate}
                  </div>
                  <div className="flex items-center gap-1.5 font-semibold text-slate-700">
                    <Clock className="w-4 h-4 text-indigo-600" /> {b.schedule?.scheduledDeparture}
                  </div>
                </div>
              </div>

              <div className="pt-4 border-t border-slate-100 flex items-center justify-between">
                <span className="text-xs text-slate-500">Train: {b.schedule?.train?.trainNumber}</span>
                <Link
                  to={`/journeys/${b.journeyId || b.id}`}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl shadow-xs transition-colors flex items-center gap-1"
                >
                  View Timeline & Coordination <ArrowRight className="w-3.5 h-3.5" />
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default MyJourneysPage;
