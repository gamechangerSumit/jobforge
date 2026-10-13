import { describe, expect, test } from 'vitest';
import {
  formatDays, formatRate, funnelRows, isEmptyOverview, niceMax, pipelineSegments, seriesGeometry,
} from '@/lib/analytics/charts';
import type { PipelineStatus } from '@/types/analytics';

const pipeline = (over: Partial<Record<PipelineStatus, number>> = {}): Record<PipelineStatus, number> => ({
  SUBMITTED: 0, UNDER_REVIEW: 0, SHORTLISTED: 0, INTERVIEW: 0, OFFERED: 0, HIRED: 0, REJECTED: 0, WITHDRAWN: 0, ...over,
});

describe('pipelineSegments', () => {
  test('keeps a fixed order with one-decimal percentages', () => {
    const rows = pipelineSegments({ applications: 42, pipeline: pipeline({ SUBMITTED: 10, HIRED: 3, REJECTED: 29 }) });
    expect(rows.map((r) => r.status)).toEqual(['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN']);
    expect(rows[0]).toMatchObject({ label: 'Submitted', count: 10, percent: 23.8 });
    expect(rows[5].percent).toBe(7.1);
  });

  test('an empty cohort yields 0 percent, never NaN', () => {
    const rows = pipelineSegments({ applications: 0, pipeline: pipeline() });
    expect(rows.every((r) => r.count === 0 && r.percent === 0)).toBe(true);
  });
});

describe('funnelRows', () => {
  const funnel = [
    { stage: 'SUBMITTED', count: 17 }, { stage: 'UNDER_REVIEW', count: 12 }, { stage: 'SHORTLISTED', count: 6 },
    { stage: 'INTERVIEW', count: 0 }, { stage: 'OFFERED', count: 0 }, { stage: 'HIRED', count: 0 },
  ] as const;

  test('scales bars to the first stage and computes step conversion', () => {
    const rows = funnelRows([...funnel]);
    expect(rows[0]).toMatchObject({ widthPercent: 100, stepPercent: null });
    expect(rows[1]).toMatchObject({ widthPercent: 70.6, stepPercent: 70.6 });
    expect(rows[2].stepPercent).toBe(50);
  });

  test('a zero previous stage gives no step percentage', () => {
    expect(funnelRows([...funnel])[4].stepPercent).toBeNull();
  });

  test('an empty funnel is handled', () => {
    expect(funnelRows([])).toEqual([]);
    expect(funnelRows([{ stage: 'SUBMITTED', count: 0 }])[0].widthPercent).toBe(0);
  });
});

describe('niceMax', () => {
  test.each([[0, 1], [1, 1], [3, 5], [7, 10], [12, 20], [180, 200], [999, 1000]])('niceMax(%d) = %d', (value, expected) => {
    expect(niceMax(value)).toBe(expected);
  });
});

describe('seriesGeometry', () => {
  const box = { width: 100, height: 60, left: 10, right: 10, top: 10, bottom: 10 };

  test('maps values into the inner box (larger value → smaller y)', () => {
    const g = seriesGeometry([{ label: 'a', value: 0 }, { label: 'b', value: 5 }], box);
    expect(g.max).toBe(5);
    expect(g.points[0]).toMatchObject({ x: 10, y: 50 });
    expect(g.points[1]).toMatchObject({ x: 90, y: 10 });
    expect(g.line.startsWith('M10,50 L90,10')).toBe(true);
    expect(g.area.endsWith('Z')).toBe(true);
    expect(g.ticks).toEqual([0, 2.5, 5]);
  });

  test('a single point is centred and no data gives empty paths', () => {
    expect(seriesGeometry([{ label: 'a', value: 1 }], box).points[0].x).toBe(50);
    const empty = seriesGeometry([], box);
    expect(empty).toMatchObject({ line: '', area: '', max: 1 });
  });

  test('all-zero data does not divide by zero', () => {
    const g = seriesGeometry([{ label: 'a', value: 0 }, { label: 'b', value: 0 }], box);
    expect(g.points.every((p) => Number.isFinite(p.y))).toBe(true);
  });
});

describe('formatting', () => {
  test('rate and days', () => {
    expect(formatRate(0.0714)).toBe('7.1%');
    expect(formatRate(null)).toBe('—');
    expect(formatDays(12.4)).toBe('12.4 days');
    expect(formatDays(1)).toBe('1 day');
    expect(formatDays(null)).toBe('—');
  });

  test('isEmptyOverview depends on applications only', () => {
    expect(isEmptyOverview({ applications: 0 })).toBe(true);
    expect(isEmptyOverview({ applications: 1 })).toBe(false);
  });
});
