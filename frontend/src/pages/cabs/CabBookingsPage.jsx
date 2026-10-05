import React, { useState, useEffect } from 'react';
import { Car, Plus, Calendar, MapPin, Sparkles, CheckCircle2, Clock } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import EmptyState from '../../components/common/EmptyState';

const CabBookingsPage = () => {
  const [journeyId, setJourneyId] = useState('1');
  const [cabs, setCabs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [formData, setFormData] = useState({
    journeyId: '1',
    pickupLocation: 'Bengaluru City Station',
    destination: 'Whitefield Tech Park',
    scheduledPickupTime: '18:30',
    cabType: 'SEDAN',
  });

  const toast = useToast();

  const fetchCabs = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/api/cabs/journey/${journeyId}`);
      if (response.data && response.data.data) {
        setCabs(response.data.data);
      }
    } catch (err) {
      setCabs([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCabs();
  }, [journeyId]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const response = await api.post('/api/cabs', {
        journeyId: Number(formData.journeyId),
        pickupLocation: formData.pickupLocation,
        destination: formData.destination,
        scheduledPickupTime: formData.scheduledPickupTime,
        cabType: formData.cabType,
      });

      if (response.data && response.data.data) {
        toast.success('[SIMULATED] Cab reservation added to journey!');
        fetchCabs();
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to add cab reservation.');
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
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Simulated Cab Reservations</h1>
            <SimulatedBadge text="MOCK DISPATCH" />
          </div>
          <p className="text-sm text-slate-500 mt-1">Manage pickup cabs auto-rescheduled when train arrival times change.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Form Column */}
        <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Plus className="w-4 h-4 text-emerald-600" /> Reserve Pickup Cab
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
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Pickup Location</label>
              <input
                type="text"
                required
                value={formData.pickupLocation}
                onChange={(e) => setFormData({ ...formData, pickupLocation: e.target.value })}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Destination</label>
              <input
                type="text"
                required
                value={formData.destination}
                onChange={(e) => setFormData({ ...formData, destination: e.target.value })}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Pickup Time</label>
                <input
                  type="text"
                  required
                  value={formData.scheduledPickupTime}
                  onChange={(e) => setFormData({ ...formData, scheduledPickupTime: e.target.value })}
                  placeholder="18:30"
                  className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Cab Category</label>
                <select
                  value={formData.cabType}
                  onChange={(e) => setFormData({ ...formData, cabType: e.target.value })}
                  className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="SEDAN">SEDAN</option>
                  <option value="SUV">SUV</option>
                  <option value="MINI">MINI</option>
                  <option value="PREMIUM">PREMIUM</option>
                </select>
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-3 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow-md transition-all flex items-center justify-center gap-2 disabled:opacity-50"
            >
              {isSubmitting ? (
                <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  <Car className="w-4 h-4" /> Save Cab Reservation
                </>
              )}
            </button>
          </form>
        </div>

        {/* List Column */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-900">Linked Cabs for Journey #{journeyId}</h2>
          </div>

          {loading ? (
            <LoadingSpinner label="Fetching cab reservations..." size="md" />
          ) : cabs.length === 0 ? (
            <EmptyState title="No Cabs Reserved" message="No simulated cab reservations found for this journey." />
          ) : (
            <div className="space-y-3">
              {cabs.map((c) => (
                <div key={c.id} className="p-5 bg-white rounded-3xl border border-slate-200/80 shadow-xs space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <h3 className="text-base font-bold text-slate-900">{c.pickupLocation} → {c.destination}</h3>
                      <p className="text-xs text-slate-500">Cab Type: {c.cabType}</p>
                    </div>
                    <SimulatedBadge text="SIMULATED" />
                  </div>

                  <div className="flex items-center gap-4 text-xs text-slate-600 bg-slate-50 p-3 rounded-2xl border border-slate-100">
                    <Clock className="w-4 h-4 text-emerald-600" />
                    <div>Scheduled Pickup Time: <span className="font-bold text-slate-900">{c.scheduledPickupTime}</span></div>
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

export default CabBookingsPage;
