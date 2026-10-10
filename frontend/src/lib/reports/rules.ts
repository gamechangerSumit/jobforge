import type { ModerationAction, ReportReason, ReportStatus, ReportTargetType } from '@/types/reports';

export const REASON_LABEL: Record<ReportReason, string> = {
  SPAM: 'Spam', HARASSMENT: 'Harassment', SCAM: 'Scam or fraud', INAPPROPRIATE: 'Inappropriate content',
  MISINFORMATION: 'Misinformation', DISCRIMINATION: 'Discrimination', OTHER: 'Something else',
};
export const REASONS = Object.keys(REASON_LABEL) as ReportReason[];

export const TARGET_LABEL: Record<ReportTargetType, string> = { POST: 'Post', COMMENT: 'Comment', USER: 'User', JOB: 'Job', COMPANY: 'Company' };
export const TARGET_TYPES = Object.keys(TARGET_LABEL) as ReportTargetType[];

/** Targets a user can report today: POST/COMMENT answer 422 until the community module registers them. */
export const REPORTABLE_TARGETS: ReportTargetType[] = ['JOB', 'COMPANY', 'USER'];

export const STATUS_LABEL: Record<ReportStatus, string> = { OPEN: 'Open', REVIEWING: 'Reviewing', RESOLVED: 'Resolved', DISMISSED: 'Dismissed' };
export const STATUSES = Object.keys(STATUS_LABEL) as ReportStatus[];

export const ACTION_LABEL: Record<ModerationAction, string> = {
  DISMISS: 'Dismiss report', HIDE_CONTENT: 'Hide content', REMOVE_CONTENT: 'Remove content', WARN_USER: 'Warn user', SUSPEND_USER: 'Suspend user',
};
export const ACTION_HELP: Record<ModerationAction, string> = {
  DISMISS: 'No violation found. The report is closed as dismissed and nothing happens to the item.',
  HIDE_CONTENT: 'Hide the content.',
  REMOVE_CONTENT: 'Removes the job from the platform (the job moderation state machine applies).',
  WARN_USER: 'Sends a warning notification to the user responsible for the item.',
  SUSPEND_USER: 'Suspends the user account. Administrators and your own account cannot be suspended.',
};

/** Actions that change data beyond closing the report and therefore need an explicit acknowledgement. */
export const DESTRUCTIVE: ModerationAction[] = ['REMOVE_CONTENT', 'SUSPEND_USER', 'HIDE_CONTENT'];

export const isActive = (status: ReportStatus) => status === 'OPEN' || status === 'REVIEWING';

/**
 * Decisions the backend accepts (docs/status/backend.md, Session 3A decision matrix). The server stays the authority and
 * answers 422 for anything else; this only avoids offering choices that cannot succeed.
 * - closed reports: none (409 INVALID_STATE_TRANSITION)
 * - target no longer exists: only DISMISS
 * - JOB: DISMISS, WARN_USER, REMOVE_CONTENT · COMPANY: DISMISS, WARN_USER · USER: DISMISS, WARN_USER, SUSPEND_USER
 * - HIDE_CONTENT is unsupported for every target today; POST/COMMENT can only be dismissed.
 */
export function allowedActions(report: { status: ReportStatus; targetType: ReportTargetType; targetAvailable: boolean }): ModerationAction[] {
  if (!isActive(report.status)) return [];
  if (!report.targetAvailable) return ['DISMISS'];
  switch (report.targetType) {
    case 'JOB': return ['DISMISS', 'WARN_USER', 'REMOVE_CONTENT'];
    case 'COMPANY': return ['DISMISS', 'WARN_USER'];
    case 'USER': return ['DISMISS', 'WARN_USER', 'SUSPEND_USER'];
    default: return ['DISMISS'];
  }
}

/** Link to the resource for admin context, only for targets that still exist and have a page. */
export function targetHref(type: ReportTargetType, id: string, available: boolean): string | null {
  if (!available) return null;
  if (type === 'JOB') return `/jobs/${id}`;
  if (type === 'COMPANY') return `/admin/companies/${id}`;
  if (type === 'USER') return `/users/${id}`;
  return null;
}
