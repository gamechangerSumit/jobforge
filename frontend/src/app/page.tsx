import Link from 'next/link';
import { Suspense } from 'react';
import { JobSearch } from '@/features/jobs/JobSearch';

export default function HomePage() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-12">
      <section className="mb-12 grid gap-8 lg:grid-cols-[1.2fr_.8fr] lg:items-center">
        <div>
          <p className="text-sm font-bold uppercase tracking-widest text-slate-500">
            JobForge
          </p>

          <h1 className="mt-3 text-5xl font-black tracking-tight">
            Find your next role. Build your career.
          </h1>

          <p className="mt-5 max-w-2xl text-lg text-slate-600">
            Search published opportunities, save jobs, apply with your profile,
            and track every application.
          </p>

          <div className="mt-6 flex gap-3">
            <Link
              href="/jobs"
              className="rounded-lg bg-slate-900 px-5 py-3 text-sm font-semibold text-white"
            >
              Explore jobs
            </Link>

            <Link
              href="/register"
              className="rounded-lg border bg-white px-5 py-3 text-sm font-semibold"
            >
              Create account
            </Link>
          </div>
        </div>

        <div className="rounded-3xl bg-slate-900 p-8 text-white">
          <p className="text-sm text-slate-300">Phase 2</p>
          <p className="mt-2 text-2xl font-bold">Jobs & Applications</p>

          <ul className="mt-5 space-y-3 text-sm text-slate-300">
            <li>• Contract-aligned job search</li>
            <li>• Save and apply to jobs</li>
            <li>• Recruiter pipeline status updates</li>
          </ul>
        </div>
      </section>

      <Suspense fallback={<p>Loading job search…</p>}>
        <JobSearch />
      </Suspense>
    </div>
  );
}