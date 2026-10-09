'use client';
import { useQuery } from '@tanstack/react-query'; import { listSavedJobs } from '@/lib/api/jobs'; import { JobCard } from '@/components/jobs/JobCard';
export default function SavedJobsPage() { const q = useQuery({ queryKey: ['saved-jobs'], queryFn: () => listSavedJobs() }); return <div className="mx-auto max-w-5xl px-4 py-10"><h1 className="text-3xl font-black">Saved jobs</h1><div className="mt-6 space-y-4">{q.data?.map((j) => <JobCard key={j.id} job={j} />)}{q.data?.length === 0 && <p>No saved jobs yet.</p>}</div></div>; }
