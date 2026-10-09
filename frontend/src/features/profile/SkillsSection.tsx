'use client';
import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { errorMessage } from '@/lib/api/errors';
import { listSkills, replaceSkills, type SeekerSkill } from '@/lib/api/seeker';
import { Section } from './Section';

export function SkillsSection() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['seeker-skills'], queryFn: listSkills });
  const [name, setName] = useState('');
  const [level, setLevel] = useState<SeekerSkill['proficiency']>('INTERMEDIATE');
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: replaceSkills,
    onSuccess: (data) => { setError(null); qc.setQueryData(['seeker-skills'], data); void qc.invalidateQueries({ queryKey: ['seeker-profile'] }); },
    onError: (e) => setError(errorMessage(e, 'Could not save skills.')),
  });
  const current = q.data ?? [];
  return (
    <Section title="Skills">
      {q.isError && <p className="text-red-700">Could not load skills. <button className="underline" onClick={() => q.refetch()}>Retry</button></p>}
      <ul className="flex flex-wrap gap-2">
        {current.map((s) => (
          <li key={s.skill} className="rounded-full bg-slate-100 px-3 py-1 text-sm">
            {s.skill} · {s.proficiency.toLowerCase()}
            <button aria-label={`Remove ${s.skill}`} className="ml-2 text-red-700" onClick={() => save.mutate(current.filter((x) => x.skill !== s.skill))}>×</button>
          </li>
        ))}
      </ul>
      <form className="mt-4 flex flex-wrap gap-2" onSubmit={(e) => {
        e.preventDefault();
        const skill = name.trim();
        if (!skill || current.some((s) => s.skill.toLowerCase() === skill.toLowerCase())) return;
        save.mutate([...current, { skill, proficiency: level }]);
        setName('');
      }}>
        <Input aria-label="Skill" placeholder="e.g. Java" maxLength={50} value={name} onChange={(e) => setName(e.target.value)} />
        <select aria-label="Proficiency" className="rounded-lg border px-2" value={level} onChange={(e) => setLevel(e.target.value as SeekerSkill['proficiency'])}>
          {['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'].map((l) => <option key={l}>{l}</option>)}
        </select>
        <Button disabled={save.isPending}>Add skill</Button>
      </form>
      {error && <p role="alert" className="mt-2 text-sm text-red-600">{error}</p>}
    </Section>
  );
}
