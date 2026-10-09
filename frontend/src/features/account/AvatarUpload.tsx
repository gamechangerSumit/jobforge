'use client';
import { useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Avatar } from '@/components/common/Avatar';
import { uploadAvatar } from '@/lib/api/account';
import { errorMessage } from '@/lib/api/errors';
import { validateImageFile } from '@/lib/validation/image';

export function AvatarUpload({ userId, name, initialHasAvatar, onChanged }: { userId: string; name: string; initialHasAvatar: boolean; onChanged?: () => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [has, setHas] = useState(initialHasAvatar);
  const [bust, setBust] = useState(() => Date.now());
  const upload = useMutation({
    mutationFn: uploadAvatar,
    onSuccess: () => { setError(null); setHas(true); setBust(Date.now()); onChanged?.(); },
    onError: (e) => setError(errorMessage(e, 'Could not upload the picture.')),
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
      <span key={bust}><Avatar userId={userId} name={name} size={72} hasAvatar={has} /></span>
      <div>
        <label htmlFor="avatar-file" className="mb-1 block text-sm font-semibold">Profile picture</label>
        <input id="avatar-file" ref={input} type="file" accept="image/png,image/jpeg,image/webp" disabled={upload.isPending} onChange={(e) => pick(e.target.files?.[0])} />
        <p className="mt-1 text-xs text-slate-500">PNG, JPEG or WebP, up to 2 MB.</p>
        {upload.isPending && <p className="text-sm" role="status">Uploading…</p>}
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      </div>
    </div>
  );
}
