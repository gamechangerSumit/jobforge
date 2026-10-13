import { expect, test } from 'vitest';
import { allowedActions, isActive, targetHref, REPORTABLE_TARGETS } from '@/lib/reports/rules';

const open = { status: 'OPEN' as const, targetAvailable: true };

test('decision matrix mirrors the backend (Session 3A)', () => {
  expect(allowedActions({ ...open, targetType: 'JOB' })).toEqual(['DISMISS', 'WARN_USER', 'REMOVE_CONTENT']);
  expect(allowedActions({ ...open, targetType: 'COMPANY' })).toEqual(['DISMISS', 'WARN_USER']);
  expect(allowedActions({ ...open, targetType: 'USER' })).toEqual(['DISMISS', 'WARN_USER', 'SUSPEND_USER']);
  expect(allowedActions({ ...open, targetType: 'POST' })).toEqual(['DISMISS']);
  expect(allowedActions({ ...open, targetType: 'COMMENT' })).toEqual(['DISMISS']);
});

test('HIDE_CONTENT is never offered (unsupported for every target)', () => {
  for (const t of ['JOB', 'COMPANY', 'USER', 'POST', 'COMMENT'] as const) {
    expect(allowedActions({ ...open, targetType: t })).not.toContain('HIDE_CONTENT');
  }
});

test('closed reports offer nothing and vanished targets only allow dismissal', () => {
  for (const status of ['RESOLVED', 'DISMISSED'] as const) {
    expect(allowedActions({ status, targetType: 'JOB', targetAvailable: true })).toEqual([]);
  }
  expect(allowedActions({ status: 'REVIEWING', targetType: 'USER', targetAvailable: false })).toEqual(['DISMISS']);
  expect(isActive('OPEN') && isActive('REVIEWING') && !isActive('RESOLVED') && !isActive('DISMISSED')).toBe(true);
});

test('only jobs, companies and users are reportable until the community module exists', () => {
  expect(REPORTABLE_TARGETS).toEqual(['JOB', 'COMPANY', 'USER']);
});

test('context links exist only for available targets with a page', () => {
  expect(targetHref('JOB', 'j1', true)).toBe('/jobs/j1');
  expect(targetHref('COMPANY', 'c1', true)).toBe('/admin/companies/c1');
  expect(targetHref('USER', 'u1', true)).toBe('/users/u1');
  expect(targetHref('JOB', 'j1', false)).toBeNull();
  expect(targetHref('POST', 'p1', true)).toBeNull();
});
