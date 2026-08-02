import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle, Bell, Ticket, Settings, Check, Clock, ArrowRight,
  Sparkles,
} from 'lucide-react';
import notificationsData from '@/data/notifications.json';
import bookingsData from '@/data/bookings.json';
import type { NotificationItem, TimelineStep } from '@/types';
import { PageHeader } from '@/components/PageHeader';
import { severityColor, categoryColor } from '@/lib/format';
import { cn } from '@/lib/cn';

type Category = 'All' | 'Disruption' | 'Seat' | 'Booking' | 'System';

const categoryIcons: Record<string, typeof Bell> = {
  Disruption: AlertTriangle,
  Seat: Sparkles,
  Booking: Ticket,
  System: Settings,
};

export function NotificationsPage() {
  const [filter, setFilter] = useState<Category>('All');
  const [readIds, setReadIds] = useState<Set<string>>(new Set());
  const allNotifications = notificationsData.notifications as NotificationItem[];
  const timeline = notificationsData.timeline as TimelineStep[];

  const markAsRead = (id: string) => {
    setReadIds((prev) => new Set(prev).add(id));
  };

  const isRead = (notif: NotificationItem) => notif.read || readIds.has(notif.id);

  const filtered = filter === 'All'
    ? allNotifications
    : allNotifications.filter((n) => n.category === filter);

  const unreadCount = allNotifications.filter((n) => !isRead(n)).length;
  const completedSteps = timeline.filter((t) => t.status !== 'pending').length;
  const timelineBooking = bookingsData.bookings.find((b) => b.id === 'b001');

  return (
    <div>
      <PageHeader
        title="Notifications"
        subtitle={`${unreadCount} unread · ${allNotifications.length} total`}
      />

      <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Notification List */}
          <div className="lg:col-span-2">
            {/* Category Filters */}
            <div className="mb-4 flex flex-wrap gap-2">
              {(['All', 'Disruption', 'Seat', 'Booking', 'System'] as Category[]).map((cat) => (
                <button
                  key={cat}
                  onClick={() => setFilter(cat)}
                  className={cn(
                    'rounded-btn px-3 py-1.5 text-14 font-medium transition-colors',
                    filter === cat
                      ? 'bg-navy-600 text-white'
                      : 'bg-white border border-navy-100 text-navy-500 hover:bg-navy-50',
                  )}
                >
                  {cat}
                  {cat !== 'All' && (
                    <span className="ml-1.5 text-12 text-navy-300">
                      {allNotifications.filter((n) => n.category === cat).length}
                    </span>
                  )}
                </button>
              ))}
            </div>

            {/* Notifications */}
            <div className="space-y-3">
              {filtered.map((notif) => {
                const Icon = categoryIcons[notif.category] || Bell;
                return (
                  <div
                    key={notif.id}
                    onClick={() => markAsRead(notif.id)}
                    className={cn(
                      'card flex cursor-pointer gap-4 p-4 transition-all hover:shadow-hover',
                      !isRead(notif) && 'border-l-4 border-l-accent-500',
                    )}
                  >
                    <div className="flex-shrink-0">
                      <div className={cn('flex h-10 w-10 items-center justify-center rounded-btn', categoryColor(notif.category))}>
                        <Icon className="h-5 w-5" strokeWidth={2} />
                      </div>
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="text-14 font-semibold text-navy-600">{notif.title}</p>
                          <p className="mt-0.5 text-14 text-navy-400">{notif.message}</p>
                        </div>
                        {!isRead(notif) && (
                          <span className="flex-shrink-0 rounded-full bg-accent-500 px-2 py-0.5 text-12 font-bold text-white">
                            New
                          </span>
                        )}
                      </div>
                      <div className="mt-2 flex items-center gap-3 text-12 text-navy-300">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {notif.timestamp}
                        </span>
                        <span className={cn('rounded-md px-2 py-0.5 text-12 font-medium', categoryColor(notif.category))}>
                          {notif.category}
                        </span>
                        {notif.bookingId && (
                          <Link to="/dashboard" className="flex items-center gap-1 text-accent-600 hover:text-accent-700">
                            View trip <ArrowRight className="h-3 w-3" />
                          </Link>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Journey Timeline */}
          <div className="lg:col-span-1">
            <div className="card sticky top-20 p-6">
              <h3 className="text-16 font-semibold text-navy-600">Journey Timeline</h3>
              <p className="mt-1 text-12 text-navy-400">{timelineBooking ? `${timelineBooking.trainName} · ${timelineBooking.journeyDate}` : 'Journey timeline'}</p>

              <div className="mt-6 space-y-0">
                {timeline.map((item, idx) => {
                  const Icon = timelineIcon(item.status);
                  return (
                    <div key={item.step} className="flex gap-3">
                      <div className="flex flex-col items-center">
                        <div className={cn(
                          'flex h-8 w-8 items-center justify-center rounded-full border-2 transition-colors',
                          item.status === 'done' && 'border-status-ontime bg-status-ontime',
                          item.status === 'warning' && 'border-status-delayed bg-status-delayed',
                          item.status === 'info' && 'border-accent-500 bg-accent-500',
                          item.status === 'pending' && 'border-navy-200 bg-white',
                        )}>
                          <Icon className={cn(
                            'h-4 w-4',
                            item.status === 'pending' ? 'text-navy-300' : 'text-white',
                          )} strokeWidth={2} />
                        </div>
                        {idx < timeline.length - 1 && (
                          <div className={cn('w-0.5 flex-1', item.status === 'pending' ? 'bg-navy-100' : 'bg-navy-200')} style={{ minHeight: '32px' }} />
                        )}
                      </div>
                      <div className="flex-1 pb-6">
                        <p className={cn(
                          'text-14 font-semibold',
                          item.status === 'pending' ? 'text-navy-300' : 'text-navy-600',
                        )}>
                          {item.label}
                        </p>
                        <p className={cn('text-12', item.status === 'pending' ? 'text-navy-300' : 'text-navy-400')}>
                          {item.detail}
                        </p>
                        <p className="mt-1 text-12 text-navy-300">{item.time}</p>
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="mt-4 border-t border-navy-100 pt-4">
                <div className="flex items-center gap-2 text-12 text-navy-400">
                  <Sparkles className="h-3.5 w-3.5 text-accent-500" />
                  {completedSteps} of {timeline.length} steps auto-handled by AI
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function timelineIcon(status: string) {
  switch (status) {
    case 'done': return Check;
    case 'warning': return AlertTriangle;
    case 'info': return Sparkles;
    case 'pending': return Clock;
    default: return Clock;
  }
}
