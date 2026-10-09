import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

const api = vi.hoisted(() => ({
  unreadCount: vi.fn(), listNotifications: vi.fn(), markNotificationRead: vi.fn(), markAllNotificationsRead: vi.fn(),
}));
vi.mock('@/lib/api/notifications', () => api);
vi.mock('@/lib/auth/session', () => ({ useSession: () => ({ user: { role: 'JOB_SEEKER' }, loading: false }) }));

import { NotificationBell } from '@/components/layout/NotificationBell';

const appId = '123e4567-e89b-42d3-a456-426614174000';
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>);

beforeEach(() => {
  Object.values(api).forEach((m) => m.mockReset());
  api.markNotificationRead.mockResolvedValue(undefined);
  api.markAllNotificationsRead.mockResolvedValue(undefined);
});

test('shows the unread count and links notifications to their target', async () => {
  api.unreadCount.mockResolvedValue({ count: 2 });
  api.listNotifications.mockResolvedValue([
    { id: 'n1', type: 'APPLICATION_STATUS_CHANGED', title: 'Status updated', body: 'Shortlisted', data: { applicationId: appId }, readAt: null, createdAt: '2026-01-01T00:00:00Z' },
    { id: 'n2', type: 'OTHER', title: 'No target', body: null, data: {}, readAt: '2026-01-01T00:00:00Z', createdAt: '2026-01-01T00:00:00Z' },
  ]);
  wrap(<NotificationBell />);
  const button = await screen.findByRole('button', { name: /Notifications, 2 unread/ });
  await userEvent.click(button);
  const link = await screen.findByRole('link', { name: 'Status updated' });
  expect(link).toHaveAttribute('href', `/applications/${appId}`);
  expect(screen.queryByRole('link', { name: 'No target' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'View all' })).toHaveAttribute('href', '/notifications');
});

test('mark all read calls the API', async () => {
  api.unreadCount.mockResolvedValue({ count: 1 });
  api.listNotifications.mockResolvedValue([]);
  wrap(<NotificationBell />);
  await userEvent.click(await screen.findByRole('button', { name: /Notifications, 1 unread/ }));
  await userEvent.click(await screen.findByRole('button', { name: 'Mark all read' }));
  await waitFor(() => expect(api.markAllNotificationsRead).toHaveBeenCalled());
});
