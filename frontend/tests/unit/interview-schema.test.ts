import { expect, test } from 'vitest';
import { buildSchedulePayload, buildUpdatePayload, interviewFormSchema } from '@/lib/validation/interviews';
import type { Interview } from '@/types/interviews';

const future = new Date(Date.now() + 3 * 86_400_000).toISOString().slice(0, 10);
const valid = { type: 'VIDEO', date: future, time: '10:00', durationMinutes: '45', timezone: 'UTC', locationOrLink: 'https://meet.example.com/x', notes: '' };

test('accepts a valid schedule and builds the contract payload', () => {
  const parsed = interviewFormSchema.parse(valid);
  expect(buildSchedulePayload(parsed)).toEqual({
    type: 'VIDEO', scheduledAt: `${future}T10:00:00Z`, durationMinutes: 45, timezone: 'UTC', locationOrLink: 'https://meet.example.com/x',
  });
  expect(buildSchedulePayload({ ...parsed, notes: 'Panel: Priya' }).notes).toBe('Panel: Priya');
});

test.each([
  ['past date', { date: '2020-01-01' }, 'date'],
  ['more than a year ahead', { date: '2099-01-01' }, 'date'],
  ['short duration', { durationMinutes: '10' }, 'durationMinutes'],
  ['long duration', { durationMinutes: '500' }, 'durationMinutes'],
  ['non-numeric duration', { durationMinutes: 'abc' }, 'durationMinutes'],
  ['unknown zone', { timezone: 'Mars/Olympus' }, 'timezone'],
  ['blank location', { locationOrLink: '  ' }, 'locationOrLink'],
  ['scripting scheme', { locationOrLink: 'javascript:alert(1)' }, 'locationOrLink'],
  ['location too long', { locationOrLink: 'x'.repeat(501) }, 'locationOrLink'],
  ['notes too long', { notes: 'x'.repeat(2001) }, 'notes'],
  ['bad type', { type: 'CARRIER_PIGEON' }, 'type'],
])('rejects %s', (_name, patch, field) => {
  const result = interviewFormSchema.safeParse({ ...valid, ...patch });
  expect(result.success).toBe(false);
  expect(result.error?.issues.some((i) => i.path[0] === field)).toBe(true);
});

test('update payload contains only changed fields and clears notes with a blank string', () => {
  const current = {
    id: 'i1', applicationId: 'a1', type: 'VIDEO', scheduledAt: `${future}T10:00:00Z`, durationMinutes: 45, timezone: 'UTC',
    locationOrLink: 'https://meet.example.com/x', status: 'CONFIRMED', seekerResponse: 'CONFIRMED', notes: 'old', createdAt: '', updatedAt: '',
  } as Interview;
  const same = interviewFormSchema.parse({ ...valid, notes: 'old' });
  expect(buildUpdatePayload(same, current)).toEqual({});
  expect(buildUpdatePayload(interviewFormSchema.parse({ ...valid, notes: 'old', time: '11:30' }), current)).toEqual({ scheduledAt: `${future}T11:30:00Z` });
  expect(buildUpdatePayload(interviewFormSchema.parse({ ...valid, notes: '' }), current)).toEqual({ notes: '' });
});

test('an interview stored with seconds is not reported as rescheduled when the form is saved unchanged', () => {
  const current = {
    id: 'i1', applicationId: 'a1', type: 'VIDEO', scheduledAt: `${future}T10:00:42.500Z`, durationMinutes: 45, timezone: 'UTC',
    locationOrLink: 'https://meet.example.com/x', status: 'SCHEDULED', seekerResponse: 'PENDING', createdAt: '', updatedAt: '',
  } as Interview;
  expect(buildUpdatePayload(interviewFormSchema.parse(valid), current)).toEqual({});
});
