import { api } from './client';

export interface AppNotification {
  id: string; type: string; title: string; body?: string | null; data?: Record<string, unknown> | null;
  readAt?: string | null; createdAt: string;
}
export const listNotifications = (unread = false, limit = 20) => api.get<AppNotification[]>(`/notifications?unread=${unread}&limit=${limit}`);
export const unreadCount = () => api.get<{ count: number }>('/notifications/unread-count');
export const markNotificationRead = (id: string) => api.post<void>(`/notifications/${id}/read`);
export const markAllNotificationsRead = () => api.post<void>('/notifications/read-all');
