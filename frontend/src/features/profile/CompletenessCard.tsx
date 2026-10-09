'use client';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@/components/ui/Card';
import { getSeekerProfile, listEducation, listExperience, listResumes } from '@/lib/api/seeker';
import { completenessHints } from '@/lib/profile/mapping';

export function CompletenessCard() {
  const profile = useQuery({ queryKey: ['seeker-profile'], queryFn: getSeekerProfile });
  const edu = useQuery({ queryKey: ['education'], queryFn: listEducation });
  const exp = useQuery({ queryKey: ['experience'], queryFn: listExperience });
  const resumes = useQuery({ queryKey: ['resumes'], queryFn: listResumes });
  if (!profile.data) return null;
  const score = Math.max(0, Math.min(100, profile.data.completenessScore));
  const hints = completenessHints(profile.data, {
    education: edu.data?.length ?? 0, experience: exp.data?.length ?? 0, hasPrimaryResume: Boolean(resumes.data?.some((r) => r.primary)),
  });
  const todo = hints.filter((h) => !h.done);
  return (
    <Card>
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold">Profile completeness</h2>
        <strong aria-live="polite">{score}%</strong>
      </div>
      <div role="progressbar" aria-label="Profile completeness" aria-valuemin={0} aria-valuemax={100} aria-valuenow={score} className="mt-3 h-2 w-full overflow-hidden rounded-full bg-slate-200">
        <div className="h-full bg-emerald-600" style={{ width: `${score}%` }} />
      </div>
      {todo.length === 0 ? <p className="mt-3 text-sm text-emerald-700">Your profile is complete.</p> : (
        <ul className="mt-3 space-y-1 text-sm text-slate-700">{todo.map((h) => <li key={h.label}>{h.label} <span className="text-slate-500">(+{h.points})</span></li>)}</ul>
      )}
    </Card>
  );
}
