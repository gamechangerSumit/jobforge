'use client';
import Link from 'next/link';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import { REASONS, REASON_LABEL, STATUSES, STATUS_LABEL, TARGET_LABEL, TARGET_TYPES } from '@/lib/reports/rules';
import { isForbidden, useAdminReports } from './hooks';
import { ReportStatusBadge } from './ReportStatusBadge';
import type { ReportReason, ReportSort, ReportStatus, ReportTargetType } from '@/types/reports';

const PAGE_SIZE = 20;

/** Admin report queue: filters by status/target/reason, oldest-first by default, paginated. */
export function AdminReportQueue() {
  const [status, setStatus] = useState<ReportStatus | ''>('OPEN');
  const [targetType, setTargetType] = useState<ReportTargetType | ''>('');
  const [reason, setReason] = useState<ReportReason | ''>('');
  const [sort, setSort] = useState<ReportSort>('createdAt,asc');
  const [page, setPage] = useState(0);
  const q = useAdminReports({ status, targetType, reason, sort, page, size: PAGE_SIZE });
  const totalPages = q.data?.page?.totalPages ?? 0;
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0); };

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <Card>
        <h1 className="text-xl font-black">Reports</h1>
        <p className="mt-1 text-sm text-slate-600">User reports waiting for a moderation decision.</p>
        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <label className="text-sm font-semibold">Status
            <Select value={status} onChange={(e) => reset(setStatus)(e.target.value as ReportStatus | '')}>
              <option value="">All</option>{STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
            </Select></label>
          <label className="text-sm font-semibold">Type
            <Select value={targetType} onChange={(e) => reset(setTargetType)(e.target.value as ReportTargetType | '')}>
              <option value="">All</option>{TARGET_TYPES.map((t) => <option key={t} value={t}>{TARGET_LABEL[t]}</option>)}
            </Select></label>
          <label className="text-sm font-semibold">Reason
            <Select value={reason} onChange={(e) => reset(setReason)(e.target.value as ReportReason | '')}>
              <option value="">All</option>{REASONS.map((r) => <option key={r} value={r}>{REASON_LABEL[r]}</option>)}
            </Select></label>
          <label className="text-sm font-semibold">Order
            <Select value={sort} onChange={(e) => reset(setSort)(e.target.value as ReportSort)}>
              <option value="createdAt,asc">Oldest first</option><option value="createdAt,desc">Newest first</option>
            </Select></label>
        </div>

        {q.isLoading && <p role="status" className="mt-4 text-sm">Loading reports…</p>}
        {q.isError && isForbidden(q.error) && <p role="alert" className="mt-4 text-red-700">You do not have permission to view reports.</p>}
        {q.isError && !isForbidden(q.error) && <p role="alert" className="mt-4 text-red-700">Could not load reports. <button className="underline" onClick={() => void q.refetch()}>Retry</button></p>}
        {q.data && q.data.items.length === 0 && <p className="mt-4 text-sm text-slate-600">No reports match these filters.</p>}
        <ul className="mt-4 divide-y">
          {q.data?.items.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
              <div>
                <Link href={`/admin/reports/${r.id}`} className="font-semibold hover:underline">{TARGET_LABEL[r.targetType] ?? r.targetType} report · {REASON_LABEL[r.reason] ?? r.reason}</Link>
                <p className="text-xs text-slate-500">Filed {new Date(r.createdAt).toLocaleString()}</p>
              </div>
              <ReportStatusBadge status={r.status} />
            </li>
          ))}
        </ul>
        {totalPages > 1 && (
          <div className="mt-4 flex items-center gap-3 text-sm">
            <Button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
            <span>Page {page + 1} of {totalPages}</span>
            <Button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
          </div>
        )}
      </Card>
    </div>
  );
}
