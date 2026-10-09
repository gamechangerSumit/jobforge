import { Suspense } from 'react';
import { JobSearch } from '@/features/jobs/JobSearch';

export default function JobsPage() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <h1 className="text-3xl font-black">Find jobs</h1>
      <p className="mt-2 text-slate-600">
        Search published JobForge opportunities.
      </p>

      <div className="mt-8">
        <Suspense fallback={<p>Loading job search…</p>}>
          <JobSearch />
        </Suspense>
      </div>
    </div>
  );
}