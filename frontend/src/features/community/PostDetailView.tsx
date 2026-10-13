'use client';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Card } from '@/components/ui/Card';
import { useSession } from '@/lib/auth/session';
import { CommentSection } from './CommentSection';
import { isNotFound, usePost } from './hooks';
import { PostCard } from './PostCard';
import { PostComposer } from './PostComposer';

export function PostDetailView({ id }: { id: string }) {
  const { user, loading } = useSession();
  const router = useRouter();
  const editing = useSearchParams().get('edit') === '1';
  const q = usePost(id);
  const back = <Link href="/community" className="text-sm font-semibold underline">← Community</Link>;
  if (loading || q.isLoading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-3xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to read this post.</div>;
  if (q.isError) return (
    <div className="mx-auto max-w-3xl space-y-3 px-4 py-10">{back}
      <Card><p role="alert" className="text-slate-700">{isNotFound(q.error) ? 'This post is no longer available.' : 'The post could not be loaded.'}</p>
        {!isNotFound(q.error) && <button type="button" className="mt-2 text-sm font-semibold underline" onClick={() => void q.refetch()}>Try again</button>}</Card></div>
  );
  const post = q.data!;
  const canEdit = editing && post.author.id === user.id && post.status === 'ACTIVE';
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10">
      {back}
      {canEdit
        ? <PostComposer post={post} onSaved={() => router.replace(`/community/posts/${id}`)} onCancel={() => router.replace(`/community/posts/${id}`)} />
        : <PostCard post={post} full onDeleted={() => router.replace('/community')} />}
      {post.status === 'ACTIVE' && <CommentSection postId={id} />}
    </div>
  );
}
