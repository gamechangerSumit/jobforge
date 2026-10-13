import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, test, vi } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { apiError, emptyOverviewFixture, envelope, overviewFixture, renderWithClient } from './analytics-test-utils';

vi.mock('next/link', () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode; [key: string]: unknown }) => <a href={href} {...rest}>{children}</a>,
}));

import { RecruiterAnalyticsView } from '@/features/analytics/RecruiterAnalyticsView';

setupMockServer();

const OVERVIEW = '/api/v1/recruiters/me/analytics/overview';
const JOBS = '/api/v1/recruiters/me/jobs';

function useJobs(rows: unknown[] = []) {
  server.use(http.get(JOBS, () => envelope(rows)));
}

describe('RecruiterAnalyticsView', () => {
  test('shows a loading skeleton, then maps the API response onto cards and the status chart', async () => {
    useJobs();
    server.use(http.get(OVERVIEW, () => envelope(overviewFixture())));
    renderWithClient(<RecruiterAnalyticsView />);

    expect(screen.getByText('Loading analytics…')).toBeInTheDocument();
    expect(await screen.findByText('42')).toBeInTheDocument(); // applications card
    expect(screen.getByText('7.1%')).toBeInTheDocument();      // conversion
    expect(screen.getByText('12.4 days')).toBeInTheDocument(); // time to hire
    expect(screen.getByText(/Median 11 days/)).toBeInTheDocument();

    const list = screen.getByRole('list', { name: 'Applications by current status' });
    const hired = within(list).getByText('Hired').closest('li') as HTMLElement;
    expect(hired).toHaveTextContent('3');
    expect(hired).toHaveTextContent('(7.1%)');
    expect(within(list).getAllByRole('listitem')).toHaveLength(8);
  });

  test('empty range shows an empty state instead of charts, with dashes for undefined rates', async () => {
    useJobs();
    server.use(http.get(OVERVIEW, () => envelope(emptyOverviewFixture())));
    renderWithClient(<RecruiterAnalyticsView />);
    expect(await screen.findByText(/No applications were submitted in this range/)).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Applications by current status' })).not.toBeInTheDocument();
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(2);
  });

  test('a server error shows an alert and Try again recovers', async () => {
    useJobs();
    let calls = 0;
    server.use(http.get(OVERVIEW, () => (++calls === 1 ? apiError(500, 'INTERNAL_ERROR', 'Something went wrong.') : envelope(overviewFixture()))));
    renderWithClient(<RecruiterAnalyticsView />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/Something went wrong/);
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(calls).toBe(2);
  });

  test('an unapproved recruiter gets the access message and no retry button', async () => {
    useJobs();
    server.use(http.get(OVERVIEW, () => apiError(403, 'RECRUITER_NOT_APPROVED', 'Your recruiter account is not approved yet.')));
    renderWithClient(<RecruiterAnalyticsView />);
    expect(await screen.findByRole('alert')).toHaveTextContent(/not approved yet/);
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });

  test('presets and custom ranges drive the request parameters', async () => {
    useJobs();
    const seen: URL[] = [];
    server.use(http.get(OVERVIEW, ({ request }) => { seen.push(new URL(request.url)); return envelope(overviewFixture()); }));
    renderWithClient(<RecruiterAnalyticsView />);
    await screen.findByText('42');

    const span = (u: URL) => (Date.parse(u.searchParams.get('to') as string) - Date.parse(u.searchParams.get('from') as string)) / 86_400_000 + 1;
    expect(span(seen[0])).toBe(30);

    fireEvent.click(screen.getByRole('button', { name: 'Last 7 days' }));
    await waitFor(() => expect(seen.length).toBe(2));
    expect(span(seen[1])).toBe(7);
    expect(screen.getByRole('button', { name: 'Last 7 days' })).toHaveAttribute('aria-pressed', 'true');

    fireEvent.change(screen.getByLabelText('From (UTC)'), { target: { value: '2026-09-01' } });
    fireEvent.change(screen.getByLabelText('To (UTC)'), { target: { value: '2026-09-30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(seen.length).toBe(3));
    expect(seen[2].searchParams.get('from')).toBe('2026-09-01');
    expect(seen[2].searchParams.get('to')).toBe('2026-09-30');
  });

  test('an invalid custom range is blocked client-side and sends no request', async () => {
    useJobs();
    let calls = 0;
    server.use(http.get(OVERVIEW, () => { calls += 1; return envelope(overviewFixture()); }));
    renderWithClient(<RecruiterAnalyticsView />);
    await screen.findByText('42');

    fireEvent.change(screen.getByLabelText('From (UTC)'), { target: { value: '2026-10-05' } });
    fireEvent.change(screen.getByLabelText('To (UTC)'), { target: { value: '2026-10-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/must not be after/);
    expect(calls).toBe(1);
  });

  test('lists the recruiter jobs with links to the per-job funnel', async () => {
    useJobs([{ id: 'job-1', title: 'Backend Engineer', slug: 'be', status: 'PUBLISHED', applicationCount: 3 }]);
    server.use(http.get(OVERVIEW, () => envelope(overviewFixture())));
    renderWithClient(<RecruiterAnalyticsView />);
    const link = await screen.findByRole('link', { name: /View funnel for Backend Engineer/ });
    expect(link).toHaveAttribute('href', '/recruiter/analytics/jobs/job-1');
  });
});
