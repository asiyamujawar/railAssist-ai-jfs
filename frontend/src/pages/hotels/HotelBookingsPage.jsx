import React, { useState, useEffect } from 'react';
import { Hotel, Plus, Calendar, MapPin, Sparkles, CheckCircle2, AlertCircle } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import EmptyState from '../../components/common/EmptyState';

const HotelBookingsPage = () => {
  const [journeyId, setJourneyId] = useState('1');
  const [hotels, setHotels] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [formData, setFormData] = useState({
    journeyId: '1',
    hotelName: 'Grand Central Hotel',
    city: 'Bengaluru',
    address: '123 MG Road',
    checkInDate: new Date().toISOString().split('T')[0],
    checkOutDate: new Date(Date.now() + 86400000).toISOString().split('T')[0],
  });

  const toast = useToast();

  const fetchHotels = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/api/hotels/journey/${journeyId}`);
      if (response.data && response.data.data) {
        setHotels(response.data.data);
      }
    } catch (err) {
      // Handle gracefully if journey has no hotels
      setHotels([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHotels();
  }, [journeyId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const response = await api.post('/api/hotels', {
        journeyId: Number(formData.journeyId),
        hotelName: formData.hotelName,
        city: formData.city,
        address: formData.address,
        checkInDate: formData.checkInDate,
        checkOutDate: formData.checkOutDate,
      });

      if (response.data && response.data.data) {
        toast.success('[SIMULATED] Hotel reservation added to journey!');
        fetchHotels();
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to add hotel reservation.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Simulated Hotel Reservations</h1>
            <SimulatedBadge text="MOCK PROVIDER" />
          </div>
          <p className="text-sm text-slate-500 mt-1">Manage linked hotel bookings automatically rescheduled during train disruptions.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Form Column */}
        <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Plus className="w-4 h-4 text-blue-600" /> Add Hotel Reservation
          </h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Target Journey ID</label>
              <input
                type="number"
                required
                value={formData.journeyId}
                onChange={(e) => {
                  setFormData({ ...formData, journeyId: e.target.value });
                  setJourneyId(e.target.value);
                }}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Hotel Name</label>
              <input
                type="text"
                required
                value={formData.hotelName}
                onChange={(e) => setFormData({ ...formData, hotelName: e.target.value })}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">City</label>
                <input
                  type="text"
                  required
                  value={formData.city}
                  onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                  className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Address</label>
                <input
                  type="text"
                  required
                  value={formData.address}
                  onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Check-in</label>
                <input
                  type="date"
                  required
                  value={formData.checkInDate}
                  onChange={(e) => setFormData({ ...formData, checkInDate: e.target.value })}
                  className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Check-out</label>
                <input
                  type="date"
                  required
                  value={formData.checkOutDate}
                  onChange={(e) => setFormData({ ...formData, checkOutDate: e.target.value })}
                  className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-3 bg-purple-600 hover:bg-purple-700 text-white font-bold text-xs rounded-xl shadow-md transition-all flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {isSubmitting ? (
                <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  <Hotel className="w-4 h-4" /> Save Hotel Booking
                </>
              )}
            </button>
          </form>
        </div>

        {/* List Column */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-900">Linked Hotels for Journey #{journeyId}</h2>
          </div>

          {loading ? (
            <LoadingSpinner label="Fetching hotel reservations..." size="md" />
          ) : hotels.length === 0 ? (
            <EmptyState title="No Hotels Linked" message="No simulated hotel reservations found for this journey." />
          ) : (
            <div className="space-y-3">
              {hotels.map((h) => (
                <div key={h.id} className="p-5 bg-white rounded-3xl border border-slate-200/80 shadow-xs space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="text-base font-bold text-slate-900">{h.hotelName}</h3>
                      <p className="text-xs text-slate-500">{h.city} — {h.address}</p>
                    </div>
                    <SimulatedBadge text="SIMULATED" />
                  </div>

                  <div className="flex items-center gap-4 text-xs text-slate-600 bg-slate-50 p-3 rounded-2xl border border-slate-100">
                    <div>Check-in: <span className="font-bold text-slate-900">{h.checkInDate}</span></div>
                    <div>Check-out: <span className="font-bold text-slate-900">{h.checkOutDate}</span></div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default HotelBookingsPage;
