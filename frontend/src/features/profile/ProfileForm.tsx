'use client';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiClientError } from '@/lib/api/client';
import { fieldErrors } from '@/lib/api/errors';
import { getSeekerProfile, saveSeekerProfile } from '@/lib/api/seeker';
import { profileToForm, formToRequest } from '@/lib/profile/mapping';
import { profileSchema, type ProfileFormInput, type ProfileFormOutput } from '@/lib/validation/profile';

function Field({ id, label, error, hint, children }: { id: string; label: string; error?: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-semibold">{label}</label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
      {error && <p id={`${id}-error`} role="alert" className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}

const SERVER_FIELD_MAP: Record<string, keyof ProfileFormInput> = {
  headline: 'headline', summary: 'summary', phone: 'phone', currentTitle: 'currentTitle', yearsExperience: 'yearsExperience',
  noticePeriodDays: 'noticePeriodDays', 'location.city': 'city', 'location.state': 'state', 'location.country': 'country',
  'expectedSalary.min': 'salaryMin', 'expectedSalary.max': 'salaryMax', 'expectedSalary.currency': 'salaryCurrency',
  'expectedSalary.period': 'salaryPeriod', 'links.linkedin': 'linkedin', 'links.github': 'github', 'links.portfolio': 'portfolio',
};

export function ProfileForm() {
  const qc = useQueryClient();
  const profile = useQuery({ queryKey: ['seeker-profile'], queryFn: getSeekerProfile });
  const [status, setStatus] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const { register, handleSubmit, reset, setError, formState: { errors, isDirty } } =
    useForm<ProfileFormInput, unknown, ProfileFormOutput>({ resolver: zodResolver(profileSchema) });

  // Re-sync from the server only while the form is pristine so background refetches (e.g. after a skill change)
  // never discard unsaved edits.
  useEffect(() => { if (profile.data && !isDirty) reset(profileToForm(profile.data)); }, [profile.data, isDirty, reset]);

  const save = useMutation({
    mutationFn: (v: ProfileFormOutput) => saveSeekerProfile(profile.data!.version, formToRequest(v)),
    onSuccess: (saved) => {
      qc.setQueryData(['seeker-profile'], saved);
      reset(profileToForm(saved));
      setStatus({ kind: 'ok', text: 'Profile saved.' });
    },
    onError: (e) => {
      if (e instanceof ApiClientError && e.error.code === 'STALE_VERSION') {
        setStatus({ kind: 'error', text: 'Your profile changed elsewhere. It was reloaded; please review and save again.' });
        void profile.refetch();
        return;
      }
      const mapped = fieldErrors(e);
      Object.entries(mapped).forEach(([field, message]) => {
        const key = SERVER_FIELD_MAP[field];
        if (key) setError(key, { message });
      });
      setStatus({ kind: 'error', text: e instanceof ApiClientError ? e.error.message : 'Could not save your profile.' });
    },
  });

  if (profile.isLoading) return <Card><p>Loading profile…</p></Card>;
  if (profile.isError || !profile.data) {
    return <Card><p className="text-red-700">Could not load your profile.</p><Button className="mt-3" onClick={() => profile.refetch()}>Retry</Button></Card>;
  }

  const err = (k: keyof ProfileFormInput) => errors[k]?.message as string | undefined;
  return (
    <Card>
      <h2 className="mb-4 text-lg font-bold">About you</h2>
      <form noValidate className="grid gap-4 md:grid-cols-2" onSubmit={handleSubmit((v) => { setStatus(null); save.mutate(v); })}>
        <div className="md:col-span-2"><Field id="headline" label="Headline" error={err('headline')} hint="Up to 120 characters"><Input id="headline" maxLength={120} {...register('headline')} /></Field></div>
        <div className="md:col-span-2"><Field id="summary" label="Summary" error={err('summary')} hint="Up to 2000 characters. Plain text."><textarea id="summary" rows={5} maxLength={2000} className="w-full rounded-lg border border-slate-300 p-3 text-sm" {...register('summary')} /></Field></div>
        <Field id="currentTitle" label="Current title" error={err('currentTitle')}><Input id="currentTitle" maxLength={120} {...register('currentTitle')} /></Field>
        <Field id="yearsExperience" label="Years of experience" error={err('yearsExperience')}><Input id="yearsExperience" inputMode="decimal" {...register('yearsExperience')} /></Field>
        <Field id="phone" label="Phone" error={err('phone')} hint="International format"><Input id="phone" type="tel" placeholder="+919876543210" {...register('phone')} /></Field>
        <Field id="noticePeriodDays" label="Notice period (days)" error={err('noticePeriodDays')}><Input id="noticePeriodDays" inputMode="numeric" {...register('noticePeriodDays')} /></Field>
        <Field id="city" label="City" error={err('city')}><Input id="city" maxLength={80} {...register('city')} /></Field>
        <Field id="state" label="State / region" error={err('state')}><Input id="state" maxLength={80} {...register('state')} /></Field>
        <Field id="country" label="Country (ISO code)" error={err('country')}><Input id="country" maxLength={2} placeholder="IN" {...register('country')} /></Field>
        <div />
        <Field id="salaryMin" label="Expected salary: minimum" error={err('salaryMin')}><Input id="salaryMin" inputMode="numeric" {...register('salaryMin')} /></Field>
        <Field id="salaryMax" label="Expected salary: maximum" error={err('salaryMax')}><Input id="salaryMax" inputMode="numeric" {...register('salaryMax')} /></Field>
        <Field id="salaryCurrency" label="Currency" error={err('salaryCurrency')}><Input id="salaryCurrency" maxLength={3} placeholder="INR" {...register('salaryCurrency')} /></Field>
        <Field id="salaryPeriod" label="Per" error={err('salaryPeriod')}>
          <Select id="salaryPeriod" {...register('salaryPeriod')}><option value="">Not set</option><option value="YEAR">Year</option><option value="MONTH">Month</option><option value="HOUR">Hour</option></Select>
        </Field>
        <Field id="linkedin" label="LinkedIn" error={err('linkedin')}><Input id="linkedin" placeholder="https://" {...register('linkedin')} /></Field>
        <Field id="github" label="GitHub" error={err('github')}><Input id="github" placeholder="https://" {...register('github')} /></Field>
        <div className="md:col-span-2"><Field id="portfolio" label="Portfolio" error={err('portfolio')}><Input id="portfolio" placeholder="https://" {...register('portfolio')} /></Field></div>
        <Field id="visibility" label="Who can see your profile" hint="Recruiters you apply to can always see your application.">
          <Select id="visibility" {...register('visibility')}><option value="RECRUITERS_ONLY">Recruiters only</option><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option></Select>
        </Field>
        <div className="flex items-end"><label className="text-sm"><input type="checkbox" className="mr-2" {...register('openToWork')} />Open to work</label></div>
        <div className="flex items-center gap-3 md:col-span-2">
          <Button disabled={save.isPending || !isDirty}>{save.isPending ? 'Saving…' : 'Save profile'}</Button>
          {status && <p role={status.kind === 'error' ? 'alert' : 'status'} className={status.kind === 'error' ? 'text-sm text-red-600' : 'text-sm text-emerald-700'}>{status.text}</p>}
        </div>
      </form>
    </Card>
  );
}
