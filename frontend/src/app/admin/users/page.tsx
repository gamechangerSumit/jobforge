'use client';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { useReasonDialog } from '@/components/ui/ReasonDialog';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { createAdminUser, forceLogoutUser, listAdminUsers, setUserStatus } from '@/lib/api/admin';

export default function AdminUsersPage() {
  const { ask, dialog } = useReasonDialog();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [role, setRole] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [invite, setInvite] = useState({ email: '', firstName: '', lastName: '' });
  const users = useQuery({
    queryKey: ['admin', 'users', q, role, status, page],
    queryFn: () => listAdminUsers({ q: q || undefined, role: role || undefined, status: status || undefined, page, size: 20 }),
  });
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin', 'users'] });
  const act = useMutation({ mutationFn: (fn: () => Promise<unknown>) => fn(), onSuccess: refresh });
  const create = useMutation({
    mutationFn: () => createAdminUser(invite),
    onSuccess: () => { setInvite({ email: '', firstName: '', lastName: '' }); void refresh(); },
  });
  const totalPages = users.data?.page?.totalPages ?? 0;

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10">
      {dialog}
      <Card>
        <h1 className="text-xl font-black">Users</h1>
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          <label className="text-sm">Search<Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} placeholder="Name, handle or email" /></label>
          <label className="text-sm">Role
            <Select value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
              <option value="">All</option><option value="JOB_SEEKER">Job seeker</option><option value="RECRUITER">Recruiter</option><option value="ADMIN">Admin</option>
            </Select>
          </label>
          <label className="text-sm">Status
            <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
              <option value="">All</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="PENDING_VERIFICATION">Pending verification</option>
            </Select>
          </label>
        </div>
        {users.isError && <p role="alert" className="mt-3 text-red-700">Could not load users. <button className="underline" onClick={() => users.refetch()}>Retry</button></p>}
        {users.data?.items.length === 0 && <p className="mt-3 text-sm text-slate-600">No users match.</p>}
        <ul className="mt-3 divide-y">
          {users.data?.items.map((u) => (
            <li key={u.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span>{u.firstName} {u.lastName} · @{u.handle} · {u.email} · {u.role} · <strong>{u.status}</strong></span>
              <span className="flex gap-2">
                {u.status === 'ACTIVE' && u.role !== 'ADMIN' && (
                  <Button variant="danger" onClick={async () => { const reason = await ask('Suspension reason'); if (reason) act.mutate(() => setUserStatus(u.id, 'SUSPENDED', reason)); }}>Suspend</Button>
                )}
                {u.status === 'SUSPENDED' && (
                  <Button onClick={async () => { const reason = await ask('Reason for reactivating'); if (reason) act.mutate(() => setUserStatus(u.id, 'ACTIVE', reason)); }}>Reactivate</Button>
                )}
                <Button onClick={() => { if (window.confirm('Sign this user out of all sessions?')) act.mutate(() => forceLogoutUser(u.id)); }}>Force logout</Button>
              </span>
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
      <Card>
        <h2 className="text-lg font-black">Add an administrator</h2>
        <p className="text-sm text-slate-600">The new admin receives an email with a link to set a password.</p>
        <div className="mt-3 grid gap-2 sm:grid-cols-3">
          <label className="text-sm">Email<Input type="email" value={invite.email} onChange={(e) => setInvite({ ...invite, email: e.target.value })} /></label>
          <label className="text-sm">First name<Input value={invite.firstName} onChange={(e) => setInvite({ ...invite, firstName: e.target.value })} /></label>
          <label className="text-sm">Last name<Input value={invite.lastName} onChange={(e) => setInvite({ ...invite, lastName: e.target.value })} /></label>
        </div>
        <Button className="mt-3" disabled={create.isPending || !invite.email || !invite.firstName || !invite.lastName} onClick={() => create.mutate()}>Send invite</Button>
        {create.isError && <p role="alert" className="mt-2 text-sm text-red-700">Could not create the administrator (email may already be registered).</p>}
        {create.isSuccess && <p role="status" className="mt-2 text-sm text-green-700">Invite sent.</p>}
      </Card>
    </div>
  );
}
