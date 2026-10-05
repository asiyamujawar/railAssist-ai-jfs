import React, { useState, useEffect } from 'react';
import { Ticket, Search, Filter, Calendar, Clock, XCircle, ArrowRight, CheckCircle2 } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import StatusBadge from '../../components/common/StatusBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import ErrorState from '../../components/common/ErrorState';
import EmptyState from '../../components/common/EmptyState';
import ConfirmationDialog from '../../components/common/ConfirmationDialog';

const MyBookingsPage = () => {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [filterStatus, setFilterStatus] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  // Cancel Dialog state
  const [cancellingBookingId, setCancellingBookingId] = useState(null);
  const [isCancelling, setIsCancelling] = useState(false);

  const toast = useToast();

  const fetchBookings = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/api/bookings/my?size=50');
      if (response.data && response.data.data) {
        setBookings(response.data.data.content || []);
      }
    } catch (err) {
      setError('Failed to fetch your bookings. Please check your network connection.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBookings();
  }, []);

  const handleCancelBooking = async () => {
    if (!cancellingBookingId) return;

    setIsCancelling(true);
    try {
      const response = await api.patch(`/api/bookings/${cancellingBookingId}/cancel`);
      if (response.data && response.data.data) {
        toast.success('Booking cancelled successfully. Seats restored.');
        setCancellingBookingId(null);
        fetchBookings();
      }
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to cancel booking.';
      toast.error(msg);
    } finally {
      setIsCancelling(false);
    }
  };

  const filteredBookings = bookings.filter((b) => {
    const matchesStatus = filterStatus === 'ALL' || b.status === filterStatus;
    const matchesSearch =
      b.bookingReference?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      b.schedule?.train?.trainNumber?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      b.schedule?.train?.trainName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      b.passengerName?.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesStatus && matchesSearch;
  });

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">My Bookings</h1>
          <p className="text-sm text-slate-500 mt-1">Manage and view all your confirmed and past train reservations.</p>
        </div>
      </div>

      {/* Filter & Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row items-center justify-between gap-4">
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search by ref, train or passenger..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="flex items-center gap-2 w-full md:w-auto overflow-x-auto pb-1 md:pb-0">
          <Filter className="w-4 h-4 text-slate-400 shrink-0" />
          {['ALL', 'CONFIRMED', 'CANCELLED', 'REBOOKED', 'REPLACED'].map((status) => (
            <button
              key={status}
              onClick={() => setFilterStatus(status)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all shrink-0 ${
                filterStatus === status
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {status}
            </button>
          ))}
        </div>
      </div>

      {/* Bookings List */}
      {loading ? (
        <LoadingSpinner label="Fetching your bookings..." size="lg" className="py-12" />
      ) : error ? (
        <ErrorState message={error} onRetry={fetchBookings} />
      ) : filteredBookings.length === 0 ? (
        <EmptyState
          title="No Bookings Found"
          message="You have no train bookings matching your current filter criteria."
        />
      ) : (
        <div className="space-y-4">
          {filteredBookings.map((booking) => (
            <div
              key={booking.id}
              className="p-6 bg-white rounded-3xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all space-y-4"
            >
              {/* Header Info */}
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
                <div>
                  <div className="flex items-center gap-3">
                    <span className="text-base font-extrabold text-slate-900">
                      {booking.schedule?.train?.trainNumber} — {booking.schedule?.train?.trainName}
                    </span>
                    <StatusBadge status={booking.status} />
                  </div>
                  <p className="text-xs text-slate-500 mt-1">
                    Booking Reference:{' '}
                    <span className="font-mono font-bold text-slate-800">{booking.bookingReference}</span>
                  </p>
                </div>

                <div className="text-right">
                  <span className="text-xs text-slate-400 block">Class & Fare</span>
                  <span className="text-base font-extrabold text-slate-900">
                    {booking.seatClass} — ₹{booking.farePaid || booking.schedule?.baseFare}
                  </span>
                </div>
              </div>

              {/* Journey details grid */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 bg-slate-50/70 p-4 rounded-2xl border border-slate-200/60 text-xs">
                <div>
                  <span className="text-slate-400 block font-semibold uppercase">Origin & Departure</span>
                  <span className="font-bold text-slate-900 text-sm">
                    {booking.schedule?.train?.originStation}
                  </span>
                  <span className="text-slate-500 block">{booking.schedule?.scheduledDeparture}</span>
                </div>

                <div className="text-center sm:border-x border-slate-200/80 px-2">
                  <span className="text-slate-400 block font-semibold uppercase">Journey Date</span>
                  <span className="font-bold text-slate-900 text-sm">{booking.schedule?.scheduledDate}</span>
                  <span className="text-slate-500 block">Passenger: {booking.passengerName}</span>
                </div>

                <div className="text-right">
                  <span className="text-slate-400 block font-semibold uppercase">Destination & Arrival</span>
                  <span className="font-bold text-slate-900 text-sm">
                    {booking.schedule?.train?.destinationStation}
                  </span>
                  <span className="text-slate-500 block">{booking.schedule?.scheduledArrival}</span>
                </div>
              </div>

              {/* Actions */}
              <div className="flex items-center justify-between pt-2">
                <span className="text-[11px] text-slate-400">
                  Booked on: {booking.createdAt ? new Date(booking.createdAt).toLocaleString() : 'N/A'}
                </span>

                {booking.status === 'CONFIRMED' && (
                  <button
                    onClick={() => setCancellingBookingId(booking.id)}
                    className="px-4 py-2 bg-rose-50 hover:bg-rose-100 text-rose-700 font-bold text-xs rounded-xl border border-rose-200 transition-colors flex items-center gap-1.5"
                  >
                    <XCircle className="w-3.5 h-3.5" /> Cancel Booking
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Cancel Confirmation Dialog */}
      <ConfirmationDialog
        isOpen={!!cancellingBookingId}
        title="Cancel Booking Reservation"
        message="Are you sure you want to cancel this booking? Releasing your reservation will restore seat availability for other travelers."
        confirmText="Yes, Cancel Booking"
        cancelText="Keep Reservation"
        isDanger={true}
        isLoading={isCancelling}
        onConfirm={handleCancelBooking}
        onCancel={() => setCancellingBookingId(null)}
      />
    </div>
  );
};

export default MyBookingsPage;
