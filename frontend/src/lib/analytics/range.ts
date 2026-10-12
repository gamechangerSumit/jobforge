import type { DateRangeParams } from '@/types/analytics';

/** Same limits as the proposed, unmerged Session 5 contract change (§12.10.1): UTC calendar dates, inclusive, at most 366 days. */
export const MAX_RANGE_DAYS = 366;
const DAY_MS = 86_400_000;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const RANGE_PRESETS = [
  { id: '7d', days: 7, label: 'Last 7 days' },
  { id: '30d', days: 30, label: 'Last 30 days' },
  { id: '90d', days: 90, label: 'Last 90 days' },
] as const;

export const DEFAULT_PRESET_DAYS = 30;

/** UTC `YYYY-MM-DD` (never the browser's local date: the API buckets days in UTC). */
export function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** A range of `days` days ending today (UTC), inclusive. */
export function presetRange(days: number, now: Date = new Date()): DateRangeParams {
  const to = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  return { from: toIsoDate(new Date(to - (days - 1) * DAY_MS)), to: toIsoDate(new Date(to)) };
}

function parseDate(value: string): number | null {
  if (!ISO_DATE.test(value)) return null;
  const time = Date.parse(`${value}T00:00:00Z`);
  if (Number.isNaN(time) || toIsoDate(new Date(time)) !== value) return null; // rejects 2026-02-30
  return time;
}

/** Returns a user-facing message, or null when the range is acceptable to the API. */
export function validateRange(range: DateRangeParams): string | null {
  const from = parseDate(range.from);
  const to = parseDate(range.to);
  if (!range.from || !range.to) return 'Choose both a start and an end date.';
  if (from === null || to === null) return 'Dates must be valid calendar dates.';
  if (from > to) return 'The start date must not be after the end date.';
  if ((to - from) / DAY_MS + 1 > MAX_RANGE_DAYS) return `The range can span at most ${MAX_RANGE_DAYS} days.`;
  return null;
}

/** Which preset (if any) the range equals, so the filter can mark it as pressed. */
export function matchingPreset(range: DateRangeParams, now: Date = new Date()): string | null {
  const hit = RANGE_PRESETS.find((p) => {
    const r = presetRange(p.days, now);
    return r.from === range.from && r.to === range.to;
  });
  return hit ? hit.id : null;
}
