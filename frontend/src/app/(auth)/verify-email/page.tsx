'use client';
import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Card } from '@/components/ui/Card';
import { ApiClientError, api } from '@/lib/api/client';

function Verify() {
  const token = useSearchParams().get('token');
  const [state, setState] = useState<'working' | 'ok' | 'error'>(token ? 'working' : 'error');
  const [message, setMessage] = useState(token ? '' : 'The verification link is missing its token.');
  useEffect(() => {
    if (!token) return;
    let active = true;
    api.post<void>('/auth/verify-email', { token })
      .then(() => { if (active) setState('ok'); })
      .catch((e) => { if (active) { setState('error'); setMessage(e instanceof ApiClientError ? e.error.message : 'Verification failed.'); } });
    return () => { active = false; };
  }, [token]);
  return (
    <Card>
      <h1 className="text-2xl font-black">Email verification</h1>
      {state === 'working' && <p className="mt-3">Verifying…</p>}
      {state === 'ok' && <p className="mt-3" role="status">Your email is verified. <Link className="underline" href="/login">Sign in</Link></p>}
      {state === 'error' && <p className="mt-3 text-red-700" role="alert">{message}</p>}
    </Card>
  );
}

export default function VerifyEmailPage() {
  return <div className="mx-auto max-w-md px-4 py-10"><Suspense fallback={<p>Loading…</p>}><Verify /></Suspense></div>;
}
