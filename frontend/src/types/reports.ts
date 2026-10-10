/** Shapes of POST /reports and /admin/reports (API_CONTRACT §12.9 / §12.11; response DTOs in API_CONTRACT §12.9 / §12.11). */
export type ReportTargetType = 'POST' | 'COMMENT' | 'USER' | 'JOB' | 'COMPANY';
export type ReportReason = 'SPAM' | 'HARASSMENT' | 'SCAM' | 'INAPPROPRIATE' | 'MISINFORMATION' | 'DISCRIMINATION' | 'OTHER';
export type ReportStatus = 'OPEN' | 'REVIEWING' | 'RESOLVED' | 'DISMISSED';
export type ModerationAction = 'DISMISS' | 'HIDE_CONTENT' | 'REMOVE_CONTENT' | 'WARN_USER' | 'SUSPEND_USER';

export interface CreateReportRequest { targetType: ReportTargetType; targetId: string; reason: ReportReason; details?: string }

/** Reporter acknowledgement: only what the reporter submitted (details are never echoed). */
export interface ReportAck { id: string; targetType: ReportTargetType; targetId: string; reason: ReportReason; status: ReportStatus; createdAt: string }

export interface AdminReportItem {
  id: string; targetType: ReportTargetType; targetId: string; reason: ReportReason; status: ReportStatus;
  reporterId: string; createdAt: string; updatedAt: string;
}

export interface ReportTarget { type: ReportTargetType; id: string; label?: string; status?: string; available: boolean }
export interface ModerationActionRecord { id: string; action: ModerationAction; moderatorId: string; reason: string; createdAt: string }

export interface AdminReportDetail {
  id: string; targetType: ReportTargetType; targetId: string; reason: ReportReason; details?: string; status: ReportStatus;
  reporter: { id: string; displayName: string }; target: ReportTarget; actions: ModerationActionRecord[];
  createdAt: string; updatedAt: string;
}

export type ReportSort = 'createdAt,asc' | 'createdAt,desc';
export interface AdminReportFilter {
  status?: ReportStatus | ''; targetType?: ReportTargetType | ''; reason?: ReportReason | ''; sort?: ReportSort; page?: number; size?: number;
}
export interface ResolveReportRequest { action: ModerationAction; reason: string }
