'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { JobCard } from '@/components/jobs/JobCard';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { companyLogoSrc, getCompany } from '@/lib/api/companies';
import { listJobsPage } from '@/lib/api/jobs';

const UUID = /^[0-9a-fA-F-]{36}$/;

/** Public company page. Resolved by id: the backend has no by-slug read (see REQ-20261009). */
export default function CompanyPublicPage() {
  const { id } = useParams<{ id: string }>();
  const valid = UUID.test(id ?? '');
  const [logoOk, setLogoOk] = useState(true);
  const company = useQuery({ queryKey: ['company', 'public', id], queryFn: () => getCompany(id), enabled: valid, retry: false });
  const jobs = useQuery({ queryKey: ['company', 'jobs', id], queryFn: () => listJobsPage({ companyId: id, size: 20 } as Parameters<typeof listJobsPage>[0]), enabled: valid && company.isSuccess });

  if (!valid || company.isError) return <div className="mx-auto max-w-4xl px-4 py-10" role="alert">Company not found.</div>;
  if (company.isLoading || !company.data) return <div className="mx-auto max-w-4xl px-4 py-10">Loading company…</div>;
  const c = company.data;
  const place = [c.hqCity, c.hqState, c.hqCountry].filter(Boolean).join(', ');
  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10">
      <Card>
        <div className="flex items-start gap-4">
          {logoOk && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={companyLogoSrc(c.id)} alt={`${c.name} logo`} width={72} height={72} className="rounded-lg border object-contain" style={{ width: 72, height: 72 }} onError={() => setLogoOk(false)} />
          )}
          <div>
            <h1 className="text-3xl font-black">{c.name}{c.verified && <span className="ml-2 rounded bg-emerald-100 px-2 py-0.5 align-middle text-xs font-semibold">Verified</span>}</h1>
            <p className="mt-1 text-sm text-slate-600">{[c.industry, place, c.foundedYear ? `Founded ${c.foundedYear}` : null].filter(Boolean).join(' · ') || 'No details provided'}</p>
            {c.websiteUrl && <p className="mt-1 text-sm"><a className="underline" href={c.websiteUrl} rel="noopener noreferrer nofollow" target="_blank">Website</a></p>}
          </div>
        </div>
        {c.description && <p className="mt-5 whitespace-pre-wrap text-slate-700">{c.description}</p>}
      </Card>
      <section aria-labelledby="open-jobs">
        <h2 id="open-jobs" className="mb-3 text-xl font-bold">Open jobs ({c.openJobCount})</h2>
        {jobs.isError && <p role="alert" className="text-red-700">Could not load jobs. <Button type="button" onClick={() => jobs.refetch()}>Retry</Button></p>}
        {jobs.data?.items.length === 0 && <p className="text-sm text-slate-600">No open positions right now.</p>}
        <div className="space-y-4">{jobs.data?.items.map((j) => <JobCard key={j.id} job={j} />)}</div>
      </section>
      <p className="text-sm"><Link className="underline" href="/jobs">Browse all jobs</Link></p>
    </div>
  );
}
