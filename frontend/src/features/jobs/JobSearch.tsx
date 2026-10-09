'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { listJobsPage } from '@/lib/api/jobs';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Button } from '@/components/ui/Button';
import { JobCard } from '@/components/jobs/JobCard';
import type {
  EmploymentType,
  ExperienceLevel,
  JobListParams,
  WorkMode,
} from '@/types/api';

const PAGE_SIZE = 12;

const employmentTypes: Array<[EmploymentType, string]> = [
  ['FULL_TIME', 'Full time'],
  ['PART_TIME', 'Part time'],
  ['CONTRACT', 'Contract'],
  ['INTERNSHIP', 'Internship'],
  ['FREELANCE', 'Freelance'],
];

const workModes: Array<[WorkMode, string]> = [
  ['ONSITE', 'On-site'],
  ['HYBRID', 'Hybrid'],
  ['REMOTE', 'Remote'],
];

const experienceLevels: Array<[ExperienceLevel, string]> = [
  ['INTERN', 'Intern'],
  ['ENTRY', 'Entry'],
  ['MID', 'Mid'],
  ['SENIOR', 'Senior'],
  ['LEAD', 'Lead'],
  ['EXECUTIVE', 'Executive'],
];

const postedOptions = [
  ['', 'Any time'],
  ['24h', 'Last 24 hours'],
  ['7d', 'Last 7 days'],
  ['30d', 'Last 30 days'],
] as const;

const sortOptions = [
  ['relevance', 'Relevance'],
  ['postedAt', 'Newest'],
  ['salary', 'Salary'],
] as const;

function parseNumber(value: string | null) {
  if (!value) return undefined;
  const number = Number(value);
  return Number.isFinite(number) ? number : undefined;
}

function csvToSkills(value: string | null) {
  return value
    ? value
        .split(',')
        .map((skill) => skill.trim())
        .filter(Boolean)
    : [];
}

export function JobSearch() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const initial = useMemo(() => {
    const sort = searchParams.get('sort');
    return {
      q: searchParams.get('q') ?? '',
      location: searchParams.get('location') ?? '',
      country: searchParams.get('country') ?? '',
      workMode: searchParams.get('workMode') ?? '',
      employmentType: searchParams.get('employmentType') ?? '',
      experienceLevel: searchParams.get('experienceLevel') ?? '',
      salaryMin: searchParams.get('salaryMin') ?? '',
      salaryMax: searchParams.get('salaryMax') ?? '',
      currency: searchParams.get('currency') ?? 'INR',
      skills: searchParams.get('skills') ?? '',
      postedWithin: searchParams.get('postedWithin') ?? '',
      sort: sort === 'salary' || sort === 'postedAt' || sort === 'relevance' ? sort : '',
      page: Math.max(0, Number(searchParams.get('page') ?? '0') || 0),
    };
  }, [searchParams]);

  const [form, setForm] = useState(initial);

  useEffect(() => {
    setForm(initial);
  }, [initial]);

  const params = useMemo<JobListParams>(() => ({
    q: form.q || undefined,
    location: form.location || undefined,
    country: form.country || undefined,
    workMode: (form.workMode || undefined) as WorkMode | undefined,
    employmentType: (form.employmentType || undefined) as EmploymentType | undefined,
    experienceLevel: (form.experienceLevel || undefined) as ExperienceLevel | undefined,
    salaryMin: parseNumber(form.salaryMin),
    salaryMax: parseNumber(form.salaryMax),
    // Currency only qualifies a salary bound; sending it alone would hide jobs without salary / in other currencies.
    currency: (form.salaryMin || form.salaryMax) && form.currency ? form.currency : undefined,
    skills: csvToSkills(form.skills),
    postedWithin: form.postedWithin as JobListParams['postedWithin'],
    page: form.page,
    size: PAGE_SIZE,
    sort: form.sort as JobListParams['sort'],
  }), [form]);

  const query = useQuery({
    queryKey: ['jobs', params],
    queryFn: () => listJobsPage(params),
  });

  const updateUrl = (next: typeof form) => {
    const query = new URLSearchParams();
    const fields: Array<[string, string]> = [
      ['q', next.q],
      ['location', next.location],
      ['country', next.country],
      ['workMode', next.workMode],
      ['employmentType', next.employmentType],
      ['experienceLevel', next.experienceLevel],
      ['salaryMin', next.salaryMin],
      ['salaryMax', next.salaryMax],
      ['currency', next.salaryMin || next.salaryMax ? next.currency : ''],
      ['skills', next.skills],
      ['postedWithin', next.postedWithin],
      ['sort', next.sort],
    ];

    fields.forEach(([key, value]) => {
      if (value) query.set(key, value);
    });
    if (next.page > 0) query.set('page', String(next.page));

    const suffix = query.toString();
    router.replace(suffix ? `${pathname}?${suffix}` : pathname, { scroll: false });
  };

  const applyFilters = (event: React.FormEvent) => {
    event.preventDefault();
    const next = { ...form, page: 0 };
    setForm(next);
    updateUrl(next);
  };

  const clearFilters = () => {
    const next = {
      q: '', location: '', country: '', workMode: '', employmentType: '',
      experienceLevel: '', salaryMin: '', salaryMax: '', currency: 'INR',
      skills: '', postedWithin: '', sort: '', page: 0,
    };
    setForm(next);
    updateUrl(next);
  };

  const hasFilters = Object.entries(form).some(([key, value]) =>
    key !== 'page' && key !== 'currency' && value !== '',
  );

  const setPage = (page: number) => {
    const next = { ...form, page: Math.max(0, page) };
    setForm(next);
    updateUrl(next);
  };

  return (
    <div>
      <form onSubmit={applyFilters} className="rounded-2xl border bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
          <div className="lg:col-span-2">
            <label className="mb-1 block text-sm font-semibold" htmlFor="q">Keywords</label>
            <Input id="q" placeholder="Job title, skills or keywords" value={form.q} onChange={(e) => setForm((v) => ({ ...v, q: e.target.value }))} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="location">Location</label>
            <Input id="location" placeholder="City or location" value={form.location} onChange={(e) => setForm((v) => ({ ...v, location: e.target.value }))} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="skills">Skills</label>
            <Input id="skills" placeholder="java, spring, aws" value={form.skills} onChange={(e) => setForm((v) => ({ ...v, skills: e.target.value }))} />
          </div>

          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="workMode">Work mode</label>
            <Select id="workMode" value={form.workMode} onChange={(e) => setForm((v) => ({ ...v, workMode: e.target.value }))}>
              <option value="">Any work mode</option>
              {workModes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="employmentType">Employment</label>
            <Select id="employmentType" value={form.employmentType} onChange={(e) => setForm((v) => ({ ...v, employmentType: e.target.value }))}>
              <option value="">Any employment</option>
              {employmentTypes.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="experienceLevel">Experience</label>
            <Select id="experienceLevel" value={form.experienceLevel} onChange={(e) => setForm((v) => ({ ...v, experienceLevel: e.target.value }))}>
              <option value="">Any experience</option>
              {experienceLevels.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="postedWithin">Posted</label>
            <Select id="postedWithin" value={form.postedWithin} onChange={(e) => setForm((v) => ({ ...v, postedWithin: e.target.value }))}>
              {postedOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
          </div>

          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="salaryMin">Min salary</label>
            <Input id="salaryMin" type="number" min="0" placeholder="0" value={form.salaryMin} onChange={(e) => setForm((v) => ({ ...v, salaryMin: e.target.value }))} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="salaryMax">Max salary</label>
            <Input id="salaryMax" type="number" min="0" placeholder="Any" value={form.salaryMax} onChange={(e) => setForm((v) => ({ ...v, salaryMax: e.target.value }))} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="currency">Currency</label>
            <Input id="currency" maxLength={3} value={form.currency} onChange={(e) => setForm((v) => ({ ...v, currency: e.target.value.toUpperCase() }))} />
          </div>
          <div>
            <label className="mb-1 block text-sm font-semibold" htmlFor="sort">Sort by</label>
            <Select id="sort" value={form.sort} onChange={(e) => setForm((v) => ({ ...v, sort: e.target.value }))}>
              <option value="">Default</option>
              {sortOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </Select>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-2">
          <Button type="submit">Search jobs</Button>
          <Button type="button" onClick={clearFilters}>Clear filters</Button>
        </div>
      </form>

      <div className="mt-6 space-y-4">
        {query.isLoading && <p className="rounded-xl border bg-white p-6">Loading jobs…</p>}
        {query.isError && <p className="rounded-xl border border-red-200 bg-red-50 p-6 text-red-700" role="alert">Unable to load jobs. Please try again.</p>}
        {!query.isLoading && !query.isError && query.data?.items.map((job) => <JobCard key={job.id} job={job} />)}
        {!query.isLoading && !query.isError && query.data?.items.length === 0 && (
          <p className="rounded-xl border bg-white p-8 text-center text-slate-600">No published jobs matched your search.</p>
        )}
      </div>

      {!query.isLoading && !query.isError && (form.page > 0 || Boolean(query.data?.page?.hasNext)) && (
        <div className="mt-6 flex items-center justify-between rounded-xl border bg-white p-4">
          <Button type="button" disabled={form.page === 0} onClick={() => setPage(form.page - 1)}>Previous</Button>
          <span className="text-sm text-slate-600">Page {form.page + 1}{query.data?.page ? ` of ${Math.max(query.data.page.totalPages, 1)}` : ''}</span>
          <Button type="button" disabled={!query.data?.page?.hasNext} onClick={() => setPage(form.page + 1)}>Next</Button>
        </div>
      )}

      {hasFilters && !query.isLoading && !query.isError && (
        <p className="mt-3 text-xs text-slate-500">Filters are synchronized with the URL, so this search can be shared or bookmarked.</p>
      )}
    </div>
  );
}
