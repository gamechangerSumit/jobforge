import { api } from './client';
import type { Page } from './client';
import type {
  AdminReportDetail, AdminReportFilter, AdminReportItem, CreateReportRequest, ReportAck, ResolveReportRequest,
} from '@/types/reports';

const qs = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== '') search.set(k, String(v)); });
  const text = search.toString();
  return text ? `?${text}` : '';
};

/** POST /reports (any authenticated role). */
export const createReport = (body: CreateReportRequest) => api.post<ReportAck>('/reports', body);

/** GET /admin/reports (ADMIN only). */
export const listAdminReports = (f: AdminReportFilter = {}): Promise<Page<AdminReportItem>> =>
  api.getPage<AdminReportItem>(`/admin/reports${qs({ ...f })}`);

export const getAdminReport = (id: string) => api.get<AdminReportDetail>(`/admin/reports/${id}`);

export const resolveReport = (id: string, body: ResolveReportRequest) => api.post<AdminReportDetail>(`/admin/reports/${id}/resolve`, body);
