import { Suspense } from 'react';
import { FeedView } from '@/features/community/FeedView';

export const metadata = { title: 'Community · JobForge' };

export default function CommunityPage() {
  return <Suspense fallback={<div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>}><FeedView /></Suspense>;
}
