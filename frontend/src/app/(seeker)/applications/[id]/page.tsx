'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getApplication } from '@/lib/api/applications';
import { Card } from '@/components/ui/Card';
import { StatusBadge } from '@/components/applications/StatusBadge';
import { SeekerWithdrawAction } from '@/features/applications/ApplicationActions';

export default function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ['application', id],
    queryFn: () => getApplication(id),
    enabled: Boolean(id),
  });

  if (q.isLoading) {
    return <div className="p-10">Loading…</div>;
  }

  if (!q.data) {
    return <div className="p-10">Application not found.</div>;
  }

  const a = q.data;

  return (
    <div className="mx-auto max-w-4xl px-4 py-10">
      <Card>
        <div className="flex items-start justify-between gap-4">
          <div>
            <Link
              href={`/jobs/${a.job.id}`}
              className="text-xl font-bold hover:underline"
            >
              {a.job.title}
            </Link>

            <p className="text-sm text-slate-600">
              {a.job.company.name}
            </p>
          </div>

          <StatusBadge status={a.status} />
        </div>

        <div className="mt-6 flex gap-3">
          <SeekerWithdrawAction
            application={a}
            onUpdated={(next) =>
              qc.setQueryData(['application', a.id], next)
            }
          />
        </div>

        <h2 className="mt-8 text-lg font-bold">Status history</h2>

        <ol className="mt-4 space-y-3">
          {a.statusHistory.map((h, i) => (
            <li
              key={`${h.at}-${i}`}
              className="rounded-lg bg-slate-50 p-3 text-sm"
            >
              <b>{h.to.replaceAll('_', ' ')}</b>
              {' · '}
              {new Date(h.at).toLocaleString()}
              {h.reason ? ` · ${h.reason}` : ''}
            </li>
          ))}
        </ol>
      </Card>
    </div>
  );
}