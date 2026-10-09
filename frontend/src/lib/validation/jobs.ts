import { z } from 'zod';

/** Empty form inputs arrive as '' - treat them as "not provided" instead of coercing them to 0. */
const blankToUndefined = (value: unknown) =>
  value === '' || value === null || (typeof value === 'number' && Number.isNaN(value)) ? undefined : value;

const wholeMoney = z.preprocess(blankToUndefined, z.coerce.number().int().nonnegative().optional());


export const jobSchema = z
  .object({
    title: z
      .string()
      .min(3)
      .max(120),

    description: z
      .string()
      .min(50)
      .max(10_000),

    requirements: z
      .string()
      .max(5_000),

    benefits: z
      .string()
      .max(5_000),

    employmentType: z.enum([
      'FULL_TIME',
      'PART_TIME',
      'CONTRACT',
      'INTERNSHIP',
      'FREELANCE',
    ]),

    workMode: z.enum([
      'ONSITE',
      'HYBRID',
      'REMOTE',
    ]),

    experienceLevel: z.enum([
      'INTERN',
      'ENTRY',
      'MID',
      'SENIOR',
      'LEAD',
      'EXECUTIVE',
    ]),

    city: z
      .string()
      .optional(),

    state: z
      .string()
      .optional(),

    // Backend: ^[A-Z]{2}$ (ISO 3166-1 alpha-2). Blank is allowed for drafts.
    country: z
      .string()
      .trim()
      .toUpperCase()
      .regex(/^([A-Z]{2})?$/, 'Use a 2-letter country code, e.g. IN')
      .optional(),

    salaryMin: wholeMoney,

    salaryMax: wholeMoney,

    // Backend: ^[A-Z]{3}$ (ISO 4217). Blank is allowed when no salary is given.
    currency: z
      .string()
      .trim()
      .toUpperCase()
      .regex(/^([A-Z]{3})?$/, 'Use a 3-letter currency code, e.g. INR')
      .optional(),

    salaryPeriod: z.enum([
      'YEAR',
      'MONTH',
      'HOUR',
    ]).optional(),

    salaryVisible:
      z.boolean(),

    openings: z
      .coerce
      .number()
      .int()
      .positive(),

    expiresAt: z
      .string()
      .optional(),

    skills: z
      .string()
      .min(1, 'Add at least one skill')
      .refine(
        (value) => value.split(',').map((x) => x.trim()).filter(Boolean).length <= 20,
        'At most 20 skills',
      )
      .refine(
        (value) => value.split(',').every((x) => x.trim().length <= 50),
        'Each skill must be at most 50 characters',
      ),

    /**
     * Optional identifier used when
     * a job was generated/assisted by
     * the AI Job Studio.
     */
    aiRequestId: z
      .string()
      .optional(),
  })
  .superRefine((value, ctx) => {
    if (
      value.salaryMin !== undefined &&
      value.salaryMax !== undefined &&
      value.salaryMin > value.salaryMax
    ) {
      ctx.addIssue({
        code: 'custom',
        path: ['salaryMax'],
        message:
          'Maximum salary must be at least minimum salary.',
      });
    }

    if (
      value.workMode !== 'REMOTE' &&
      !value.city?.trim()
    ) {
      ctx.addIssue({
        code: 'custom',
        path: ['city'],
        message:
          'City is required unless the role is remote.',
      });
    }
  });

export type JobFormValues = z.output<typeof jobSchema>;

const clean = (value?: string) => (value && value.trim() ? value.trim() : undefined);

/** Builds the API body: blank optional values are omitted so they cannot trip backend pattern checks. */
export function buildJobPayload(v: JobFormValues) {
  const city = clean(v.city);
  const state = clean(v.state);
  const country = clean(v.country);
  const hasLocation = v.workMode !== 'REMOTE' && (city || state || country);
  const hasSalary = v.salaryMin !== undefined || v.salaryMax !== undefined;
  return {
    title: v.title.trim(),
    description: v.description,
    requirements: clean(v.requirements),
    benefits: clean(v.benefits),
    employmentType: v.employmentType,
    workMode: v.workMode,
    experienceLevel: v.experienceLevel,
    location: hasLocation ? { city, state, country } : undefined,
    salary: hasSalary
      ? { min: v.salaryMin, max: v.salaryMax, currency: clean(v.currency) ?? 'INR', period: v.salaryPeriod ?? 'YEAR' }
      : undefined,
    salaryVisible: v.salaryVisible,
    openings: v.openings,
    expiresAt: clean(v.expiresAt),
    aiRequestId: clean(v.aiRequestId),
    skills: v.skills
      .split(',')
      .map((name) => name.trim())
      .filter(Boolean)
      .map((name) => ({ name, required: true })),
  };
}

const pad = (n: number) => String(n).padStart(2, '0');

/** API Instant (UTC ISO string) -> value for <input type="datetime-local"> in the viewer's local time. */
export function isoToLocalInput(iso?: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** <input type="datetime-local"> value -> UTC ISO Instant the backend can parse (it rejects zone-less values). */
export function localInputToIso(value?: string): string | undefined {
  if (!value || !value.trim()) return undefined;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

export const applicationSchema =
  z.object({
    resumeId: z
      .string()
      .min(1),

    coverLetter: z
      .string()
      .max(5_000)
      .optional(),
  });