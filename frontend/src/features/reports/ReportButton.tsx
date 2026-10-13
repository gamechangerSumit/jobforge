'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useSession } from '@/lib/auth/session';
import { TARGET_LABEL } from '@/lib/reports/rules';
import { ReportForm } from './ReportForm';
import type { ReportTargetType } from '@/types/reports';

/**
 * "Report" entry point for a job, company or user page. Signed-out visitors are asked to sign in; whether the item may be
 * reported (own content, hidden items) is decided by the API and surfaced as a message, never assumed here.
 */
export function ReportButton({ targetType, targetId }: { targetType: ReportTargetType; targetId: string }) {
  const { user, loading } = useSession();
  const [open, setOpen] = useState(false);
  const [sent, setSent] = useState(false);
  if (loading) return null;
  const noun = TARGET_LABEL[targetType].toLowerCase();
  if (!user) return <Link href="/login" className="text-sm font-semibold text-slate-600 underline">Sign in to report this {noun}</Link>;
  if (sent) return <p role="status" className="text-sm text-emerald-800">Thanks, your report was sent. Our team will review it.</p>;
  return (
    <div>
      {!open && <button type="button" onClick={() => setOpen(true)} className="text-sm font-semibold text-slate-600 underline">Report this {noun}</button>}
      {open && <ReportForm targetType={targetType} targetId={targetId} onDone={() => { setSent(true); setOpen(false); }} onCancel={() => setOpen(false)} />}
    </div>
  );
}
