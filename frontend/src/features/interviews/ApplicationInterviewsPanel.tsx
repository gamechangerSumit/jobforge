'use client';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { InterviewCard } from './InterviewCard';
import { InterviewForm } from './InterviewForm';
import { useInterviewList } from './hooks';
import { InterviewLoadError, InterviewLoading } from './InterviewState';
import type { ApplicationStatus } from '@/types/api';

/**
 * Interview section of the recruiter's application detail page (GET /interviews?applicationId=…).
 */
export function ApplicationInterviewsPanel({ applicationId, status }: { applicationId: string; status: ApplicationStatus }) {
  const [scheduling, setScheduling] = useState(false);
  const q = useInterviewList({ applicationId, size: 50 });
  const items = q.data?.items ?? [];
  // Scheduling is only possible for SHORTLISTED (moves to INTERVIEW) or INTERVIEW applications.
  const canSchedule = status === 'SHORTLISTED' || status === 'INTERVIEW';
  return (
    <section className="mt-8" aria-labelledby="application-interviews">
      <div className="flex items-center justify-between gap-3">
        <h2 id="application-interviews" className="text-lg font-bold">Interviews</h2>
        {canSchedule && !scheduling && <Button onClick={() => setScheduling(true)}>Schedule interview</Button>}
      </div>
      {!canSchedule && items.length === 0 && <p className="mt-2 text-sm text-slate-600">Shortlist the candidate to schedule an interview.</p>}
      {scheduling && <div className="mt-3"><InterviewForm mode="schedule" applicationId={applicationId} onDone={() => setScheduling(false)} onCancel={() => setScheduling(false)} /></div>}
      {q.isLoading && <InterviewLoading label="Loading interviews…" />}
      {q.isError && <InterviewLoadError error={q.error} fallback="Interviews could not be loaded." onRetry={() => void q.refetch()} retrying={q.isFetching} />}
      <div className="mt-3 space-y-3">
        {items.map((i) => <InterviewCard key={i.id} interview={i} role="RECRUITER" showApplicationLink={false} />)}
        {q.data && canSchedule && items.length === 0 && !scheduling && <p className="text-sm text-slate-600">No interviews scheduled yet.</p>}
      </div>
    </section>
  );
}
