'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { deleteAccount } from '@/lib/api/account';
import { setAccessToken } from '@/lib/api/client';
import { errorMessage } from '@/lib/api/errors';
import { useSession } from '@/lib/auth/session';

/** Password-confirmed anonymization (DELETE /users/me). ADMIN accounts are refused by the server; the UI hides the control. */
export function DeleteAccount() {
  const router = useRouter();
  const qc = useQueryClient();
  const { user, refresh } = useSession();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const remove = useMutation({
    mutationFn: () => deleteAccount(password),
    onSuccess: async () => {
      setAccessToken(null);
      qc.clear();
      await refresh();
      router.replace('/');
    },
    onError: (e) => setError(errorMessage(e, 'Could not delete the account.')),
  });
  if (user?.role === 'ADMIN') {
    return <p className="text-sm text-slate-600">Administrator accounts cannot be self-deleted. Ask another administrator.</p>;
  }
  if (!open) return <Button type="button" variant="danger" onClick={() => setOpen(true)}>Delete my account…</Button>;
  const ready = password.length > 0 && confirm === 'DELETE';
  return (
    <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); setError(null); if (ready) remove.mutate(); }}>
      <p className="text-sm text-slate-700">This permanently anonymizes your account and signs you out everywhere. This cannot be undone.</p>
      <div>
        <label htmlFor="del-password" className="mb-1 block text-sm font-semibold">Confirm your password</label>
        <Input id="del-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </div>
      <div>
        <label htmlFor="del-confirm" className="mb-1 block text-sm font-semibold">Type DELETE to confirm</label>
        <Input id="del-confirm" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="off" />
      </div>
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      <div className="flex gap-2">
        <Button variant="danger" disabled={!ready || remove.isPending}>{remove.isPending ? 'Deleting…' : 'Permanently delete account'}</Button>
        <Button type="button" onClick={() => { setOpen(false); setPassword(''); setConfirm(''); setError(null); }}>Cancel</Button>
      </div>
    </form>
  );
}
