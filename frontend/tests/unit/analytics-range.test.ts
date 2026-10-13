import { describe, expect, test } from 'vitest';
import { matchingPreset, MAX_RANGE_DAYS, presetRange, toIsoDate, validateRange } from '@/lib/analytics/range';

const NOW = new Date('2026-10-11T23:30:00Z');

describe('presetRange', () => {
  test('ends today in UTC and is inclusive', () => {
    expect(presetRange(30, NOW)).toEqual({ from: '2026-09-12', to: '2026-10-11' });
    expect(presetRange(1, NOW)).toEqual({ from: '2026-10-11', to: '2026-10-11' });
  });

  test('uses the UTC date even when the local date differs', () => {
    expect(presetRange(7, new Date('2026-10-12T00:10:00Z')).to).toBe('2026-10-12');
    expect(toIsoDate(new Date('2026-10-11T23:59:59.999Z'))).toBe('2026-10-11');
  });
});

describe('validateRange', () => {
  test('accepts valid and boundary ranges', () => {
    expect(validateRange({ from: '2026-10-01', to: '2026-10-01' })).toBeNull();
    expect(validateRange({ from: '2025-10-11', to: '2026-10-11' })).toBeNull(); // 366 days inclusive
  });

  test('rejects missing, malformed and impossible dates', () => {
    expect(validateRange({ from: '', to: '2026-10-01' })).toMatch(/both/i);
    expect(validateRange({ from: '2026-10-1', to: '2026-10-02' })).toMatch(/valid/i);
    expect(validateRange({ from: '2026-02-30', to: '2026-03-01' })).toMatch(/valid/i);
  });

  test('rejects reversed and oversized ranges', () => {
    expect(validateRange({ from: '2026-10-05', to: '2026-10-04' })).toMatch(/after/i);
    expect(validateRange({ from: '2025-10-10', to: '2026-10-11' })).toContain(String(MAX_RANGE_DAYS));
  });
});

test('matchingPreset marks only an exact preset range', () => {
  expect(matchingPreset(presetRange(30, NOW), NOW)).toBe('30d');
  expect(matchingPreset(presetRange(7, NOW), NOW)).toBe('7d');
  expect(matchingPreset({ from: '2026-10-01', to: '2026-10-11' }, NOW)).toBeNull();
});
