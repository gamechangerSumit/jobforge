'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { getJob } from '@/lib/api/jobs';
import { Card } from '@/components/ui/Card';

export default function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const query = useQuery({ queryKey: ['job', id], queryFn: () => getJob(id), enabled: Boolean(id) });
  if (query.isLoading) return <div className="mx-auto max-w-5xl px-4 py-10">Loading job…</div>;
  if (query.error || !query.data) return <div className="mx-auto max-w-5xl px-4 py-10" role="alert">Job not found or unavailable.</div>;
  const job = query.data;
  return <div className="mx-auto max-w-5xl px-4 py-10"><Card><div className="flex flex-col justify-between gap-5 md:flex-row"><div><p className="text-sm font-semibold text-slate-500"><Link href={`/companies/${job.company.id}`} className="hover:underline">{job.company.name}</Link></p><h1 className="mt-1 text-3xl font-black">{job.title}</h1><p className="mt-2 text-slate-600">{job.location?.city ?? 'Remote'} · {job.workMode} · {job.employmentType} · {job.experienceLevel}</p></div><Link href={`/jobs/${job.id}/apply`} className="inline-flex min-h-10 items-center justify-center rounded-lg bg-slate-900 px-5 py-2 text-sm font-semibold text-white">Apply now</Link></div><div className="mt-8 grid gap-8 md:grid-cols-[2fr_1fr]"><article><h2 className="text-xl font-bold">Description</h2><p className="mt-2 whitespace-pre-wrap text-slate-700">{job.description}</p><h2 className="mt-8 text-xl font-bold">Requirements</h2><p className="mt-2 whitespace-pre-wrap text-slate-700">{job.requirements}</p><h2 className="mt-8 text-xl font-bold">Benefits</h2><p className="mt-2 whitespace-pre-wrap text-slate-700">{job.benefits}</p></article><aside><div className="rounded-xl bg-slate-50 p-4"><p className="font-semibold">Skills</p><div className="mt-3 flex flex-wrap gap-2">{job.skills.map((s) => <span key={s.slug || s.name} className="rounded-full bg-white px-2.5 py-1 text-xs ring-1 ring-slate-200">{s.name}{s.required ? '' : ' (nice to have)'}</span>)}</div></div>{job.salary && <div className="mt-4 rounded-xl bg-slate-50 p-4"><p className="font-semibold">Salary</p><p className="mt-1 text-sm">{job.salary.currency} {[job.salary.min, job.salary.max].filter((v) => v != null).map((v) => v.toLocaleString()).join('–')} / {job.salary.period.toLowerCase()}</p></div>}</aside></div></Card></div>;
}
