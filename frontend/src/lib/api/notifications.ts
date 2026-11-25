import { api, getAccessToken } from './client';
import { me } from './auth';

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body?: string | null;
  data?: Record<string, unknown> | null;
  readAt?: string | null;
  createdAt: string;
}
export interface NotificationPreference {
  type: string;
  inApp: boolean;
  email: boolean;
}
export const listNotifications = (unread = false, cursor?: string | null, limit = 20) => {
  const params = new URLSearchParams({ unread: String(unread), limit: String(limit) });
  if (cursor) params.set('cursor', cursor);
  return api.getCursorPage<AppNotification>(`/notifications?${params.toString()}`);
};
export const unreadCount = () => api.get<{ count: number }>('/notifications/unread-count');
export const markNotificationRead = (id: string) => api.post<void>(`/notifications/${id}/read`);
export const markAllNotificationsRead = () => api.post<void>('/notifications/read-all');
export const getNotificationPreferences = () => api.get<NotificationPreference[]>('/notifications/preferences');
export const updateNotificationPreferences = (preferences: NotificationPreference[]) =>
  api.put<NotificationPreference[]>('/notifications/preferences', preferences);

/** Authenticated fetch-based SSE (EventSource cannot send the required bearer header). */
export async function streamNotifications(
  onNotification: (notification: AppNotification) => void,
  signal: AbortSignal,
): Promise<void> {
  if (!getAccessToken()) {
    try { await me(); } catch { return; }
  }
  const token = getAccessToken();
  if (!token) return;
  const response = await fetch('/api/v1/notifications/stream', {
    headers: { Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
    credentials: 'include',
    cache: 'no-store',
    signal,
  });
  if (!response.ok || !response.body) throw new Error(`Notification stream failed (${response.status}).`);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (!signal.aborted) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const frame = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const eventName = frame.split('\n').find((line) => line.startsWith('event:'))?.slice(6).trim();
      const data = frame.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trim()).join('\n');
      if (eventName === 'notification' && data) {
        try { onNotification(JSON.parse(data) as AppNotification); } catch { /* Ignore malformed event payloads. */ }
      }
      boundary = buffer.indexOf('\n\n');
    }
  }
}
