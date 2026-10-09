'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { useReasonDialog } from '@/components/ui/ReasonDialog';
import { Card } from '@/components/ui/Card';
import { useSession } from '@/lib/auth/session';
import { approveRecruiter, listAdminCompanies, listAdminRecruiters, rejectCompany, rejectRecruiter, verifyCompany } from '@/lib/api/companies';

export default function AdminApprovalsPage() {
  const { ask, dialog } = useReasonDialog();
  const { user, loading } = useSession();
  const qc = useQueryClient();
  const enabled = user?.role === 'ADMIN';
  const recruiters = useQuery({ queryKey: ['admin', 'recruiters'], queryFn: () => listAdminRecruiters('PENDING'), enabled });
  const companies = useQuery({ queryKey: ['admin', 'companies'], queryFn: () => listAdminCompanies('PENDING'), enabled });
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin'] });
  const act = useMutation({ mutationFn: (fn: () => Promise<void>) => fn(), onSuccess: refresh });

  if (loading) return <div className="mx-auto max-w-4xl px-4 py-10">Loading…</div>;
  if (!enabled) return <div className="mx-auto max-w-4xl px-4 py-10">This page is for administrators.</div>;

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10">
      {dialog}
      <Card>
        <h1 className="text-xl font-black">Recruiters awaiting approval</h1>
        {recruiters.isError && <p className="text-red-700">Could not load recruiters. <button className="underline" onClick={() => recruiters.refetch()}>Retry</button></p>}
        {recruiters.data?.items.length === 0 && <p className="mt-2 text-sm text-slate-600">Nothing pending.</p>}
        <ul className="mt-3 divide-y">
          {recruiters.data?.items.map((r) => (
            <li key={r.userId} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span><Link className="underline" href={`/admin/recruiters/${r.userId}`}>{r.firstName} {r.lastName}</Link> · {r.email} · {r.companyName ?? 'no company yet'}</span>
              <span className="flex gap-2">
                <Button onClick={() => act.mutate(() => approveRecruiter(r.userId))}>Approve</Button>
                <Button variant="danger" onClick={async () => { const reason = await ask('Rejection reason'); if (reason) act.mutate(() => rejectRecruiter(r.userId, reason)); }}>Reject</Button>
              </span>
            </li>
          ))}
        </ul>
      </Card>
      <Card>
        <h1 className="text-xl font-black">Companies awaiting verification</h1>
        {companies.isError && <p className="text-red-700">Could not load companies. <button className="underline" onClick={() => companies.refetch()}>Retry</button></p>}
        {companies.data?.items.length === 0 && <p className="mt-2 text-sm text-slate-600">Nothing pending.</p>}
        <ul className="mt-3 divide-y">
          {companies.data?.items.map((c) => (
            <li key={c.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span><Link className="underline" href={`/admin/companies/${c.id}`}>{c.name}</Link> · owner {c.ownerEmail ?? 'n/a'} · {c.memberCount} member(s)</span>
              <span className="flex gap-2">
                <Button onClick={() => act.mutate(() => verifyCompany(c.id))}>Verify</Button>
                <Button variant="danger" onClick={async () => { const reason = await ask('Rejection reason'); if (reason) act.mutate(() => rejectCompany(c.id, reason)); }}>Reject</Button>
              </span>
            </li>
          ))}
        </ul>
      </Card>
      {act.isError && <p role="alert" className="text-sm text-red-700">The action failed. Please retry.</p>}
    </div>
  );
}
