export type UserRole = 'JOB_SEEKER' | 'RECRUITER' | 'ADMIN';

export type EmploymentType =
  | 'FULL_TIME'
  | 'PART_TIME'
  | 'CONTRACT'
  | 'INTERNSHIP'
  | 'FREELANCE';

export type WorkMode = 'ONSITE' | 'HYBRID' | 'REMOTE';

export type ExperienceLevel =
  | 'INTERN'
  | 'ENTRY'
  | 'MID'
  | 'SENIOR'
  | 'LEAD'
  | 'EXECUTIVE';

export type SalaryPeriod = 'YEAR' | 'MONTH' | 'HOUR';

export type JobStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'UNPUBLISHED'
  | 'CLOSED'
  | 'EXPIRED'
  | 'REMOVED';

export type ApplicationStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'SHORTLISTED'
  | 'INTERVIEW'
  | 'OFFERED'
  | 'HIRED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface ApiMeta {
  requestId: string;
  timestamp: string;
  page?: PageMeta;
}

export interface PageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ApiResponse<T> {
  data: T;
  meta: ApiMeta;
}

export interface ApiErrorDetail {
  field?: string;
  code: string;
  message: string;
}

export interface ApiError {
  code: string;
  message: string;
  status: number;
  requestId: string;
  timestamp: string;
  path: string;
  details?: ApiErrorDetail[];
}

export interface ApiErrorEnvelope {
  error: ApiError;
}

export interface UserSummary {
  id: string;
  handle: string;
  firstName: string;
  lastName: string;
  avatarUrl: string | null;
  role: UserRole;
}

export interface CompanySummary {
  id: string;
  name: string;
  slug: string;
  logoUrl: string | null;
  verified: boolean;
}

export interface Location {
  city?: string;
  state?: string;
  country?: string;
}

export interface Salary {
  /** Either bound may be null (open-ended range). */
  min: number | null;
  max: number | null;
  currency: string;
  period: SalaryPeriod;
}

export interface JobSummary {
  id: string;
  title: string;
  company: CompanySummary;
  location: Location;
  workMode: WorkMode;
  employmentType: EmploymentType;
  experienceLevel: ExperienceLevel;
  salary: Salary | null;
  skills: string[];
  postedAt: string;
  saved?: boolean;
  applied?: boolean;
}

/**
 * Row of GET /recruiters/me/jobs (backend RecruiterJobItem). It is NOT a JobSummary: there is no `company`,
 * `skills` or `salary`; `location`, `publishedAt` and `expiresAt` are null when not set.
 */
export interface RecruiterJobSummary {
  id: string;
  title: string;
  slug: string;
  status: JobStatus;
  workMode: WorkMode;
  employmentType: EmploymentType;
  location: Location | null;
  publishedAt: string | null;
  expiresAt: string | null;
  applicationCount: number;
  version: number;
  updatedAt: string;
}

export interface JobSkill {
  name: string;
  required: boolean;
}

/** Skill as returned by GET /jobs/{id} (backend JobDetailView.SkillOut). */
export interface JobDetailSkill extends JobSkill {
  slug: string;
}

/**
 * Detail differs from JobSummary: `skills` carry the required flag, and `location` is omitted by the backend
 * (JsonInclude.NON_NULL) for jobs without a location, e.g. remote roles.
 */
export interface JobDetail extends Omit<JobSummary, 'skills' | 'location'> {
  skills: JobDetailSkill[];
  location?: Location;
  slug: string;
  status: JobStatus;
  description: string;
  requirements: string;
  benefits: string;
  publishedAt: string | null;
  closedAt: string | null;
  aiGenerated: boolean;
  qualityScore: number | null;
  applicationCount?: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
  openings: number;
  salaryVisible: boolean;
}

export interface ResumeSummary {
  id: string;
  fileName: string;
  primary: boolean;
  createdAt: string;
}

export interface ApplicationStatusHistory {
  from: ApplicationStatus | null;
  to: ApplicationStatus;
  at: string;
  reason?: string | null;
}

export interface ProfileSnapshot {
  headline: string | null;
  skills: string[];
  yearsExperience: number | null;
}

/** Job reference embedded in applications (backend JobLiteView): not a full JobSummary. */
export interface ApplicationJob {
  id: string;
  title: string;
  company: CompanySummary;
  location: Location | null;
}

export interface Application {
  id: string;
  jobId: string;
  job: ApplicationJob;
  seeker?: UserSummary;
  status: ApplicationStatus;
  coverLetter: string | null;
  resumeId: string;
  rating: number | null;
  appliedAt: string;
  statusUpdatedAt: string;
  statusHistory: ApplicationStatusHistory[];
  profileSnapshot?: ProfileSnapshot;
  version: number;
}

export interface ApplicationNote {
  id: string;
  applicationId: string;
  author?: UserSummary;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface NoteRequest {
  body: string;
}

export interface AuthUser {
  id: string;
  email: string;
  role: UserRole;
  handle: string;
  firstName: string;
  lastName: string;
}

export interface AuthSession {
  accessToken: string;
  user: AuthUser;
}

export interface JobListParams {
  q?: string;
  location?: string;
  country?: string;
  workMode?: WorkMode;
  employmentType?: EmploymentType;
  experienceLevel?: ExperienceLevel;
  salaryMin?: number;
  salaryMax?: number;
  currency?: string;
  skills?: string[];
  companyId?: string;
  postedWithin?: '24h' | '7d' | '30d';
  page?: number;
  size?: number;
  sort?: 'relevance' | 'postedAt' | 'salary';
}

export interface ApplicationListParams {
  status?: ApplicationStatus;
  jobId?: string;
  page?: number;
  size?: number;
  sort?: 'appliedAt' | 'rating';
  q?: string;
}

/** Salary as sent to the API: either bound may be omitted (backend validates min <= max when both are present). */
export interface SalaryInput {
  min?: number;
  max?: number;
  currency: string;
  period: SalaryPeriod;
}

export interface JobUpsert {
  title: string;
  description: string;
  requirements?: string;
  benefits?: string;
  employmentType: EmploymentType;
  workMode: WorkMode;
  experienceLevel: ExperienceLevel;
  location?: Location;
  salary?: SalaryInput;
  salaryVisible: boolean;
  openings: number;
  expiresAt?: string;
  skills: JobSkill[];
  aiRequestId?: string | null;
}

export interface ApplyRequest {
  resumeId: string;
  coverLetter?: string;
}

export interface StatusUpdateRequest {
  status: ApplicationStatus;
  reason?: string;
}