import { api } from './client';
import type { Page } from './client';

export interface Company {
  id: string; name: string; slug: string; description?: string | null; industry?: string | null;
  sizeBand?: string | null; websiteUrl?: string | null; hqCity?: string | null; hqState?: string | null;
  hqCountry?: string | null; foundedYear?: number | null; verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'SUSPENDED';
  verified: boolean; logoUrl?: string | null; rejectionReason?: string | null; openJobCount: number; version: number;
}
export interface CompanyMember { userId: string; firstName: string; lastName: string; email: string; memberRole: 'OWNER' | 'MEMBER' }
export interface MyCompany { company: Company; memberRole: 'OWNER' | 'MEMBER'; members: CompanyMember[] }
export interface CompanyInput {
  name: string; description?: string; industry?: string; sizeBand?: string; websiteUrl?: string;
  hqCity?: string; hqState?: string; hqCountry?: string; foundedYear?: number;
}
export interface AdminCompanyRow { id: string; name: string; slug: string; verificationStatus: string; rejectionReason?: string | null; ownerEmail?: string | null; memberCount: number }
export interface AdminRecruiterRow { userId: string; email: string; firstName: string; lastName: string; jobTitle?: string | null; approvalStatus: string; companyName?: string | null }

export const getMyCompany = () => api.get<MyCompany>('/companies/me');
export const createCompany = (input: CompanyInput) => api.post<Company>('/companies', input);
export const updateCompany = (id: string, version: number, input: Partial<CompanyInput>) =>
  api.patch<Company>(`/companies/${id}`, input, { 'If-Match': `"${version}"` });
export const addCompanyMember = (id: string, email: string) => api.post<CompanyMember[]>(`/companies/${id}/members`, { email });
export const removeCompanyMember = (id: string, userId: string) => api.delete<void>(`/companies/${id}/members/${userId}`);

export const listAdminCompanies = (status = 'PENDING'): Promise<Page<AdminCompanyRow>> =>
  api.getPage<AdminCompanyRow>(`/admin/companies?status=${encodeURIComponent(status)}&size=50`);
export const verifyCompany = (id: string) => api.post<void>(`/admin/companies/${id}/verify`);
export const rejectCompany = (id: string, reason: string) => api.post<void>(`/admin/companies/${id}/reject`, { reason });
export const listAdminRecruiters = (status = 'PENDING'): Promise<Page<AdminRecruiterRow>> =>
  api.getPage<AdminRecruiterRow>(`/admin/recruiters?status=${encodeURIComponent(status)}&size=50`);
export const approveRecruiter = (userId: string) => api.post<void>(`/admin/recruiters/${userId}/approve`);
export const rejectRecruiter = (userId: string, reason: string) => api.post<void>(`/admin/recruiters/${userId}/reject`, { reason });

/** Public company profile (GET /companies/{id}); the backend has no by-slug read yet, so pages resolve by id. */
export const getCompany = (id: string) => api.get<Company>(`/companies/${id}`);
export const companyLogoSrc = (id: string) => `/api/v1/companies/${id}/logo`;
