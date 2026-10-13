
import { z } from 'zod';

export const REPORT_DETAILS_MAX = 1000;

/**
 * Validate the raw details string before trimming.
 * JavaScript string.length counts UTF-16 code units.
 */
export const reportFormSchema = z.object({
  reason: z.enum(
    [
      'SPAM',
      'HARASSMENT',
      'SCAM',
      'INAPPROPRIATE',
      'MISINFORMATION',
      'DISCRIMINATION',
      'OTHER',
    ],
    { message: 'Choose a reason' },
  ),

  details: z
    .string()
    .refine(
      (value) => value.length <= REPORT_DETAILS_MAX,
      { message: 'At most 1000 characters' },
    )
    .transform((value) => value.trim()),
});

export type ReportFormInput = z.input<typeof reportFormSchema>;
export type ReportFormOutput = z.output<typeof reportFormSchema>;

export const RESOLVE_REASON_MIN = 10;
export const RESOLVE_REASON_MAX = 500;

/**
 * Count Unicode code points rather than UTF-16 code units.
 * For example, one emoji counts as one character.
 */
export function codePointLength(value: string): number {
  return Array.from(value).length;
}

export const resolveFormSchema = z
  .object({
    action: z.enum(
      [
        'DISMISS',
        'HIDE_CONTENT',
        'REMOVE_CONTENT',
        'WARN_USER',
        'SUSPEND_USER',
      ],
      { message: 'Choose a decision' },
    ),

    reason: z
      .string()
      .trim()
      .refine(
        (value) => codePointLength(value) >= RESOLVE_REASON_MIN,
        { message: 'Explain the decision in at least 10 characters' },
      )
      .refine(
        (value) => codePointLength(value) <= RESOLVE_REASON_MAX,
        { message: 'At most 500 characters' },
      ),

    confirmed: z.boolean(),
  })
  .superRefine((value, context) => {
    const requiresConfirmation = [
      'REMOVE_CONTENT',
      'SUSPEND_USER',
      'HIDE_CONTENT',
    ].includes(value.action);

    if (requiresConfirmation && !value.confirmed) {
      context.addIssue({
        code: 'custom',
        path: ['confirmed'],
        message: 'Confirm that you want to apply this action',
      });
    }
  });

export type ResolveFormInput = z.input<typeof resolveFormSchema>;
export type ResolveFormOutput = z.output<typeof resolveFormSchema>;
