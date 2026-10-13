'use client';
import { useState } from 'react';
import { DEFAULT_PRESET_DAYS, presetRange } from '@/lib/analytics/range';
import type { AdminInterval, AdminMetric, DateRangeParams } from '@/types/analytics';
import { AnalyticsError, AnalyticsSkeleton } from './AnalyticsState';
import { DateRangeFilter } from './DateRangeFilter';
import { useAdminOverview, useAdminTimeseries } from './hooks';
import { StatCard } from './StatCard';
import { TrendChart } from './TrendChart';

const METRICS: { value: AdminMetric; label: string }[] = [
  { value: 'APPLICATIONS_SUBMITTED', label: 'Applications submitted' },
  { value: 'JOBS_PUBLISHED', label: 'Jobs published' },
  { value: 'USERS_REGISTERED', label: 'Users registered' },
];
const INTERVALS: { value: AdminInterval; label: string }[] = [
  { value: 'DAY', label: 'Daily' }, { value: 'WEEK', label: 'Weekly' }, { value: 'MONTH', label: 'Monthly' },
];

/** Platform KPIs and time series for the proposed, unmerged Session 5 contract change (§12.11.2). ADMIN only. */
export function AdminAnalyticsView() {
  const [range, setRange] = useState<DateRangeParams>(() => presetRange(DEFAULT_PRESET_DAYS));
  const [metric, setMetric] = useState<AdminMetric>('APPLICATIONS_SUBMITTED');
  const [interval, setInterval] = useState<AdminInterval>('DAY');
  const overview = useAdminOverview(range);
  const series = useAdminTimeseries(metric, interval, range);
  const metricLabel = METRICS.find((m) => m.value === metric)?.label ?? metric;
  const o = overview.data;

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <h1 className="text-3xl font-black">Platform analytics</h1>
      <p className="mt-1 text-slate-600">Aggregate platform numbers. Dates are UTC.</p>
      <DateRangeFilter value={range} onChange={setRange} />

      {overview.isPending && <AnalyticsSkeleton />}
      {overview.isError && <AnalyticsError error={overview.error} onRetry={() => void overview.refetch()} retrying={overview.isFetching} />}
      {o && (
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-busy={overview.isFetching}>
          <StatCard label="New users" value={o.users.registered} hint={`${o.users.jobSeekers} seekers · ${o.users.recruiters} recruiters · ${o.users.admins} admins`} />
          <StatCard label="Jobs published" value={o.jobs.published} hint={`${o.jobs.currentlyOpen} open now`} />
          <StatCard label="Applications" value={o.applications.submitted} hint={`${o.applications.hired} hired`} />
          <StatCard label="Reports filed" value={o.reports.filed} hint={`${o.reports.open} open now`} />
        </div>
      )}

      <div className="mt-8 flex flex-wrap items-end gap-4">
        <div>
          <label htmlFor="admin-metric" className="block text-xs font-semibold text-slate-600">Metric</label>
          <select id="admin-metric" value={metric} onChange={(e) => setMetric(e.target.value as AdminMetric)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
            {METRICS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="admin-interval" className="block text-xs font-semibold text-slate-600">Interval</label>
          <select id="admin-interval" value={interval} onChange={(e) => setInterval(e.target.value as AdminInterval)} className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
            {INTERVALS.map((i) => <option key={i.value} value={i.value}>{i.label}</option>)}
          </select>
        </div>
      </div>

      {series.isError && <AnalyticsError error={series.error} onRetry={() => void series.refetch()} retrying={series.isFetching} />}
      {series.data && (
        <div className="mt-4" aria-busy={series.isFetching}>
          <TrendChart title={metricLabel} description={`${INTERVALS.find((i) => i.value === interval)?.label ?? interval} totals across the selected range.`}
            valueLabel={metricLabel} data={series.data.points.map((p) => ({ label: p.date, value: p.value }))} />
        </div>
      )}
    </div>
  );
}
