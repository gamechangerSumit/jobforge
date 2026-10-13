import { Suspense } from 'react';
import { PostDetailView } from '@/features/community/PostDetailView';

export default async function PostPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Suspense fallback={<div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>}><PostDetailView id={id} /></Suspense>;
}
