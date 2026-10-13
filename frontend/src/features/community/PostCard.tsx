'use client';
import Link from 'next/link';
import { useState } from 'react';
import { Avatar } from '@/components/common/Avatar';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { errorMessage } from '@/lib/api/errors';
import { useSession } from '@/lib/auth/session';
import { ReportButton } from '@/features/reports/ReportButton';
import { isHttpsUrl } from '@/lib/validation/community';
import { useDeletePost, usePostActions } from './hooks';
import { POST_TYPE_LABEL, fullName, when } from './labels';
import type { Post, TypeMetadata } from '@/types/community';

const META_LABEL: Partial<Record<keyof TypeMetadata, string>> = {
  companyName: 'Company', roleTitle: 'Role', location: 'Location', referralDetails: 'Referral details', codeLanguage: 'Language',
  applyUrl: 'Apply', repoUrl: 'Repository', liveUrl: 'Live demo', onlineUrl: 'Join online', registrationUrl: 'Register',
  startsAt: 'Starts', endsAt: 'Ends', techStack: 'Tech stack',
};
const URL_KEYS = new Set(['applyUrl', 'repoUrl', 'liveUrl', 'onlineUrl', 'registrationUrl']);

/** Plain-text metadata rows; links only render for https URLs (the API enforces it too) and never use unsafe schemes. */
function MetadataList({ meta }: { meta: TypeMetadata }) {
  const rows = (Object.keys(META_LABEL) as (keyof TypeMetadata)[]).filter((k) => meta[k] && (!Array.isArray(meta[k]) || (meta[k] as string[]).length));
  if (!rows.length) return null;
  return (
    <dl className="mt-3 grid gap-x-4 gap-y-1 rounded-lg bg-slate-50 p-3 text-sm sm:grid-cols-2">
      {rows.map((k) => {
        const raw = meta[k];
        const text = Array.isArray(raw) ? raw.join(', ') : k === 'startsAt' || k === 'endsAt' ? when(String(raw)) : String(raw);
        return (
          <div key={k}>
            <dt className="text-xs font-semibold uppercase text-slate-500">{META_LABEL[k]}</dt>
            <dd>{URL_KEYS.has(k) && isHttpsUrl(text)
              ? <a href={text} target="_blank" rel="noopener noreferrer nofollow" className="break-all underline">{text}</a> : text}</dd>
          </div>
        );
      })}
    </dl>
  );
}

export function PostCard({ post, full = false, onDeleted }: { post: Post; full?: boolean; onDeleted?: () => void }) {
  const { user } = useSession();
  const { like, save, follow, share } = usePostActions(post);
  const remove = useDeletePost();
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const mine = user?.id === post.author.id;
  const canDelete = mine || user?.role === 'ADMIN';
  const run = (p: Promise<unknown>) => { setError(null); p.catch((e) => setError(errorMessage(e, 'That did not work. Please try again.'))); };
  const name = fullName(post.author);

  const onShare = () => {
    setError(null);
    share.mutateAsync().then(async (r) => {
      try { await navigator.clipboard.writeText(r.url); setNotice('Link copied'); } catch { setNotice(r.url); }
      setTimeout(() => setNotice(null), 4000);
    }).catch((e) => setError(errorMessage(e, 'Could not create a share link.')));
  };
  const onDelete = () => {
    if (!window.confirm('Delete this post? This cannot be undone.')) return;
    remove.mutateAsync(post.id).then(() => onDeleted?.()).catch((e) => setError(errorMessage(e, 'Could not delete the post.')));
  };

  return (
    <Card className="space-y-3">
      <header className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <Avatar userId={post.author.id} name={name} hasAvatar={Boolean(post.author.avatarUrl)} />
          <div>
            <Link href={`/users/${post.author.id}`} className="font-semibold hover:underline">{name}</Link>
            <p className="text-xs text-slate-500">@{post.author.handle} · {when(post.createdAt)}{post.editedAt && ' · edited'}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold">{POST_TYPE_LABEL[post.type]}</span>
          {post.status !== 'ACTIVE' && <span className="rounded-full bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-900" role="status">{post.status === 'HIDDEN' ? 'Hidden by moderators' : 'Removed'}</span>}
        </div>
      </header>

      {post.title && <h2 className="text-lg font-bold">{full ? post.title : <Link href={`/community/posts/${post.id}`} className="hover:underline">{post.title}</Link>}</h2>}
      <p className={`whitespace-pre-wrap break-words text-sm leading-relaxed ${full ? '' : 'line-clamp-6'}`}>{post.body}</p>
      <MetadataList meta={post.typeMetadata} />
      {post.tags.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label="Tags">
          {post.tags.map((t) => <li key={t}><Link href={`/community?tag=${encodeURIComponent(t)}`} className="rounded-full border border-slate-300 px-2 py-0.5 text-xs hover:bg-slate-100">#{t}</Link></li>)}
        </ul>
      )}

      <footer className="flex flex-wrap items-center gap-2 border-t border-slate-100 pt-3 text-sm">
        <button type="button" aria-pressed={post.viewer.liked} disabled={like.isPending} onClick={() => run(like.mutateAsync(!post.viewer.liked))}
          className={`rounded-lg border px-3 py-1.5 font-semibold ${post.viewer.liked ? 'border-rose-300 bg-rose-50 text-rose-800' : 'border-slate-300'}`}>
          {post.viewer.liked ? 'Liked' : 'Like'} · {post.counts.likes}
        </button>
        <Link href={`/community/posts/${post.id}#comments`} className="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold">Comments · {post.counts.comments}</Link>
        <button type="button" aria-pressed={post.viewer.saved} disabled={save.isPending} onClick={() => run(save.mutateAsync(!post.viewer.saved))}
          className={`rounded-lg border px-3 py-1.5 font-semibold ${post.viewer.saved ? 'border-sky-300 bg-sky-50 text-sky-800' : 'border-slate-300'}`}>
          {post.viewer.saved ? 'Saved' : 'Save'}
        </button>
        <button type="button" disabled={share.isPending} onClick={onShare} className="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold">Share · {post.counts.shares}</button>
        {!mine && user && <button type="button" aria-pressed={post.viewer.following} disabled={follow.isPending} onClick={() => run(follow.mutateAsync(!post.viewer.following))}
          className="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold">{post.viewer.following ? 'Following' : `Follow @${post.author.handle}`}</button>}
        {mine && post.status === 'ACTIVE' && <Link href={`/community/posts/${post.id}?edit=1`} className="rounded-lg border border-slate-300 px-3 py-1.5 font-semibold">Edit</Link>}
        {canDelete && <Button type="button" variant="danger" onClick={onDelete} disabled={remove.isPending} className="min-h-0 px-3 py-1.5">Delete</Button>}
        {!mine && post.status === 'ACTIVE' && <span className="ml-auto"><ReportButton targetType="POST" targetId={post.id} /></span>}
      </footer>
      {notice && <p role="status" className="text-xs text-emerald-800">{notice}</p>}
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    </Card>
  );
}
