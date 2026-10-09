'use client';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { listRecruiterApplications } from '@/lib/api/applications';
import { StatusBadge } from '@/components/applications/StatusBadge';
import { Card } from '@/components/ui/Card';
export default function RecruiterApplicationsPage(){const q=useQuery({queryKey:['recruiter-applications'],queryFn:()=>listRecruiterApplications({page:0,size:50})});if(q.isLoading)return <main className="mx-auto max-w-6xl px-4 py-10">Loading applications…</main>;if(q.error)return <main className="mx-auto max-w-6xl px-4 py-10" role="alert">Unable to load applications.</main>;return <main className="mx-auto max-w-6xl px-4 py-10"><h1 className="text-3xl font-black">Application pipeline</h1><div className="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-3">{q.data?.map(a=><Card key={a.id}><p className="font-bold">{a.seeker?.firstName} {a.seeker?.lastName}</p><Link href={`/recruiter/applications/${a.id}`} className="mt-1 block text-sm font-semibold hover:underline">{a.job.title}</Link><div className="mt-3 flex items-center justify-between"><StatusBadge status={a.status}/><span className="text-xs text-slate-500">{new Date(a.appliedAt).toLocaleDateString()}</span></div></Card>)}{q.data?.length===0&&<Card><p>No applications yet.</p></Card>}</div></main>}
