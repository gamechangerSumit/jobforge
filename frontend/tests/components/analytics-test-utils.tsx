import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import type { AdminOverview, JobAnalytics, PipelineStatus, RecruiterOverview } from '@/types/analytics';

export function renderWithClient(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

export const envelope = (data: unknown) => HttpResponse.json({ data, meta: { requestId: 'test', timestamp: '' } });

export const apiError = (status: number, code: string, message: string) =>
  HttpResponse.json({ error: { code, message, requestId: 'test' } }, { status });

const pipeline = (over: Partial<Record<PipelineStatus, number>> = {}): Record<PipelineStatus, number> => ({
  SUBMITTED: 0, UNDER_REVIEW: 0, SHORTLISTED: 0, INTERVIEW: 0, OFFERED: 0, HIRED: 0, REJECTED: 0, WITHDRAWN: 0, ...over,
});

export const overviewFixture = (over: Partial<RecruiterOverview> = {}): RecruiterOverview => ({
  from: '2026-09-12', to: '2026-10-11', applications: 42,
  pipeline: pipeline({ SUBMITTED: 10, UNDER_REVIEW: 8, SHORTLISTED: 5, INTERVIEW: 4, OFFERED: 2, HIRED: 3, REJECTED: 7, WITHDRAWN: 3 }),
  conversion: { hired: 3, rate: 0.0714 }, timeToHire: { hired: 3, averageDays: 12.4, medianDays: 11 }, jobs: { published: 6, open: 4 }, ...over,
});

export const emptyOverviewFixture = (): RecruiterOverview => overviewFixture({
  applications: 0, pipeline: pipeline(), conversion: { hired: 0, rate: null }, timeToHire: { hired: 0, averageDays: null, medianDays: null },
});

export const jobFixture = (): JobAnalytics => ({
  jobId: 'j-1', from: '2026-10-01', to: '2026-10-03', applications: 17,
  funnel: [
    { stage: 'SUBMITTED', count: 17 }, { stage: 'UNDER_REVIEW', count: 12 }, { stage: 'SHORTLISTED', count: 6 },
    { stage: 'INTERVIEW', count: 4 }, { stage: 'OFFERED', count: 2 }, { stage: 'HIRED', count: 1 },
  ],
  outcomes: { REJECTED: 5, WITHDRAWN: 2 },
  series: [{ date: '2026-10-01', applications: 0 }, { date: '2026-10-02', applications: 9 }, { date: '2026-10-03', applications: 8 }],
});

export const adminOverviewFixture = (): AdminOverview => ({
  from: '2026-09-12', to: '2026-10-11', users: { registered: 120, jobSeekers: 100, recruiters: 19, admins: 1 },
  jobs: { published: 55, currentlyOpen: 31 }, applications: { submitted: 410, hired: 12 }, reports: { filed: 9, open: 3 },
});
