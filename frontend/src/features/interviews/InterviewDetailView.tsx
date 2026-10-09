'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useInterview } from './hooks';
import { InterviewCard } from './InterviewCard';
import { InterviewLoadError, InterviewLoading, isNotFound } from './InterviewState';

export function InterviewDetailView({ role }: { role: 'RECRUITER' | 'JOB_SEEKER' }) {
  const { id } = useParams<{ id: string }>();
  const q = useInterview(id);
  const listHref = role === 'RECRUITER' ? '/recruiter/interviews' : '/interviews';
  return (
    <main className="mx-auto max-w-3xl px-4 py-10">
      <Link href={listHref} className="text-sm font-semibold underline">All interviews</Link>
      {q.isLoading && <InterviewLoading label="Loading interview…" />}
      {q.isError && (isNotFound(q.error)
        ? <p role="alert" className="mt-6">Interview not found or unavailable.</p>
        : <InterviewLoadError error={q.error} fallback="The interview could not be loaded." onRetry={() => void q.refetch()} retrying={q.isFetching} />)}
      {q.data && <div className="mt-4"><InterviewCard interview={q.data} role={role} /></div>}
    </main>
  );
}
