'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { ApiClientError } from '@/lib/api/client';
import {
  getAiUsage,
  getCareerInsights,
  getJobRecommendations,
  type AiJobRecommendation,
  type AiUsage,
  type CareerInsights,
} from '@/lib/api/ai';

export default function CareerInsightsPage() {
  const [recommendations, setRecommendations] = useState<AiJobRecommendation[]>([]);
  const [insights, setInsights] = useState<CareerInsights | null>(null);
  const [usage, setUsage] = useState<AiUsage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [jobs, career, quota] = await Promise.all([
        getJobRecommendations(),
        getCareerInsights(),
        getAiUsage(),
      ]);
      setRecommendations(jobs.recommendations ?? []);
      setInsights(career);
      setUsage(quota);
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.message : 'Could not load your career insights. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  return (
    <main className="mx-auto max-w-6xl space-y-7 px-4 py-10">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-bold uppercase tracking-[.16em] text-violet-600">Career workspace</p>
          <h1 className="mt-2 text-3xl font-black tracking-tight text-slate-950 sm:text-4xl">Your AI career insights</h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
            Recommendations use your own profile and currently public jobs. Treat match scores as guidance, not a hiring prediction.
          </p>
        </div>
        <button type="button" onClick={() => void load()} disabled={loading}
          className="jf-button jf-button-secondary disabled:cursor-not-allowed disabled:opacity-60">
          {loading ? 'Refreshing…' : 'Refresh insights'}
        </button>
      </div>

      {usage && <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-violet-100 bg-violet-50/70 px-4 py-3 text-sm">
        <span className="font-bold text-violet-950">AI credits</span>
        <span className="text-violet-800">{usage.remaining} remaining of {usage.limit}</span>
        <span className="text-xs text-violet-700">Resets {new Date(usage.resetsAt).toLocaleString()}</span>
      </div>}

      {error && <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
        {error} <button className="ml-2 font-bold underline" onClick={() => void load()}>Retry</button>
      </div>}

      {loading ? <div className="grid gap-5 lg:grid-cols-2">
        {[0, 1, 2, 3].map((item) => <div key={item} className="h-44 animate-pulse rounded-2xl bg-slate-100" />)}
      </div> : <>
        {insights && <div className="grid gap-5 md:grid-cols-3">
          <Card>
            <p className="text-sm font-semibold text-slate-500">Profile strength</p>
            <p className="mt-2 text-4xl font-black text-slate-950">{insights.profileStrength.score}<span className="text-lg text-slate-400">/100</span></p>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-violet-600" style={{ width: `${Math.max(0, Math.min(100, insights.profileStrength.score))}%` }} />
            </div>
            <p className="mt-3 text-xs text-slate-500">Based on your saved profile completeness.</p>
          </Card>
          <Card className="md:col-span-2">
            <h2 className="font-bold text-slate-950">Profile improvements</h2>
            {insights.profileStrength.suggestions.length ? <ul className="mt-3 space-y-2 text-sm text-slate-700">
              {insights.profileStrength.suggestions.map((item) => <li key={item} className="flex gap-2"><span className="text-violet-600">•</span>{item}</li>)}
            </ul> : <p className="mt-3 text-sm text-emerald-700">Your profile has the key information. Keep it updated as your experience grows.</p>}
          </Card>
        </div>}

        <section className="space-y-4">
          <div className="flex items-end justify-between gap-3">
            <div><h2 className="text-xl font-black text-slate-950">Jobs that fit your skills</h2><p className="mt-1 text-sm text-slate-600">Ranked against your profile skills and location.</p></div>
            <Link href="/jobs" className="text-sm font-bold text-violet-700 hover:underline">Browse all jobs</Link>
          </div>
          {recommendations.length ? <div className="grid gap-4 md:grid-cols-2">
            {recommendations.map((job) => <Card key={job.jobId} className="space-y-3">
              <div className="flex items-start justify-between gap-3">
                <div><h3 className="font-bold text-slate-950">{job.title}</h3><p className="mt-1 text-sm text-slate-500">{job.companyName || 'Verified company'}</p></div>
                <span className="shrink-0 rounded-full bg-violet-50 px-3 py-1 text-sm font-bold text-violet-700">{job.matchScore}% match</span>
              </div>
              <p className="text-sm leading-6 text-slate-600">{job.reason}</p>
              {!!job.matchedSkills.length && <div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Matching skills</p><div className="mt-2 flex flex-wrap gap-2">{job.matchedSkills.map((skill) => <span key={skill} className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-800">{skill}</span>)}</div></div>}
              {!!job.missingSkills.length && <div><p className="text-xs font-bold uppercase tracking-wide text-slate-500">Skills to strengthen</p><div className="mt-2 flex flex-wrap gap-2">{job.missingSkills.map((skill) => <span key={skill} className="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800">{skill}</span>)}</div></div>}
              <Link href={`/jobs/${job.jobId}`} className="inline-flex text-sm font-bold text-violet-700 hover:underline">View job →</Link>
            </Card>)}
          </div> : <Card><p className="font-semibold text-slate-800">No matching public jobs found yet.</p><p className="mt-1 text-sm text-slate-600">Add skills and a preferred city to your profile, then refresh your recommendations.</p></Card>}
        </section>

        {insights && <div className="grid gap-5 lg:grid-cols-2">
          <Card>
            <h2 className="font-bold text-slate-950">Skills appearing in current jobs</h2>
            <p className="mt-1 text-xs text-slate-500">A small snapshot of up to 10 public jobs matching your search context.</p>
            <ul className="mt-4 space-y-3">
              {insights.marketSnapshot.topSkills.map((item) => <li key={item.skill} className="flex items-center justify-between gap-3 text-sm">
                <span className="font-semibold text-slate-700">{item.skill}</span><span className="text-slate-500">{item.jobCount} job{item.jobCount === 1 ? '' : 's'}</span>
              </li>)}
            </ul>
            {!insights.marketSnapshot.topSkills.length && <p className="mt-3 text-sm text-slate-500">Not enough public job data for a market snapshot.</p>}
          </Card>
          <Card>
            <h2 className="font-bold text-slate-950">Potential skill gaps</h2>
            <ul className="mt-4 space-y-3">
              {insights.skillGaps.map((item) => <li key={item.skill} className="flex items-center justify-between gap-3 text-sm">
                <span className="font-semibold text-slate-700">{item.skill}</span><span className="text-slate-500">In {item.jobsMentioningSkill} sampled jobs</span>
              </li>)}
            </ul>
            {!insights.skillGaps.length && <p className="mt-3 text-sm text-slate-500">No clear skill gaps in the current sample.</p>}
          </Card>
        </div>}

        {insights && <Card>
          <h2 className="font-bold text-slate-950">Your next steps</h2>
          <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm text-slate-700">
            {insights.nextSteps.map((item) => <li key={item}>{item}</li>)}
          </ol>
        </Card>}
      </>}
    </main>
  );
}
