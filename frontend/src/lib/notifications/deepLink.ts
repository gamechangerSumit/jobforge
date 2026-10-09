import type { AppNotification } from '@/lib/api/notifications';

/** Only same-origin relative paths are ever followed (no open redirects via notification data). */
export function isSafeInternalPath(value: unknown): value is string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.includes('\\') && !/[\u0000-\u001f]/.test(value);
}

/**
 * Resolves where a notification should lead. Prefers an explicit data.deepLink (API_CONTRACT 12.8) when it is a
 * safe internal path, otherwise derives it from the ids the backend consumers put in `data`.
 */
export function notificationHref(n: Pick<AppNotification, 'type' | 'data'>, role?: string): string | null {
  const data = n.data ?? {};
  if (isSafeInternalPath(data.deepLink)) return data.deepLink;
  const id = (key: string) => (typeof data[key] === 'string' && /^[0-9a-fA-F-]{36}$/.test(data[key] as string) ? (data[key] as string) : null);
  const applicationId = id('applicationId');
  if (applicationId) return role === 'RECRUITER' ? `/recruiter/applications/${applicationId}` : `/applications/${applicationId}`;
  const jobId = id('jobId');
  if (jobId) return `/jobs/${jobId}`;
  if (n.type.startsWith('RECRUITER_') || n.type.startsWith('COMPANY_')) return role === 'RECRUITER' ? '/recruiter/company' : null;
  return null;
}
