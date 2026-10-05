import React, { useState } from 'react';
import { ShieldAlert, Play, RefreshCw, Trash2, CheckCircle2, AlertTriangle, Sparkles, Sliders } from 'lucide-react';
import api from '../../services/api';
import { useToast } from '../../context/ToastContext';
import SimulatedBadge from '../../components/common/SimulatedBadge';
import ConfirmationDialog from '../../components/common/ConfirmationDialog';

const AdminSimulationPage = () => {
  const [scheduleId, setScheduleId] = useState('1');
  const [delayMinutes, setDelayMinutes] = useState(120);
  const [journeyId, setJourneyId] = useState('1');

  const [loadingAction, setLoadingAction] = useState(null);
  const [confirmDialogConfig, setConfirmDialogConfig] = useState(null);
  const [lastResult, setLastResult] = useState(null);

  const toast = useToast();

  const executeAction = async (actionType, apiCall, successMsg) => {
    setLoadingAction(actionType);
    try {
      const response = await apiCall();
      if (response.data) {
        setLastResult(response.data);
        toast.success(successMsg);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Admin simulation action failed.');
    } finally {
      setLoadingAction(null);
      setConfirmDialogConfig(null);
    }
  };

  const handleTriggerDelay = () => {
    setConfirmDialogConfig({
      title: 'Trigger Simulated Train Delay',
      message: `Are you sure you want to simulate a ${delayMinutes}-minute delay on Schedule #${scheduleId}?`,
      confirmText: 'Trigger Delay',
      isDanger: false,
      onConfirm: () =>
        executeAction(
          'delay',
          () => api.post(`/api/admin/simulation/control/trigger-delay?scheduleId=${scheduleId}&delayMinutes=${delayMinutes}`),
          `Simulated ${delayMinutes}-minute delay triggered on Schedule #${scheduleId}.`
        ),
    });
  };

  const handleTriggerCancellation = () => {
    setConfirmDialogConfig({
      title: 'Trigger Simulated Train Cancellation',
      message: `Are you sure you want to simulate a total CANCELLATION on Schedule #${scheduleId}? This will trigger automatic rebooking for all passengers.`,
      confirmText: 'Cancel Schedule',
      isDanger: true,
      onConfirm: () =>
        executeAction(
          'cancellation',
          () => api.post(`/api/admin/simulation/control/trigger-cancellation?scheduleId=${scheduleId}`),
          `Schedule #${scheduleId} set to CANCELLED.`
        ),
    });
  };

  const handleRestoreNormal = () => {
    executeAction(
      'restore',
      () => api.post(`/api/admin/simulation/control/restore-normal?scheduleId=${scheduleId}`),
      `Schedule #${scheduleId} restored to ON_TIME.`
    );
  };

  const handleRunMonitoringCycle = () => {
    executeAction(
      'cycle',
      () => api.post('/api/admin/simulation/control/monitoring-cycle'),
      'Manual monitoring cycle executed!'
    );
  };

  const handleClearCache = () => {
    executeAction(
      'cache',
      () => api.delete('/api/admin/simulation/control/monitoring-cache'),
      'Monitoring status cache cleared.'
    );
  };

  const handleDisruptionEvaluation = () => {
    executeAction(
      'eval',
      () => api.post(`/api/admin/simulation/control/disruption-evaluation?journeyId=${journeyId}`),
      `Disruption evaluation completed for Journey #${journeyId}.`
    );
  };

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Admin Simulation Control Center</h1>
            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-100 text-purple-900 border border-purple-300">
              <ShieldAlert className="w-3.5 h-3.5 text-purple-700" /> ROLE_ADMIN
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Simulate delays, cancellations, monitoring sweeps, and disruption evaluations for demonstration purposes.
          </p>
        </div>
        <SimulatedBadge text="ADMIN CONTROL PANEL" />
      </div>

      {/* Grid of Simulation Tools */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Tool 1: Train Disruption Simulation */}
        <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2 border-b border-slate-100 pb-3">
            <Sliders className="w-5 h-5 text-blue-600" /> Train Status Disruption Simulator
          </h2>

          <div className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Target Schedule ID</label>
              <input
                type="number"
                value={scheduleId}
                onChange={(e) => setScheduleId(e.target.value)}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">Simulated Delay Minutes</label>
              <input
                type="number"
                value={delayMinutes}
                onChange={(e) => setDelayMinutes(e.target.value)}
                className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            <div className="grid grid-cols-3 gap-2 pt-2">
              <button
                onClick={handleTriggerDelay}
                disabled={!!loadingAction}
                className="py-3 px-2 bg-amber-600 hover:bg-amber-700 text-white font-bold text-xs rounded-xl shadow-xs transition-all flex items-center justify-center gap-1 disabled:opacity-50"
              >
                {loadingAction === 'delay' ? 'Running...' : 'Trigger Delay'}
              </button>

              <button
                onClick={handleTriggerCancellation}
                disabled={!!loadingAction}
                className="py-3 px-2 bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs rounded-xl shadow-xs transition-all flex items-center justify-center gap-1 disabled:opacity-50"
              >
                {loadingAction === 'cancellation' ? 'Running...' : 'Cancel Schedule'}
              </button>

              <button
                onClick={handleRestoreNormal}
                disabled={!!loadingAction}
                className="py-3 px-2 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow-xs transition-all flex items-center justify-center gap-1 disabled:opacity-50"
              >
                {loadingAction === 'restore' ? 'Running...' : 'Restore Normal'}
              </button>
            </div>
          </div>
        </div>

        {/* Tool 2: High-Level Scheduler & Evaluation Control */}
        <div className="bg-white p-6 rounded-3xl border border-slate-200/80 shadow-xs space-y-6">
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2 border-b border-slate-100 pb-3">
            <Play className="w-5 h-5 text-indigo-600" /> Monitoring Engine Controls
          </h2>

          <div className="space-y-4">
            <div className="flex items-center justify-between p-4 bg-slate-50 rounded-2xl border border-slate-100">
              <div>
                <span className="text-xs font-bold text-slate-900 block">Trigger Monitoring Sweep</span>
                <span className="text-[11px] text-slate-500">Polls simulated statuses and creates disruption events.</span>
              </div>
              <button
                onClick={handleRunMonitoringCycle}
                disabled={!!loadingAction}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded-xl shadow-xs transition-colors shrink-0"
              >
                {loadingAction === 'cycle' ? 'Running...' : 'Run Cycle'}
              </button>
            </div>

            <div className="flex items-center justify-between p-4 bg-slate-50 rounded-2xl border border-slate-100">
              <div>
                <span className="text-xs font-bold text-slate-900 block">Clear Monitoring Cache</span>
                <span className="text-[11px] text-slate-500">Clears last-known status cache for fresh observations.</span>
              </div>
              <button
                onClick={handleClearCache}
                disabled={!!loadingAction}
                className="px-4 py-2 bg-slate-700 hover:bg-slate-800 text-white text-xs font-bold rounded-xl transition-colors shrink-0 flex items-center gap-1"
              >
                <Trash2 className="w-3.5 h-3.5" /> Clear
              </button>
            </div>

            <div className="p-4 bg-slate-50 rounded-2xl border border-slate-100 space-y-3">
              <span className="text-xs font-bold text-slate-900 block">Force Disruption Evaluation</span>
              <div className="flex gap-2">
                <input
                  type="number"
                  placeholder="Journey ID"
                  value={journeyId}
                  onChange={(e) => setJourneyId(e.target.value)}
                  className="flex-1 px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <button
                  onClick={handleDisruptionEvaluation}
                  disabled={!!loadingAction}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl shadow-xs transition-colors shrink-0"
                >
                  Evaluate
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Last Result Log Box */}
      {lastResult && (
        <div className="p-6 bg-slate-900 text-slate-200 rounded-3xl font-mono text-xs space-y-2 border border-slate-800 shadow-xl">
          <div className="flex items-center justify-between text-slate-400 border-b border-slate-800 pb-2">
            <span className="font-bold text-blue-400">LAST ADMIN OPERATION RESPONSE</span>
            <span>{new Date().toLocaleTimeString()}</span>
          </div>
          <pre className="overflow-x-auto text-[11px] text-emerald-400">
            {JSON.stringify(lastResult, null, 2)}
          </pre>
        </div>
      )}

      {/* Confirmation Dialog */}
      {confirmDialogConfig && (
        <ConfirmationDialog
          isOpen={!!confirmDialogConfig}
          title={confirmDialogConfig.title}
          message={confirmDialogConfig.message}
          confirmText={confirmDialogConfig.confirmText}
          isDanger={confirmDialogConfig.isDanger}
          isLoading={!!loadingAction}
          onConfirm={confirmDialogConfig.onConfirm}
          onCancel={() => setConfirmDialogConfig(null)}
        />
      )}
    </div>
  );
};

export default AdminSimulationPage;
