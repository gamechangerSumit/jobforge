'use client';
import { useState } from 'react';
import { Avatar } from '@/components/common/Avatar';
import { MentionInput } from '@/components/common/MentionInput';
import { Button } from '@/components/ui/Button';
import { errorMessage } from '@/lib/api/errors';
import { useSession } from '@/lib/auth/session';
import { ReportButton } from '@/features/reports/ReportButton';
import { COMMENT_MAX, commentSchema } from '@/lib/validation/community';
import { useCommentMutations, useComments } from './hooks';
import { fullName, when } from './labels';
import type { CommunityComment } from '@/types/community';

function Composer({ label, initial = '', submitLabel, onSubmit, onCancel }: {
  label: string; initial?: string; submitLabel: string; onSubmit: (body: string) => Promise<unknown>; onCancel?: () => void;
}) {
  const [body, setBody] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const send = async () => {
    const parsed = commentSchema.safeParse({ body });
    if (!parsed.success) { setError(parsed.error.issues[0]?.message ?? 'Invalid comment'); return; }
    setBusy(true); setError(null);
    try { await onSubmit(parsed.data.body); setBody(''); } catch (e) { setError(errorMessage(e, 'Could not save the comment.')); } finally { setBusy(false); }
  };
  return (
    <div className="space-y-2">
      <MentionInput label={label} rows={3} value={body} onChange={setBody} maxLength={COMMENT_MAX} />
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <div className="flex items-center gap-3">
        <Button type="button" onClick={() => void send()} disabled={busy}>{busy ? 'Saving…' : submitLabel}</Button>
        {onCancel && <button type="button" className="text-sm font-semibold underline" onClick={onCancel}>Cancel</button>}
      </div>
    </div>
  );
}

function CommentItem({ comment, postId, topLevel }: { comment: CommunityComment; postId: string; topLevel: boolean }) {
  const { user } = useSession();
  const m = useCommentMutations(postId);
  const [mode, setMode] = useState<'view' | 'edit' | 'reply'>('view');
  const [showReplies, setShowReplies] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mine = user?.id === comment.author.id;
  const name = fullName(comment.author);
  const run = (p: Promise<unknown>) => { setError(null); p.catch((e) => setError(errorMessage(e, 'That did not work. Please try again.'))); };
  return (
    <li className="space-y-2" id={`comment-${comment.id}`}>
      <div className="flex gap-3">
        <Avatar userId={comment.author.id} name={name} size={32} hasAvatar={Boolean(comment.author.avatarUrl)} />
        <div className="min-w-0 flex-1">
          <p className="text-sm"><span className="font-semibold">{name}</span> <span className="text-xs text-slate-500">@{comment.author.handle} · {when(comment.createdAt)}{comment.editedAt && ' · edited'}</span></p>
          {mode === 'edit'
            ? <Composer label="Edit comment" initial={comment.body} submitLabel="Save" onCancel={() => setMode('view')}
                onSubmit={(body) => m.edit.mutateAsync({ id: comment.id, body }).then(() => setMode('view'))} />
            : <p className="whitespace-pre-wrap break-words text-sm">{comment.body}</p>}
          {mode !== 'edit' && (
            <div className="mt-1 flex flex-wrap items-center gap-3 text-xs font-semibold">
              <button type="button" aria-pressed={comment.viewer.liked} onClick={() => run(m.like.mutateAsync({ id: comment.id, on: !comment.viewer.liked }))}>
                {comment.viewer.liked ? 'Liked' : 'Like'} · {comment.counts.likes}
              </button>
              {topLevel && <button type="button" onClick={() => setMode(mode === 'reply' ? 'view' : 'reply')}>Reply</button>}
              {topLevel && comment.counts.replies > 0 && <button type="button" onClick={() => setShowReplies((s) => !s)}>{showReplies ? 'Hide' : 'Show'} replies ({comment.counts.replies})</button>}
              {mine && <button type="button" onClick={() => setMode('edit')}>Edit</button>}
              {(mine || user?.role === 'ADMIN') && <button type="button" className="text-red-700" onClick={() => { if (window.confirm('Delete this comment?')) run(m.remove.mutateAsync(comment.id)); }}>Delete</button>}
              {!mine && user && <ReportButton targetType="COMMENT" targetId={comment.id} />}
            </div>
          )}
          {error && <p role="alert" className="text-xs text-red-700">{error}</p>}
        </div>
      </div>
      {mode === 'reply' && (
        <div className="ml-11">
          <Composer label={`Reply to ${name}`} submitLabel="Reply" onCancel={() => setMode('view')}
            onSubmit={(body) => m.create.mutateAsync({ body, parentId: comment.id }).then(() => { setMode('view'); setShowReplies(true); })} />
        </div>
      )}
      {topLevel && showReplies && <div className="ml-11"><CommentList postId={postId} parentId={comment.id} /></div>}
    </li>
  );
}

function CommentList({ postId, parentId }: { postId: string; parentId?: string }) {
  const q = useComments(postId, parentId);
  const items = q.data?.pages.flatMap((p) => p.items) ?? [];
  if (q.isLoading) return <p role="status" className="text-sm text-slate-500">Loading comments…</p>;
  if (q.isError) return <p role="alert" className="text-sm text-red-700">Comments could not be loaded. <button type="button" className="underline" onClick={() => void q.refetch()}>Retry</button></p>;
  if (!items.length) return parentId ? null : <p className="text-sm text-slate-500">No comments yet. Start the conversation.</p>;
  return (
    <div className="space-y-3">
      <ul className="space-y-4">{items.map((c) => <CommentItem key={c.id} comment={c} postId={postId} topLevel={!parentId} />)}</ul>
      {q.hasNextPage && <button type="button" className="text-sm font-semibold underline" disabled={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>{q.isFetchingNextPage ? 'Loading…' : 'Load more'}</button>}
    </div>
  );
}

export function CommentSection({ postId }: { postId: string }) {
  const m = useCommentMutations(postId);
  return (
    <section id="comments" aria-labelledby="comments-heading" className="space-y-4">
      <h2 id="comments-heading" className="text-xl font-black">Comments</h2>
      <Composer label="Add a comment" submitLabel="Comment" onSubmit={(body) => m.create.mutateAsync({ body })} />
      <CommentList postId={postId} />
    </section>
  );
}
