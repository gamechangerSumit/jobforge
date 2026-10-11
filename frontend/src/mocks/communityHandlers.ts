import { http, HttpResponse } from 'msw';
import { ids, meta } from './data';
import type { CommunityComment, Post } from '@/types/community';

/**
 * MSW handlers for API_CONTRACT 12.9 (community). They mirror the server rules the UI depends on: idempotent like/save,
 * counters, https-only metadata, 400 field errors, cursor pagination (opaque cursor = offset), max reply depth 2.
 */
const B = '/api/v1/community';
const ok = (data: unknown, extra: Record<string, unknown> = {}, init?: ResponseInit) => HttpResponse.json({ data, meta: meta(extra) }, init);
const fail = (status: number, code: string, message: string, details?: { field: string; code: string; message: string }[]) =>
  HttpResponse.json({ error: { code, message, details, requestId: 'mock' } }, { status });

const author = { id: ids.recruiter, handle: 'ravi_r', firstName: 'Ravi', lastName: 'Rao', avatarUrl: null, role: 'RECRUITER' as const };

export const communityIds = { first: 'c0000000-0000-4000-8000-000000000001', second: 'c0000000-0000-4000-8000-000000000002', comment: 'd0000000-0000-4000-8000-000000000001' };

let posts: Post[] = [];
let comments: CommunityComment[] = [];

function fixtures() {
  const now = Date.now();
  posts = [
    { id: communityIds.first, type: 'DISCUSSION', title: 'How do you prepare for system design rounds?', body: 'Share your routine.', tags: ['interviews'], typeMetadata: {},
      author, counts: { likes: 2, comments: 1, saves: 0, shares: 0 }, viewer: { liked: false, saved: false, following: false }, status: 'ACTIVE', createdAt: new Date(now - 3_600_000).toISOString(), editedAt: null },
    { id: communityIds.second, type: 'HIRING', title: 'Backend engineer at Acme', body: 'We are hiring in Nagpur.', tags: ['hiring', 'java'],
      typeMetadata: { companyName: 'Acme', roleTitle: 'Backend Engineer', applyUrl: 'https://acme.example/apply' },
      author, counts: { likes: 0, comments: 0, saves: 0, shares: 0 }, viewer: { liked: false, saved: false, following: false }, status: 'ACTIVE', createdAt: new Date(now - 7_200_000).toISOString(), editedAt: null },
  ];
  comments = [{ id: communityIds.comment, postId: communityIds.first, parentId: null, body: 'Mock interviews help a lot.', author,
    counts: { likes: 0, replies: 0 }, viewer: { liked: false }, status: 'ACTIVE', createdAt: new Date(now - 1_800_000).toISOString(), editedAt: null }];
}
fixtures();
export const resetCommunityMocks = fixtures;

const slice = <T,>(items: T[], url: URL) => {
  const limit = Math.min(Number(url.searchParams.get('limit') ?? 20) || 20, 50);
  const start = Number(url.searchParams.get('cursor') ?? 0) || 0;
  const part = items.slice(start, start + limit);
  const hasNext = start + limit < items.length;
  return ok(part, { cursor: { next: hasNext ? String(start + limit) : null, hasNext, limit } });
};

export const communityHandlers = (getUserId: () => string) => [
  http.get(`${B}/posts`, ({ request }) => {
    const url = new URL(request.url);
    const tag = url.searchParams.get('tag'); const type = url.searchParams.get('type'); const q = url.searchParams.get('q')?.toLowerCase();
    let rows = posts.filter((p) => p.status === 'ACTIVE');
    if (tag) rows = rows.filter((p) => p.tags.includes(tag));
    if (type) rows = rows.filter((p) => p.type === type);
    if (q) rows = rows.filter((p) => `${p.title ?? ''} ${p.body}`.toLowerCase().includes(q));
    if (url.searchParams.get('feed') === 'following') rows = rows.filter((p) => p.viewer.following);
    return slice(rows, url);
  }),
  http.get(`${B}/saved`, ({ request }) => slice(posts.filter((p) => p.viewer.saved), new URL(request.url))),
  http.post(`${B}/posts`, async ({ request }) => {
    const body = (await request.json()) as { type?: string; title?: string; body?: string; tags?: string[]; typeMetadata?: Record<string, string> };
    const details: { field: string; code: string; message: string }[] = [];
    if (!body.body || !body.body.trim()) details.push({ field: 'body', code: 'SIZE', message: 'must be between 1 and 10000 characters' });
    if (body.type === 'HIRING' && !body.typeMetadata?.companyName) details.push({ field: 'typeMetadata.companyName', code: 'REQUIRED', message: 'is required for HIRING' });
    for (const [k, v] of Object.entries(body.typeMetadata ?? {})) if (/url$/i.test(k) && v && !v.startsWith('https://')) details.push({ field: `typeMetadata.${k}`, code: 'URL', message: 'must be an https URL' });
    if (details.length) return fail(400, 'VALIDATION_FAILED', 'Validation failed.', details);
    const post: Post = { id: crypto.randomUUID(), type: body.type as Post['type'], title: body.title || null, body: body.body!.trim(), tags: body.tags ?? [], typeMetadata: body.typeMetadata ?? {},
      author: { ...author, id: getUserId() }, counts: { likes: 0, comments: 0, saves: 0, shares: 0 }, viewer: { liked: false, saved: false, following: false }, status: 'ACTIVE', createdAt: new Date().toISOString(), editedAt: null };
    posts.unshift(post);
    return ok(post, {}, { status: 201, headers: { Location: `/api/v1/community/posts/${post.id}` } });
  }),
  http.get(`${B}/posts/:id`, ({ params }) => {
    const post = posts.find((p) => p.id === params.id && p.status === 'ACTIVE');
    return post ? ok(post) : fail(404, 'RESOURCE_NOT_FOUND', 'The post was not found.');
  }),
  http.delete(`${B}/posts/:id`, ({ params }) => {
    const before = posts.length; posts = posts.filter((p) => p.id !== params.id);
    return posts.length === before ? fail(404, 'RESOURCE_NOT_FOUND', 'The post was not found.') : new HttpResponse(null, { status: 204 });
  }),
  http.put(`${B}/posts/:id/like`, ({ params }) => toggle(params.id as string, 'liked', 'likes', true)),
  http.delete(`${B}/posts/:id/like`, ({ params }) => toggle(params.id as string, 'liked', 'likes', false)),
  http.put(`${B}/posts/:id/save`, ({ params }) => toggle(params.id as string, 'saved', 'saves', true)),
  http.delete(`${B}/posts/:id/save`, ({ params }) => toggle(params.id as string, 'saved', 'saves', false)),
  http.post(`${B}/posts/:id/share`, ({ params }) => {
    const post = posts.find((p) => p.id === params.id);
    if (!post) return fail(404, 'RESOURCE_NOT_FOUND', 'The post was not found.');
    post.counts.shares += 1;
    return ok({ url: `http://localhost:3000/community/posts/${post.id}` });
  }),
  http.get(`${B}/posts/:id/comments`, ({ params, request }) => {
    const url = new URL(request.url); const parent = url.searchParams.get('parentId');
    return slice(comments.filter((c) => c.postId === params.id && (parent ? c.parentId === parent : c.parentId === null)), url);
  }),
  http.post(`${B}/posts/:id/comments`, async ({ params, request }) => {
    const body = (await request.json()) as { body?: string; parentId?: string };
    const post = posts.find((p) => p.id === params.id);
    if (!post) return fail(404, 'RESOURCE_NOT_FOUND', 'The post was not found.');
    if (!body.body?.trim()) return fail(400, 'VALIDATION_FAILED', 'Validation failed.', [{ field: 'body', code: 'SIZE', message: 'must be between 1 and 2000 characters' }]);
    const parent = body.parentId ? comments.find((c) => c.id === body.parentId) : undefined;
    if (parent?.parentId) return fail(422, 'BUSINESS_RULE_VIOLATED', 'Replies can only be nested 2 levels deep.');
    const comment: CommunityComment = { id: crypto.randomUUID(), postId: post.id, parentId: parent?.id ?? null, body: body.body.trim(), author: { ...author, id: getUserId() },
      counts: { likes: 0, replies: 0 }, viewer: { liked: false }, status: 'ACTIVE', createdAt: new Date().toISOString(), editedAt: null };
    comments.push(comment); post.counts.comments += 1; if (parent) parent.counts.replies += 1;
    return ok(comment, {}, { status: 201 });
  }),
  http.get(`${B}/tags/trending`, () => ok([{ slug: 'interviews', name: 'interviews', postCount: 4 }, { slug: 'java', name: 'java', postCount: 3 }])),
  http.get(`${B}/tags`, () => ok([{ slug: 'java', name: 'java' }])),
  http.put(`${B}/tags/:slug/follow`, () => new HttpResponse(null, { status: 204 })),
  http.delete(`${B}/tags/:slug/follow`, () => new HttpResponse(null, { status: 204 })),
  http.put('/api/v1/users/:id/follow', ({ params }) => { posts.forEach((p) => { if (p.author.id === params.id) p.viewer.following = true; }); return new HttpResponse(null, { status: 204 }); }),
  http.delete('/api/v1/users/:id/follow', ({ params }) => { posts.forEach((p) => { if (p.author.id === params.id) p.viewer.following = false; }); return new HttpResponse(null, { status: 204 }); }),
];

function toggle(id: string, flag: 'liked' | 'saved', counter: 'likes' | 'saves', on: boolean) {
  const post = posts.find((p) => p.id === id && p.status === 'ACTIVE');
  if (!post) return fail(404, 'RESOURCE_NOT_FOUND', 'The post was not found.');
  if (post.viewer[flag] !== on) { post.viewer[flag] = on; post.counts[counter] = Math.max(0, post.counts[counter] + (on ? 1 : -1)); }
  return new HttpResponse(null, { status: 204 });
}
