import { screen, within } from '@testing-library/react';
import { http } from 'msw';
import { describe, expect, test, vi } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { apiError, envelope, jobFixture, renderWithClient } from './analytics-test-utils';

vi.mock('next/link', () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode; [key: string]: unknown }) => <a href={href} {...rest}>{children}</a>,
}));

import { JobAnalyticsView } from '@/features/analytics/JobAnalyticsView';

setupMockServer();
const URL_ = '/api/v1/recruiters/me/analytics/jobs/j-1';

describe('JobAnalyticsView', () => {
  test('renders the funnel, outcomes and an accessible daily trend from the response', async () => {
    server.use(http.get(URL_, () => envelope(jobFixture())));
    renderWithClient(<JobAnalyticsView jobId="j-1" />);

    const funnel = await screen.findByRole('list', { name: 'Hiring funnel stages' });
    const rows = within(funnel).getAllByRole('listitem');
    expect(rows).toHaveLength(6);
    expect(rows[1]).toHaveTextContent('Under review');
    expect(rows[1]).toHaveTextContent('12');
    expect(rows[1]).toHaveTextContent('70.6% of previous');
    expect(screen.getByText('Rejected').closest('section')).toHaveTextContent('5');

    const chart = screen.getByRole('img', { name: /Applications per day/ });
    expect(chart).toBeInTheDocument();
    const table = screen.getByRole('table', { name: 'Applications per day' });
    expect(within(table).getAllByRole('row')).toHaveLength(4); // header + 3 days
    expect(within(table).getByText('2026-10-02').closest('tr')).toHaveTextContent('9');
  });

  test('zero applications shows the empty state', async () => {
    server.use(http.get(URL_, () => envelope({ ...jobFixture(), applications: 0 })));
    renderWithClient(<JobAnalyticsView jobId="j-1" />);
    expect(await screen.findByText(/No applications for this job/)).toBeInTheDocument();
  });

  test('a job outside the company (404) is shown as not found with no retry', async () => {
    server.use(http.get(URL_, () => apiError(404, 'RESOURCE_NOT_FOUND', 'Job not found.')));
    renderWithClient(<JobAnalyticsView jobId="j-1" />);
    expect(await screen.findByRole('alert')).toHaveTextContent('This job was not found.');
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
  });
});
