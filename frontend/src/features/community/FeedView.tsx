'use client';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { errorMessage } from '@/lib/api/errors';
import { useSession } from '@/lib/auth/session';
import { parseTags } from '@/lib/validation/community';
import { POST_TYPES, type FeedKind, type PostType } from '@/types/community';
import { useFeed, useSavedPosts, useTagFollow, useTrendingTags } from './hooks';
import { POST_TYPE_LABEL } from './labels';
import { PostCard } from './PostCard';

const TABS: { key: FeedKind; label: string }[] = [{ key: 'latest', label: 'Latest' }, { key: 'following', label: 'Following' }, { key: 'trending', label: 'Trending' }];

function PostList({ q, empty, retry }: { q: ReturnType<typeof useFeed>; empty: string; retry: () => void }) {
  const items = q.data?.pages.flatMap((p) => p.items) ?? [];
  if (q.isLoading) return <p role="status" className="py-8 text-center text-slate-500">Loading posts…</p>;
  if (q.isError) return <Card><p role="alert" className="text-red-700">{errorMessage(q.error, 'Posts could not be loaded.')}</p><button type="button" className="mt-2 text-sm font-semibold underline" onClick={retry}>Try again</button></Card>;
  if (!items.length) return <Card><p className="text-slate-600">{empty}</p></Card>;
  return (
    <div className="space-y-4">
      {items.map((p) => <PostCard key={p.id} post={p} />)}
      {q.hasNextPage && <div className="text-center"><Button type="button" onClick={() => void q.fetchNextPage()} disabled={q.isFetchingNextPage}>{q.isFetchingNextPage ? 'Loading…' : 'Load more'}</Button></div>}
    </div>
  );
}

export function FeedView() {
  const { user, loading } = useSession();
  const params = useSearchParams();
  const [feed, setFeed] = useState<FeedKind>('latest');
  const [type, setType] = useState<PostType | ''>('');
  const [tag, setTag] = useState(() => params.get('tag') ?? '');
  const [search, setSearch] = useState('');
  const [applied, setApplied] = useState({ tag: params.get('tag') ?? '', q: '' });
  const trending = useTrendingTags();
  const followTag = useTagFollow();
  const [tagNote, setTagNote] = useState<string | null>(null);
  const tagValid = applied.tag === '' || /^[a-z0-9-]{2,30}$/.test(applied.tag);
  const feedQuery = useFeed({ feed, type: type || undefined, tag: tagValid ? applied.tag || undefined : undefined, q: applied.q || undefined });

  if (loading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-3xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to join the community.</div>;

  const applyFilters = (e: React.FormEvent) => {
    e.preventDefault();
    const t = parseTags(tag)[0] ?? '';
    setApplied({ tag: t, q: search.trim().length >= 2 ? search.trim() : '' });
  };
  const followCurrentTag = (on: boolean) => {
    setTagNote(null);
    followTag.mutateAsync({ slug: applied.tag, on }).then(() => setTagNote(on ? `Following #${applied.tag}` : `Unfollowed #${applied.tag}`)).catch((er) => setTagNote(errorMessage(er, 'Could not update tag follow.')));
  };

  return (
    <div className="mx-auto grid max-w-5xl gap-6 px-4 py-10 lg:grid-cols-[1fr_16rem]">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-3xl font-black">Community</h1>
          <div className="flex gap-3"><Link href="/community/saved" className="self-center text-sm font-semibold underline">Saved</Link><Link href="/community/new" className="inline-flex min-h-10 items-center rounded-lg bg-slate-900 px-4 text-sm font-semibold text-white">New post</Link></div>
        </div>
        <div role="tablist" aria-label="Feed" className="flex gap-2">
          {TABS.map((t) => <button key={t.key} role="tab" type="button" aria-selected={feed === t.key} onClick={() => setFeed(t.key)}
            className={`rounded-lg px-3 py-1.5 text-sm font-semibold ${feed === t.key ? 'bg-slate-900 text-white' : 'border border-slate-300'}`}>{t.label}</button>)}
        </div>
        <form onSubmit={applyFilters} className="grid gap-2 sm:grid-cols-[1fr_9rem_9rem_auto]" role="search" aria-label="Filter posts">
          <Input aria-label="Search posts" placeholder="Search posts" value={search} onChange={(e) => setSearch(e.target.value)} maxLength={100} />
          <Input aria-label="Filter by tag" placeholder="#tag" value={tag} onChange={(e) => setTag(e.target.value)} maxLength={31} />
          <Select aria-label="Post type" value={type} onChange={(e) => setType(e.target.value as PostType | '')}>
            <option value="">All types</option>{POST_TYPES.map((t) => <option key={t} value={t}>{POST_TYPE_LABEL[t]}</option>)}
          </Select>
          <Button type="submit">Apply</Button>
        </form>
        {!tagValid && <p role="alert" className="text-sm text-red-700">Tags use 2–30 letters, digits or hyphens.</p>}
        {applied.tag && tagValid && (
          <p className="flex flex-wrap items-center gap-3 text-sm">Showing <strong>#{applied.tag}</strong>
            <button type="button" className="underline" onClick={() => followCurrentTag(true)}>Follow tag</button>
            <button type="button" className="underline" onClick={() => followCurrentTag(false)}>Unfollow</button>
            <button type="button" className="underline" onClick={() => { setApplied((a) => ({ ...a, tag: '' })); setTag(''); }}>Clear</button>
            {tagNote && <span role="status" className="text-slate-600">{tagNote}</span>}</p>
        )}
        <PostList q={feedQuery} retry={() => void feedQuery.refetch()}
          empty={feed === 'following' ? 'Follow people or tags to fill this feed.' : 'No posts yet. Be the first to share something.'} />
      </div>
      <aside aria-label="Trending tags" className="space-y-2">
        <Card>
          <h2 className="font-bold">Trending tags</h2>
          {trending.isLoading && <p role="status" className="text-sm text-slate-500">Loading…</p>}
          {trending.isError && <p className="text-sm text-slate-500">Tags unavailable right now.</p>}
          {trending.data?.length === 0 && <p className="text-sm text-slate-500">Nothing is trending yet.</p>}
          <ul className="mt-2 space-y-1 text-sm">{trending.data?.map((t) => <li key={t.slug}><button type="button" className="hover:underline" onClick={() => { setTag(t.slug); setApplied((a) => ({ ...a, tag: t.slug })); }}>#{t.slug}</button> <span className="text-slate-500">{t.postCount}</span></li>)}</ul>
        </Card>
      </aside>
    </div>
  );
}

export function SavedPostsView() {
  const { user, loading } = useSession();
  const q = useSavedPosts();
  if (loading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-3xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to see saved posts.</div>;
  const items = q.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10">
      <div className="flex items-center justify-between"><h1 className="text-3xl font-black">Saved posts</h1><Link href="/community" className="text-sm font-semibold underline">← Community</Link></div>
      {q.isLoading && <p role="status" className="text-slate-500">Loading…</p>}
      {q.isError && <p role="alert" className="text-red-700">{errorMessage(q.error, 'Saved posts could not be loaded.')}</p>}
      {!q.isLoading && !q.isError && !items.length && <Card><p className="text-slate-600">You have not saved any posts yet.</p></Card>}
      {items.map((p) => <PostCard key={p.id} post={p} />)}
      {q.hasNextPage && <Button type="button" onClick={() => void q.fetchNextPage()} disabled={q.isFetchingNextPage}>{q.isFetchingNextPage ? 'Loading…' : 'Load more'}</Button>}
    </div>
  );
}
