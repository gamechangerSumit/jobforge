import { expect, test, type Page, type Route } from '@playwright/test';

/**
 * Community flows against a mocked API (page.route): only the Next.js dev server is needed, not the backend.
 * Session bootstrap is GET /api/v1/auth/me; every other call is answered by the stateful stub below.
 */
const API = '**/api/v1';
const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
const cursorMeta = (next: string | null = null) => ({ cursor: { next, hasNext: next !== null, limit: 20 } });

const POST_ID = '11111111-1111-4111-8111-111111111111';
const author = { id: 'u-author', handle: 'ravi_r', firstName: 'Ravi', lastName: 'Rao', avatarUrl: null, role: 'RECRUITER' };
const basePost = {
  id: POST_ID, type: 'DISCUSSION', title: 'Interview prep tips', body: 'What worked for you?', tags: ['interviews'], typeMetadata: {},
  author, counts: { likes: 1, comments: 0, saves: 0, shares: 0 }, viewer: { liked: false, saved: false, following: false },
  status: 'ACTIVE', createdAt: '2026-10-01T10:00:00Z', editedAt: null,
};

async function mock(page: Page, role: 'JOB_SEEKER' | 'ADMIN' = 'JOB_SEEKER') {
  let post: Record<string, unknown> & { counts: Record<string, number>; viewer: Record<string, boolean> } = JSON.parse(JSON.stringify(basePost));
  let comments: Record<string, unknown>[] = [];
  const created: unknown[] = [];
  const calls: { method: string; path: string; body?: unknown }[] = [];
  let rejectNextPost: { status: number; code: string; message: string } | null = null;

  await page.route(`${API}/**`, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    const method = request.method();
    const body = request.postData() ? JSON.parse(request.postData() as string) : undefined;
    if (path === '/auth/me') return json(route, { data: { id: 'u-me', email: 'me@example.test', role, handle: 'me', firstName: 'Mia', lastName: 'Me' } });
    if (path.startsWith('/notifications')) return json(route, { data: [], meta: { page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } } });
    if (path === '/community/tags/trending') return json(route, { data: [{ slug: 'interviews', name: 'interviews', postCount: 3 }] });
    if (path === '/community/posts' && method === 'GET') return json(route, { data: [post], meta: cursorMeta() });
    if (path === '/community/posts' && method === 'POST') {
      calls.push({ method, path, body });
      if (rejectNextPost) { const e = rejectNextPost; rejectNextPost = null; return json(route, { error: { code: e.code, message: e.message } }, e.status); }
      created.push(body);
      return json(route, { data: { ...basePost, id: '22222222-2222-4222-8222-222222222222', ...(body as object), author: { ...author, id: 'u-me', handle: 'me' } } }, 201);
    }
    if (path === `/community/posts/${POST_ID}` && method === 'GET') return json(route, { data: post });
    if (path === `/community/posts/${POST_ID}/like`) {
      calls.push({ method, path });
      const on = method === 'PUT';
      if (post.viewer.liked !== on) { post.viewer.liked = on; post.counts.likes += on ? 1 : -1; }
      return route.fulfill({ status: 204 });
    }
    if (path === `/community/posts/${POST_ID}/comments` && method === 'GET') return json(route, { data: comments, meta: cursorMeta() });
    if (path === `/community/posts/${POST_ID}/comments` && method === 'POST') {
      calls.push({ method, path, body });
      const c = { id: '33333333-3333-4333-8333-333333333333', postId: POST_ID, parentId: null, body: (body as { body: string }).body, author: { ...author, id: 'u-me', handle: 'me' },
        counts: { likes: 0, replies: 0 }, viewer: { liked: false }, status: 'ACTIVE', createdAt: new Date().toISOString(), editedAt: null };
      comments = [c]; post = { ...post, counts: { ...post.counts, comments: 1 } };
      return json(route, { data: c }, 201);
    }
    if (path === '/reports' && method === 'POST') { calls.push({ method, path, body }); return json(route, { data: { id: 'r1', status: 'OPEN' } }, 201); }
    return json(route, { error: { code: 'RESOURCE_NOT_FOUND', message: 'Not mocked' } }, 404);
  });
  return { calls, created, failNextPost: (e: { status: number; code: string; message: string }) => { rejectNextPost = e; } };
}

test('feed shows posts, trending tags and the viewer can like a post', async ({ page }) => {
  const m = await mock(page);
  await page.goto('/community');
  await expect(page.getByRole('heading', { name: 'Community' })).toBeVisible();
  await expect(page.getByText('Interview prep tips')).toBeVisible();
  await expect(page.getByRole('complementary', { name: 'Trending tags' }).getByText('#interviews')).toBeVisible();
  await page.getByRole('button', { name: /^Like · 1/ }).click();
  await expect(page.getByRole('button', { name: /^Liked · 2/ })).toBeVisible();
  expect(m.calls).toContainEqual({ method: 'PUT', path: `/community/posts/${POST_ID}/like` });
});

test('creating a post validates first, then sends only the allowed metadata', async ({ page }) => {
  const m = await mock(page);
  await page.goto('/community/new');
  await page.getByRole('button', { name: 'Publish post' }).click();
  await expect(page.getByText('Write something first')).toBeVisible();
  expect(m.calls).toEqual([]);

  await page.getByLabel('Post type').selectOption('HIRING');
  await page.getByLabel('Your post').fill('We are hiring a backend engineer.');
  await page.getByLabel('Company').fill('Acme');
  await page.getByLabel('Role').fill('Backend Engineer');
  await page.getByLabel('Apply link (https)').fill('http://insecure.example');
  await page.getByRole('button', { name: 'Publish post' }).click();
  await expect(page.getByText('Must be an https URL')).toBeVisible();

  await page.getByLabel('Apply link (https)').fill('https://acme.example/apply');
  await page.getByLabel(/^Tags/).fill('#Hiring java');
  await page.getByRole('button', { name: 'Publish post' }).click();
  await expect.poll(() => m.created.length).toBe(1);
  expect(m.created[0]).toMatchObject({ type: 'HIRING', tags: ['hiring', 'java'], typeMetadata: { companyName: 'Acme', roleTitle: 'Backend Engineer', applyUrl: 'https://acme.example/apply' } });
});

test('anti-spam answers from the server are shown to the author', async ({ page }) => {
  const m = await mock(page);
  m.failNextPost({ status: 429, code: 'RATE_LIMITED', message: 'You are posting too quickly. Please wait a few minutes.' });
  await page.goto('/community/new');
  await page.getByLabel('Your post').fill('Another post');
  await page.getByRole('button', { name: 'Publish post' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'posting too quickly' })).toBeVisible();
});

test('post detail: add a comment and report the post', async ({ page }) => {
  const m = await mock(page);
  await page.goto(`/community/posts/${POST_ID}`);
  await expect(page.getByRole('heading', { name: 'Interview prep tips' })).toBeVisible();
  await expect(page.getByText('No comments yet. Start the conversation.')).toBeVisible();
  await page.getByLabel('Add a comment').fill('Mock interviews helped me most.');
  await page.getByRole('button', { name: 'Comment', exact: true }).click();
  await expect(page.getByText('Mock interviews helped me most.')).toBeVisible();
  expect(m.calls).toContainEqual({ method: 'POST', path: `/community/posts/${POST_ID}/comments`, body: { body: 'Mock interviews helped me most.' } });

  await page.getByRole('button', { name: 'Report this post' }).click();
  await page.getByLabel('Reason').selectOption('SPAM');
  await page.getByRole('button', { name: 'Send report' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'your report was sent' })).toBeVisible();
  expect(m.calls.at(-1)).toEqual({ method: 'POST', path: '/reports', body: { targetType: 'POST', targetId: POST_ID, reason: 'SPAM' } });
});

test('a post that is no longer available shows a friendly message', async ({ page }) => {
  await mock(page);
  await page.goto('/community/posts/99999999-9999-4999-8999-999999999999');
  await expect(page.getByText('This post is no longer available.')).toBeVisible();
});
