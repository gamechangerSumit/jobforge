import { z } from 'zod';

/** Mirrors API_CONTRACT section 10 and SeekerProfileRequest. Empty inputs become undefined (PUT clears omitted fields). */
const text = (max: number) => z.string().trim().max(max, `At most ${max} characters`).transform((v) => (v === '' ? undefined : v)).optional();
const num = (min: number, max: number, message: string) =>
  z.string().trim()
    .refine((v) => v === '' || (/^\d+(\.\d)?$/.test(v) && Number(v) >= min && Number(v) <= max), message)
    .transform((v) => (v === '' ? undefined : Number(v))).optional();
const whole = (min: number, max: number, message: string) =>
  z.string().trim()
    .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= min && Number(v) <= max), message)
    .transform((v) => (v === '' ? undefined : Number(v))).optional();
const httpsUrl = z.string().trim().max(255, 'At most 255 characters')
  .refine((v) => v === '' || /^https:\/\/\S+$/.test(v), 'Must start with https://')
  .transform((v) => (v === '' ? undefined : v)).optional();

export const profileSchema = z.object({
  headline: text(120),
  summary: text(2000),
  phone: z.string().trim().refine((v) => v === '' || /^\+[1-9]\d{1,14}$/.test(v), 'Use international format, e.g. +919876543210')
    .transform((v) => (v === '' ? undefined : v)).optional(),
  city: text(80),
  state: text(80),
  country: z.string().trim().toUpperCase().refine((v) => v === '' || /^[A-Z]{2}$/.test(v), 'Use a 2-letter country code')
    .transform((v) => (v === '' ? undefined : v)).optional(),
  currentTitle: text(120),
  yearsExperience: num(0, 60, 'Enter 0–60 with at most one decimal'),
  salaryMin: whole(0, 999_999_999_999, 'Enter a whole number'),
  salaryMax: whole(0, 999_999_999_999, 'Enter a whole number'),
  salaryCurrency: z.string().trim().toUpperCase().refine((v) => v === '' || /^[A-Z]{3}$/.test(v), 'Use a 3-letter currency code')
    .transform((v) => (v === '' ? undefined : v)).optional(),
  salaryPeriod: z.enum(['', 'YEAR', 'MONTH', 'HOUR']).transform((v) => (v === '' ? undefined : v)).optional(),
  noticePeriodDays: whole(0, 365, 'Enter 0–365 days'),
  openToWork: z.boolean(),
  visibility: z.enum(['PUBLIC', 'RECRUITERS_ONLY', 'PRIVATE']),
  linkedin: httpsUrl,
  github: httpsUrl,
  portfolio: httpsUrl,
}).superRefine((v, ctx) => {
  if (v.salaryMin !== undefined && v.salaryMax !== undefined && v.salaryMax < v.salaryMin) {
    ctx.addIssue({ code: 'custom', path: ['salaryMax'], message: 'Maximum must be at least the minimum' });
  }
  const hasSalary = v.salaryMin !== undefined || v.salaryMax !== undefined;
  if (hasSalary && !v.salaryCurrency) ctx.addIssue({ code: 'custom', path: ['salaryCurrency'], message: 'Currency is required with a salary' });
  if (hasSalary && !v.salaryPeriod) ctx.addIssue({ code: 'custom', path: ['salaryPeriod'], message: 'Period is required with a salary' });
});

export type ProfileFormInput = z.input<typeof profileSchema>;
export type ProfileFormOutput = z.output<typeof profileSchema>;
