import type { InterviewStatus } from '@/types/interviews';

const styles: Record<string, string> = {
  SCHEDULED: 'bg-amber-100 text-amber-900',
  CONFIRMED: 'bg-emerald-100 text-emerald-900',
  DECLINED: 'bg-red-100 text-red-900',
  COMPLETED: 'bg-slate-200 text-slate-800',
  CANCELLED: 'bg-slate-100 text-slate-600',
  NO_SHOW: 'bg-red-50 text-red-800',
};

/** Unknown future statuses fall back to a neutral badge (clients must tolerate new enum values). */
export function InterviewStatusBadge({ status }: { status: InterviewStatus | string }) {
  return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${styles[status] ?? 'bg-slate-100 text-slate-700'}`}>{status.replaceAll('_', ' ')}</span>;
}
