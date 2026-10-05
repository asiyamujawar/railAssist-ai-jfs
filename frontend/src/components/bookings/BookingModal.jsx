import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { X, Ticket, User, Calendar, CheckCircle2, AlertCircle, Sparkles } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import SimulatedBadge from '../common/SimulatedBadge';
import LoadingSpinner from '../common/LoadingSpinner';

const BookingModal = ({ isOpen, schedule, onClose }) => {
  const [availabilityList, setAvailabilityList] = useState([]);
  const [loadingAvailability, setLoadingAvailability] = useState(true);

  const [selectedClass, setSelectedClass] = useState('AC_3_TIER');
  const [passengerName, setPassengerName] = useState('');
  const [passengerAge, setPassengerAge] = useState(28);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const toast = useToast();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchAvailability = async () => {
      setLoadingAvailability(true);
      try {
        const response = await api.get(`/api/schedules/${schedule.id}/availability`);
        if (response.data && response.data.data) {
          setAvailabilityList(response.data.data);
          if (response.data.data.length > 0) {
            setSelectedClass(response.data.data[0].seatClass);
          }
        }
      } catch (err) {
        setErrorMessage('Failed to load live seat availability.');
      } finally {
        setLoadingAvailability(false);
      }
    };

    if (schedule?.id) {
      fetchAvailability();
    }
  }, [schedule?.id]);

  if (!isOpen || !schedule) return null;

  const handleBooking = async (e) => {
    e.preventDefault();
    setErrorMessage('');

    if (!passengerName.trim()) {
      setErrorMessage('Please enter passenger full name.');
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await api.post('/api/bookings', {
        scheduleId: schedule.id,
        seatClass: selectedClass,
        passengerName,
        passengerAge: Number(passengerAge),
      });

      if (response.data && response.data.data) {
        toast.success(`Booking Confirmed! Reference: ${response.data.data.bookingReference}`);
        onClose();
        navigate('/bookings');
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Booking failed. Selected seat class may be full.';
      setErrorMessage(msg);
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const currentClassInfo = availabilityList.find((a) => a.seatClass === selectedClass);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-fade-in">
      <div className="w-full max-w-xl bg-white rounded-3xl shadow-2xl border border-slate-100 overflow-hidden transform transition-all">
        {/* Header */}
        <div className="p-6 bg-slate-900 text-white flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-extrabold tracking-tight">
                Book Ticket — {schedule.train?.trainNumber}
              </h2>
              <SimulatedBadge />
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              {schedule.train?.originStation} → {schedule.train?.destinationStation} ({schedule.scheduledDate})
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-xl transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <form onSubmit={handleBooking} className="p-6 space-y-6">
          {errorMessage && (
            <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl flex items-start gap-3 text-rose-800 text-xs">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <div>
                <p className="font-bold">Booking Transaction Error</p>
                <p className="mt-0.5">{errorMessage}</p>
              </div>
            </div>
          )}

          {/* Seat Availability Classes */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-3">
              Select Seat Class
            </label>
            {loadingAvailability ? (
              <LoadingSpinner label="Checking seat inventory..." size="sm" />
            ) : availabilityList.length === 0 ? (
              <p className="text-xs text-slate-500">No seat class availability information found.</p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {availabilityList.map((item) => {
                  const isSelected = item.seatClass === selectedClass;
                  const isAvailable = item.availableSeats > 0;
                  return (
                    <button
                      key={item.seatClass}
                      type="button"
                      disabled={!isAvailable}
                      onClick={() => setSelectedClass(item.seatClass)}
                      className={`p-3 rounded-2xl border text-left transition-all ${
                        isSelected
                          ? 'bg-blue-50 border-blue-600 ring-2 ring-blue-500/20'
                          : isAvailable
                          ? 'bg-slate-50 border-slate-200 hover:border-slate-300'
                          : 'bg-slate-100 border-slate-200 opacity-50 cursor-not-allowed'
                      }`}
                    >
                      <div className="text-xs font-bold text-slate-900">{item.seatClass}</div>
                      <div className={`text-xs mt-1 font-semibold ${isAvailable ? 'text-emerald-600' : 'text-rose-600'}`}>
                        {isAvailable ? `${item.availableSeats} Available` : 'Sold Out'}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* Passenger Information */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">
                Passenger Full Name *
              </label>
              <div className="relative">
                <User className="w-5 h-5 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  required
                  value={passengerName}
                  onChange={(e) => setPassengerName(e.target.value)}
                  placeholder="e.g. John Doe"
                  className="w-full pl-11 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">
                Age *
              </label>
              <input
                type="number"
                min="1"
                max="120"
                required
                value={passengerAge}
                onChange={(e) => setPassengerAge(e.target.value)}
                className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          {/* Booking Summary Box */}
          <div className="p-4 bg-slate-50 rounded-2xl border border-slate-200/80 flex items-center justify-between text-sm">
            <div>
              <span className="text-xs text-slate-400 block font-medium">Total Fare Amount</span>
              <span className="text-xl font-extrabold text-slate-900">₹{schedule.baseFare}</span>
            </div>
            <div className="text-right">
              <span className="text-xs text-slate-400 block font-medium">Class Selected</span>
              <span className="text-xs font-bold text-blue-600 bg-blue-50 px-2.5 py-1 rounded-lg border border-blue-200 inline-block">
                {selectedClass}
              </span>
            </div>
          </div>

          {/* Submit Action */}
          <div className="flex items-center justify-end gap-3 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2.5 text-sm font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || (currentClassInfo && currentClassInfo.availableSeats <= 0)}
              className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm rounded-xl shadow-lg shadow-blue-600/20 transition-all flex items-center gap-2 disabled:opacity-50"
            >
              {isSubmitting ? (
                <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  <CheckCircle2 className="w-4 h-4" /> Confirm Booking
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default BookingModal;
