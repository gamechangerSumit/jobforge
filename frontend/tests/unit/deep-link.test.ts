import { expect, test } from 'vitest';
import { isSafeInternalPath, notificationHref } from '@/lib/notifications/deepLink';

const id = '123e4567-e89b-42d3-a456-426614174000';

test('only same-origin relative paths are safe', () => {
  expect(isSafeInternalPath('/applications/1')).toBe(true);
  expect(isSafeInternalPath('//evil.example')).toBe(false);
  expect(isSafeInternalPath('https://evil.example')).toBe(false);
  expect(isSafeInternalPath('/a\\b')).toBe(false);
  expect(isSafeInternalPath('javascript:alert(1)')).toBe(false);
  expect(isSafeInternalPath(undefined)).toBe(false);
});

test('prefers an explicit safe deepLink', () => {
  expect(notificationHref({ type: 'X', data: { deepLink: '/jobs/abc' } })).toBe('/jobs/abc');
});

test('ignores an unsafe deepLink and falls back to ids', () => {
  expect(notificationHref({ type: 'X', data: { deepLink: '//evil.example', jobId: id } })).toBe(`/jobs/${id}`);
});

test('routes application notifications by role', () => {
  const n = { type: 'APPLICATION_STATUS_CHANGED', data: { applicationId: id } };
  expect(notificationHref(n, 'JOB_SEEKER')).toBe(`/applications/${id}`);
  expect(notificationHref(n, 'RECRUITER')).toBe(`/recruiter/applications/${id}`);
});

test('rejects malformed ids and returns null when nothing matches', () => {
  expect(notificationHref({ type: 'X', data: { jobId: '../../etc' } })).toBeNull();
  expect(notificationHref({ type: 'X', data: null })).toBeNull();
  expect(notificationHref({ type: 'COMPANY_VERIFIED', data: {} }, 'RECRUITER')).toBe('/recruiter/company');
  expect(notificationHref({ type: 'COMPANY_VERIFIED', data: {} }, 'JOB_SEEKER')).toBeNull();
});
