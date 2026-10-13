import { STATUS_LABEL } from '@/lib/reports/rules';
import type { ReportStatus } from '@/types/reports';

const styles: Record<string, string> = {
  OPEN: 'bg-amber-100 text-amber-900', REVIEWING: 'bg-blue-100 text-blue-900',
  RESOLVED: 'bg-emerald-100 text-emerald-900', DISMISSED: 'bg-slate-200 text-slate-700',
};

/** Unknown future statuses fall back to a neutral badge. */
export function ReportStatusBadge({ status }: { status: ReportStatus | string }) {
  return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${styles[status] ?? 'bg-slate-100 text-slate-700'}`}>{STATUS_LABEL[status as ReportStatus] ?? status}</span>;
}
