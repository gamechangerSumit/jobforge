'use client';
import Link from 'next/link';
import { useSession } from '@/lib/auth/session';
import type { UserRole } from '@/types/api';

/** UX guard only: the backend remains the authority for every request. */
export function RoleGuard({ role, children }: { role: UserRole; children: React.ReactNode }) {
  const { user, loading } = useSession();
  if (loading) return <div className="mx-auto max-w-4xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-4xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to continue.</div>;
  if (user.role !== role) return <div className="mx-auto max-w-4xl px-4 py-10" role="alert">You do not have access to this area.</div>;
  return <>{children}</>;
}
