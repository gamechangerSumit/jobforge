'use client';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { listAuditLogs } from '@/lib/api/admin';

const toIso = (local: string) => (local ? new Date(local).toISOString() : undefined);

export default function AdminAuditPage() {
  const [form, setForm] = useState({ action: '', entityType: '', entityId: '', actorId: '', from: '', to: '' });
  const [applied, setApplied] = useState(form);
  const [page, setPage] = useState(0);
  const logs = useQuery({
    queryKey: ['admin', 'audit', applied, page],
    queryFn: () => listAuditLogs({
      action: applied.action || undefined, entityType: applied.entityType || undefined, entityId: applied.entityId || undefined,
      actorId: applied.actorId || undefined, from: toIso(applied.from), to: toIso(applied.to), page, size: 25,
    }),
  });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });
  const totalPages = logs.data?.page?.totalPages ?? 0;
  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <Card>
        <h1 className="text-xl font-black">Audit log</h1>
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          <label className="text-sm">Action<Input value={form.action} onChange={set('action')} placeholder="JOB_PUBLISHED" /></label>
          <label className="text-sm">Entity type<Input value={form.entityType} onChange={set('entityType')} placeholder="Job" /></label>
          <label className="text-sm">Entity id<Input value={form.entityId} onChange={set('entityId')} /></label>
          <label className="text-sm">Actor id<Input value={form.actorId} onChange={set('actorId')} /></label>
          <label className="text-sm">From<Input type="datetime-local" value={form.from} onChange={set('from')} /></label>
          <label className="text-sm">To<Input type="datetime-local" value={form.to} onChange={set('to')} /></label>
        </div>
        <Button className="mt-3" onClick={() => { setApplied(form); setPage(0); }}>Apply filters</Button>
        {logs.isError && <p role="alert" className="mt-3 text-red-700">Could not load the audit log (ids must be valid UUIDs). <button className="underline" onClick={() => logs.refetch()}>Retry</button></p>}
        {logs.data?.items.length === 0 && <p className="mt-3 text-sm text-slate-600">No entries.</p>}
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead><tr><th className="p-1">Time (UTC)</th><th className="p-1">Action</th><th className="p-1">Entity</th><th className="p-1">Actor</th><th className="p-1">Outcome</th></tr></thead>
            <tbody>
              {logs.data?.items.map((l) => (
                <tr key={l.id} className="border-t align-top">
                  <td className="whitespace-nowrap p-1">{l.occurredAt}</td>
                  <td className="p-1">{l.action}</td>
                  <td className="p-1">{l.entityType}{l.entityId ? ` ${l.entityId}` : ''}</td>
                  <td className="p-1">{l.actorRole ?? l.source}{l.actorUserId ? ` ${l.actorUserId}` : ''}</td>
                  <td className="p-1">{l.outcome}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {totalPages > 1 && (
          <div className="mt-3 flex items-center gap-3 text-sm">
            <Button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
            <span>Page {page + 1} of {totalPages}</span>
            <Button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
          </div>
        )}
      </Card>
    </div>
  );
}
