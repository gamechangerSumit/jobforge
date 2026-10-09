import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import { useMockServer } from '../msw-node';

vi.mock('next/navigation', () => ({ useParams: () => ({ id: '123e4567-e89b-42d3-a456-426614174000' }) }));
vi.mock('next/link', () => ({ default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a> }));

import AdminAuditPage from '@/app/admin/audit/page';
import AdminJobsPage from '@/app/admin/jobs/page';
import AdminRecruiterDetailPage from '@/app/admin/recruiters/[id]/page';
import AdminUsersPage from '@/app/admin/users/page';

useMockServer();

const wrap = (ui: React.ReactNode) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>);

test('users page lists users from the API', async () => {
  wrap(<AdminUsersPage />);
  expect(await screen.findByText(/asha_k/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Force logout' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Send invite' })).toBeDisabled();
});

test('jobs page offers removal of a published job', async () => {
  wrap(<AdminJobsPage />);
  expect(await screen.findByText(/Backend Engineer/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Remove' })).toBeInTheDocument();
});

test('audit page renders log rows', async () => {
  wrap(<AdminAuditPage />);
  expect(await screen.findByText('COMPANY_VERIFIED')).toBeInTheDocument();
});

test('recruiter detail shows the profile and decision buttons', async () => {
  wrap(<AdminRecruiterDetailPage />);
  expect(await screen.findByText('Rhea Patil')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Acme' })).toHaveAttribute('href');
});
