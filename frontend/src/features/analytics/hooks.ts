'use client';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getAdminOverview, getAdminTimeseries, getJobAnalytics, getRecruiterOverview } from '@/lib/api/analytics';
import type { AdminInterval, AdminMetric, DateRangeParams } from '@/types/analytics';

export const analyticsKeys = {
  recruiterOverview: (r: DateRangeParams) => ['analytics', 'recruiter', 'overview', r.from, r.to] as const,
  job: (jobId: string, r: DateRangeParams) => ['analytics', 'recruiter', 'job', jobId, r.from, r.to] as const,
  adminOverview: (r: DateRangeParams) => ['analytics', 'admin', 'overview', r.from, r.to] as const,
  adminSeries: (m: AdminMetric, i: AdminInterval, r: DateRangeParams) => ['analytics', 'admin', 'series', m, i, r.from, r.to] as const,
};

/** The previous result stays on screen while a new range loads, so charts never flash empty during a filter change. */
export const useRecruiterOverview = (range: DateRangeParams) =>
  useQuery({ queryKey: analyticsKeys.recruiterOverview(range), queryFn: () => getRecruiterOverview(range), placeholderData: keepPreviousData });

export const useJobAnalytics = (jobId: string, range: DateRangeParams) =>
  useQuery({
    queryKey: analyticsKeys.job(jobId, range), queryFn: () => getJobAnalytics(jobId, range), enabled: Boolean(jobId),
    placeholderData: keepPreviousData,
  });

export const useAdminOverview = (range: DateRangeParams) =>
  useQuery({ queryKey: analyticsKeys.adminOverview(range), queryFn: () => getAdminOverview(range), placeholderData: keepPreviousData });

export const useAdminTimeseries = (metric: AdminMetric, interval: AdminInterval, range: DateRangeParams) =>
  useQuery({
    queryKey: analyticsKeys.adminSeries(metric, interval, range), queryFn: () => getAdminTimeseries(metric, interval, range),
    placeholderData: keepPreviousData,
  });
