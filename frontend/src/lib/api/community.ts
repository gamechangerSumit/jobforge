import { api } from './client';
import type { CursorPage } from './client';
import type {
  CommunityComment, CreatePostRequest, EditPostRequest, FeedFilter, Post, ShareChannel, TagSummary,
} from '@/types/community';

const qs = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== '') search.set(k, String(v)); });
  const text = search.toString();
  return text ? `?${text}` : '';
};

const PAGE = 20;
const base = '/community';

/** GET /community/posts: cursor-paginated feed (latest | following | trending). */
export const listPosts = (f: FeedFilter, cursor?: string): Promise<CursorPage<Post>> =>
  api.getCursorPage<Post>(`${base}/posts${qs({ feed: f.feed, type: f.type, tag: f.tag, authorId: f.authorId, q: f.q, cursor, limit: PAGE })}`);

export const listSavedPosts = (cursor?: string) => api.getCursorPage<Post>(`${base}/saved${qs({ cursor, limit: PAGE })}`);
export const getPost = (id: string) => api.get<Post>(`${base}/posts/${id}`);
export const createPost = (body: CreatePostRequest) => api.post<Post>(`${base}/posts`, body);
export const editPost = (id: string, body: EditPostRequest) => api.patch<Post>(`${base}/posts/${id}`, body);
export const deletePost = (id: string) => api.delete<void>(`${base}/posts/${id}`);

export const likePost = (id: string) => api.put<void>(`${base}/posts/${id}/like`);
export const unlikePost = (id: string) => api.delete<void>(`${base}/posts/${id}/like`);
export const savePost = (id: string) => api.put<void>(`${base}/posts/${id}/save`);
export const unsavePost = (id: string) => api.delete<void>(`${base}/posts/${id}/save`);
export const sharePost = (id: string, channel: ShareChannel) => api.post<{ url: string }>(`${base}/posts/${id}/share`, { channel });

export const listComments = (postId: string, parentId?: string, cursor?: string) =>
  api.getCursorPage<CommunityComment>(`${base}/posts/${postId}/comments${qs({ parentId, cursor, limit: PAGE })}`);
export const createComment = (postId: string, body: string, parentId?: string) =>
  api.post<CommunityComment>(`${base}/posts/${postId}/comments`, parentId ? { body, parentId } : { body });
export const editComment = (id: string, body: string) => api.patch<CommunityComment>(`${base}/comments/${id}`, { body });
export const deleteComment = (id: string) => api.delete<void>(`${base}/comments/${id}`);
export const likeComment = (id: string) => api.put<void>(`${base}/comments/${id}/like`);
export const unlikeComment = (id: string) => api.delete<void>(`${base}/comments/${id}/like`);

export const followUser = (id: string) => api.put<void>(`/users/${id}/follow`);
export const unfollowUser = (id: string) => api.delete<void>(`/users/${id}/follow`);
export const followTag = (slug: string) => api.put<void>(`${base}/tags/${encodeURIComponent(slug)}/follow`);
export const unfollowTag = (slug: string) => api.delete<void>(`${base}/tags/${encodeURIComponent(slug)}/follow`);
export const searchTags = (q: string) => api.get<TagSummary[]>(`${base}/tags${qs({ q })}`);
export const trendingTags = () => api.get<TagSummary[]>(`${base}/tags/trending`);
