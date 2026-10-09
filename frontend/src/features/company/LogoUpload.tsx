'use client';
import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { uploadCompanyLogo } from '@/lib/api/account';
import { companyLogoSrc } from '@/lib/api/companies';
import { errorMessage } from '@/lib/api/errors';
import { validateImageFile } from '@/lib/validation/image';

/** Owner-only logo control. The image is a public same-origin GET, so a cache-busting query param refreshes it. */
export function LogoUpload({ companyId, hasLogo }: { companyId: string; hasLogo: boolean }) {
  const qc = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [bust, setBust] = useState(() => Date.now());
  const [shown, setShown] = useState(hasLogo);
  const upload = useMutation({
    mutationFn: (file: File) => uploadCompanyLogo(companyId, file),
    onSuccess: () => { setError(null); setShown(true); setBust(Date.now()); void qc.invalidateQueries({ queryKey: ['company', 'me'] }); },
    onError: (e) => setError(errorMessage(e, 'Could not upload the logo.')),
  });
  const pick = (file: File | undefined) => {
    if (!file) return;
    const problem = validateImageFile(file);
    if (problem) { setError(problem); return; }
    upload.mutate(file);
    if (input.current) input.current.value = '';
  };
  return (
    <div className="flex items-center gap-4">
      {shown ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={`${companyLogoSrc(companyId)}?v=${bust}`} alt="Company logo" width={72} height={72} className="h-18 w-18 rounded-lg border object-contain" style={{ width: 72, height: 72 }} onError={() => setShown(false)} />
      ) : <span aria-hidden className="inline-flex items-center justify-center rounded-lg border bg-slate-100 text-xs text-slate-500" style={{ width: 72, height: 72 }}>No logo</span>}
      <div>
        <label htmlFor="company-logo" className="mb-1 block text-sm font-semibold">Company logo</label>
        <input id="company-logo" ref={input} type="file" accept="image/png,image/jpeg,image/webp" disabled={upload.isPending} onChange={(e) => pick(e.target.files?.[0])} />
        <p className="mt-1 text-xs text-slate-500">PNG, JPEG or WebP, up to 2 MB.</p>
        {upload.isPending && <p className="text-sm" role="status">Uploading…</p>}
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      </div>
    </div>
  );
}
