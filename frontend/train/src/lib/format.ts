import type { TrainClassStatus } from '@/types';

export function formatINR(amount: number): string {
  return '₹' + amount.toLocaleString('en-IN');
}

export function statusColor(status: string): string {
  const map: Record<string, string> = {
    'On Time': 'text-status-ontime bg-status-ontime/10 border-status-ontime/20',
    'Delayed': 'text-status-delayed bg-status-delayed/10 border-status-delayed/20',
    'Cancelled': 'text-status-cancelled bg-status-cancelled/10 border-status-cancelled/20',
    'Rebooked': 'text-accent-600 bg-accent-50 border-accent-200',
    'Completed': 'text-navy-400 bg-navy-50 border-navy-100',
    'Confirmed': 'text-status-ontime bg-status-ontime/10 border-status-ontime/20',
    'Waitlist': 'text-status-waitlist bg-status-waitlist/10 border-status-waitlist/20',
  };
  return map[status] || 'text-navy-400 bg-navy-50 border-navy-100';
}

export function classStatusColor(status: TrainClassStatus): string {
  const map: Record<TrainClassStatus, string> = {
    available: 'text-status-ontime',
    limited: 'text-status-delayed',
    waitlist: 'text-status-waitlist',
  };
  return map[status];
}

export function classStatusBg(status: TrainClassStatus): string {
  const map: Record<TrainClassStatus, string> = {
    available: 'bg-status-ontime',
    limited: 'bg-status-delayed',
    waitlist: 'bg-status-waitlist',
  };
  return map[status];
}

export function classStatusLabel(status: TrainClassStatus): string {
  const map: Record<TrainClassStatus, string> = {
    available: 'Available',
    limited: 'Filling fast',
    waitlist: 'Waitlist',
  };
  return map[status];
}

export function severityColor(severity: string): string {
  const map: Record<string, string> = {
    warning: 'bg-status-delayed',
    error: 'bg-status-cancelled',
    success: 'bg-status-ontime',
    info: 'bg-accent-500',
  };
  return map[severity] || 'bg-navy-300';
}

export function categoryColor(category: string): string {
  const map: Record<string, string> = {
    Disruption: 'text-status-delayed bg-status-delayed/10',
    Seat: 'text-accent-600 bg-accent-50',
    Booking: 'text-status-ontime bg-status-ontime/10',
    System: 'text-navy-500 bg-navy-50',
  };
  return map[category] || 'text-navy-400 bg-navy-50';
}
