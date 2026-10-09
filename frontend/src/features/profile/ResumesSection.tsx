'use client';
import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { errorMessage } from '@/lib/api/errors';
import { deleteResume, downloadResume, listResumes, setPrimaryResume, uploadResume } from '@/lib/api/seeker';
import { Section } from './Section';

const MAX_RESUME_BYTES = 5 * 1024 * 1024;

export function ResumesSection() {
  const qc = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const q = useQuery({ queryKey: ['resumes'], queryFn: listResumes });
  const refresh = () => Promise.all([qc.invalidateQueries({ queryKey: ['resumes'] }), qc.invalidateQueries({ queryKey: ['seeker-profile'] })]);
  const fail = (e: unknown) => setError(errorMessage(e, 'Something went wrong. Please retry.'));
  const upload = useMutation({ mutationFn: uploadResume, onSuccess: () => { setError(null); return refresh(); }, onError: fail });
  const primary = useMutation({ mutationFn: setPrimaryResume, onSuccess: refresh, onError: fail });
  const remove = useMutation({ mutationFn: deleteResume, onSuccess: refresh, onError: fail });

  const pick = (file: File | undefined) => {
    if (!file) return;
    if (file.size > MAX_RESUME_BYTES) { setError('The file must be at most 5 MB.'); return; }
    if (!/\.(pdf|docx)$/i.test(file.name)) { setError('Only PDF and DOCX files are accepted.'); return; }
    upload.mutate(file);
    if (input.current) input.current.value = '';
  };
  const download = async (id: string, name: string) => {
    try {
      const url = URL.createObjectURL(await downloadResume(id));
      const a = document.createElement('a'); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url);
    } catch (e) { fail(e); }
  };

  return (
    <Section title="Resumes">
      {q.isLoading && <p>Loading…</p>}
      {q.isError && <p className="text-red-700">Could not load resumes. <button className="underline" onClick={() => q.refetch()}>Retry</button></p>}
      {q.data?.length === 0 && <p className="text-sm text-slate-600">No resume yet. Upload a PDF or DOCX (max 5 MB) to apply for jobs.</p>}
      <ul className="divide-y">
        {q.data?.map((r) => (
          <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
            <span>{r.fileName} {r.primary && <strong className="ml-1 rounded bg-emerald-100 px-2 py-0.5 text-xs">Primary</strong>}</span>
            <span className="flex gap-2">
              <Button type="button" onClick={() => void download(r.id, r.fileName)}>Download</Button>
              {!r.primary && <Button type="button" onClick={() => primary.mutate(r.id)}>Make primary</Button>}
              <Button type="button" variant="danger" onClick={() => remove.mutate(r.id)}>Delete</Button>
            </span>
          </li>
        ))}
      </ul>
      <div className="mt-4">
        <label className="mb-1 block text-sm font-semibold" htmlFor="resume-file">Upload resume</label>
        <input id="resume-file" ref={input} type="file" accept=".pdf,.docx" disabled={upload.isPending} onChange={(e) => pick(e.target.files?.[0])} />
        {upload.isPending && <p className="mt-1 text-sm">Uploading…</p>}
      </div>
      {error && <p role="alert" className="mt-2 text-sm text-red-600">{error}</p>}
    </Section>
  );
}
