'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { listNotifications, markAllNotificationsRead, markNotificationRead } from '@/lib/api/notifications';
import { useSession } from '@/lib/auth/session';
import { notificationHref } from '@/lib/notifications/deepLink';

const PAGE = 50;

export default function NotificationsPage() {
  const { user, loading } = useSession();
  const qc = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const list = useQuery({ queryKey: ['notifications', 'page', unreadOnly], queryFn: () => listNotifications(unreadOnly, PAGE), enabled: Boolean(user) });
  const refresh = () => qc.invalidateQueries({ queryKey: ['notifications'] });
  const readOne = useMutation({ mutationFn: markNotificationRead, onSuccess: refresh });
  const readAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: refresh });

  if (loading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-3xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to see your notifications.</div>;
  const items = list.data ?? [];
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-black">Notifications</h1>
        <div className="flex items-center gap-3 text-sm">
          <label><input type="checkbox" className="mr-2" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} />Unread only</label>
          <Button type="button" disabled={readAll.isPending || items.every((n) => n.readAt)} onClick={() => readAll.mutate()}>Mark all read</Button>
        </div>
      </div>
      {list.isLoading && <p>Loading…</p>}
      {list.isError && <p role="alert" className="text-red-700">Could not load notifications. <button className="underline" onClick={() => list.refetch()}>Retry</button></p>}
      {list.isSuccess && items.length === 0 && <Card><p className="text-sm text-slate-600">{unreadOnly ? 'No unread notifications.' : 'You have no notifications yet.'}</p></Card>}
      <ul className="space-y-3">
        {items.map((n) => {
          const href = notificationHref(n, user.role);
          return (
            <li key={n.id}>
              <Card className={n.readAt ? 'opacity-80' : 'border-slate-400'}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className={n.readAt ? 'font-medium' : 'font-bold'}>{n.title}</p>
                    {n.body && <p className="mt-1 text-sm text-slate-700">{n.body}</p>}
                    <p className="mt-2 text-xs text-slate-500">{new Date(n.createdAt).toLocaleString()}</p>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-2 text-sm">
                    {href && <Link href={href} className="underline" onClick={() => { if (!n.readAt) readOne.mutate(n.id); }}>Open</Link>}
                    {!n.readAt && <button className="underline" onClick={() => readOne.mutate(n.id)}>Mark read</button>}
                  </div>
                </div>
              </Card>
            </li>
          );
        })}
      </ul>
      {items.length === PAGE && <p className="text-xs text-slate-500">Showing the latest {PAGE} notifications.</p>}
    </div>
  );
}
