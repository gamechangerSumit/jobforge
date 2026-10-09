import type { ApplicationStatus, CompanySummary, Location } from './api';

/** API_CONTRACT §12.7 / DATABASE_SCHEMA §3.5. Clients must tolerate new enum values. */
export type InterviewType = 'PHONE' | 'VIDEO' | 'ONSITE';
export type InterviewStatus = 'SCHEDULED' | 'CONFIRMED' | 'DECLINED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type InterviewResponse = 'PENDING' | 'CONFIRMED' | 'DECLINED';
export type SeekerChoice = 'CONFIRM' | 'DECLINE';
export type InterviewOutcome = 'COMPLETED' | 'NO_SHOW';

export interface InterviewPerson { id: string; handle: string; firstName: string; lastName: string }

export interface InterviewJob { id: string; title: string; company: CompanySummary; location?: Location | null }

/** Null fields are omitted by the server. `seeker` and `notes` are recruiter-only. */
export interface Interview {
  id: string;
  applicationId: string;
  applicationStatus?: ApplicationStatus;
  job?: InterviewJob;
  seeker?: InterviewPerson;
  scheduledBy?: InterviewPerson;
  type: InterviewType;
  scheduledAt: string;
  durationMinutes: number;
  timezone: string;
  locationOrLink?: string | null;
  status: InterviewStatus;
  seekerResponse: InterviewResponse;
  seekerResponseNote?: string | null;
  cancelledReason?: string | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleInterviewRequest {
  type: InterviewType;
  scheduledAt: string;
  durationMinutes: number;
  timezone: string;
  locationOrLink: string;
  notes?: string;
}

/** PATCH = merge patch: absent = unchanged; blank notes clear them. */
export type UpdateInterviewRequest = Partial<ScheduleInterviewRequest>;
export interface CancelInterviewRequest { reason: string }
export interface RespondInterviewRequest { response: SeekerChoice; note?: string }
export interface CompleteInterviewRequest { outcome: InterviewOutcome }

export interface InterviewListParams { applicationId?: string; from?: string; to?: string; status?: InterviewStatus | ''; page?: number; size?: number }
