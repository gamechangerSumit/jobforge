'use client';
import { useState } from 'react';
import { Select } from '@/components/ui/Select';
import { useInterviewList } from './hooks';
import { InterviewCard } from './InterviewCard';
import { InterviewLoadError, InterviewLoading } from './InterviewState';
import type { InterviewStatus } from '@/types/interviews';

const statuses: InterviewStatus[] = ['SCHEDULED', 'CONFIRMED', 'DECLINED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];

/** Shared list for both roles; the API scopes results to the caller. */
export function InterviewListView({ role }: { role: 'RECRUITER' | 'JOB_SEEKER' }) {
  const [status, setStatus] = useState<InterviewStatus | ''>('');
  const [page, setPage] = useState(0);
  const q = useInterviewList({ status, page, size: 20 });
  const totalPages = q.data?.page?.totalPages ?? 1;
  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <div className="flex flex-col justify-between gap-3 md:flex-row md:items-end">
        <h1 className="text-3xl font-black">{role === 'RECRUITER' ? 'Interviews' : 'My interviews'}</h1>
        <div className="w-full md:w-56">
          <label htmlFor="interview-status" className="block text-sm font-semibold">Status</label>
          <Select id="interview-status" value={status} onChange={(e) => { setStatus(e.target.value as InterviewStatus | ''); setPage(0); }}>
            <option value="">All</option>
            {statuses.map((s) => <option key={s} value={s}>{s.replaceAll('_', ' ')}</option>)}
          </Select>
        </div>
      </div>
      {q.isLoading && <InterviewLoading label="Loading interviews…" />}
      {q.isError && <InterviewLoadError error={q.error} fallback="Interviews could not be loaded." onRetry={() => void q.refetch()} retrying={q.isFetching} />}
      <div className="mt-6 space-y-3">
        {q.data?.items.map((i) => <InterviewCard key={i.id} interview={i} role={role} />)}
        {q.data && q.data.items.length === 0 && <p data-testid="interviews-empty">{role === 'RECRUITER' ? 'No interviews yet. Schedule one from a shortlisted application.' : 'No interviews yet.'}</p>}
      </div>
      {totalPages > 1 && (
        <div className="mt-6 flex items-center gap-3 text-sm">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="rounded-lg px-3 py-2 ring-1 ring-slate-300 disabled:opacity-50">Previous</button>
          <span>Page {page + 1} of {totalPages}</span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} className="rounded-lg px-3 py-2 ring-1 ring-slate-300 disabled:opacity-50">Next</button>
        </div>
      )}
    </div>
  );
}
