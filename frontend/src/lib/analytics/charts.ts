import type { FunnelStage, JobAnalytics, PipelineStatus, RecruiterOverview } from '@/types/analytics';

export const PIPELINE_ORDER: PipelineStatus[] = [
  'SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN',
];

export const STATUS_LABELS: Record<PipelineStatus, string> = {
  SUBMITTED: 'Submitted', UNDER_REVIEW: 'Under review', SHORTLISTED: 'Shortlisted', INTERVIEW: 'Interview',
  OFFERED: 'Offered', HIRED: 'Hired', REJECTED: 'Rejected', WITHDRAWN: 'Withdrawn',
};

export interface Segment { status: PipelineStatus; label: string; count: number; percent: number }

/** Distribution rows in a fixed order; `percent` has one decimal and is 0 for an empty cohort. */
export function pipelineSegments(overview: Pick<RecruiterOverview, 'pipeline' | 'applications'>): Segment[] {
  const total = overview.applications;
  return PIPELINE_ORDER.map((status) => {
    const count = overview.pipeline[status] ?? 0;
    return { status, label: STATUS_LABELS[status], count, percent: total > 0 ? Math.round((count / total) * 1000) / 10 : 0 };
  });
}

export interface FunnelRow { stage: FunnelStage; label: string; count: number; widthPercent: number; stepPercent: number | null }

/** Bars are scaled to the first stage; `stepPercent` = share of the previous stage that reached this one. */
export function funnelRows(funnel: JobAnalytics['funnel']): FunnelRow[] {
  const top = funnel[0]?.count ?? 0;
  return funnel.map((row, i) => {
    const previous = i === 0 ? null : funnel[i - 1].count;
    return {
      stage: row.stage,
      label: STATUS_LABELS[row.stage],
      count: row.count,
      widthPercent: top > 0 ? Math.round((row.count / top) * 1000) / 10 : 0,
      stepPercent: previous === null ? null : previous > 0 ? Math.round((row.count / previous) * 1000) / 10 : null,
    };
  });
}

/** Round a maximum up to 1, 2, 5 × 10^n so axis ticks read naturally; at least 1. */
export function niceMax(value: number): number {
  if (value <= 1) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return step * magnitude;
}

export interface SeriesPoint { x: number; y: number; label: string; value: number }
export interface SeriesGeometry { points: SeriesPoint[]; line: string; area: string; max: number; ticks: number[] }

export interface Box { width: number; height: number; left: number; right: number; top: number; bottom: number }

/** Scales `{label,value}` pairs into SVG coordinates. A single point is centred; no points yields empty paths. */
export function seriesGeometry(data: { label: string; value: number }[], box: Box): SeriesGeometry {
  const max = niceMax(Math.max(0, ...data.map((d) => d.value)));
  const innerW = box.width - box.left - box.right;
  const innerH = box.height - box.top - box.bottom;
  const points = data.map((d, i) => ({
    x: box.left + (data.length === 1 ? innerW / 2 : (i / (data.length - 1)) * innerW),
    y: box.top + innerH - (d.value / max) * innerH,
    label: d.label,
    value: d.value,
  }));
  const f = (n: number) => Math.round(n * 100) / 100;
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${f(p.x)},${f(p.y)}`).join(' ');
  const baseline = box.top + innerH;
  const area = points.length
    ? `${line} L${f(points[points.length - 1].x)},${f(baseline)} L${f(points[0].x)},${f(baseline)} Z`
    : '';
  const ticks = [0, max / 2, max].filter((t, i, all) => all.indexOf(t) === i).map((t) => Math.round(t * 100) / 100);
  return { points, line, area, max, ticks };
}

export const formatRate = (rate: number | null): string => (rate === null ? '—' : `${Math.round(rate * 1000) / 10}%`);

export const formatDays = (days: number | null): string => (days === null ? '—' : `${days} ${days === 1 ? 'day' : 'days'}`);

/** True when there is nothing to chart for the selected range. */
export const isEmptyOverview = (overview: Pick<RecruiterOverview, 'applications'>): boolean =>
  overview.applications === 0;
