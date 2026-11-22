'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { ApiClientError } from '@/lib/api/client';
import { getAiRequestDetail } from '@/lib/api/ai';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { buildJobPayload, jobSchema } from '@/lib/validation/jobs';
import { createJob } from '@/lib/api/jobs';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Card } from '@/components/ui/Card';

type FormInput = z.input<typeof jobSchema>;
const FORM_FIELDS = ['title', 'description', 'requirements', 'benefits', 'employmentType', 'workMode', 'experienceLevel', 'city', 'state', 'country', 'salaryMin', 'salaryMax', 'currency', 'salaryPeriod', 'openings', 'skills', 'aiRequestId'] as const;
type FormField = (typeof FORM_FIELDS)[number];
type FormOutput = z.output<typeof jobSchema>;

/** Maps backend field paths (location.country, salary.currency, skills[0].name ...) to form field names. */
function serverFieldToForm(field?: string): FormField | undefined {
  if (!field) return undefined;
  const map: Record<string, FormField> = { 'location.city': 'city', 'location.state': 'state', 'location.country': 'country', 'salary.min': 'salaryMin', 'salary.max': 'salaryMax', 'salary.currency': 'currency', 'salary.period': 'salaryPeriod' };
  if (map[field]) return map[field];
  if (field.startsWith('skills')) return 'skills';
  return (FORM_FIELDS as readonly string[]).includes(field) ? (field as FormField) : undefined;
}

export default function NewJobPage() {
  return <Suspense fallback={<div className="jf-container py-16 text-sm text-slate-500">Preparing job editor…</div>}><NewJobForm /></Suspense>;
}

function NewJobForm() {
  const router = useRouter();
  const search = useSearchParams();
  const aiRequestId = search.get('aiRequestId');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const aiRequest = useQuery({
    queryKey: ['ai-request-detail', aiRequestId],
    queryFn: () => getAiRequestDetail(aiRequestId!),
    enabled: Boolean(aiRequestId),
    retry: 1,
  });
  const { register, handleSubmit, setError, setValue, formState: { errors, isSubmitting } } = useForm<FormInput, unknown, FormOutput>({
    resolver: zodResolver(jobSchema),
    defaultValues: { salaryVisible: true, openings: 1, requirements: '', benefits: '', country: 'IN', currency: 'INR', salaryPeriod: 'YEAR' },
  });

  useEffect(() => {
    const request = aiRequest.data;
    if (!request || request.operation !== 'JOB_GENERATION' || request.status !== 'SUCCEEDED' || !request.output) return;
    const draft = request.output;
    const description = [
      draft.summary,
      '',
      '## Responsibilities',
      ...draft.responsibilities.map((item) => '- ' + item),
      '',
      '## Requirements',
      ...draft.requirements.map((item) => '- ' + item),
      ...(draft.niceToHave.length ? ['', '## Nice to have', ...draft.niceToHave.map((item) => '- ' + item)] : []),
      ...(draft.benefits.length ? ['', '## Benefits', ...draft.benefits.map((item) => '- ' + item)] : []),
    ].join('\n');
    setValue('title', draft.title);
    setValue('description', description.slice(0, 10000));
    setValue('requirements', draft.requirements.map((item) => '- ' + item).join('\n').slice(0, 5000));
    setValue('benefits', draft.benefits.map((item) => '- ' + item).join('\n').slice(0, 5000));
    setValue('employmentType', draft.employmentType as FormInput['employmentType']);
    setValue('workMode', draft.workMode as FormInput['workMode']);
    setValue('experienceLevel', draft.experienceLevel as FormInput['experienceLevel']);
    setValue('skills', draft.skills.map((skill) => skill.name).join(', '));
    setValue('aiRequestId', request.id);
  }, [aiRequest.data, setValue]);

  return <div className="min-h-screen bg-[#f6f7fb] py-8 sm:py-12">
    <div className="jf-container max-w-4xl">
      <Link href="/recruiter/dashboard" className="text-sm font-semibold text-violet-700 hover:text-violet-900">← Recruiter workspace</Link>
      <div className="mb-6 mt-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div><p className="text-xs font-bold uppercase tracking-[.16em] text-violet-600">Job management</p><h1 className="mt-2 text-3xl font-black tracking-[-.05em] text-slate-950">Create a job</h1><p className="mt-2 text-sm text-slate-600">Review the details carefully before saving your draft.</p></div>
        {aiRequestId && <span className="inline-flex w-fit items-center gap-2 rounded-full border border-violet-200 bg-violet-50 px-3 py-1.5 text-xs font-bold text-violet-800">✦ AI-assisted draft</span>}
      </div>

      {aiRequestId && <div className="mb-5 rounded-xl border border-violet-200 bg-violet-50 p-4 text-sm text-violet-900">
        {aiRequest.isLoading ? 'Loading your AI-generated draft…' : aiRequest.isError ? <span role="alert">Could not load this AI draft. It may not belong to your account or may no longer be available. <button className="font-bold underline" onClick={() => void aiRequest.refetch()}>Try again</button></span> : aiRequest.data?.status !== 'SUCCEEDED' ? 'This AI request has not completed successfully. Generate a new draft before continuing.' : 'Your generated content has been loaded into the editor. Review and adjust every field before saving.'}
      </div>}

      <Card>
        <form onSubmit={handleSubmit(async (values) => {
          setSubmitError(null);
          try {
            const job = await createJob(buildJobPayload(values));
            router.push(`/recruiter/jobs/${job.id}/applications`);
          } catch (error) {
            if (!(error instanceof ApiClientError)) { setSubmitError('Could not create the job. Please retry.'); return; }
            const details = error.error.details ?? [];
            details.forEach((detail) => { const name = serverFieldToForm(detail.field); if (name) setError(name, { type: 'server', message: detail.message }); });
            setSubmitError(details.length ? `${error.error.message} ${details.map((detail) => `${detail.field ?? 'request'}: ${detail.message}`).join('; ')}` : error.error.message);
          }
        })} className="grid gap-5 md:grid-cols-2">
          <div className="md:col-span-2"><h2 className="text-lg font-extrabold">Role details</h2><p className="mt-1 text-xs text-slate-500">Fields marked with an asterisk are required.</p></div>
          <Field label="Job title *" error={errors.title?.message}><Input {...register('title')} /></Field>
          <Field label="Employment type *" error={errors.employmentType?.message}><Select {...register('employmentType')}><option value="FULL_TIME">Full time</option><option value="PART_TIME">Part time</option><option value="CONTRACT">Contract</option><option value="INTERNSHIP">Internship</option><option value="FREELANCE">Freelance</option></Select></Field>
          <Field label="Work mode *" error={errors.workMode?.message}><Select {...register('workMode')}><option value="ONSITE">On-site</option><option value="HYBRID">Hybrid</option><option value="REMOTE">Remote</option></Select></Field>
          <Field label="Experience level *" error={errors.experienceLevel?.message}><Select {...register('experienceLevel')}><option value="INTERN">Intern</option><option value="ENTRY">Entry</option><option value="MID">Mid</option><option value="SENIOR">Senior</option><option value="LEAD">Lead</option><option value="EXECUTIVE">Executive</option></Select></Field>
          <Field label="City" error={errors.city?.message}><Input {...register('city')} /></Field>
          <Field label="State" error={errors.state?.message}><Input {...register('state')} /></Field>
          <Field label="Country (2 letters, e.g. IN)" error={errors.country?.message}><Input maxLength={2} {...register('country')} /></Field>
          <Field label="Openings *" error={errors.openings?.message}><Input type="number" {...register('openings')} /></Field>
          <div className="md:col-span-2"><Field label="Skills (comma separated) *" error={errors.skills?.message}><Input {...register('skills')} /></Field></div>
          <div className="md:col-span-2"><Field label="Description (Markdown) *" error={errors.description?.message}><textarea rows={12} {...register('description')} className="jf-input min-h-64 resize-y" /></Field></div>
          <div className="md:col-span-2"><Field label="Requirements (Markdown)" error={errors.requirements?.message}><textarea rows={6} {...register('requirements')} className="jf-input resize-y" /></Field></div>
          <div className="md:col-span-2"><Field label="Benefits (Markdown)" error={errors.benefits?.message}><textarea rows={5} {...register('benefits')} className="jf-input resize-y" /></Field></div>
          <div className="md:col-span-2"><h2 className="border-t border-slate-100 pt-5 text-lg font-extrabold">Compensation (optional)</h2></div>
          <Field label="Salary minimum" error={errors.salaryMin?.message}><Input type="number" {...register('salaryMin')} /></Field>
          <Field label="Salary maximum" error={errors.salaryMax?.message}><Input type="number" {...register('salaryMax')} /></Field>
          <Field label="Currency (3 letters, e.g. INR)" error={errors.currency?.message}><Input maxLength={3} {...register('currency')} /></Field>
          <Field label="Salary period" error={errors.salaryPeriod?.message}><Select {...register('salaryPeriod')}><option value="YEAR">Year</option><option value="MONTH">Month</option><option value="HOUR">Hour</option></Select></Field>
          <input type="hidden" {...register('aiRequestId')} />
          {submitError && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700 md:col-span-2">{submitError} {submitError.toLowerCase().includes('company') && <Link href="/recruiter/company" className="font-bold underline">Set up your company</Link>}</p>}
          <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-center sm:justify-between md:col-span-2">
            <p className="text-xs text-slate-500">Saving creates a draft only. Publishing is a separate step.</p>
            <Button disabled={isSubmitting || (Boolean(aiRequestId) && (!aiRequest.data || aiRequest.data.status !== 'SUCCEEDED'))}>{isSubmitting ? 'Saving draft…' : 'Save job draft'}</Button>
          </div>
        </form>
      </Card>
    </div>
  </div>;
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return <div><label className="mb-2 block text-sm font-semibold text-slate-700">{label}</label>{children}{error && <p className="mt-1 text-xs text-rose-600">{error}</p>}</div>;
}
