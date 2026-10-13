'use client';
import { useId } from 'react';
import { Card } from '@/components/ui/Card';
import { seriesGeometry } from '@/lib/analytics/charts';

const BOX = { width: 640, height: 220, left: 40, right: 12, top: 12, bottom: 28 };

/**
 * Accessible line/area chart: the SVG has a title and description, every point has a native tooltip (`<title>`), and a
 * collapsible data table gives screen-reader and keyboard users the exact values.
 */
export function TrendChart({ title, description, valueLabel, data }: {
  title: string; description: string; valueLabel: string; data: { label: string; value: number }[];
}) {
  const id = useId();
  const g = seriesGeometry(data, BOX);
  const total = data.reduce((sum, d) => sum + d.value, 0);
  const labelEvery = Math.max(1, Math.ceil(data.length / 6));
  return (
    <Card>
      <h2 id={`${id}-h`} className="text-lg font-bold">{title}</h2>
      <p className="text-sm text-slate-600">{description}</p>
      <figure className="mt-4">
        <svg viewBox={`0 0 ${BOX.width} ${BOX.height}`} role="img" aria-labelledby={`${id}-t ${id}-d`} className="h-auto w-full">
          <title id={`${id}-t`}>{title}</title>
          <desc id={`${id}-d`}>{`${valueLabel}: ${total} in total across ${data.length} points; maximum ${g.max}.`}</desc>
          {g.ticks.map((tick) => {
            const y = BOX.top + (BOX.height - BOX.top - BOX.bottom) * (1 - tick / g.max);
            return (
              <g key={tick}>
                <line x1={BOX.left} x2={BOX.width - BOX.right} y1={y} y2={y} stroke="#e2e8f0" />
                <text x={BOX.left - 6} y={y + 4} textAnchor="end" fontSize="11" fill="#475569">{tick}</text>
              </g>
            );
          })}
          {g.area && <path d={g.area} fill="#0ea5e9" fillOpacity="0.12" />}
          {g.line && <path d={g.line} fill="none" stroke="#0369a1" strokeWidth="2" />}
          {g.points.map((p, i) => (
            <g key={p.label}>
              <circle cx={p.x} cy={p.y} r={data.length > 60 ? 1.5 : 3} fill="#0369a1"><title>{`${p.label}: ${p.value}`}</title></circle>
              {i % labelEvery === 0 && <text x={p.x} y={BOX.height - 8} textAnchor="middle" fontSize="10" fill="#475569">{p.label.slice(5)}</text>}
            </g>
          ))}
        </svg>
        <figcaption className="mt-1 text-xs text-slate-500">{valueLabel}, by UTC date.</figcaption>
      </figure>
      <details className="mt-3 text-sm">
        <summary className="cursor-pointer font-semibold">View data table</summary>
        <div className="mt-2 max-h-56 overflow-auto">
          <table className="min-w-full text-left">
            <caption className="sr-only">{title}</caption>
            <thead><tr><th scope="col" className="p-1">Date</th><th scope="col" className="p-1">{valueLabel}</th></tr></thead>
            <tbody>{data.map((d) => <tr key={d.label}><td className="p-1">{d.label}</td><td className="p-1 tabular-nums">{d.value}</td></tr>)}</tbody>
          </table>
        </div>
      </details>
    </Card>
  );
}
