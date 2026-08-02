import { cn } from '@/lib/cn';
import { statusColor } from '@/lib/format';

interface StatusBadgeProps {
  status: string;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-12 font-semibold',
        statusColor(status),
        className,
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', dotColor(status))} />
      {status}
    </span>
  );
}

function dotColor(status: string): string {
  const map: Record<string, string> = {
    'On Time': 'bg-status-ontime',
    Delayed: 'bg-status-delayed',
    Cancelled: 'bg-status-cancelled',
    Rebooked: 'bg-accent-500',
    Completed: 'bg-navy-400',
    Confirmed: 'bg-status-ontime',
    Waitlist: 'bg-status-waitlist',
  };
  return map[status] || 'bg-navy-400';
}
