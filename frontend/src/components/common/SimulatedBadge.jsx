import React from 'react';
import { Sparkles } from 'lucide-react';

const SimulatedBadge = ({ text = 'SIMULATED', className = '' }) => {
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-amber-100/80 text-amber-900 border border-amber-300/80 tracking-wider uppercase ${className}`}
      title="This environment simulates external provider operations (no live IRCTC, hotel, or cab APIs are contacted)."
    >
      <Sparkles className="w-3 h-3 text-amber-600" />
      {text}
    </span>
  );
};

export default SimulatedBadge;
