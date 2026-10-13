'use client';
import Link from 'next/link';
import { useState } from 'react';
import { DEFAULT_PRESET_DAYS, presetRange } from '@/lib/analytics/range';
import type { DateRangeParams } from '@/types/analytics';
import { AnalyticsEmpty, AnalyticsError, AnalyticsSkeleton } from './AnalyticsState';
import { DateRangeFilter } from './DateRangeFilter';
import { FunnelChart } from './FunnelChart';
import { useJobAnalytics } from './hooks';
import { StatCard } from './StatCard';
import { TrendChart } from './TrendChart';

/** Per-job funnel and daily applications for the proposed, unmerged Session 5 contract change (§12.10.1). */
export function JobAnalyticsView({ jobId }: { jobId: string }) {
  const [range, setRange] = useState<DateRangeParams>(() => presetRange(DEFAULT_PRESET_DAYS));
  const query = useJobAnalytics(jobId, range);
  const data = query.data;

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <Link href="/recruiter/analytics" className="text-sm underline">← All analytics</Link>
      <h1 className="mt-2 text-3xl font-black">Job analytics</h1>
      <DateRangeFilter value={range} onChange={setRange} />

      {query.isPending && <AnalyticsSkeleton />}
      {query.isError && <AnalyticsError error={query.error} onRetry={() => void query.refetch()} retrying={query.isFetching} />}

      {data && (
        <div aria-busy={query.isFetching} className={query.isFetching ? 'opacity-70' : undefined}>
          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            <StatCard label="Applications" value={data.applications} />
            <StatCard label="Rejected" value={data.outcomes.REJECTED} />
            <StatCard label="Withdrawn" value={data.outcomes.WITHDRAWN} />
          </div>
          {data.applications === 0
            ? <AnalyticsEmpty>No applications for this job in the selected range.</AnalyticsEmpty>
            : (
              <div className="mt-6 grid gap-4 lg:grid-cols-2">
                <FunnelChart funnel={data.funnel} />
                <TrendChart title="Applications per day" description="Applications submitted each UTC day."
                  valueLabel="Applications" data={data.series.map((p) => ({ label: p.date, value: p.applications }))} />
              </div>
            )}
        </div>
      )}
    </div>
  );
}
