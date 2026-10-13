'use client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { PostComposer } from '@/features/community/PostComposer';
import { useSession } from '@/lib/auth/session';

export default function NewPostPage() {
  const { user, loading } = useSession();
  const router = useRouter();
  if (loading) return <div className="mx-auto max-w-3xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-3xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to post.</div>;
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10">
      <div className="flex items-center justify-between"><h1 className="text-3xl font-black">New post</h1><Link href="/community" className="text-sm font-semibold underline">← Community</Link></div>
      <PostComposer onSaved={(p) => router.push(`/community/posts/${p.id}`)} onCancel={() => router.push('/community')} />
    </div>
  );
}
