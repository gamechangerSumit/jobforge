'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AvatarUpload } from '@/features/account/AvatarUpload';
import { DeleteAccount } from '@/features/account/DeleteAccount';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { getAccount, updateAccount, type Account } from '@/lib/api/account';
import { errorMessage } from '@/lib/api/errors';
import { useSession } from '@/lib/auth/session';

function Details({ account }: { account: Account }) {
  const qc = useQueryClient();
  const { refresh } = useSession();
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const save = useMutation({
    mutationFn: updateAccount,
    onSuccess: async (a) => { qc.setQueryData(['account'], a); await refresh(); setMsg({ ok: true, text: 'Saved.' }); },
    onError: (e) => setMsg({ ok: false, text: errorMessage(e, 'Could not save.') }),
  });
  return (
    <form className="grid gap-3 md:grid-cols-2" onSubmit={(e) => {
      e.preventDefault(); const f = new FormData(e.currentTarget); setMsg(null);
      save.mutate({ firstName: String(f.get('firstName') ?? '').trim(), lastName: String(f.get('lastName') ?? '').trim(), handle: String(f.get('handle') ?? '').trim().toLowerCase() });
    }}>
      <div><label htmlFor="a-first" className="mb-1 block text-sm font-semibold">First name</label><Input id="a-first" name="firstName" defaultValue={account.firstName} required maxLength={60} /></div>
      <div><label htmlFor="a-last" className="mb-1 block text-sm font-semibold">Last name</label><Input id="a-last" name="lastName" defaultValue={account.lastName} required maxLength={60} /></div>
      <div><label htmlFor="a-handle" className="mb-1 block text-sm font-semibold">Handle</label><Input id="a-handle" name="handle" defaultValue={account.handle} required minLength={3} maxLength={30} pattern="[a-z0-9_]+" title="Lowercase letters, digits and underscores" /></div>
      <div><label htmlFor="a-email" className="mb-1 block text-sm font-semibold">Email</label><Input id="a-email" value={account.email} readOnly disabled /></div>
      <div className="flex items-center gap-3 md:col-span-2">
        <Button disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save details'}</Button>
        {msg && <p role={msg.ok ? 'status' : 'alert'} className={msg.ok ? 'text-sm text-emerald-700' : 'text-sm text-red-600'}>{msg.text}</p>}
      </div>
    </form>
  );
}

function useAccountQuery() { return useQuery({ queryKey: ['account'], queryFn: getAccount }); }

export default function AccountPage() {
  const { user, loading } = useSession();
  const qc = useQueryClient();
  const account = useAccountQuery();
  if (loading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-3xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to continue.</div>;
  if (account.isLoading) return <div className="mx-auto max-w-3xl px-4 py-10">Loading account…</div>;
  if (account.isError || !account.data) return <div className="mx-auto max-w-3xl px-4 py-10" role="alert">Could not load your account. <button className="underline" onClick={() => account.refetch()}>Retry</button></div>;
  const a = account.data;
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10">
      <h1 className="text-3xl font-black">My account</h1>
      <Card><AvatarUpload userId={a.id} name={`${a.firstName} ${a.lastName}`} initialHasAvatar={Boolean(a.avatarUrl)} onChanged={() => void qc.invalidateQueries({ queryKey: ['account'] })} /></Card>
      <Card><h2 className="mb-3 text-lg font-bold">Details</h2><Details account={a} /></Card>
      <Card><h2 className="mb-3 text-lg font-bold text-red-800">Delete account</h2><DeleteAccount /></Card>
    </div>
  );
}
