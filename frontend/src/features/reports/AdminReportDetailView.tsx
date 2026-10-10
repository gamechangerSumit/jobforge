'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { Card } from '@/components/ui/Card';
import { ACTION_LABEL, REASON_LABEL, TARGET_LABEL, targetHref } from '@/lib/reports/rules';
import { isForbidden, isNotFound, useAdminReport } from './hooks';
import { ReportStatusBadge } from './ReportStatusBadge';
import { ResolvePanel } from './ResolvePanel';

const UUID = /^[0-9a-fA-F-]{36}$/;

/**
 * Report detail with safe context: reporter display name only (no e-mail), the target's admin label and status
 * (never body content), the reporter's private details as plain text, and the moderation history.
 */
export function AdminReportDetailView() {
  const { id } = useParams<{ id: string }>();
  const valid = UUID.test(id ?? '');
  const q = useAdminReport(valid ? id : '');
  const back = <Link href="/admin/reports" className="text-sm font-semibold underline">← Back to reports</Link>;

  if (!valid) return <div className="mx-auto max-w-3xl px-4 py-10" role="alert">Report not found. {back}</div>;
  if (q.isLoading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading report…</div>;
  if (q.isError && isForbidden(q.error)) return <div className="mx-auto max-w-3xl px-4 py-10" role="alert">You do not have permission to view this report.</div>;
  if (q.isError && isNotFound(q.error)) return <div className="mx-auto max-w-3xl px-4 py-10" role="alert">Report not found. {back}</div>;
  if (q.isError || !q.data) return <div className="mx-auto max-w-3xl px-4 py-10" role="alert">Could not load this report. <button className="underline" onClick={() => void q.refetch()}>Retry</button> {back}</div>;

  const r = q.data;
  const href = targetHref(r.target.type, r.target.id, r.target.available);
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10">
      {back}
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-xl font-black">{TARGET_LABEL[r.targetType] ?? r.targetType} report</h1>
            <p className="text-sm text-slate-600">Reason: {REASON_LABEL[r.reason] ?? r.reason} · filed {new Date(r.createdAt).toLocaleString()}</p>
          </div>
          <ReportStatusBadge status={r.status} />
        </div>
        <dl className="mt-4 grid gap-3 text-sm md:grid-cols-2">
          <div><dt className="text-slate-500">Reported by</dt><dd className="font-semibold">{r.reporter.displayName}</dd></div>
          <div><dt className="text-slate-500">Reported item</dt>
            <dd className="font-semibold">
              {r.target.available ? (r.target.label ?? TARGET_LABEL[r.targetType]) : 'No longer available'}
              {r.target.available && r.target.status ? <span className="ml-2 text-xs font-normal text-slate-500">({r.target.status})</span> : null}
            </dd>
            {href && <Link href={href} className="text-xs underline">Open</Link>}
          </div>
          {r.details && <div className="md:col-span-2"><dt className="text-slate-500">Reporter&apos;s details (private)</dt><dd className="whitespace-pre-wrap rounded-lg bg-slate-50 p-3">{r.details}</dd></div>}
        </dl>
      </Card>

      <Card>
        <h2 className="text-lg font-bold">Decision</h2>
        <div className="mt-3"><ResolvePanel report={r} /></div>
      </Card>

      <Card>
        <h2 className="text-lg font-bold">Moderation history</h2>
        {r.actions.length === 0 && <p className="mt-2 text-sm text-slate-600">No decision recorded yet.</p>}
        <ul className="mt-2 divide-y text-sm">
          {r.actions.map((a) => (
            <li key={a.id} className="py-2">
              <p className="font-semibold">{ACTION_LABEL[a.action] ?? a.action} <span className="font-normal text-slate-500">· {new Date(a.createdAt).toLocaleString()}</span></p>
              <p className="whitespace-pre-wrap text-slate-700">{a.reason}</p>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
