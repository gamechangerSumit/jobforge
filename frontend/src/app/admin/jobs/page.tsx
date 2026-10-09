'use client';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { useReasonDialog } from '@/components/ui/ReasonDialog';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { listAdminJobs, removeJob, restoreJob } from '@/lib/api/admin';

export default function AdminJobsPage() {
  const { ask, dialog } = useReasonDialog();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const jobs = useQuery({ queryKey: ['admin', 'jobs', q, page], queryFn: () => listAdminJobs({ q: q || undefined, page, size: 20 }) });
  const act = useMutation({ mutationFn: (fn: () => Promise<unknown>) => fn(), onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'jobs'] }) });
  const totalPages = jobs.data?.page?.totalPages ?? 0;
  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      {dialog}
      <Card>
        <h1 className="text-xl font-black">Job moderation</h1>
        <label className="mt-3 block text-sm">Search<Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} placeholder="Title or company" /></label>
        {jobs.isError && <p role="alert" className="mt-3 text-red-700">Could not load jobs. <button className="underline" onClick={() => jobs.refetch()}>Retry</button></p>}
        {jobs.data?.items.length === 0 && <p className="mt-3 text-sm text-slate-600">No jobs match.</p>}
        <ul className="mt-3 divide-y">
          {jobs.data?.items.map((j) => (
            <li key={j.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span>{j.title} · {j.company?.name ?? 'unknown company'} · <strong>{j.status}</strong></span>
              {j.status === 'REMOVED' ? (
                <Button onClick={() => act.mutate(() => restoreJob(j.id))}>Restore</Button>
              ) : (
                <Button variant="danger" onClick={async () => {
                  const reason = await ask('Removal reason', 10);
                  if (reason) act.mutate(() => removeJob(j.id, reason));
                }}>Remove</Button>
              )}
            </li>
          ))}
        </ul>
        {totalPages > 1 && (
          <div className="mt-3 flex items-center gap-3 text-sm">
            <Button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
            <span>Page {page + 1} of {totalPages}</span>
            <Button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Next</Button>
          </div>
        )}
        {act.isError && <p role="alert" className="mt-3 text-sm text-red-700">The action failed. Please retry.</p>}
      </Card>
    </div>
  );
}
