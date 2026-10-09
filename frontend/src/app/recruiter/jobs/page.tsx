'use client';

import Link from 'next/link';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';

import {
  closeJob,
  deleteJob,
  listRecruiterJobs,
  publishJob,
  unpublishJob,
} from '@/lib/api/jobs';

import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

export default function RecruiterJobsPage() {
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ['recruiter-jobs'],
    queryFn: () =>
      listRecruiterJobs({
        page: 0,
        size: 50,
      }),
  });

  const remove = useMutation({
    mutationFn: deleteJob,
    onSuccess: () => {
      void qc.invalidateQueries({
        queryKey: ['recruiter-jobs'],
      });
    },
  });

  const transition = useMutation({
    mutationFn: async ({
      action,
      id,
      version,
    }: {
      action: 'publish' | 'unpublish' | 'close';
      id: string;
      version: number;
    }) => {
      switch (action) {
        case 'publish':
          return publishJob(id, version);

        case 'unpublish':
          return unpublishJob(id, version);

        case 'close':
          return closeJob(id, version);
      }
    },

    onSuccess: () => {
      void qc.invalidateQueries({
        queryKey: ['recruiter-jobs'],
      });
    },
  });

  if (q.isLoading) {
    return (
      <main className="mx-auto max-w-6xl px-4 py-10">
        Loading jobs…
      </main>
    );
  }

  if (q.error) {
    return (
      <main
        className="mx-auto max-w-6xl px-4 py-10"
        role="alert"
      >
        Unable to load recruiter jobs.
      </main>
    );
  }

  const jobs = q.data ?? [];

  return (
    <main className="mx-auto max-w-6xl px-4 py-10">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-black">
            My jobs
          </h1>

          <p className="mt-1 text-slate-600">
            Create, publish and manage your company jobs.
          </p>
        </div>

        <Link
          href="/recruiter/jobs/new"
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white"
        >
          Create job
        </Link>
      </div>

      <div className="mt-6 space-y-4">
        {jobs.map((job) => (
          <Card key={job.id}>
            <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-center">
              <div>
                <Link
                  href={`/jobs/${job.id}`}
                  className="text-lg font-bold hover:underline"
                >
                  {job.title}
                </Link>

                <p className="text-sm text-slate-600">
                  {job.location?.city ?? 'Remote'} ·{' '}
                  {job.workMode} ·{' '}
                  {job.employmentType} ·{' '}
                  {job.applicationCount} application{job.applicationCount === 1 ? '' : 's'}
                </p>

                <p className="mt-1 text-xs font-semibold uppercase text-slate-500">
                  {job.status}
                </p>
              </div>

              <div className="flex flex-wrap gap-2">
                <Link
                  href={`/recruiter/jobs/${job.id}/applications`}
                  className="rounded-lg border px-3 py-2 text-sm font-semibold"
                >
                  Applications
                </Link>

                <Link
                  href={`/recruiter/jobs/${job.id}/edit`}
                  className="rounded-lg border px-3 py-2 text-sm font-semibold"
                >
                  Edit
                </Link>

                {(job.status === 'DRAFT' || job.status === 'UNPUBLISHED' || job.status === 'CLOSED' || job.status === 'EXPIRED') && (
                  <Button
                    disabled={transition.isPending}
                    onClick={() =>
                      void transition.mutate({
                        action: 'publish',
                        id: job.id,
                        version: job.version,
                      })
                    }
                  >
                    Publish
                  </Button>
                )}

                {job.status === 'PUBLISHED' && (
                  <>
                    <Button
                      disabled={transition.isPending}
                      onClick={() =>
                        void transition.mutate({
                          action: 'unpublish',
                          id: job.id,
                          version: job.version,
                        })
                      }
                    >
                      Unpublish
                    </Button>

                    <Button
                      disabled={transition.isPending}
                      onClick={() =>
                        void transition.mutate({
                          action: 'close',
                          id: job.id,
                          version: job.version,
                        })
                      }
                    >
                      Close
                    </Button>
                  </>
                )}

                <Button
                  className="bg-white text-red-700 ring-1 ring-red-200 hover:bg-red-50"
                  disabled={remove.isPending}
                  onClick={() => {
                    if (
                      window.confirm(
                        'Delete this job?',
                      )
                    ) {
                      void remove.mutate(job.id);
                    }
                  }}
                >
                  Delete
                </Button>
              </div>
            </div>
          </Card>
        ))}

        {jobs.length === 0 && (
          <Card>
            <p className="text-slate-600">
              No jobs yet. Create your first job.
            </p>
          </Card>
        )}
      </div>
    </main>
  );
}