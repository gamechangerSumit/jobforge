'use client';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { listRecruiterJobs } from '@/lib/api/jobs';
import { formatDays, formatRate, isEmptyOverview } from '@/lib/analytics/charts';
import { DEFAULT_PRESET_DAYS, presetRange } from '@/lib/analytics/range';
import type { DateRangeParams } from '@/types/analytics';
import { AnalyticsEmpty, AnalyticsError, AnalyticsSkeleton } from './AnalyticsState';
import { DateRangeFilter } from './DateRangeFilter';
import { useRecruiterOverview } from './hooks';
import { StatCard } from './StatCard';
import { StatusDistribution } from './StatusDistribution';

/** Recruiter/company overview for the proposed, unmerged Session 5 contract change (§12.10.1) with links to per-job funnels. */
export function RecruiterAnalyticsView() {
  const [range, setRange] = useState<DateRangeParams>(() => presetRange(DEFAULT_PRESET_DAYS));
  const overview = useRecruiterOverview(range);
  const jobs = useQuery({ queryKey: ['recruiter-jobs', 'analytics'], queryFn: () => listRecruiterJobs({ page: 0, size: 50 }) });
  const data = overview.data;

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="text-3xl font-black">Analytics</h1>
      <p className="mt-1 text-slate-600">How your jobs and applications are performing. Dates are UTC.</p>
      <DateRangeFilter value={range} onChange={setRange} />

      {overview.isPending && <AnalyticsSkeleton />}
      {overview.isError && <AnalyticsError error={overview.error} onRetry={() => void overview.refetch()} retrying={overview.isFetching} />}

      {data && (
        <div aria-busy={overview.isFetching} className={overview.isFetching ? 'opacity-70' : undefined}>
          <p className="mt-6 text-sm text-slate-500">Showing {data.from} to {data.to}.</p>
          <div className="mt-2 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Applications" value={data.applications} hint="Submitted in range" />
            <StatCard label="Conversion" value={formatRate(data.conversion.rate)} hint={`${data.conversion.hired} hired`} />
            <StatCard label="Time to hire" value={formatDays(data.timeToHire.averageDays)}
              hint={data.timeToHire.medianDays === null ? 'No hires in range' : `Median ${formatDays(data.timeToHire.medianDays)}`} />
            <StatCard label="Jobs" value={data.jobs.open} hint={`open now · ${data.jobs.published} published in range`} />
          </div>
          {isEmptyOverview(data)
            ? <AnalyticsEmpty>No applications were submitted in this range. Try a longer range.</AnalyticsEmpty>
            : <div className="mt-6"><StatusDistribution overview={data} /></div>}
        </div>
      )}

      <section className="mt-8" aria-labelledby="job-analytics-h">
        <h2 id="job-analytics-h" className="text-lg font-bold">Per-job funnel</h2>
        {jobs.isPending && <p className="mt-2 text-sm text-slate-500" role="status">Loading jobs…</p>}
        {jobs.isError && <p className="mt-2 text-sm text-red-700" role="alert">Your jobs could not be loaded.</p>}
        {jobs.data?.length === 0 && <p className="mt-2 text-sm text-slate-600">You have no jobs yet.</p>}
        <ul className="mt-2 divide-y rounded-2xl border bg-white">
          {jobs.data?.map((job) => (
            <li key={job.id} className="flex items-center justify-between p-4 text-sm">
              <span className="font-semibold">{job.title}</span>
              <Link className="underline" href={`/recruiter/analytics/jobs/${job.id}`} aria-label={`View funnel for ${job.title}`}>View funnel</Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
