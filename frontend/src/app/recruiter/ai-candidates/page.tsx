'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { ApiClientError } from '@/lib/api/client';
import { getCandidateRecommendations, type AiCandidateRecommendation } from '@/lib/api/ai';
import { listRecruiterJobs } from '@/lib/api/jobs';
import type { RecruiterJobSummary } from '@/types/api';

export default function AiCandidatesPage() {
  const [jobs, setJobs] = useState<RecruiterJobSummary[]>([]);
  const [jobId, setJobId] = useState('');
  const [candidates, setCandidates] = useState<AiCandidateRecommendation[]>([]);
  const [loadingJobs, setLoadingJobs] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listRecruiterJobs({ page: 0, size: 50 })
      .then((items) => {
        setJobs(items);
        const firstPublished = items.find((job) => job.status === 'PUBLISHED');
        if (firstPublished) setJobId(firstPublished.id);
      })
      .catch(() => setError('Could not load your jobs.'))
      .finally(() => setLoadingJobs(false));
  }, []);

  async function run() {
    if (!jobId) return;
    setLoading(true);
    setError(null);
    setCandidates([]);
    try {
      const result = await getCandidateRecommendations(jobId);
      setCandidates(result.candidates ?? []);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : 'Candidate recommendations could not be generated.');
    } finally {
      setLoading(false);
    }
  }

  const eligibleJobs = jobs.filter((job) => job.status === 'PUBLISHED');

  return (
    <main className="mx-auto max-w-6xl space-y-7 px-4 py-10">
      <div>
        <p className="text-sm font-bold uppercase tracking-[.16em] text-violet-600">Recruiter workspace</p>
        <h1 className="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">AI candidate matching</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
          Rank applicants for one of your published jobs by matching their recruiter-visible profile skills to the job requirements.
          This is decision support only; review every application yourself.
        </p>
      </div>

      <Card className="space-y-4">
        <label className="block max-w-2xl">
          <span className="mb-2 block text-sm font-semibold text-slate-700">Published job</span>
          <select className="jf-input" value={jobId} onChange={(event) => setJobId(event.target.value)} disabled={loadingJobs || loading || !eligibleJobs.length}>
            {eligibleJobs.map((job) => <option key={job.id} value={job.id}>{job.title}</option>)}
          </select>
        </label>
        <div className="flex flex-wrap items-center gap-3">
          <button type="button" onClick={() => void run()} disabled={!jobId || loading || loadingJobs}
            className="jf-button jf-button-primary disabled:cursor-not-allowed disabled:opacity-50">
            {loading ? 'Matching applicants…' : 'Find matching applicants'}
          </button>
          {jobId && <Link href={`/recruiter/jobs/${jobId}/applications`} className="text-sm font-bold text-violet-700 hover:underline">Open applications</Link>}
        </div>
      </Card>

      {error && <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</div>}
      {!loadingJobs && !eligibleJobs.length && <Card><p className="font-semibold text-slate-800">No published jobs available.</p><p className="mt-1 text-sm text-slate-600">Publish a job before matching its applicants.</p></Card>}
      {loading && <div className="h-40 animate-pulse rounded-2xl bg-slate-100" />}
      {!loading && candidates.length > 0 && <section className="space-y-4">
        <div className="flex items-end justify-between gap-3">
          <div><h2 className="text-xl font-black text-slate-950">Matching applicants</h2><p className="mt-1 text-sm text-slate-600">{candidates.length} recruiter-visible profiles matched against this job&apos;s skills.</p></div>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          {candidates.map((candidate) => <Card key={candidate.applicationId} className="space-y-3">
            <div className="flex items-start justify-between gap-3">
              <div><h3 className="font-bold text-slate-950">{candidate.headline || candidate.currentTitle || 'Applicant profile'}</h3><p className="mt-1 text-sm text-slate-500">{candidate.currentTitle || 'Title not provided'}</p></div>
              <span className="shrink-0 rounded-full bg-violet-50 px-3 py-1 text-sm font-bold text-violet-700">{candidate.matchScore}% match</span>
            </div>
            <p className="text-sm leading-6 text-slate-600">{candidate.reason}</p>
            {!!candidate.matchedSkills.length && <div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Matching skills</p><div className="mt-2 flex flex-wrap gap-2">{candidate.matchedSkills.map((skill) => <span key={skill} className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-800">{skill}</span>)}</div></div>}
            {!!candidate.missingSkills.length && <div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Other required skills</p><div className="mt-2 flex flex-wrap gap-2">{candidate.missingSkills.map((skill) => <span key={skill} className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800">{skill}</span>)}</div></div>}
            <Link href={`/recruiter/applications/${candidate.applicationId}`} className="inline-flex text-sm font-bold text-violet-700 hover:underline">Review application →</Link>
          </Card>)}
        </div>
      </section>}
      {!loading && candidates.length === 0 && !error && jobId && <Card><p className="font-semibold text-slate-800">No results yet.</p><p className="mt-1 text-sm text-slate-600">Select a published job and run matching. Only active applicants with non-private profiles are included.</p></Card>}
    </main>
  );
}
