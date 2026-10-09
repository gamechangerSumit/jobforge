import { api } from './client';
import type { Page } from './client';

export interface AdminUserRow {
  id: string; email: string; firstName: string; lastName: string; handle: string;
  role: 'JOB_SEEKER' | 'RECRUITER' | 'ADMIN'; status: 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'DELETED';
  emailVerified: boolean; createdAt: string; lastLoginAt?: string | null;
}
export interface AdminJobRow {
  id: string; title: string; slug: string; status: string; company?: { id: string; name: string } | null;
  publishedAt?: string | null; expiresAt?: string | null;
}
export interface AuditLogRow {
  id: string; occurredAt: string; actorUserId?: string | null; actorRole?: string | null; source: string; action: string;
  entityType: string; entityId?: string | null; outcome: string; metadata?: Record<string, unknown> | null;
  ip?: string | null; requestId?: string | null;
}
export interface AuditFilter { actorId?: string; action?: string; entityType?: string; entityId?: string; from?: string; to?: string; page?: number; size?: number }

const qs = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== '') search.set(k, String(v)); });
  const text = search.toString();
  return text ? `?${text}` : '';
};

// users
export const listAdminUsers = (f: { q?: string; role?: string; status?: string; page?: number; size?: number }): Promise<Page<AdminUserRow>> =>
  api.getPage<AdminUserRow>(`/admin/users${qs(f)}`);
export const setUserStatus = (id: string, status: 'ACTIVE' | 'SUSPENDED', reason: string) =>
  api.patch<AdminUserRow>(`/admin/users/${id}/status`, { status, reason });
export const forceLogoutUser = (id: string) => api.post<void>(`/admin/users/${id}/force-logout`);
export const createAdminUser = (input: { email: string; firstName: string; lastName: string }) =>
  api.post<AdminUserRow>('/admin/users', input);

// jobs
export const listAdminJobs = (f: { q?: string; status?: string; page?: number; size?: number }): Promise<Page<AdminJobRow>> =>
  api.getPage<AdminJobRow>(`/admin/jobs${qs(f)}`);
export const removeJob = (id: string, reason: string) => api.post<AdminJobRow>(`/admin/jobs/${id}/remove`, { reason });
export const restoreJob = (id: string) => api.post<AdminJobRow>(`/admin/jobs/${id}/restore`);

// audit
export const listAuditLogs = (f: AuditFilter): Promise<Page<AuditLogRow>> => api.getPage<AuditLogRow>(`/admin/audit-logs${qs({ ...f })}`);

export interface AdminRecruiterDetail {
  userId: string; email: string; firstName: string; lastName: string; jobTitle?: string | null; phone?: string | null;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'; rejectionReason?: string | null;
  companyName?: string | null; companyId?: string | null; createdAt: string;
}
export const getAdminRecruiter = (userId: string) => api.get<AdminRecruiterDetail>(`/admin/recruiters/${userId}`);
