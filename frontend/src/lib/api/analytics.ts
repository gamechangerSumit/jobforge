import { api } from './client';
import type {
  AdminInterval, AdminMetric, AdminOverview, AdminTimeseries, DateRangeParams, JobAnalytics, RecruiterOverview,
} from '@/types/analytics';

function query(params: Record<string, string | undefined>) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value) q.set(key, value);
  });
  const result = q.toString();
  return result ? `?${result}` : '';
}

export const getRecruiterOverview = (range: DateRangeParams) =>
  api.get<RecruiterOverview>(`/recruiters/me/analytics/overview${query({ from: range.from, to: range.to })}`);

export const getJobAnalytics = (jobId: string, range: DateRangeParams) =>
  api.get<JobAnalytics>(`/recruiters/me/analytics/jobs/${encodeURIComponent(jobId)}${query({ from: range.from, to: range.to })}`);

export const getAdminOverview = (range: DateRangeParams) =>
  api.get<AdminOverview>(`/admin/analytics/overview${query({ from: range.from, to: range.to })}`);

export const getAdminTimeseries = (metric: AdminMetric, interval: AdminInterval, range: DateRangeParams) =>
  api.get<AdminTimeseries>(`/admin/analytics/timeseries${query({ metric, interval, from: range.from, to: range.to })}`);
