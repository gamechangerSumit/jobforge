import { expect, test } from 'vitest';
import { formatInZone, instantToZoned, isHttpUrl, isValidTimeZone, zonedToInstant } from '@/lib/interviews/time';

test('converts wall-clock time in a zone to a UTC instant', () => {
  expect(zonedToInstant('2026-10-12', '15:30', 'Asia/Kolkata')).toBe('2026-10-12T10:00:00Z');
  expect(zonedToInstant('2026-10-12', '10:00', 'UTC')).toBe('2026-10-12T10:00:00Z');
  expect(zonedToInstant('2026-01-15', '09:00', 'America/New_York')).toBe('2026-01-15T14:00:00Z');
  expect(zonedToInstant('2026-07-15', '09:00', 'America/New_York')).toBe('2026-07-15T13:00:00Z');
});

test('rejects malformed input and unknown zones', () => {
  expect(zonedToInstant('12/10/2026', '10:00', 'UTC')).toBeNull();
  expect(zonedToInstant('2026-10-12', '10am', 'UTC')).toBeNull();
  expect(zonedToInstant('2026-10-12', '10:00', 'Mars/Olympus')).toBeNull();
  expect(isValidTimeZone('Asia/Kolkata')).toBe(true);
  expect(isValidTimeZone('Mars/Olympus')).toBe(false);
});

test('round-trips an instant through its zone', () => {
  const iso = '2026-10-12T10:00:00Z';
  expect(instantToZoned(iso, 'Asia/Kolkata')).toEqual({ date: '2026-10-12', time: '15:30' });
  expect(zonedToInstant('2026-10-12', '15:30', 'Asia/Kolkata')).toBe(iso);
});

test('formats with the zone name and falls back to UTC for unknown zones', () => {
  expect(formatInZone('2026-10-12T10:00:00Z', 'Asia/Kolkata')).toContain('15:30 (Asia/Kolkata)');
  expect(formatInZone('2026-10-12T10:00:00Z', 'Nowhere/City')).toContain('(UTC)');
});

test('only http(s) values become links', () => {
  expect(isHttpUrl('https://meet.example.com/a')).toBe(true);
  expect(isHttpUrl('javascript:alert(1)')).toBe(false);
  expect(isHttpUrl('Floor 3, HQ')).toBe(false);
});
