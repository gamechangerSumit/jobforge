'use client';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  getNotificationPreferences, listNotifications, markAllNotificationsRead, markNotificationRead,
  NotificationPreference, streamNotifications, updateNotificationPreferences,
} from '@/lib/api/notifications';
import { useSession } from '@/lib/auth/session';
import { notificationHref } from '@/lib/notifications/deepLink';

const PAGE_SIZE = 20;

export default function NotificationsPage() {
  const { user, loading } = useSession();
  const qc = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [preferences, setPreferences] = useState<NotificationPreference[]>([]);
  const list = useInfiniteQuery({
    queryKey: ['notifications', 'pages', unreadOnly],
    queryFn: ({ pageParam }) => listNotifications(unreadOnly, pageParam, PAGE_SIZE),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.next : undefined,
    enabled: Boolean(user),
  });
  const preferenceQuery = useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: getNotificationPreferences,
    enabled: Boolean(user),
  });
  useEffect(() => { if (preferenceQuery.data) setPreferences(preferenceQuery.data); }, [preferenceQuery.data]);
  useEffect(() => {
    if (!user) return;
    const controller = new AbortController();
    void streamNotifications(() => { void qc.invalidateQueries({ queryKey: ['notifications'] }); }, controller.signal)
      .catch(() => { /* Inbox remains usable through explicit refresh and pagination. */ });
    return () => controller.abort();
  }, [user?.id, qc]);
  const refresh = () => qc.invalidateQueries({ queryKey: ['notifications'] });
  const readOne = useMutation({ mutationFn: markNotificationRead, onSuccess: refresh });
  const readAll = useMutation({ mutationFn: markAllNotificationsRead, onSuccess: refresh });
  const savePreferences = useMutation({ mutationFn: updateNotificationPreferences, onSuccess: (saved) => {
    setPreferences(saved);
    void qc.invalidateQueries({ queryKey: ['notifications', 'preferences'] });
  } });
  if (loading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-3xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to see your notifications.</div>;
  const items = list.data?.pages.flatMap((page) => page.items) ?? [];
  const hasUnread = items.some((n) => !n.readAt);
  const togglePreference = (type: string, key: 'inApp' | 'email', checked: boolean) => {
    setPreferences((current) => current.map((item) => item.type === type ? { ...item, [key]: checked } : item));
  };
  return (
    <div className="mx-auto max-w-3xl space-y-5 px-4 py-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-black">Notifications</h1>
        <div className="flex items-center gap-3 text-sm">
          <label><input type="checkbox" className="mr-2" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} />Unread only</label>
          <Button type="button" disabled={readAll.isPending || !hasUnread} onClick={() => readAll.mutate()}>Mark all read</Button>
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
      {list.hasNextPage && <div className="text-center"><Button type="button" disabled={list.isFetchingNextPage} onClick={() => list.fetchNextPage()}>{list.isFetchingNextPage ? 'Loading…' : 'Load more'}</Button></div>}
      <section className="space-y-3 pt-5">
        <div>
          <h2 className="text-xl font-bold">Notification preferences</h2>
          <p className="text-sm text-slate-600">Choose which notification types can appear in your inbox or be sent by email.</p>
        </div>
        {preferenceQuery.isError && <p role="alert" className="text-sm text-red-700">Could not load preferences.</p>}
        <Card>
          <div className="grid grid-cols-[1fr_5rem_5rem] gap-3 border-b pb-2 text-sm font-semibold">
            <span>Type</span><span>In-app</span><span>Email</span>
          </div>
          <div className="divide-y">
            {preferences.map((item) => (
              <div key={item.type} className="grid grid-cols-[1fr_5rem_5rem] items-center gap-3 py-2 text-sm">
                <span className="break-words">{item.type.replaceAll('_', ' ').toLowerCase()}</span>
                <input aria-label={item.type + ' in-app'} type="checkbox" checked={item.inApp} onChange={(e) => togglePreference(item.type, 'inApp', e.target.checked)} />
                <input aria-label={item.type + ' email'} type="checkbox" checked={item.email} onChange={(e) => togglePreference(item.type, 'email', e.target.checked)} />
              </div>
            ))}
          </div>
          <div className="mt-4 flex justify-end">
            <Button type="button" disabled={savePreferences.isPending || preferenceQuery.isLoading} onClick={() => savePreferences.mutate(preferences)}>
              {savePreferences.isPending ? 'Saving…' : 'Save preferences'}
            </Button>
          </div>
          {savePreferences.isError && <p role="alert" className="mt-2 text-sm text-red-700">Could not save preferences.</p>}
        </Card>
      </section>
    </div>
  );
}
