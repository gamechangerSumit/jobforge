'use client';
import Link from 'next/link';
import { useState } from 'react';
import { InterviewStatusBadge } from '@/components/interviews/InterviewStatusBadge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useReasonDialog } from '@/components/ui/ReasonDialog';
import { errorMessage } from '@/lib/api/errors';
import { formatInZone, hasStarted, isHttpUrl } from '@/lib/interviews/time';
import { useCancelInterview, useCompleteInterview, useRespondToInterview } from './hooks';
import { InterviewForm } from './InterviewForm';
import type { Interview, InterviewOutcome, SeekerChoice } from '@/types/interviews';

const typeLabel: Record<string, string> = { PHONE: 'Phone call', VIDEO: 'Video call', ONSITE: 'On site' };
const CLOSED_APPLICATION = ['HIRED', 'REJECTED', 'WITHDRAWN'];

export function InterviewCard({ interview, role, showApplicationLink = true }: { interview: Interview; role: 'RECRUITER' | 'JOB_SEEKER'; showApplicationLink?: boolean }) {
  const when = formatInZone(interview.scheduledAt, interview.timezone);
  const place = interview.locationOrLink ?? '';
  const person = role === 'RECRUITER' ? interview.seeker : interview.scheduledBy;
  const applicationHref = role === 'RECRUITER' ? `/recruiter/applications/${interview.applicationId}` : `/applications/${interview.applicationId}`;
  return (
    <Card>
      <div className="flex flex-col justify-between gap-3 md:flex-row">
        <div>
          <Link href={role === 'RECRUITER' ? `/recruiter/interviews/${interview.id}` : `/interviews/${interview.id}`} className="font-bold hover:underline">
            {interview.job?.title ?? 'Interview'}
          </Link>
          <p className="text-sm text-slate-600">
            {role === 'RECRUITER' ? 'Candidate' : interview.job?.company.name ?? 'Company'}
            {person ? `: ${person.firstName} ${person.lastName}` : ''}
          </p>
        </div>
        <InterviewStatusBadge status={interview.status} />
      </div>
      <dl className="mt-4 grid gap-2 text-sm md:grid-cols-2">
        <div><dt className="text-slate-500">When</dt><dd className="font-semibold">{when}</dd></div>
        <div><dt className="text-slate-500">Type · length</dt><dd>{typeLabel[interview.type] ?? interview.type} · {interview.durationMinutes} min</dd></div>
        <div className="md:col-span-2"><dt className="text-slate-500">Where</dt>
          <dd>{isHttpUrl(place) ? <a href={place} target="_blank" rel="noopener noreferrer" className="break-all font-semibold underline">{place}</a> : place || '—'}</dd></div>
        {role === 'RECRUITER' && interview.status !== 'CANCELLED' && <div><dt className="text-slate-500">Candidate answer</dt>
          <dd>{interview.seekerResponse === 'PENDING' ? 'Waiting for an answer' : interview.seekerResponse === 'CONFIRMED' ? 'Confirmed' : 'Declined'}{interview.seekerResponseNote ? ` — “${interview.seekerResponseNote}”` : ''}</dd></div>}
        {interview.status === 'CANCELLED' && interview.cancelledReason && <div className="md:col-span-2"><dt className="text-slate-500">Cancelled because</dt><dd>{interview.cancelledReason}</dd></div>}
        {role === 'RECRUITER' && interview.notes && <div className="md:col-span-2"><dt className="text-slate-500">Internal notes</dt><dd className="whitespace-pre-wrap">{interview.notes}</dd></div>}
      </dl>
      {interview.applicationStatus && CLOSED_APPLICATION.includes(interview.applicationStatus) && interview.status !== 'CANCELLED' && interview.status !== 'COMPLETED' && interview.status !== 'NO_SHOW' &&
        <p className="mt-3 rounded-lg bg-amber-50 p-2 text-xs text-amber-900">The application is {interview.applicationStatus.toLowerCase()}, so this interview can no longer be changed{role === 'RECRUITER' ? ' — cancel it to clear it from the calendar' : ''}.</p>}
      <div className="mt-4 flex flex-wrap items-center gap-2">
        {role === 'RECRUITER' ? <RecruiterActions interview={interview} /> : <SeekerActions interview={interview} />}
        {showApplicationLink && <Link href={applicationHref} className="text-sm font-semibold underline">View application</Link>}
      </div>
    </Card>
  );
}

function SeekerActions({ interview }: { interview: Interview }) {
  const respond = useRespondToInterview(interview.id);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const closed = interview.applicationStatus ? CLOSED_APPLICATION.includes(interview.applicationStatus) : false;
  const canAnswer = (interview.status === 'SCHEDULED' || interview.status === 'CONFIRMED') && !hasStarted(interview.scheduledAt) && !closed;
  if (!canAnswer) {
    const notice = seekerNotice(interview, closed);
    return notice ? <p data-testid="seeker-notice" className="w-full text-sm text-slate-600">{notice}</p> : null;
  }
  const answer = async (response: SeekerChoice) => {
    setError('');
    try { await respond.mutateAsync({ response, ...(note.trim() ? { note: note.trim() } : {}) }); setNote(''); }
    catch (e) { setError(errorMessage(e, 'Unable to send your answer. Please try again.')); }
  };
  return (
    <div className="w-full space-y-2">
      <label className="block text-sm font-semibold" htmlFor={`note-${interview.id}`}>Message to the recruiter (optional)</label>
      <input id={`note-${interview.id}`} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} className="min-h-10 w-full rounded-lg border border-slate-300 px-3 text-sm" />
      <div className="flex gap-2">
        <Button disabled={respond.isPending || interview.status === 'CONFIRMED'} onClick={() => void answer('CONFIRM')}>{interview.status === 'CONFIRMED' ? 'Confirmed' : 'Confirm'}</Button>
        <Button variant="danger" disabled={respond.isPending} onClick={() => void answer('DECLINE')}>Decline</Button>
      </div>
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
    </div>
  );
}

/** Why a seeker has no buttons on an interview (the status badge alone does not say what happens next). */
function seekerNotice(i: Interview, applicationClosed: boolean): string | null {
  if (i.status === 'CANCELLED') return 'This interview was cancelled.';
  if (i.status === 'COMPLETED' || i.status === 'NO_SHOW') return 'This interview is over.';
  if (applicationClosed) return null; // the closed-application banner already explains it
  if (i.status === 'DECLINED') return 'You declined this interview. The recruiter can offer you a new time.';
  if (hasStarted(i.scheduledAt)) return 'This interview has already started, so it can no longer be answered here.';
  return null;
}

function RecruiterActions({ interview }: { interview: Interview }) {
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState('');
  const cancel = useCancelInterview(interview.id);
  const complete = useCompleteInterview(interview.id);
  const { ask, dialog } = useReasonDialog();
  const open = interview.status === 'SCHEDULED' || interview.status === 'CONFIRMED' || interview.status === 'DECLINED';
  const closed = interview.applicationStatus ? CLOSED_APPLICATION.includes(interview.applicationStatus) : false;
  const canFinish = (interview.status === 'SCHEDULED' || interview.status === 'CONFIRMED') && hasStarted(interview.scheduledAt);
  if (!open) return null;

  const doCancel = async () => {
    const reason = await ask('Why is this interview cancelled? The candidate will see this.');
    if (!reason) return;
    setError('');
    try { await cancel.mutateAsync({ reason }); } catch (e) { setError(errorMessage(e, 'Unable to cancel the interview.')); }
  };
  const doComplete = async (outcome: InterviewOutcome) => {
    setError('');
    try { await complete.mutateAsync({ outcome }); } catch (e) { setError(errorMessage(e, 'Unable to update the interview.')); }
  };
  return (
    <div className="w-full space-y-3">
      <div className="flex flex-wrap gap-2">
        {!closed && <button type="button" onClick={() => setEditing((v) => !v)} aria-expanded={editing} className="rounded-lg px-4 py-2 text-sm font-semibold ring-1 ring-slate-300">{editing ? 'Close' : 'Reschedule / edit'}</button>}
        {canFinish && <Button disabled={complete.isPending} onClick={() => void doComplete('COMPLETED')}>Mark completed</Button>}
        {canFinish && <button type="button" disabled={complete.isPending} onClick={() => void doComplete('NO_SHOW')} className="rounded-lg px-4 py-2 text-sm font-semibold ring-1 ring-slate-300">Mark no-show</button>}
        <Button variant="danger" disabled={cancel.isPending} onClick={() => void doCancel()}>Cancel interview</Button>
      </div>
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      {editing && <InterviewForm mode="reschedule" interview={interview} onDone={() => setEditing(false)} onCancel={() => setEditing(false)} />}
      {dialog}
    </div>
  );
}
