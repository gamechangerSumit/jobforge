'use client';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { errorMessage } from '@/lib/api/errors';
import {
  addEducation, addExperience, deleteEducation, deleteExperience, listEducation, listExperience, patchEducation, patchExperience,
  type Education, type EducationPatch, type Experience, type ExperiencePatch,
} from '@/lib/api/seeker';
import { Section } from './Section';

const val = (f: FormData, k: string) => String(f.get(k) ?? '').trim();
const opt = (f: FormData, k: string) => val(f, k) || undefined;
/** In PATCH an empty string clears an optional text field (contract change REQ-20261009). */
const clearable = (f: FormData, k: string) => val(f, k);

function EducationRow({ item, onSaved, onError }: { item: Education; onSaved: () => void; onError: (e: unknown) => void }) {
  const [editing, setEditing] = useState(false);
  const save = useMutation({
    mutationFn: (patch: EducationPatch) => patchEducation(item.id, patch),
    onSuccess: () => { setEditing(false); onSaved(); }, onError,
  });
  const remove = useMutation({ mutationFn: () => deleteEducation(item.id), onSuccess: onSaved, onError });
  if (!editing) {
    return (
      <li className="flex items-center justify-between gap-2 py-2 text-sm">
        <span>{item.degree}{item.fieldOfStudy ? ` in ${item.fieldOfStudy}` : ''}, {item.institution} ({item.startDate} – {item.endDate ?? '—'})</span>
        <span className="flex gap-2">
          <Button type="button" onClick={() => setEditing(true)} aria-label={`Edit ${item.degree} at ${item.institution}`}>Edit</Button>
          <Button type="button" variant="danger" onClick={() => remove.mutate()}>Delete</Button>
        </span>
      </li>
    );
  }
  return (
    <li className="py-3">
      <form className="grid gap-2 md:grid-cols-2" onSubmit={(e) => {
        e.preventDefault(); const f = new FormData(e.currentTarget);
        save.mutate({
          institution: val(f, 'institution'), degree: val(f, 'degree'), fieldOfStudy: clearable(f, 'fieldOfStudy'),
          startDate: val(f, 'start'), endDate: opt(f, 'end'), clearEndDate: !opt(f, 'end') && !!item.endDate, grade: clearable(f, 'grade'),
        });
      }}>
        <Input name="institution" aria-label="Institution" defaultValue={item.institution} required maxLength={150} />
        <Input name="degree" aria-label="Degree" defaultValue={item.degree} required maxLength={100} />
        <Input name="fieldOfStudy" aria-label="Field of study" placeholder="Field of study" defaultValue={item.fieldOfStudy ?? ''} maxLength={100} />
        <Input name="grade" aria-label="Grade" placeholder="Grade" defaultValue={item.grade ?? ''} maxLength={30} />
        <Input name="start" type="date" aria-label="Start date" defaultValue={item.startDate} required />
        <Input name="end" type="date" aria-label="End date" defaultValue={item.endDate ?? ''} />
        <div className="flex gap-2 md:col-span-2">
          <Button disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</Button>
          <Button type="button" onClick={() => setEditing(false)}>Cancel</Button>
        </div>
      </form>
    </li>
  );
}

function ExperienceRow({ item, onSaved, onError }: { item: Experience; onSaved: () => void; onError: (e: unknown) => void }) {
  const [editing, setEditing] = useState(false);
  const save = useMutation({
    mutationFn: (patch: ExperiencePatch) => patchExperience(item.id, patch),
    onSuccess: () => { setEditing(false); onSaved(); }, onError,
  });
  const remove = useMutation({ mutationFn: () => deleteExperience(item.id), onSuccess: onSaved, onError });
  if (!editing) {
    return (
      <li className="flex items-center justify-between gap-2 py-2 text-sm">
        <span>{item.title} · {item.companyName} ({item.startDate} – {item.current ? 'present' : item.endDate ?? '—'})</span>
        <span className="flex gap-2">
          <Button type="button" onClick={() => setEditing(true)} aria-label={`Edit ${item.title} at ${item.companyName}`}>Edit</Button>
          <Button type="button" variant="danger" onClick={() => remove.mutate()}>Delete</Button>
        </span>
      </li>
    );
  }
  return (
    <li className="py-3">
      <form className="grid gap-2 md:grid-cols-2" onSubmit={(e) => {
        e.preventDefault(); const f = new FormData(e.currentTarget);
        const current = f.get('current') === 'on';
        save.mutate({
          title: val(f, 'title'), companyName: val(f, 'company'), location: clearable(f, 'location'),
          startDate: val(f, 'start'), endDate: current ? undefined : opt(f, 'end'), clearEndDate: !current && !opt(f, 'end') && !!item.endDate, current, description: clearable(f, 'description'),
        });
      }}>
        <Input name="title" aria-label="Title" defaultValue={item.title} required maxLength={120} />
        <Input name="company" aria-label="Company" defaultValue={item.companyName} required maxLength={150} />
        <Input name="location" aria-label="Location" placeholder="Location" defaultValue={item.location ?? ''} maxLength={120} />
        <div />
        <Input name="start" type="date" aria-label="Start date" defaultValue={item.startDate} required />
        <Input name="end" type="date" aria-label="End date" defaultValue={item.endDate ?? ''} />
        <label className="text-sm"><input name="current" type="checkbox" className="mr-2" defaultChecked={item.current} />Current position</label>
        <textarea name="description" aria-label="Description" placeholder="What did you do?" defaultValue={item.description ?? ''} maxLength={2000} rows={3} className="w-full rounded-lg border border-slate-300 p-2 text-sm md:col-span-2" />
        <div className="flex gap-2 md:col-span-2">
          <Button disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</Button>
          <Button type="button" onClick={() => setEditing(false)}>Cancel</Button>
        </div>
      </form>
    </li>
  );
}

export function HistorySections() {
  const qc = useQueryClient();
  const edu = useQuery({ queryKey: ['education'], queryFn: listEducation });
  const exp = useQuery({ queryKey: ['experience'], queryFn: listExperience });
  const [error, setError] = useState<string | null>(null);
  const fail = (e: unknown) => setError(errorMessage(e, 'Something went wrong. Please retry.'));
  // Completeness depends on these lists, so refresh the profile score as well.
  const refresh = (key: 'education' | 'experience') => () => {
    setError(null);
    void qc.invalidateQueries({ queryKey: [key] });
    void qc.invalidateQueries({ queryKey: ['seeker-profile'] });
  };
  const addEdu = useMutation({ mutationFn: addEducation, onSuccess: refresh('education'), onError: fail });
  const addExp = useMutation({ mutationFn: addExperience, onSuccess: refresh('experience'), onError: fail });

  return (
    <>
      <Section title="Experience">
        {exp.isError && <p className="text-red-700">Could not load experience. <button className="underline" onClick={() => exp.refetch()}>Retry</button></p>}
        <ul className="divide-y">{exp.data?.map((x) => <ExperienceRow key={x.id} item={x} onSaved={refresh('experience')} onError={fail} />)}</ul>
        {exp.data?.length === 0 && <p className="text-sm text-slate-600">No experience added yet.</p>}
        <form className="mt-4 grid gap-2 md:grid-cols-2" onSubmit={(e) => {
          e.preventDefault(); const form = e.currentTarget; const f = new FormData(form);
          const current = f.get('current') === 'on';
          addExp.mutate({ title: val(f, 'title'), companyName: val(f, 'company'), startDate: val(f, 'start'), endDate: current ? undefined : opt(f, 'end'), current }, { onSuccess: () => form.reset() });
        }}>
          <Input name="title" aria-label="Title" placeholder="Title" required maxLength={120} />
          <Input name="company" aria-label="Company" placeholder="Company" required maxLength={150} />
          <Input name="start" type="date" aria-label="Start date" required />
          <Input name="end" type="date" aria-label="End date" />
          <label className="text-sm"><input name="current" type="checkbox" className="mr-2" />Current position</label>
          <Button disabled={addExp.isPending}>Add experience</Button>
        </form>
      </Section>
      <Section title="Education">
        {edu.isError && <p className="text-red-700">Could not load education. <button className="underline" onClick={() => edu.refetch()}>Retry</button></p>}
        <ul className="divide-y">{edu.data?.map((x) => <EducationRow key={x.id} item={x} onSaved={refresh('education')} onError={fail} />)}</ul>
        {edu.data?.length === 0 && <p className="text-sm text-slate-600">No education added yet.</p>}
        <form className="mt-4 grid gap-2 md:grid-cols-2" onSubmit={(e) => {
          e.preventDefault(); const form = e.currentTarget; const f = new FormData(form);
          addEdu.mutate({ institution: val(f, 'institution'), degree: val(f, 'degree'), startDate: val(f, 'start'), endDate: opt(f, 'end') }, { onSuccess: () => form.reset() });
        }}>
          <Input name="institution" aria-label="Institution" placeholder="Institution" required maxLength={150} />
          <Input name="degree" aria-label="Degree" placeholder="Degree" required maxLength={100} />
          <Input name="start" type="date" aria-label="Start date" required />
          <Input name="end" type="date" aria-label="End date" />
          <Button disabled={addEdu.isPending}>Add education</Button>
        </form>
      </Section>
      {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
    </>
  );
}
