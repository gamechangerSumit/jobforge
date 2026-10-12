/** Mirrors the proposed, unmerged Session 5 contract change (§12.10.1 and §12.11.2). Aggregates only: no personal data. */
export type PipelineStatus =
  | 'SUBMITTED' | 'UNDER_REVIEW' | 'SHORTLISTED' | 'INTERVIEW' | 'OFFERED' | 'HIRED' | 'REJECTED' | 'WITHDRAWN';

export type FunnelStage = 'SUBMITTED' | 'UNDER_REVIEW' | 'SHORTLISTED' | 'INTERVIEW' | 'OFFERED' | 'HIRED';

/** Inclusive UTC calendar dates, `YYYY-MM-DD`. */
export interface DateRangeParams { from: string; to: string }

export interface RecruiterOverview {
  from: string;
  to: string;
  applications: number;
  pipeline: Record<PipelineStatus, number>;
  conversion: { hired: number; rate: number | null };
  timeToHire: { hired: number; averageDays: number | null; medianDays: number | null };
  jobs: { published: number; open: number };
}

export interface JobAnalytics {
  jobId: string;
  from: string;
  to: string;
  applications: number;
  funnel: { stage: FunnelStage; count: number }[];
  outcomes: { REJECTED: number; WITHDRAWN: number };
  series: { date: string; applications: number }[];
}

export interface AdminOverview {
  from: string;
  to: string;
  users: { registered: number; jobSeekers: number; recruiters: number; admins: number };
  jobs: { published: number; currentlyOpen: number };
  applications: { submitted: number; hired: number };
  reports: { filed: number; open: number };
}

export type AdminMetric = 'USERS_REGISTERED' | 'JOBS_PUBLISHED' | 'APPLICATIONS_SUBMITTED';
export type AdminInterval = 'DAY' | 'WEEK' | 'MONTH';

export interface AdminTimeseries {
  metric: AdminMetric;
  interval: AdminInterval;
  from: string;
  to: string;
  points: { date: string; value: number }[];
}
