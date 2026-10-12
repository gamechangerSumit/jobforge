import { render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';

const session = vi.hoisted(() => ({ user: null as { role: string } | null, loading: false }));
vi.mock('@/lib/auth/session', () => ({ useSession: () => session }));
vi.mock('next/link', () => ({
  default: ({ href, children, ...rest }: { href: string; children: React.ReactNode; [key: string]: unknown }) => <a href={href} {...rest}>{children}</a>,
}));

import AdminLayout from '@/app/admin/layout';
import RecruiterLayout from '@/app/recruiter/layout';

/** The analytics routes sit under the existing role-guarded layouts; the guard is UX only (the API enforces access). */
describe('analytics route protection', () => {
  test('a seeker cannot see the recruiter or admin analytics areas', () => {
    session.user = { role: 'JOB_SEEKER' };
    const a = render(<RecruiterLayout><p>recruiter analytics</p></RecruiterLayout>);
    expect(screen.getByRole('alert')).toHaveTextContent(/do not have access/);
    expect(screen.queryByText('recruiter analytics')).not.toBeInTheDocument();
    a.unmount();
    render(<AdminLayout><p>admin analytics</p></AdminLayout>);
    expect(screen.queryByText('admin analytics')).not.toBeInTheDocument();
  });

  test('a recruiter cannot see the admin analytics area; a signed-out visitor is sent to sign in', () => {
    session.user = { role: 'RECRUITER' };
    const a = render(<AdminLayout><p>admin analytics</p></AdminLayout>);
    expect(screen.queryByText('admin analytics')).not.toBeInTheDocument();
    a.unmount();
    session.user = null;
    render(<RecruiterLayout><p>recruiter analytics</p></RecruiterLayout>);
    expect(screen.getByRole('link', { name: 'sign in' })).toHaveAttribute('href', '/login');
  });

  test('matching roles see their content', () => {
    session.user = { role: 'RECRUITER' };
    render(<RecruiterLayout><p>recruiter analytics</p></RecruiterLayout>);
    expect(screen.getByText('recruiter analytics')).toBeInTheDocument();
  });
});
