'use client';
import { useId, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { errorMessage, fieldErrors } from '@/lib/api/errors';
import { defaultTimeZone, instantToZoned } from '@/lib/interviews/time';
import {
  buildSchedulePayload, buildUpdatePayload, formDefaultsFor, interviewFormSchema, type InterviewFormInput, type InterviewFormOutput,
} from '@/lib/validation/interviews';
import { useScheduleInterview, useUpdateInterview } from './hooks';
import type { Interview } from '@/types/interviews';

const tomorrow = () => new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);

type Props =
  | { mode: 'schedule'; applicationId: string; onDone: () => void; onCancel: () => void }
  | { mode: 'reschedule'; interview: Interview; onDone: () => void; onCancel: () => void };

/** One form for scheduling and rescheduling. The recruiter enters wall-clock time in the chosen IANA zone. */
export function InterviewForm(props: Props) {
  const id = useId();
  const interview = props.mode === 'reschedule' ? props.interview : null;
  const schedule = useScheduleInterview(props.mode === 'schedule' ? props.applicationId : '');
  const update = useUpdateInterview(interview?.id ?? '');
  const [serverError, setServerError] = useState('');
  const form = useForm<InterviewFormInput, unknown, InterviewFormOutput>({
    resolver: zodResolver(interviewFormSchema),
    defaultValues: interview
      ? formDefaultsFor(interview, instantToZoned)
      : { type: 'VIDEO', date: tomorrow(), time: '10:00', durationMinutes: '45', timezone: defaultTimeZone(), locationOrLink: '', notes: '' },
  });
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = form;
  const busy = isSubmitting || schedule.isPending || update.isPending;

  const submit = handleSubmit(async (values) => {
    setServerError('');
    try {
      if (interview) {
        const patch = buildUpdatePayload(values, interview);
        if (Object.keys(patch).length > 0) await update.mutateAsync(patch);
      } else {
        await schedule.mutateAsync(buildSchedulePayload(values));
      }
      props.onDone();
    } catch (e) {
      const fields = fieldErrors(e);
      const map: Record<string, keyof InterviewFormInput> = { scheduledAt: 'date', durationMinutes: 'durationMinutes', timezone: 'timezone', locationOrLink: 'locationOrLink', notes: 'notes', type: 'type' };
      let mapped = false;
      Object.entries(fields).forEach(([field, message]) => { const target = map[field]; if (target) { setError(target, { message }); mapped = true; } });
      if (!mapped) setServerError(errorMessage(e, 'Unable to save the interview. Please try again.'));
    }
  });

  const label = 'block text-sm font-semibold';
  const err = (msg?: string) => (msg ? <p role="alert" className="mt-1 text-xs text-red-600">{msg}</p> : null);
  return (
    <form onSubmit={(e) => void submit(e)} noValidate aria-label={interview ? 'Reschedule interview' : 'Schedule interview'} className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
      {interview && <p className="text-xs text-slate-600">Changing the date, time, duration, place or type resets the candidate&apos;s answer and asks them to confirm again.</p>}
      <div className="grid gap-4 md:grid-cols-2">
        <div><label htmlFor={`${id}-type`} className={label}>Type</label>
          <Select id={`${id}-type`} {...register('type')}><option value="VIDEO">Video call</option><option value="PHONE">Phone call</option><option value="ONSITE">On site</option></Select>{err(errors.type?.message)}</div>
        <div><label htmlFor={`${id}-duration`} className={label}>Duration (minutes)</label>
          <Input id={`${id}-duration`} inputMode="numeric" {...register('durationMinutes')} />{err(errors.durationMinutes?.message)}</div>
        <div><label htmlFor={`${id}-date`} className={label}>Date</label>
          <Input id={`${id}-date`} type="date" {...register('date')} />{err(errors.date?.message)}</div>
        <div><label htmlFor={`${id}-time`} className={label}>Time</label>
          <Input id={`${id}-time`} type="time" {...register('time')} />{err(errors.time?.message)}</div>
        <div className="md:col-span-2"><label htmlFor={`${id}-tz`} className={label}>Time zone</label>
          <Input id={`${id}-tz`} placeholder="Asia/Kolkata" {...register('timezone')} />{err(errors.timezone?.message)}</div>
        <div className="md:col-span-2"><label htmlFor={`${id}-loc`} className={label}>Meeting link or address</label>
          <Input id={`${id}-loc`} placeholder="https://meet.example.com/… or office address" {...register('locationOrLink')} />{err(errors.locationOrLink?.message)}</div>
        <div className="md:col-span-2"><label htmlFor={`${id}-notes`} className={label}>Internal notes <span className="font-normal text-slate-500">(only your team sees these)</span></label>
          <textarea id={`${id}-notes`} rows={3} className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-200" {...register('notes')} />{err(errors.notes?.message)}</div>
      </div>
      {serverError && <p role="alert" className="text-sm text-red-600">{serverError}</p>}
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>{busy ? 'Saving…' : interview ? 'Save changes' : 'Schedule interview'}</Button>
        <button type="button" onClick={props.onCancel} className="rounded-lg px-4 py-2 text-sm font-semibold ring-1 ring-slate-300">Close</button>
      </div>
    </form>
  );
}
