import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, test } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { adminOverviewFixture, apiError, envelope, renderWithClient } from './analytics-test-utils';
import { AdminAnalyticsView } from '@/features/analytics/AdminAnalyticsView';

setupMockServer();
const OVERVIEW = '/api/v1/admin/analytics/overview';
const SERIES = '/api/v1/admin/analytics/timeseries';

const series = (metric: string, interval: string) => ({
  metric, interval, from: '2026-10-01', to: '2026-10-03',
  points: [{ date: '2026-10-01', value: 14 }, { date: '2026-10-02', value: 0 }, { date: '2026-10-03', value: 9 }],
});

describe('AdminAnalyticsView', () => {
  test('maps KPIs and the default time series', async () => {
    const requests: URL[] = [];
    server.use(
      http.get(OVERVIEW, () => envelope(adminOverviewFixture())),
      http.get(SERIES, ({ request }) => { const u = new URL(request.url); requests.push(u); return envelope(series(u.searchParams.get('metric') ?? '', u.searchParams.get('interval') ?? '')); }),
    );
    renderWithClient(<AdminAnalyticsView />);

    expect(await screen.findByText('120')).toBeInTheDocument();
    expect(screen.getByText(/100 seekers · 19 recruiters · 1 admins/)).toBeInTheDocument();
    expect(screen.getByText('410')).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: /Applications submitted/ })).toBeInTheDocument();
    expect(requests[0].searchParams.get('metric')).toBe('APPLICATIONS_SUBMITTED');
    expect(requests[0].searchParams.get('interval')).toBe('DAY');
  });

  test('changing metric and interval re-requests the series', async () => {
    const requests: URL[] = [];
    server.use(
      http.get(OVERVIEW, () => envelope(adminOverviewFixture())),
      http.get(SERIES, ({ request }) => { const u = new URL(request.url); requests.push(u); return envelope(series(u.searchParams.get('metric') ?? '', u.searchParams.get('interval') ?? '')); }),
    );
    renderWithClient(<AdminAnalyticsView />);
    await screen.findByRole('img', { name: /Applications submitted/ });

    fireEvent.change(screen.getByLabelText('Metric'), { target: { value: 'USERS_REGISTERED' } });
    fireEvent.change(screen.getByLabelText('Interval'), { target: { value: 'WEEK' } });
    await waitFor(() => expect(requests.some((u) => u.searchParams.get('metric') === 'USERS_REGISTERED' && u.searchParams.get('interval') === 'WEEK')).toBe(true));
    expect(await screen.findByRole('img', { name: /Users registered/ })).toBeInTheDocument();
  });

  test('a 403 from the API shows the access message', async () => {
    server.use(
      http.get(OVERVIEW, () => apiError(403, 'ACCESS_DENIED', 'You do not have access to this resource.')),
      http.get(SERIES, () => apiError(403, 'ACCESS_DENIED', 'You do not have access to this resource.')),
    );
    renderWithClient(<AdminAnalyticsView />);
    expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent(/do not have access/);
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });
});
