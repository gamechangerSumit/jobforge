'use client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { ApiClientError } from '@/lib/api/client';
import { updateCompany, type Company } from '@/lib/api/companies';
import { SIZE_BANDS, companyEditSchema, type CompanyEditInput, type CompanyEditOutput } from '@/lib/validation/company';

function Field({ id, label, error, children }: { id: string; label: string; error?: string; children: React.ReactNode }) {
  return <div><label htmlFor={id} className="mb-1 block text-sm font-semibold">{label}</label>{children}{error && <p role="alert" className="mt-1 text-xs text-red-600">{error}</p>}</div>;
}

/** PATCH /companies/{id} with If-Match. Absent fields stay unchanged server-side, so only changed values are sent. */
export function CompanyEditForm({ company }: { company: Company }) {
  const qc = useQueryClient();
  const [status, setStatus] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const defaults: CompanyEditInput = {
    name: company.name, industry: company.industry ?? '', sizeBand: company.sizeBand ?? '', websiteUrl: company.websiteUrl ?? '',
    hqCity: company.hqCity ?? '', hqState: company.hqState ?? '', hqCountry: company.hqCountry ?? '',
    foundedYear: company.foundedYear ? String(company.foundedYear) : '', description: company.description ?? '',
  };
  const { register, handleSubmit, reset, formState: { errors, dirtyFields, isDirty } } =
    useForm<CompanyEditInput, unknown, CompanyEditOutput>({ resolver: zodResolver(companyEditSchema), defaultValues: defaults });

  const save = useMutation({
    mutationFn: (patch: Partial<CompanyEditOutput>) => updateCompany(company.id, company.version, patch),
    onSuccess: (saved) => {
      setStatus({ kind: 'ok', text: 'Company details saved.' });
      reset({ ...defaults, name: saved.name, industry: saved.industry ?? '', sizeBand: saved.sizeBand ?? '', websiteUrl: saved.websiteUrl ?? '',
        hqCity: saved.hqCity ?? '', hqState: saved.hqState ?? '', hqCountry: saved.hqCountry ?? '',
        foundedYear: saved.foundedYear ? String(saved.foundedYear) : '', description: saved.description ?? '' });
      void qc.invalidateQueries({ queryKey: ['company', 'me'] });
    },
    onError: (e) => {
      if (e instanceof ApiClientError && e.error.code === 'STALE_VERSION') {
        setStatus({ kind: 'error', text: 'This company was changed by someone else. Details were reloaded; please re-apply your edits.' });
        void qc.invalidateQueries({ queryKey: ['company', 'me'] });
        return;
      }
      setStatus({ kind: 'error', text: e instanceof ApiClientError ? e.error.message : 'Could not save the company.' });
    },
  });

  const onSubmit = (values: CompanyEditOutput) => {
    setStatus(null);
    const patch: Partial<CompanyEditOutput> = {};
    (Object.keys(dirtyFields) as (keyof CompanyEditOutput)[]).forEach((k) => {
      if (values[k] !== undefined) (patch as Record<string, unknown>)[k] = values[k];
    });
    if (Object.keys(patch).length === 0) { setStatus({ kind: 'ok', text: 'Nothing to save.' }); return; }
    save.mutate(patch);
  };

  return (
    <form noValidate className="grid gap-4 md:grid-cols-2" onSubmit={handleSubmit(onSubmit)}>
      <Field id="c-name" label="Company name" error={errors.name?.message}><Input id="c-name" {...register('name')} /></Field>
      <Field id="c-industry" label="Industry" error={errors.industry?.message}><Input id="c-industry" {...register('industry')} /></Field>
      <Field id="c-size" label="Size band" error={errors.sizeBand?.message}><Select id="c-size" {...register('sizeBand')}>{Object.entries(SIZE_BANDS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</Select></Field>
      <Field id="c-web" label="Website" error={errors.websiteUrl?.message}><Input id="c-web" placeholder="https://" {...register('websiteUrl')} /></Field>
      <Field id="c-city" label="HQ city" error={errors.hqCity?.message}><Input id="c-city" {...register('hqCity')} /></Field>
      <Field id="c-state" label="HQ state / region" error={errors.hqState?.message}><Input id="c-state" {...register('hqState')} /></Field>
      <Field id="c-country" label="HQ country (ISO code)" error={errors.hqCountry?.message}><Input id="c-country" maxLength={2} {...register('hqCountry')} /></Field>
      <Field id="c-founded" label="Founded year" error={errors.foundedYear?.message}><Input id="c-founded" inputMode="numeric" {...register('foundedYear')} /></Field>
      <div className="md:col-span-2"><Field id="c-desc" label="Description" error={errors.description?.message}>
        <textarea id="c-desc" rows={5} className="w-full rounded-lg border border-slate-300 p-3 text-sm" {...register('description')} />
      </Field></div>
      <div className="flex items-center gap-3 md:col-span-2">
        <Button disabled={save.isPending || !isDirty}>{save.isPending ? 'Saving…' : 'Save changes'}</Button>
        {status && <p role={status.kind === 'error' ? 'alert' : 'status'} className={status.kind === 'error' ? 'text-sm text-red-600' : 'text-sm text-emerald-700'}>{status.text}</p>}
      </div>
    </form>
  );
}
