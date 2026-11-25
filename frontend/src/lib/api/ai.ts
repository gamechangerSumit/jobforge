import { api } from './client';

export interface AiDraftInput {
  title: string;
  experienceLevel: 'INTERN' | 'ENTRY' | 'MID' | 'SENIOR' | 'LEAD' | 'EXECUTIVE';
  employmentType: 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP' | 'FREELANCE';
  workMode: 'ONSITE' | 'HYBRID' | 'REMOTE';
  location: { city: string; state: string; country: string };
  skills: string[];
  tone: 'PROFESSIONAL' | 'FRIENDLY' | 'CONCISE';
  notes?: string;
  language: 'en';
}
export interface AiDraft {
  title: string;
  summary: string;
  responsibilities: string[];
  requirements: string[];
  niceToHave: string[];
  benefits: string[];
  skills: Array<{ name: string; required: boolean }>;
  employmentType: string;
  workMode: string;
  experienceLevel: string;
  descriptionMarkdown: string;
}
export interface AiDraftResult {
  requestId: string;
  operation: string;
  provider: string;
  model: string;
  schemaVersion: string;
  draft: AiDraft;
  quality: { score: number; issues: Array<{ code: string; severity: 'INFO' | 'WARN' | 'ERROR'; field?: string; message: string; suggestion?: string }> };
  quota: { creditsCharged: number; remaining: number; limit: number; period: string; resetsAt: string };
}
export interface AiUsage {
  period: string;
  limit: number;
  used: number;
  remaining: number;
  resetsAt: string;
  rateLimitPerMinute: number;
  operations: Array<{ operation: string; credits: number }>;
}
export const generateJobDraft = (input: AiDraftInput, idempotencyKey: string) =>
  api.post<AiDraftResult>('/ai/job-drafts', input, { 'Idempotency-Key': idempotencyKey });
export const getAiUsage = () => api.get<AiUsage>('/ai/usage/me');
export interface AiRequestDetail { id: string; operation: string; status: string; output: AiDraft; qualityScore: number | null; }
export const getAiRequestDetail = (id: string) => api.get<AiRequestDetail>(`/ai/requests/${id}`);

export interface AiQuotaPolicy {
  role: 'JOB_SEEKER' | 'RECRUITER' | 'ADMIN';
  period: 'DAILY' | 'MONTHLY';
  creditLimit: number;
  rateLimitPerMinute: number;
  active: boolean;
}
export interface AiAdminUsage {
  operation: string;
  requestCount: number;
  creditsUsed: number;
  releasedCount: number;
}
export const getAiQuotaPolicies = () => api.get<AiQuotaPolicy[]>('/ai/admin/quota-policies');
export const updateAiQuotaPolicy = (policy: AiQuotaPolicy) =>
  api.put<AiQuotaPolicy[]>('/ai/admin/quota-policies', policy);
export const getAiAdminUsage = () => api.get<AiAdminUsage[]>('/ai/admin/usage');

export interface AiJobRecommendation {
  jobId: string;
  title: string;
  companyName: string;
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  reason: string;
}
export interface AiJobRecommendations {
  recommendations: AiJobRecommendation[];
  meta: { provider: string };
}
export interface CareerInsights {
  profileStrength: { score: number; suggestions: string[] };
  skillGaps: Array<{ skill: string; jobsMentioningSkill: number }>;
  marketSnapshot: { topSkills: Array<{ skill: string; jobCount: number }>; sampledJobs: number };
  nextSteps: string[];
}
export const getJobRecommendations = () => api.post<AiJobRecommendations>('/ai/recommendations/jobs');
export const getCareerInsights = () => api.post<CareerInsights>('/ai/career-insights');

export interface AiCandidateRecommendation {
  profileId: string;
  applicationId: string;
  headline: string;
  currentTitle: string;
  matchScore: number;
  matchedSkills: string[];
  missingSkills: string[];
  reason: string;
}
export interface AiCandidateRecommendations {
  candidates: AiCandidateRecommendation[];
  meta: { provider: string };
}
export const getCandidateRecommendations = (jobId: string) =>
  api.post<AiCandidateRecommendations>('/ai/recommendations/candidates', { jobId });
