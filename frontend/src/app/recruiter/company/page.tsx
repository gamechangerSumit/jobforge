'use client';
import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { ApiClientError } from '@/lib/api/client';
import { addCompanyMember, createCompany, getMyCompany, removeCompanyMember } from '@/lib/api/companies';
import { CompanyEditForm } from '@/features/company/CompanyEditForm';
import { LogoUpload } from '@/features/company/LogoUpload';

const optional = (max: number) => z.string().trim().max(max).optional().or(z.literal('').transform(() => undefined));
const schema = z.object({
  name: z.string().trim().min(1, 'Company name is required').max(150),
  industry: optional(80),
  websiteUrl: z.string().trim().max(255).regex(/^https?:\/\/\S+$/, 'Must start with http:// or https://').optional().or(z.literal('').transform(() => undefined)),
  hqCity: optional(80),
  hqCountry: z.string().trim().toUpperCase().regex(/^[A-Z]{2}$/, 'Use a 2-letter country code').optional().or(z.literal('').transform(() => undefined)),
  description: optional(5000),
});
type FormInput = z.input<typeof schema>;
type FormOutput = z.output<typeof schema>;

const STATUS_TEXT: Record<string, string> = {
  PENDING: 'Awaiting admin verification. You can draft jobs now, but publishing requires a verified company and an approved recruiter account.',
  VERIFIED: 'Verified. Approved recruiters can publish jobs.',
  REJECTED: 'Verification was rejected. Update your company details or contact support.',
  SUSPENDED: 'This company is suspended. Publishing is disabled.',
};

export default function CompanyPage() {
  const qc = useQueryClient();
  const mine = useQuery({ queryKey: ['company', 'me'], queryFn: getMyCompany, retry: false });
  const [memberEmail, setMemberEmail] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormInput, unknown, FormOutput>({ resolver: zodResolver(schema) });

  const create = useMutation({
    mutationFn: createCompany,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['company', 'me'] }),
    onError: (e) => setFormError(e instanceof ApiClientError ? e.error.message : 'Could not create the company.'),
  });
  const add = useMutation({
    mutationFn: (email: string) => addCompanyMember(mine.data!.company.id, email),
    onSuccess: () => { setMemberEmail(''); setFormError(null); return qc.invalidateQueries({ queryKey: ['company', 'me'] }); },
    onError: (e) => setFormError(e instanceof ApiClientError ? e.error.message : 'Could not add the member.'),
  });
  const remove = useMutation({
    mutationFn: (userId: string) => removeCompanyMember(mine.data!.company.id, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['company', 'me'] }),
    onError: (e) => setFormError(e instanceof ApiClientError ? e.error.message : 'Could not remove the member.'),
  });

  if (mine.isLoading) return <div className="mx-auto max-w-3xl px-4 py-10">Loading company…</div>;
  const notOnboarded = mine.error instanceof ApiClientError && mine.error.status === 404;
  if (mine.isError && !notOnboarded) {
    return <div className="mx-auto max-w-3xl px-4 py-10"><Card><p className="text-red-700">Could not load your company.</p><Button className="mt-4" onClick={() => mine.refetch()}>Retry</Button></Card></div>;
  }

  if (notOnboarded || !mine.data) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10"><Card>
        <h1 className="text-2xl font-black">Set up your company</h1>
        <p className="mt-2 text-sm text-slate-600">Recruiters must belong to a company before posting jobs. Create yours now; an admin will verify it.</p>
        <form className="mt-6 grid gap-4" onSubmit={handleSubmit((v) => { setFormError(null); create.mutate(v); })}>
          <Field label="Company name" error={errors.name?.message}><Input {...register('name')} /></Field>
          <Field label="Industry" error={errors.industry?.message}><Input {...register('industry')} /></Field>
          <Field label="Website" error={errors.websiteUrl?.message}><Input placeholder="https://" {...register('websiteUrl')} /></Field>
          <Field label="HQ city" error={errors.hqCity?.message}><Input {...register('hqCity')} /></Field>
          <Field label="HQ country (ISO code)" error={errors.hqCountry?.message}><Input maxLength={2} {...register('hqCountry')} /></Field>
          <Field label="Description" error={errors.description?.message}><textarea rows={5} className="w-full rounded-lg border p-3" {...register('description')} /></Field>
          {formError && <p role="alert" className="text-sm text-red-600">{formError}</p>}
          <Button disabled={isSubmitting || create.isPending}>{create.isPending ? 'Creating…' : 'Create company'}</Button>
        </form>
      </Card></div>
    );
  }

  const { company, memberRole, members } = mine.data;
  const isOwner = memberRole === 'OWNER';
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10">
      <Card>
        <h1 className="text-2xl font-black">{company.name}</h1>
        <p className="mt-1 text-sm text-slate-600">{company.industry ?? 'No industry set'} · {company.openJobCount} open jobs</p>
        <p className="mt-4 rounded-lg bg-slate-100 p-3 text-sm" role="status"><strong>{company.verificationStatus}</strong> — {STATUS_TEXT[company.verificationStatus]}</p>
        {company.rejectionReason && <p className="mt-2 text-sm text-red-700">Reason: {company.rejectionReason}</p>}
        <p className="mt-3 text-sm"><Link className="underline" href={`/companies/${company.id}`}>View public page</Link></p>
      </Card>
      {isOwner && <Card><h2 className="mb-3 text-lg font-bold">Logo</h2><LogoUpload companyId={company.id} hasLogo={Boolean(company.logoUrl)} /></Card>}
      <Card>
        <h2 className="mb-3 text-lg font-bold">Company details</h2>
        <CompanyEditForm key={company.version} company={company} />
      </Card>
      <Card>
        <h2 className="text-lg font-bold">Members</h2>
        <ul className="mt-3 divide-y">
          {members.map((m) => (
            <li key={m.userId} className="flex items-center justify-between py-2 text-sm">
              <span>{m.firstName} {m.lastName} · {m.email} <em className="text-slate-500">({m.memberRole})</em></span>
              {isOwner && m.memberRole !== 'OWNER' && <Button type="button" variant="danger" onClick={() => remove.mutate(m.userId)}>Remove</Button>}
            </li>
          ))}
        </ul>
        {isOwner && (
          <form className="mt-4 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (memberEmail.trim()) add.mutate(memberEmail.trim()); }}>
            <Input type="email" aria-label="Recruiter email" placeholder="recruiter@company.com" value={memberEmail} onChange={(e) => setMemberEmail(e.target.value)} />
            <Button disabled={add.isPending}>Add</Button>
          </form>
        )}
        {formError && <p role="alert" className="mt-2 text-sm text-red-600">{formError}</p>}
      </Card>
    </div>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return <div><label className="mb-1 block text-sm font-semibold">{label}</label>{children}{error && <p className="mt-1 text-xs text-red-600">{error}</p>}</div>;
}
