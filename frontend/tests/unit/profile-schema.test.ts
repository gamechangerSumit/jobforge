import { describe, expect, test } from 'vitest';
import { profileSchema } from '@/lib/validation/profile';
import { completenessHints, formToRequest, profileToForm } from '@/lib/profile/mapping';
import type { SeekerProfile } from '@/lib/api/seeker';

const base = {
  headline: '', summary: '', phone: '', city: '', state: '', country: '', currentTitle: '', yearsExperience: '',
  salaryMin: '', salaryMax: '', salaryCurrency: '', salaryPeriod: '' as const, noticePeriodDays: '',
  openToWork: true, visibility: 'RECRUITERS_ONLY' as const, linkedin: '', github: '', portfolio: '',
};

describe('profileSchema', () => {
  test('turns empty strings into undefined', () => {
    const r = profileSchema.safeParse(base);
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.headline).toBeUndefined();
      expect(r.data.yearsExperience).toBeUndefined();
      expect(r.data.salaryPeriod).toBeUndefined();
    }
  });

  test('parses numbers and uppercases codes', () => {
    const r = profileSchema.safeParse({ ...base, yearsExperience: '4.5', noticePeriodDays: '30', country: 'in', salaryMin: '100', salaryMax: '200', salaryCurrency: 'inr', salaryPeriod: 'YEAR' });
    expect(r.success).toBe(true);
    if (r.success) {
      expect(r.data.yearsExperience).toBe(4.5);
      expect(r.data.noticePeriodDays).toBe(30);
      expect(r.data.country).toBe('IN');
      expect(r.data.salaryCurrency).toBe('INR');
    }
  });

  test.each([
    ['phone', { phone: '9876543210' }],
    ['phone', { phone: '+0123' }],
    ['linkedin', { linkedin: 'http://insecure.example' }],
    ['country', { country: 'IND' }],
    ['yearsExperience', { yearsExperience: '61' }],
    ['yearsExperience', { yearsExperience: '1.55' }],
    ['noticePeriodDays', { noticePeriodDays: '366' }],
    ['headline', { headline: 'x'.repeat(121) }],
    ['summary', { summary: 'x'.repeat(2001) }],
  ])('rejects invalid %s', (field, patch) => {
    const r = profileSchema.safeParse({ ...base, ...patch });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.issues.some((i) => i.path[0] === field)).toBe(true);
  });

  test('salary max must not be below min and needs currency and period', () => {
    const order = profileSchema.safeParse({ ...base, salaryMin: '200', salaryMax: '100', salaryCurrency: 'INR', salaryPeriod: 'YEAR' });
    expect(order.success).toBe(false);
    const missing = profileSchema.safeParse({ ...base, salaryMin: '100' });
    expect(missing.success).toBe(false);
    if (!missing.success) expect(missing.error.issues.map((i) => i.path[0])).toEqual(expect.arrayContaining(['salaryCurrency', 'salaryPeriod']));
  });
});

describe('mapping', () => {
  const profile: SeekerProfile = {
    id: 'p', userId: 'u', headline: 'Dev', summary: null, phone: null, location: { city: 'Nagpur', state: null, country: 'IN' },
    currentTitle: null, yearsExperience: 3, expectedSalary: null, noticePeriodDays: null, openToWork: true, visibility: 'PUBLIC',
    links: null, completenessScore: 30, skills: [{ skill: 'java', proficiency: 'ADVANCED' }], version: 2, updatedAt: '2026-01-01T00:00:00Z',
  };

  test('profileToForm uses empty strings for missing values', () => {
    const f = profileToForm(profile);
    expect(f.headline).toBe('Dev');
    expect(f.summary).toBe('');
    expect(f.yearsExperience).toBe('3');
    expect(f.state).toBe('');
  });

  test('formToRequest omits empty groups so the server clears them', () => {
    const parsed = profileSchema.parse(profileToForm(profile));
    const req = formToRequest(parsed);
    expect(req.location).toEqual({ city: 'Nagpur', state: undefined, country: 'IN' });
    expect(req.expectedSalary).toBeUndefined();
    expect(req.links).toBeUndefined();
    expect(req.visibility).toBe('PUBLIC');
  });

  test('completeness hints reflect server weights and sum to 100', () => {
    const hints = completenessHints(profile, { education: 0, experience: 1, hasPrimaryResume: false });
    expect(hints.reduce((n, h) => n + h.points, 0)).toBe(100);
    expect(hints.find((h) => h.label.includes('headline'))?.done).toBe(true);
    expect(hints.find((h) => h.label.includes('3 skills'))?.done).toBe(false);
    expect(hints.find((h) => h.label.includes('resume'))?.done).toBe(false);
  });
});
