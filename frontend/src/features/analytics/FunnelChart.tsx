import { Card } from '@/components/ui/Card';
import { funnelRows } from '@/lib/analytics/charts';
import type { JobAnalytics } from '@/types/analytics';

/** Cumulative "reached this stage" funnel for the proposed, unmerged Session 5 contract change (§12.10.1). Counts are non-increasing. */
export function FunnelChart({ funnel }: { funnel: JobAnalytics['funnel'] }) {
  const rows = funnelRows(funnel);
  return (
    <Card>
      <h2 className="text-lg font-bold">Hiring funnel</h2>
      <p className="text-sm text-slate-600">Applications that reached each stage (including those that moved on).</p>
      <ol className="mt-4 space-y-3" aria-label="Hiring funnel stages">
        {rows.map((row) => (
          <li key={row.stage}>
            <div className="flex justify-between text-sm">
              <span>{row.label}</span>
              <span className="tabular-nums">
                {row.count}
                {row.stepPercent !== null && <span className="text-slate-500"> ({row.stepPercent}% of previous)</span>}
              </span>
            </div>
            <div className="mt-1 h-3 rounded bg-slate-100" aria-hidden="true">
              <div className="h-3 rounded bg-sky-600" style={{ width: `${row.widthPercent}%` }} />
            </div>
          </li>
        ))}
      </ol>
    </Card>
  );
}
