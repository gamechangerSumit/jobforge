'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { useReasonDialog } from '@/components/ui/ReasonDialog';
import { Card } from '@/components/ui/Card';
import { getCompany, rejectCompany, verifyCompany } from '@/lib/api/companies';

export default function AdminCompanyDetailPage() {
  const { ask, dialog } = useReasonDialog();
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  // GET /companies/{id} returns the full (non-public) view to ADMIN callers.
  const company = useQuery({ queryKey: ['admin', 'company', id], queryFn: () => getCompany(id) });
  const act = useMutation({ mutationFn: (fn: () => Promise<void>) => fn(), onSuccess: () => qc.invalidateQueries({ queryKey: ['admin'] }) });
  const c = company.data;
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10">
      {dialog}
      <Link className="text-sm underline" href="/admin/approvals">← Back to approvals</Link>
      <Card>
        {company.isLoading && <p role="status">Loading…</p>}
        {company.isError && <p role="alert" className="text-red-700">Company not found or could not be loaded.</p>}
        {c && (
          <>
            <h1 className="text-xl font-black">{c.name}</h1>
            <dl className="mt-3 grid grid-cols-[10rem_1fr] gap-y-1 text-sm">
              <dt className="text-slate-600">Status</dt><dd><strong>{c.verificationStatus}</strong></dd>
              <dt className="text-slate-600">Industry</dt><dd>{c.industry ?? '—'}</dd>
              <dt className="text-slate-600">Size</dt><dd>{c.sizeBand ?? '—'}</dd>
              <dt className="text-slate-600">Website</dt><dd>{c.websiteUrl ?? '—'}</dd>
              <dt className="text-slate-600">Location</dt><dd>{[c.hqCity, c.hqState, c.hqCountry].filter(Boolean).join(', ') || '—'}</dd>
              <dt className="text-slate-600">Description</dt><dd className="whitespace-pre-wrap">{c.description ?? '—'}</dd>
            </dl>
            <div className="mt-4 flex gap-2">
              {c.verificationStatus !== 'VERIFIED' && <Button onClick={() => act.mutate(() => verifyCompany(c.id))}>Verify</Button>}
              {c.verificationStatus !== 'REJECTED' && (
                <Button variant="danger" onClick={async () => {
                  const reason = await ask('Rejection reason');
                  if (reason) act.mutate(() => rejectCompany(c.id, reason));
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
