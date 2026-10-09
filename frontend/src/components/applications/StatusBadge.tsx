import type { ApplicationStatus } from '@/types/api';
export function StatusBadge({ status }: { status: ApplicationStatus }) { return <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold">{status.replaceAll('_', ' ')}</span>; }
