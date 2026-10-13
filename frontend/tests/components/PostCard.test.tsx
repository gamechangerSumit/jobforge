import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiClientError } from '@/lib/api/client';
import type { Post } from '@/types/community';

const likePost = vi.fn(); const unlikePost = vi.fn(); const savePost = vi.fn(); const unsavePost = vi.fn();
vi.mock('@/lib/api/community', () => ({
  likePost: (...a: unknown[]) => likePost(...a), unlikePost: (...a: unknown[]) => unlikePost(...a),
  savePost: (...a: unknown[]) => savePost(...a), unsavePost: (...a: unknown[]) => unsavePost(...a),
  sharePost: vi.fn(), followUser: vi.fn(), unfollowUser: vi.fn(), deletePost: vi.fn(),
}));
vi.mock('@/lib/auth/session', () => ({ useSession: () => ({ user: { id: 'viewer-1', role: 'JOB_SEEKER' }, loading: false }) }));

import { PostCard } from '@/features/community/PostCard';

const post: Post = {
  id: 'p1', type: 'HIRING', title: 'Backend engineer', body: 'We are hiring.\n<script>alert(1)</script>', tags: ['java'],
  typeMetadata: { companyName: 'Acme', roleTitle: 'Dev', applyUrl: 'https://acme.example/apply', repoUrl: 'javascript:alert(1)' },
  author: { id: 'author-1', handle: 'ravi_r', firstName: 'Ravi', lastName: 'Rao', avatarUrl: null, role: 'RECRUITER' },
  counts: { likes: 1, comments: 2, saves: 0, shares: 0 }, viewer: { liked: false, saved: false, following: false },
  status: 'ACTIVE', createdAt: '2026-10-01T10:00:00Z', editedAt: null,
};

function renderCard() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false }, queries: { retry: false } } });
  render(<QueryClientProvider client={client}><PostCard post={post} /></QueryClientProvider>);
}

beforeEach(() => { [likePost, unlikePost, savePost, unsavePost].forEach((m) => m.mockReset().mockResolvedValue(undefined)); });

test('renders the body as plain text and only links https URLs', () => {
  renderCard();
  expect(screen.getByText(/We are hiring/)).toHaveTextContent('<script>alert(1)</script>'); // text, never HTML
  expect(document.querySelector('script')).toBeNull();
  const link = screen.getByRole('link', { name: 'https://acme.example/apply' });
  expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
});

test('like calls the API once and the toggle state follows the server answer', async () => {
  renderCard();
  await userEvent.click(screen.getByRole('button', { name: /^like/i }));
  await waitFor(() => expect(likePost).toHaveBeenCalledWith('p1'));
});

test('a failed like surfaces the API error message', async () => {
  likePost.mockRejectedValue(new ApiClientError({ code: 'RESOURCE_NOT_FOUND', message: 'The post was not found.' } as never, 404));
  renderCard();
  await userEvent.click(screen.getByRole('button', { name: /^like/i }));
  expect(await screen.findByRole('alert')).toHaveTextContent(/not found/i);
});

test('other people can report the post; there is no edit or delete for them', () => {
  renderCard();
  expect(screen.getByRole('button', { name: /report this post/i })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /^delete$/i })).toBeNull();
  expect(screen.queryByRole('link', { name: /^edit$/i })).toBeNull();
});
