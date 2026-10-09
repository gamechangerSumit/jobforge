'use client';
import { useParams } from 'next/navigation';
import { useInterview } from './hooks';
import { InterviewCard } from './InterviewCard';

export function InterviewDetailView({ role }: { role: 'RECRUITER' | 'JOB_SEEKER' }) {
  const { id } = useParams<{ id: string }>();
  const q = useInterview(id);
  if (q.isLoading) return <main className="mx-auto max-w-3xl px-4 py-10">Loading interview…</main>;
  if (q.isError || !q.data) return <main className="mx-auto max-w-3xl px-4 py-10" role="alert">Interview not found or unavailable.</main>;
  return <main className="mx-auto max-w-3xl px-4 py-10"><InterviewCard interview={q.data} role={role} /></main>;
}
