'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { Avatar } from '@/components/common/Avatar';
import { Card } from '@/components/ui/Card';
import { ReportButton } from '@/features/reports/ReportButton';
import { getPublicCard } from '@/lib/api/users';
import { useSession } from '@/lib/auth/session';

const UUID = /^[0-9a-fA-F-]{36}$/;
const ROLE_LABEL: Record<string, string> = { JOB_SEEKER: 'Job seeker', RECRUITER: 'Recruiter', ADMIN: 'Administrator' };

/** GET /users/{id}/public is authenticated (any role), so signed-out visitors are sent to sign in. */
export default function PublicUserPage() {
  const { id } = useParams<{ id: string }>();
  const { user, loading } = useSession();
  const valid = UUID.test(id ?? '');
  const card = useQuery({ queryKey: ['user', 'public', id], queryFn: () => getPublicCard(id), enabled: valid && Boolean(user), retry: false });
  if (loading) return <div className="mx-auto max-w-2xl px-4 py-10" role="status">Loading…</div>;
  if (!user) return <div className="mx-auto max-w-2xl px-4 py-10">Please <Link className="underline" href="/login">sign in</Link> to view profiles.</div>;
  if (!valid || card.isError) return <div className="mx-auto max-w-2xl px-4 py-10" role="alert">This profile is not available.</div>;
  if (card.isLoading || !card.data) return <div className="mx-auto max-w-2xl px-4 py-10">Loading…</div>;
  const c = card.data;
  const name = `${c.firstName} ${c.lastName}`;
  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <Card>
        <div className="flex items-center gap-4">
          <Avatar userId={c.id} name={name} size={72} />
          <div>
            <h1 className="text-2xl font-black">{name}</h1>
            <p className="text-sm text-slate-600">@{c.handle} · {ROLE_LABEL[c.role] ?? c.role}</p>
            {c.headline && <p className="mt-2 text-slate-800">{c.headline}</p>}
          </div>
        </div>
        {user.id !== c.id && <div className="mt-5 border-t pt-4"><ReportButton targetType="USER" targetId={c.id} /></div>}
      </Card>
    </div>
  );
}
