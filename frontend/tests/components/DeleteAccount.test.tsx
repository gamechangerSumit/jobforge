import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';

const deleteAccount = vi.fn();
const replace = vi.fn();
const refresh = vi.fn().mockResolvedValue(undefined);
const session = vi.fn();
vi.mock('@/lib/api/account', () => ({ deleteAccount: (p: string) => deleteAccount(p) }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }) }));
vi.mock('@/lib/auth/session', () => ({ useSession: () => session() }));

import { DeleteAccount } from '@/features/account/DeleteAccount';

const wrap = () => render(<QueryClientProvider client={new QueryClient()}><DeleteAccount /></QueryClientProvider>);
beforeEach(() => { deleteAccount.mockReset(); replace.mockReset(); session.mockReturnValue({ user: { role: 'JOB_SEEKER' }, refresh }); });

test('requires password and typed confirmation before enabling the button', async () => {
  wrap();
  await userEvent.click(screen.getByRole('button', { name: /Delete my account/ }));
  const submit = screen.getByRole('button', { name: 'Permanently delete account' });
  expect(submit).toBeDisabled();
  await userEvent.type(screen.getByLabelText('Confirm your password'), 'Secret123');
  expect(submit).toBeDisabled();
  await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'DELETE');
  expect(submit).toBeEnabled();
});

test('sends the password and redirects home on success', async () => {
  deleteAccount.mockResolvedValue(undefined);
  wrap();
  await userEvent.click(screen.getByRole('button', { name: /Delete my account/ }));
  await userEvent.type(screen.getByLabelText('Confirm your password'), 'Secret123');
  await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'DELETE');
  await userEvent.click(screen.getByRole('button', { name: 'Permanently delete account' }));
  await waitFor(() => expect(deleteAccount).toHaveBeenCalledWith('Secret123'));
  await waitFor(() => expect(replace).toHaveBeenCalledWith('/'));
});

test('shows the server error and does not redirect', async () => {
  const { ApiClientError } = await import('@/lib/api/client');
  deleteAccount.mockRejectedValue(new ApiClientError({ code: 'AUTH_INVALID_CREDENTIALS', message: 'Wrong password.' } as never, 401));
  wrap();
  await userEvent.click(screen.getByRole('button', { name: /Delete my account/ }));
  await userEvent.type(screen.getByLabelText('Confirm your password'), 'bad');
  await userEvent.type(screen.getByLabelText('Type DELETE to confirm'), 'DELETE');
  await userEvent.click(screen.getByRole('button', { name: 'Permanently delete account' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Wrong password.');
  expect(replace).not.toHaveBeenCalled();
});

test('admins cannot self-delete', () => {
  session.mockReturnValue({ user: { role: 'ADMIN' }, refresh });
  wrap();
  expect(screen.queryByRole('button', { name: /Delete my account/ })).not.toBeInTheDocument();
  expect(screen.getByText(/cannot be self-deleted/)).toBeInTheDocument();
});
