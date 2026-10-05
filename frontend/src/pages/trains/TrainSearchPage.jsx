import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, TrainTrack, Calendar, MapPin, ArrowRight, ArrowRightLeft, Clock, Ticket, AlertCircle } from 'lucide-react';
import api from '../../services/api';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import StatusBadge from '../../components/common/StatusBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';
import EmptyState from '../../components/common/EmptyState';
import BookingModal from '../../components/bookings/BookingModal';

const TrainSearchPage = () => {
  const [originStation, setOriginStation] = useState('Mumbai');
  const [destinationStation, setDestinationStation] = useState('Bengaluru');
  const [journeyDate, setJourneyDate] = useState(() => new Date().toISOString().split('T')[0]);

  const [schedules, setSchedules] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [hasSearched, setHasSearched] = useState(false);

  // Booking Modal State
  const [selectedSchedule, setSelectedSchedule] = useState(null);
  const [isBookingModalOpen, setIsBookingModalOpen] = useState(false);

  const stations = ['Mumbai', 'Bengaluru', 'Delhi', 'Chennai', 'Kolkata', 'Hyderabad', 'Pune'];

  const handleSearch = async (e) => {
    if (e) e.preventDefault();
    if (originStation === destinationStation) {
      setError('Origin and destination stations must be different.');
      return;
    }

    setLoading(true);
    setError(null);
    setHasSearched(true);

    try {
      const response = await api.get(
        `/api/schedules/search?originStation=${originStation}&destinationStation=${destinationStation}&date=${journeyDate}`
      );
      if (response.data && response.data.data) {
        setSchedules(response.data.data);
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to search train schedules. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  // Run default search on page load
  useEffect(() => {
    handleSearch();
  }, []);

  const handleSwapStations = () => {
    setOriginStation(destinationStation);
    setDestinationStation(originStation);
  };

  const handleOpenBooking = (schedule) => {
    setSelectedSchedule(schedule);
    setIsBookingModalOpen(true);
  };

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Train Search & Schedules</h1>
            <SimulatedBadge text="SIMULATED SEAT DATA" />
          </div>
          <p className="text-sm text-slate-500 mt-1">Search real-time schedules and seat availability across all classes.</p>
        </div>
      </div>

      {/* Search Filter Panel */}
      <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-md">
        <form onSubmit={handleSearch} className="grid grid-cols-1 md:grid-cols-12 gap-4 items-end">
          {/* Origin Station */}
          <div className="md:col-span-3">
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">
              From (Origin)
            </label>
            <div className="relative">
              <MapPin className="w-5 h-5 text-blue-600 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <select
                value={originStation}
                onChange={(e) => setOriginStation(e.target.value)}
                className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all appearance-none"
              >
                {stations.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Swap Button */}
          <div className="md:col-span-1 flex justify-center pb-2">
            <button
              type="button"
              onClick={handleSwapStations}
              className="p-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl transition-colors"
              title="Swap Stations"
            >
              <ArrowRightLeft className="w-4 h-4" />
            </button>
          </div>

          {/* Destination Station */}
          <div className="md:col-span-3">
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">
              To (Destination)
            </label>
            <div className="relative">
              <MapPin className="w-5 h-5 text-indigo-600 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <select
                value={destinationStation}
                onChange={(e) => setDestinationStation(e.target.value)}
                className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all appearance-none"
              >
                {stations.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>

          {/* Journey Date */}
          <div className="md:col-span-3">
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">
              Journey Date
            </label>
            <div className="relative">
              <Calendar className="w-5 h-5 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="date"
                value={journeyDate}
                onChange={(e) => setJourneyDate(e.target.value)}
                className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all"
              />
            </div>
          </div>

          {/* Search Button */}
          <div className="md:col-span-2">
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm rounded-xl shadow-md shadow-blue-600/20 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
            >
              <Search className="w-4 h-4" /> Search
            </button>
          </div>
        </form>

        {error && (
          <div className="mt-4 p-3 bg-rose-50 border border-rose-200 rounded-xl flex items-center gap-2 text-rose-800 text-xs font-semibold">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
        )}
      </div>

      {/* Results Section */}
      {loading ? (
        <LoadingSpinner label="Searching available train schedules..." size="lg" className="py-12" />
      ) : schedules.length === 0 && hasSearched ? (
        <EmptyState
          title="No Train Schedules Found"
          message={`No trains found operating between ${originStation} and ${destinationStation} on ${journeyDate}.`}
        />
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between px-2">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">
              {schedules.length} Available Schedules Found
            </span>
            <span className="text-xs text-slate-400">All fares listed in INR (₹)</span>
          </div>

          {schedules.map((schedule) => (
            <div
              key={schedule.id}
              className="p-6 bg-white rounded-3xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all space-y-4"
            >
              {/* Train Header */}
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 border-b border-slate-100 pb-4">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center font-bold text-sm border border-blue-100">
                    <TrainTrack className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-slate-900">
                      {schedule.train?.trainNumber} — {schedule.train?.trainName}
                    </h3>
                    <p className="text-xs text-slate-500">{schedule.train?.operatorName || 'Indian Railways'}</p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <StatusBadge status={schedule.scheduleStatus} />
                  {schedule.delayMinutes > 0 && (
                    <span className="text-xs font-bold text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
                      Delayed by {schedule.delayMinutes} min
                    </span>
                  )}
                </div>
              </div>

              {/* Schedule Timing Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 items-center bg-slate-50/70 p-4 rounded-2xl border border-slate-200/60 text-sm">
                <div>
                  <span className="text-xs text-slate-400 block font-semibold uppercase">Departure</span>
                  <span className="text-base font-extrabold text-slate-900">{schedule.scheduledDeparture}</span>
                  <span className="text-xs text-slate-500 block font-medium">{schedule.train?.originStation}</span>
                </div>

                <div className="text-center sm:border-x border-slate-200/80 px-2 py-1">
                  <span className="text-[11px] font-bold text-blue-600 bg-blue-50 px-2.5 py-0.5 rounded-full border border-blue-200 inline-block mb-1">
                    Platform {schedule.platform || '1'}
                  </span>
                  <span className="text-xs text-slate-400 block font-medium">Date: {schedule.scheduledDate}</span>
                </div>

                <div className="text-right">
                  <span className="text-xs text-slate-400 block font-semibold uppercase">Arrival</span>
                  <span className="text-base font-extrabold text-slate-900">{schedule.scheduledArrival}</span>
                  <span className="text-xs text-slate-500 block font-medium">{schedule.train?.destinationStation}</span>
                </div>
              </div>

              {/* Fare & Booking Button Footer */}
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pt-2">
                <div>
                  <span className="text-xs text-slate-400 block">Base Fare</span>
                  <span className="text-xl font-extrabold text-slate-900">₹{schedule.baseFare}</span>
                </div>

                <button
                  onClick={() => handleOpenBooking(schedule)}
                  className="w-full sm:w-auto px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm rounded-xl shadow-md shadow-blue-600/20 transition-all flex items-center justify-center gap-2"
                >
                  <Ticket className="w-4 h-4" /> Book Seats
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Booking Modal */}
      {isBookingModalOpen && selectedSchedule && (
        <BookingModal
          isOpen={isBookingModalOpen}
          schedule={selectedSchedule}
          onClose={() => setIsBookingModalOpen(false)}
        />
      )}
    </div>
  );
};

export default TrainSearchPage;
