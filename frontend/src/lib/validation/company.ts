import { z } from 'zod';

const text = (max: number) => z.string().trim().max(max, `At most ${max} characters`).transform((v) => (v === '' ? undefined : v)).optional();

/** Company edit form (PATCH). Limits mirror the create form and the backend CompanyRequest. */
export const companyEditSchema = z.object({
  name: z.string().trim().min(1, 'Company name is required').max(150),
  industry: text(80),
  sizeBand: z.enum(['', '1_10', '11_50', '51_200', '201_500', '501_1000', '1000_PLUS']).transform((v) => (v === '' ? undefined : v)).optional(),
  websiteUrl: z.string().trim().max(255)
    .refine((v) => v === '' || /^https?:\/\/\S+$/.test(v), 'Must start with http:// or https://')
    .transform((v) => (v === '' ? undefined : v)).optional(),
  hqCity: text(80),
  hqState: text(80),
  hqCountry: z.string().trim().toUpperCase()
    .refine((v) => v === '' || /^[A-Z]{2}$/.test(v), 'Use a 2-letter country code')
    .transform((v) => (v === '' ? undefined : v)).optional(),
  foundedYear: z.string().trim()
    .refine((v) => v === '' || (/^\d{4}$/.test(v) && Number(v) >= 1800 && Number(v) <= new Date().getFullYear()), 'Enter a valid year')
    .transform((v) => (v === '' ? undefined : Number(v))).optional(),
  description: text(5000),
});
export type CompanyEditInput = z.input<typeof companyEditSchema>;
export type CompanyEditOutput = z.output<typeof companyEditSchema>;

export const SIZE_BANDS: Record<string, string> = { '': 'Not set', '1_10': '1–10', '11_50': '11–50', '51_200': '51–200', '201_500': '201–500', '501_1000': '501–1000', '1000_PLUS': '1000+' };
