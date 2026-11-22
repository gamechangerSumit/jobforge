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
