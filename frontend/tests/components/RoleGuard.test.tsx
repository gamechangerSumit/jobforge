import { render, screen } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

const session = vi.fn();
vi.mock('@/lib/auth/session', () => ({ useSession: () => session() }));

import { RoleGuard } from '@/components/layout/RoleGuard';

beforeEach(() => session.mockReset());

test('shows a loading status while the session resolves', () => {
  session.mockReturnValue({ user: null, loading: true });
  render(<RoleGuard role="ADMIN"><p>secret</p></RoleGuard>);
  expect(screen.getByRole('status')).toBeInTheDocument();
  expect(screen.queryByText('secret')).not.toBeInTheDocument();
});

test('asks anonymous visitors to sign in', () => {
  session.mockReturnValue({ user: null, loading: false });
  render(<RoleGuard role="ADMIN"><p>secret</p></RoleGuard>);
  expect(screen.getByRole('link', { name: 'sign in' })).toHaveAttribute('href', '/login');
  expect(screen.queryByText('secret')).not.toBeInTheDocument();
});

test('blocks a signed-in user with the wrong role', () => {
  session.mockReturnValue({ user: { role: 'JOB_SEEKER' }, loading: false });
  render(<RoleGuard role="ADMIN"><p>secret</p></RoleGuard>);
  expect(screen.getByRole('alert')).toHaveTextContent('do not have access');
  expect(screen.queryByText('secret')).not.toBeInTheDocument();
});

test('renders children for the right role', () => {
  session.mockReturnValue({ user: { role: 'ADMIN' }, loading: false });
  render(<RoleGuard role="ADMIN"><p>secret</p></RoleGuard>);
  expect(screen.getByText('secret')).toBeInTheDocument();
});
