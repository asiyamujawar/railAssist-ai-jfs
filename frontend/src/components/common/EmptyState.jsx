import React from 'react';
import { Inbox } from 'lucide-react';

const EmptyState = ({ title = 'No Data Found', message = 'There are no records to display at this time.', icon: Icon = Inbox, actionButton }) => {
  return (
    <div className="flex flex-col items-center justify-center p-12 text-center bg-white rounded-2xl border border-slate-200/80 shadow-xs">
      <div className="w-16 h-16 bg-slate-100 rounded-2xl flex items-center justify-center text-slate-400 mb-4">
        <Icon className="w-8 h-8" />
      </div>
      <h3 className="text-lg font-semibold text-slate-900 mb-1">{title}</h3>
      <p className="text-sm text-slate-500 max-w-sm mb-6">{message}</p>
      {actionButton}
    </div>
  );
};

export default EmptyState;
