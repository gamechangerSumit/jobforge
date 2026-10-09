import type { SeekerProfile, SeekerProfileInput } from '@/lib/api/seeker';
import type { ProfileFormInput, ProfileFormOutput } from '@/lib/validation/profile';

const s = (v: string | number | null | undefined) => (v === null || v === undefined ? '' : String(v));

export function profileToForm(p: SeekerProfile): ProfileFormInput {
  return {
    headline: s(p.headline), summary: s(p.summary), phone: s(p.phone),
    city: s(p.location?.city), state: s(p.location?.state), country: s(p.location?.country),
    currentTitle: s(p.currentTitle), yearsExperience: s(p.yearsExperience),
    salaryMin: s(p.expectedSalary?.min), salaryMax: s(p.expectedSalary?.max),
    salaryCurrency: s(p.expectedSalary?.currency), salaryPeriod: (p.expectedSalary?.period ?? '') as ProfileFormInput['salaryPeriod'],
    noticePeriodDays: s(p.noticePeriodDays), openToWork: p.openToWork, visibility: p.visibility,
    linkedin: s(p.links?.linkedin), github: s(p.links?.github), portfolio: s(p.links?.portfolio),
  };
}

const anyDefined = (o: Record<string, unknown>) => Object.values(o).some((x) => x !== undefined);

/** Builds the PUT body; groups with no values are omitted so the server clears them. */
export function formToRequest(v: ProfileFormOutput): SeekerProfileInput {
  const location = { city: v.city, state: v.state, country: v.country };
  const expectedSalary = { min: v.salaryMin, max: v.salaryMax, currency: v.salaryCurrency, period: v.salaryPeriod };
  const links = { linkedin: v.linkedin, github: v.github, portfolio: v.portfolio };
  return {
    headline: v.headline, summary: v.summary, phone: v.phone, currentTitle: v.currentTitle,
    yearsExperience: v.yearsExperience, noticePeriodDays: v.noticePeriodDays,
    openToWork: v.openToWork, visibility: v.visibility,
    ...(anyDefined(location) ? { location } : {}),
    ...(anyDefined(expectedSalary) ? { expectedSalary } : {}),
    ...(anyDefined(links) ? { links } : {}),
  };
}

/** Mirrors the server weights (see completeness doc): headline 15, summary 15, location 10, title 10, 3+ skills 20, education 10, experience 10, primary resume 10. */
export interface CompletenessHint { label: string; points: number; done: boolean }
export function completenessHints(p: SeekerProfile, counts: { education: number; experience: number; hasPrimaryResume: boolean }): CompletenessHint[] {
  return [
    { label: 'Add a headline', points: 15, done: Boolean(p.headline?.trim()) },
    { label: 'Write a summary', points: 15, done: Boolean(p.summary?.trim()) },
    { label: 'Set your location', points: 10, done: Boolean(p.location?.city || p.location?.state || p.location?.country) },
    { label: 'Add your current title', points: 10, done: Boolean(p.currentTitle?.trim()) },
    { label: 'List at least 3 skills', points: 20, done: (p.skills?.length ?? 0) >= 3 },
    { label: 'Add education', points: 10, done: counts.education > 0 },
    { label: 'Add work experience', points: 10, done: counts.experience > 0 },
    { label: 'Upload a primary resume', points: 10, done: counts.hasPrimaryResume },
  ];
}
