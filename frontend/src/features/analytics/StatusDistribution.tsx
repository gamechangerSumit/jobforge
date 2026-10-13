import { Card } from '@/components/ui/Card';
import { pipelineSegments } from '@/lib/analytics/charts';
import type { RecruiterOverview } from '@/types/analytics';

const BAR: Record<string, string> = {
  HIRED: 'bg-emerald-600', REJECTED: 'bg-rose-500', WITHDRAWN: 'bg-slate-400',
};

/** Horizontal bars; every row also prints its count and percentage, so colour is never the only signal. */
export function StatusDistribution({ overview }: { overview: Pick<RecruiterOverview, 'pipeline' | 'applications'> }) {
  const segments = pipelineSegments(overview);
  return (
    <Card>
      <h2 className="text-lg font-bold">Application status</h2>
      <p className="text-sm text-slate-600">Current status of applications submitted in this range.</p>
      <ul className="mt-4 space-y-3" aria-label="Applications by current status">
        {segments.map((s) => (
          <li key={s.status}>
            <div className="flex justify-between text-sm">
              <span>{s.label}</span>
              <span className="tabular-nums">{s.count} <span className="text-slate-500">({s.percent}%)</span></span>
            </div>
            <div className="mt-1 h-2 rounded bg-slate-100" aria-hidden="true">
              <div className={`h-2 rounded ${BAR[s.status] ?? 'bg-sky-600'}`} style={{ width: `${s.percent}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}
