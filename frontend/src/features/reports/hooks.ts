'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createReport, getAdminReport, listAdminReports, resolveReport } from '@/lib/api/reports';
import { ApiClientError } from '@/lib/api/client';
import type { AdminReportFilter, CreateReportRequest, ResolveReportRequest } from '@/types/reports';

export const reportKeys = {
  all: ['admin', 'reports'] as const,
  list: (f: AdminReportFilter) => ['admin', 'reports', 'list', f] as const,
  detail: (id: string) => ['admin', 'reports', 'detail', id] as const,
};

export const isForbidden = (e: unknown) => e instanceof ApiClientError && e.status === 403;
export const isNotFound = (e: unknown) => e instanceof ApiClientError && e.status === 404;

export function useCreateReport() {
  return useMutation({ mutationFn: (body: CreateReportRequest) => createReport(body) });
}

export function useAdminReports(filter: AdminReportFilter) {
  return useQuery({ queryKey: reportKeys.list(filter), queryFn: () => listAdminReports(filter), retry: (n, e) => !isForbidden(e) && n < 2 });
}

export function useAdminReport(id: string) {
  return useQuery({ queryKey: reportKeys.detail(id), queryFn: () => getAdminReport(id), enabled: Boolean(id), retry: (n, e) => !isForbidden(e) && !isNotFound(e) && n < 2 });
}

/** The decision returns the updated report: it replaces the cached detail and refreshes every queue page. */
export function useResolveReport(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ResolveReportRequest) => resolveReport(id, body),
    onSuccess: (updated) => {
      qc.setQueryData(reportKeys.detail(id), updated);
      void qc.invalidateQueries({ queryKey: [...reportKeys.all, 'list'] });
      // a removed job / suspended user also changes the other admin lists
      void qc.invalidateQueries({ queryKey: ['admin', 'jobs'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'users'] });
    },
  });
}
