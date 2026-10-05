import React from 'react';

const StatCard = ({ title, value, icon: Icon, description, trend, color = 'blue', className = '' }) => {
  const colorMap = {
    blue: 'bg-blue-50 text-blue-600 border-blue-100',
    emerald: 'bg-emerald-50 text-emerald-600 border-emerald-100',
    amber: 'bg-amber-50 text-amber-600 border-amber-100',
    rose: 'bg-rose-50 text-rose-600 border-rose-100',
    indigo: 'bg-indigo-50 text-indigo-600 border-indigo-100',
  };

  return (
    <div className={`p-6 bg-white rounded-2xl border border-slate-200/80 shadow-xs hover:shadow-md transition-all ${className}`}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{title}</span>
        {Icon && (
          <div className={`w-10 h-10 rounded-xl border flex items-center justify-center ${colorMap[color] || colorMap.blue}`}>
            <Icon className="w-5 h-5" />
          </div>
        )}
      </div>
      <div className="mt-3">
        <span className="text-3xl font-extrabold text-slate-900 tracking-tight">{value}</span>
      </div>
      {(description || trend) && (
        <div className="mt-2 flex items-center text-xs text-slate-500 gap-1.5">
          {trend && <span className="font-semibold text-emerald-600">{trend}</span>}
          {description && <span>{description}</span>}
        </div>
      )}
    </div>
  );
};

export default StatCard;
