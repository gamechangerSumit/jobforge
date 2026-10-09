import { expect, test } from 'vitest';
import { companyEditSchema } from '@/lib/validation/company';

const base = { name: 'Acme', industry: '', sizeBand: '' as const, websiteUrl: '', hqCity: '', hqState: '', hqCountry: '', foundedYear: '', description: '' };

test('accepts a minimal company and normalizes blanks', () => {
  const r = companyEditSchema.safeParse(base);
  expect(r.success).toBe(true);
  if (r.success) { expect(r.data.industry).toBeUndefined(); expect(r.data.sizeBand).toBeUndefined(); }
});

test('validates URL, country, year and size band', () => {
  expect(companyEditSchema.safeParse({ ...base, websiteUrl: 'ftp://x' }).success).toBe(false);
  expect(companyEditSchema.safeParse({ ...base, hqCountry: 'IND' }).success).toBe(false);
  expect(companyEditSchema.safeParse({ ...base, foundedYear: '1700' }).success).toBe(false);
  expect(companyEditSchema.safeParse({ ...base, sizeBand: 'HUGE' as never }).success).toBe(false);
  const ok = companyEditSchema.safeParse({ ...base, websiteUrl: 'https://acme.example', hqCountry: 'in', foundedYear: '2015', sizeBand: '51_200' });
  expect(ok.success).toBe(true);
  if (ok.success) { expect(ok.data.hqCountry).toBe('IN'); expect(ok.data.foundedYear).toBe(2015); }
});

test('name is required', () => {
  expect(companyEditSchema.safeParse({ ...base, name: '   ' }).success).toBe(false);
});
