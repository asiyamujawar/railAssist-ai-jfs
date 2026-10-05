import React from 'react';

const statusStyles = {
  // Booking & Journey Statuses
  CONFIRMED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  ACTIVE: 'bg-blue-50 text-blue-700 border-blue-200',
  COMPLETED: 'bg-slate-100 text-slate-700 border-slate-200',
  CANCELLED: 'bg-rose-50 text-rose-700 border-rose-200',
  REBOOKED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  REPLACED: 'bg-amber-50 text-amber-700 border-amber-200',
  PLANNED: 'bg-sky-50 text-sky-700 border-sky-200',

  // Schedule Statuses
  ON_TIME: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  DELAYED: 'bg-amber-50 text-amber-700 border-amber-200',

  // Disruption Severities
  LOW: 'bg-blue-50 text-blue-700 border-blue-200',
  MEDIUM: 'bg-amber-50 text-amber-700 border-amber-200',
  HIGH: 'bg-orange-50 text-orange-700 border-orange-200',
  CRITICAL: 'bg-rose-50 text-rose-700 border-rose-200',

  // Default fallback
  DEFAULT: 'bg-slate-100 text-slate-700 border-slate-200',
};

const StatusBadge = ({ status = 'DEFAULT', text, className = '' }) => {
  const normalizedKey = String(status).toUpperCase();
  const style = statusStyles[normalizedKey] || statusStyles.DEFAULT;

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${style} ${className}`}
    >
      {text || status}
    </span>
  );
};

export default StatusBadge;
