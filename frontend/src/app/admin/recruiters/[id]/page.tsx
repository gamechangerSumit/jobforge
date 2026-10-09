'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { useReasonDialog } from '@/components/ui/ReasonDialog';
import { Card } from '@/components/ui/Card';
import { approveRecruiter, rejectRecruiter } from '@/lib/api/companies';
import { getAdminRecruiter } from '@/lib/api/admin';

export default function AdminRecruiterDetailPage() {
  const { ask, dialog } = useReasonDialog();
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const recruiter = useQuery({ queryKey: ['admin', 'recruiter', id], queryFn: () => getAdminRecruiter(id) });
  const act = useMutation({ mutationFn: (fn: () => Promise<void>) => fn(), onSuccess: () => qc.invalidateQueries({ queryKey: ['admin'] }) });
  const r = recruiter.data;
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10">
      {dialog}
      <Link className="text-sm underline" href="/admin/approvals">← Back to approvals</Link>
      <Card>
        {recruiter.isLoading && <p role="status">Loading…</p>}
        {recruiter.isError && <p role="alert" className="text-red-700">Recruiter not found or could not be loaded.</p>}
        {r && (
          <>
            <h1 className="text-xl font-black">{r.firstName} {r.lastName}</h1>
            <dl className="mt-3 grid grid-cols-[10rem_1fr] gap-y-1 text-sm">
              <dt className="text-slate-600">Email</dt><dd>{r.email}</dd>
              <dt className="text-slate-600">Job title</dt><dd>{r.jobTitle ?? '—'}</dd>
              <dt className="text-slate-600">Phone</dt><dd>{r.phone ?? '—'}</dd>
              <dt className="text-slate-600">Status</dt><dd><strong>{r.approvalStatus}</strong>{r.rejectionReason ? ` — ${r.rejectionReason}` : ''}</dd>
              <dt className="text-slate-600">Company</dt>
              <dd>{r.companyId ? <Link className="underline" href={`/admin/companies/${r.companyId}`}>{r.companyName}</Link> : 'none yet'}</dd>
              <dt className="text-slate-600">Registered</dt><dd>{r.createdAt}</dd>
            </dl>
            <div className="mt-4 flex gap-2">
              {r.approvalStatus !== 'APPROVED' && <Button onClick={() => act.mutate(() => approveRecruiter(r.userId))}>Approve</Button>}
              {r.approvalStatus !== 'REJECTED' && (
                <Button variant="danger" onClick={async () => {
                  const reason = await ask('Rejection reason');
                  if (reason) act.mutate(() => rejectRecruiter(r.userId, reason));
                }}>Reject</Button>
              )}
            </div>
            {act.isError && <p role="alert" className="mt-2 text-sm text-red-700">The action failed. Please retry.</p>}
          </>
        )}
      </Card>
    </div>
  );
}
