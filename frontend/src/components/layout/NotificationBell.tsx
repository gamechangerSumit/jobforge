'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSession } from '@/lib/auth/session';
import { notificationHref } from '@/lib/notifications/deepLink';
import { listNotifications, markAllNotificationsRead, markNotificationRead, unreadCount } from '@/lib/api/notifications';

export function NotificationBell() {
  const qc = useQueryClient();
  const { user } = useSession();
  const [open, setOpen] = useState(false);
  const count = useQuery({ queryKey: ['notifications', 'count'], queryFn: unreadCount, refetchInterval: 60_000 });
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: () => listNotifications(false, 20), enabled: open });
  const refresh = () => qc.invalidateQueries({ queryKey: ['notifications'] });
  const readOne = useMutation({ mutationFn: markNotificationRead, onSuccess: refresh });
  const readAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: refresh });
  const unread = count.data?.count ?? 0;
  return (
    <div className="relative">
      <button aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`} aria-expanded={open} onClick={() => setOpen((v) => !v)} className="relative font-semibold">
        Alerts{unread > 0 && <span className="ml-1 rounded-full bg-red-700 px-1.5 text-xs text-white">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div role="region" aria-label="Notifications" className="absolute right-0 z-20 mt-2 w-80 rounded-xl border border-slate-200 bg-white p-3 shadow-lg">
          <div className="mb-2 flex items-center justify-between text-sm">
            <strong>Notifications</strong>
            <Link href="/notifications" className="underline" onClick={() => setOpen(false)}>View all</Link>
            <button className="underline disabled:opacity-50" disabled={unread === 0} onClick={() => readAll.mutate()}>Mark all read</button>
          </div>
          {list.isError && <p role="alert" className="text-sm text-red-700">Could not load notifications.</p>}
          {list.data?.length === 0 && <p className="text-sm text-slate-600">You are all caught up.</p>}
          <ul className="max-h-80 divide-y overflow-y-auto">
            {list.data?.map((n) => (
              <li key={n.id} className="py-2 text-sm">
                {(() => {
                  const href = notificationHref(n, user?.role);
                  const title = <span className={n.readAt ? 'text-slate-600' : 'font-semibold'}>{n.title}</span>;
                  return href
                    ? <Link href={href} className="block hover:underline" onClick={() => { if (!n.readAt) readOne.mutate(n.id); setOpen(false); }}>{title}</Link>
                    : <p>{title}</p>;
                })()}
                {n.body && <p className="text-slate-600">{n.body}</p>}
                {!n.readAt && <button className="text-xs underline" onClick={() => readOne.mutate(n.id)}>Mark read</button>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
