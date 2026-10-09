import { z } from 'zod';
import { isValidTimeZone, zonedToInstant } from '@/lib/interviews/time';
import type { Interview, ScheduleInterviewRequest, UpdateInterviewRequest } from '@/types/interviews';

/** Limits mirror the backend (API_CONTRACT §12.7, DATABASE_SCHEMA §4.5): duration 15–480, location/link ≤500, notes ≤2000. */
export const interviewFormSchema = z.object({
  type: z.enum(['PHONE', 'VIDEO', 'ONSITE']),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Choose a date'),
  time: z.string().regex(/^\d{2}:\d{2}$/, 'Choose a time'),
  durationMinutes: z.string().trim().regex(/^\d+$/, 'Enter whole minutes')
    .refine((v) => Number(v) >= 15 && Number(v) <= 480, 'Between 15 and 480 minutes'),
  timezone: z.string().trim().min(1, 'Choose a time zone').max(50).refine(isValidTimeZone, 'Use an IANA time zone such as Asia/Kolkata'),
  locationOrLink: z.string().trim().min(1, 'Add a meeting link or address').max(500, 'At most 500 characters')
    .refine((v) => !/^\s*(javascript|data|vbscript):/i.test(v), 'Use an address or an http(s) link'),
  notes: z.string().trim().max(2000, 'At most 2000 characters'),
}).superRefine((v, ctx) => {
  const instant = zonedToInstant(v.date, v.time, v.timezone);
  if (!instant) return;
  const at = new Date(instant).getTime();
  if (at <= Date.now()) ctx.addIssue({ code: 'custom', path: ['date'], message: 'Choose a time in the future' });
  else if (at > Date.now() + 365 * 86_400_000) ctx.addIssue({ code: 'custom', path: ['date'], message: 'At most one year ahead' });
});
export type InterviewFormInput = z.input<typeof interviewFormSchema>;
export type InterviewFormOutput = z.output<typeof interviewFormSchema>;

/** POST body. `notes` is omitted when blank. */
export function buildSchedulePayload(v: InterviewFormOutput): ScheduleInterviewRequest {
  const scheduledAt = zonedToInstant(v.date, v.time, v.timezone);
  if (!scheduledAt) throw new Error('Invalid date, time or time zone');
  return {
    type: v.type, scheduledAt, durationMinutes: Number(v.durationMinutes), timezone: v.timezone,
    locationOrLink: v.locationOrLink, ...(v.notes ? { notes: v.notes } : {}),
  };
}

/** PATCH body: only fields that differ from the stored interview (blank notes clear them). */
export function buildUpdatePayload(v: InterviewFormOutput, current: Interview): UpdateInterviewRequest {
  const next = buildSchedulePayload(v);
  const patch: UpdateInterviewRequest = {};
  if (next.type !== current.type) patch.type = next.type;
  if (new Date(next.scheduledAt).getTime() !== new Date(current.scheduledAt).getTime()) patch.scheduledAt = next.scheduledAt;
  if (next.durationMinutes !== current.durationMinutes) patch.durationMinutes = next.durationMinutes;
  if (next.timezone !== current.timezone) patch.timezone = next.timezone;
  if (next.locationOrLink !== (current.locationOrLink ?? '')) patch.locationOrLink = next.locationOrLink;
  if ((next.notes ?? '') !== (current.notes ?? '')) patch.notes = next.notes ?? '';
  return patch;
}

/** Form values for editing an existing interview, shown in the interview's own zone. */
export function formDefaultsFor(interview: Interview, instantToZoned: (iso: string, tz: string) => { date: string; time: string }): InterviewFormInput {
  const zone = isValidTimeZone(interview.timezone) ? interview.timezone : 'UTC';
  const { date, time } = instantToZoned(interview.scheduledAt, zone);
  return {
    type: interview.type, date, time, durationMinutes: String(interview.durationMinutes), timezone: zone,
    locationOrLink: interview.locationOrLink ?? '', notes: interview.notes ?? '',
  };
}
