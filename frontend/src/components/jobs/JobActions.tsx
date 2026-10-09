'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { saveJob, unsaveJob } from '@/lib/api/jobs';
import { Button } from '@/components/ui/Button';
import { ApiClientError } from '@/lib/api/client';
import type { JobSummary } from '@/types/api';

export function SaveJobButton({ job }: { job: Pick<JobSummary, 'id' | 'saved'> }) {
  const [saved, setSaved] = useState(Boolean(job.saved));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const qc = useQueryClient();
  const toggle = async () => { setBusy(true); setError(''); try { if (saved) await unsaveJob(job.id); else await saveJob(job.id); setSaved(!saved); await qc.invalidateQueries({ queryKey: ['saved-jobs'] }); } catch (e) { setError(e instanceof ApiClientError ? e.message : 'Unable to update saved jobs.'); } finally { setBusy(false); } };
  return <div><Button type="button" disabled={busy} onClick={() => void toggle()}>{busy ? 'Saving…' : saved ? 'Saved' : 'Save job'}</Button>{error && <p role="alert" className="mt-1 text-xs text-red-600">{error}</p>}</div>;
}
