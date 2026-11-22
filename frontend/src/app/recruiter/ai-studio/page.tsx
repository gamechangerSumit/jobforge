'use client';

import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiClientError } from '@/lib/api/client';
import { generateJobDraft, getAiUsage, type AiDraftInput, type AiDraftResult } from '@/lib/api/ai';
import { useSession } from '@/lib/auth/session';

const field = 'jf-input';
const initial: AiDraftInput = {
  title: '', experienceLevel: 'ENTRY', employmentType: 'FULL_TIME', workMode: 'HYBRID',
  location: { city: 'Pune', state: 'Maharashtra', country: 'IN' },
  skills: [''], tone: 'PROFESSIONAL', notes: '', language: 'en',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="mb-2 block text-sm font-semibold text-slate-700">{label}</span>{children}</label>;
}
function SelectField({ value, onChange, options }: { value: string; onChange: (value: string) => void; options: Array<[string, string]> }) {
  return <select className={field} value={value} onChange={(event) => onChange(event.target.value)}>{options.map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>;
}

export default function AiStudioPage() {
  const { user, loading } = useSession();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<AiDraftInput>(initial);
  const [result, setResult] = useState<AiDraftResult | null>(null);
  const [copied, setCopied] = useState(false);
  const usage = useQuery({ queryKey: ['ai-usage'], queryFn: getAiUsage, enabled: user?.role === 'RECRUITER', retry: 1 });
  const mutation = useMutation({
    mutationFn: (input: AiDraftInput) => generateJobDraft(input, crypto.randomUUID()),
    onSuccess: (value) => { setResult(value); void queryClient.invalidateQueries({ queryKey: ['ai-usage'] }); },
  });

  const set = <K extends keyof AiDraftInput>(key: K, value: AiDraftInput[K]) => setForm((current) => ({ ...current, [key]: value }));
  const setSkill = (index: number, value: string) => setForm((current) => ({ ...current, skills: current.skills.map((skill, i) => i === index ? value : skill) }));
  const addSkill = () => setForm((current) => current.skills.length >= 20 ? current : ({ ...current, skills: [...current.skills, ''] }));
  const removeSkill = (index: number) => setForm((current) => ({ ...current, skills: current.skills.filter((_, i) => i !== index) }));

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const input = { ...form, skills: form.skills.map((skill) => skill.trim()).filter(Boolean), notes: form.notes?.trim() || undefined };
    if (input.title.trim().length < 3 || input.skills.length < 1) return;
    mutation.mutate(input);
  };
  const copyDraft = async () => {
    if (!result) return;
    const draft = result.draft;
    const text = [
      draft.title, '', draft.summary, '', 'Responsibilities', ...draft.responsibilities.map((x) => '- ' + x),
      '', 'Requirements', ...draft.requirements.map((x) => '- ' + x),
      ...(draft.niceToHave.length ? ['', 'Nice to have', ...draft.niceToHave.map((x) => '- ' + x)] : []),
      ...(draft.benefits.length ? ['', 'Benefits', ...draft.benefits.map((x) => '- ' + x)] : []),
      '', 'Skills', draft.skills.map((x) => x.name).join(', '),
    ].join('\n');
    await navigator.clipboard.writeText(text);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  };

  if (loading) return <div className="jf-container py-16 text-sm text-slate-500">Loading your workspace…</div>;
  if (!user || user.role !== 'RECRUITER') return <div className="jf-container py-16"><div className="jf-card mx-auto max-w-xl p-8 text-center"><h1 className="text-2xl font-black">Recruiter workspace</h1><p className="mt-3 text-slate-600">Sign in with an approved recruiter account to use AI Job Studio.</p><Link href="/login" className="jf-button jf-button-primary mt-6">Sign in</Link></div></div>;

  const remaining = result?.quota.remaining ?? usage.data?.remaining;
  const limit = result?.quota.limit ?? usage.data?.limit;
  const error = mutation.error instanceof ApiClientError ? mutation.error.message : mutation.error?.message;

  return <div className="min-h-screen bg-[#f6f7fb] pb-16">
    <div className="border-b border-violet-100 bg-white">
      <div className="jf-container py-9 sm:py-12">
        <Link href="/recruiter/dashboard" className="text-sm font-semibold text-violet-700 hover:text-violet-900">← Recruiter workspace</Link>
        <div className="mt-5 flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl"><span className="jf-eyebrow">✦ AI-powered workflow</span><h1 className="mt-4 text-4xl font-black tracking-[-.055em] text-slate-950 sm:text-5xl">AI Job Studio</h1><p className="mt-3 text-base leading-7 text-slate-600">Turn a clear role brief into a structured, editable job description. Review every section before publishing.</p></div>
          <div className="jf-card min-w-48 p-4"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">AI credits remaining</p><p className="mt-1 text-3xl font-black tracking-tight text-slate-950">{remaining ?? '—'}<span className="ml-1 text-sm font-semibold text-slate-400">/ {limit ?? '—'}</span></p><p className="mt-1 text-xs text-slate-500">{usage.data?.period ?? 'Daily quota'} · refreshes automatically</p></div>
        </div>
      </div>
    </div>

    <div className="jf-container mt-8 grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(360px,.82fr)]">
      <form onSubmit={submit} className="jf-card p-5 sm:p-7">
        <div className="mb-6"><h2 className="text-xl font-extrabold tracking-tight">Role brief</h2><p className="mt-1 text-sm text-slate-500">Give the AI enough context to create a useful first draft.</p></div>
        <div className="grid gap-5 sm:grid-cols-2">
          <div className="sm:col-span-2"><Field label="Job title *"><input className={field} required minLength={3} maxLength={120} placeholder="e.g. Junior DevOps Engineer" value={form.title} onChange={(e) => set('title', e.target.value)} /></Field></div>
          <Field label="Experience level"><SelectField value={form.experienceLevel} onChange={(v) => set('experienceLevel', v as AiDraftInput['experienceLevel'])} options={ [['INTERN','Intern'],['ENTRY','Entry level'],['MID','Mid level'],['SENIOR','Senior'],['LEAD','Lead'],['EXECUTIVE','Executive']] } /></Field>
          <Field label="Employment type"><SelectField value={form.employmentType} onChange={(v) => set('employmentType', v as AiDraftInput['employmentType'])} options={ [['FULL_TIME','Full time'],['PART_TIME','Part time'],['CONTRACT','Contract'],['INTERNSHIP','Internship'],['FREELANCE','Freelance']] } /></Field>
          <Field label="Work mode"><SelectField value={form.workMode} onChange={(v) => set('workMode', v as AiDraftInput['workMode'])} options={ [['ONSITE','On-site'],['HYBRID','Hybrid'],['REMOTE','Remote']] } /></Field>
          <Field label="Writing tone"><SelectField value={form.tone} onChange={(v) => set('tone', v as AiDraftInput['tone'])} options={ [['PROFESSIONAL','Professional'],['FRIENDLY','Friendly'],['CONCISE','Concise']] } /></Field>
          <Field label="City"><input className={field} required value={form.location.city} onChange={(e) => set('location', { ...form.location, city: e.target.value })} /></Field>
          <Field label="State"><input className={field} required value={form.location.state} onChange={(e) => set('location', { ...form.location, state: e.target.value })} /></Field>
          <div className="sm:col-span-2">
            <div className="mb-2 flex items-center justify-between"><span className="text-sm font-semibold text-slate-700">Required and preferred skills *</span><button type="button" onClick={addSkill} disabled={form.skills.length >= 20} className="text-xs font-bold text-violet-700 disabled:opacity-40">+ Add skill</button></div>
            <div className="grid gap-2 sm:grid-cols-2">{form.skills.map((skill, index) => <div key={index} className="flex gap-2"><input className={field} maxLength={50} placeholder={index === 0 ? 'e.g. Linux' : 'Add a skill'} value={skill} onChange={(e) => setSkill(index, e.target.value)} /><button type="button" aria-label={`Remove skill ${index + 1}`} onClick={() => removeSkill(index)} disabled={form.skills.length === 1} className="rounded-lg border border-slate-200 px-3 text-slate-500 hover:bg-slate-50 disabled:opacity-30">×</button></div>)}</div>
          </div>
          <div className="sm:col-span-2"><Field label="Additional context (optional)"><textarea className={field + ' min-h-28 resize-y'} maxLength={1000} placeholder="Team mission, key responsibilities, domain context, on-call expectations…" value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value)} /><span className="mt-1 block text-right text-xs text-slate-400">{form.notes?.length ?? 0}/1000</span></Field></div>
        </div>
        {error && <div role="alert" className="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div>}
        <div className="mt-7 flex flex-col gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:items-center sm:justify-between"><p className="text-xs text-slate-500">Uses 5 AI credits per successful generation.</p><button type="submit" disabled={mutation.isPending || form.title.trim().length < 3 || !form.skills.some((s) => s.trim())} className="jf-button jf-button-primary disabled:cursor-not-allowed disabled:opacity-50">{mutation.isPending ? 'Creating your draft…' : '✦ Generate job draft'}</button></div>
      </form>

      <section className="space-y-5">
        <div className="jf-card p-5 sm:p-6">
          <div className="flex items-center justify-between"><div><h2 className="text-lg font-extrabold tracking-tight">Draft preview</h2><p className="mt-1 text-xs text-slate-500">Your generated content appears here.</p></div>{result && <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-700">Generated</span>}</div>
          {!result ? <div className="mt-6 rounded-2xl border border-dashed border-slate-300 bg-slate-50 px-5 py-12 text-center"><div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-100 text-xl text-violet-700">✦</div><p className="mt-4 font-bold text-slate-800">A great job post starts with a great brief.</p><p className="mx-auto mt-2 max-w-xs text-sm leading-6 text-slate-500">Complete the role details and generate your first draft. You stay in control of the final content.</p></div> : <div className="mt-5 space-y-5">
            <div><h3 className="text-xl font-black tracking-tight">{result.draft.title}</h3><p className="mt-2 text-sm leading-6 text-slate-600">{result.draft.summary}</p><div className="mt-3 flex flex-wrap gap-2">{[result.draft.experienceLevel, result.draft.employmentType.replaceAll('_',' '), result.draft.workMode].map((tag) => <span key={tag} className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-bold text-slate-600">{tag}</span>)}</div></div>
            <div className="grid grid-cols-2 gap-3 rounded-xl bg-violet-50 p-4"><div><p className="text-xs font-semibold text-violet-700">Quality score</p><p className="mt-1 text-2xl font-black text-violet-950">{result.quality.score}<span className="text-sm text-violet-500">/100</span></p></div><div><p className="text-xs font-semibold text-violet-700">Credits used</p><p className="mt-1 text-2xl font-black text-violet-950">{result.quota.creditsCharged}</p></div></div>
            {result.quality.issues.length > 0 && <div><h4 className="text-sm font-bold">Review suggestions</h4><ul className="mt-2 space-y-2">{result.quality.issues.map((issue, index) => <li key={index} className="rounded-xl border border-amber-200 bg-amber-50 p-3"><p className="text-xs font-bold text-amber-900">{issue.severity} · {issue.field ?? issue.code}</p><p className="mt-1 text-sm text-amber-800">{issue.message}</p>{issue.suggestion && <p className="mt-1 text-xs text-amber-700">{issue.suggestion}</p>}</li>)}</ul></div>}
            {([['Responsibilities', result.draft.responsibilities], ['Requirements', result.draft.requirements], ['Nice to have', result.draft.niceToHave], ['Benefits', result.draft.benefits]] as Array<[string,string[]]>).map(([heading, items]) => items.length > 0 && <div key={heading}><h4 className="text-sm font-bold text-slate-900">{heading}</h4><ul className="mt-2 space-y-2">{items.map((item, index) => <li key={index} className="flex gap-2 text-sm leading-6 text-slate-600"><span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-violet-500"/>{item}</li>)}</ul></div>)}
            <div><h4 className="text-sm font-bold">Skills</h4><div className="mt-2 flex flex-wrap gap-2">{result.draft.skills.map((skill) => <span key={skill.name} className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700">{skill.name}</span>)}</div></div>
            <button type="button" onClick={() => void copyDraft()} className="jf-button jf-button-secondary w-full">{copied ? '✓ Copied to clipboard' : 'Copy draft content'}</button>
            <p className="text-xs leading-5 text-slate-500">AI-generated content can contain inaccuracies. Review and edit every section before publishing. This draft is not published automatically.</p>
          </div>}
        </div>
        {result && <button type="button" onClick={() => { setResult(null); mutation.reset(); }} className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-bold text-slate-600 hover:bg-slate-50">Start a new draft</button>}
      </section>
    </div>
  </div>;
}
