'use client';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData, type QueryClient } from '@tanstack/react-query';
import * as community from '@/lib/api/community';
import { ApiClientError } from '@/lib/api/client';
import type { CursorPage } from '@/lib/api/client';
import type { CommunityComment, CreatePostRequest, EditPostRequest, FeedFilter, Post } from '@/types/community';

export const communityKeys = {
  all: ['community'] as const,
  feed: (f: FeedFilter) => ['community', 'feed', f] as const,
  saved: ['community', 'saved'] as const,
  post: (id: string) => ['community', 'post', id] as const,
  comments: (postId: string, parentId?: string) => ['community', 'comments', postId, parentId ?? 'top'] as const,
  tags: ['community', 'tags'] as const,
};

export const isNotFound = (e: unknown) => e instanceof ApiClientError && e.status === 404;

export function useFeed(filter: FeedFilter) {
  return useInfiniteQuery({
    queryKey: communityKeys.feed(filter),
    queryFn: ({ pageParam }) => community.listPosts(filter, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last: CursorPage<Post>) => (last.hasNext ? last.next ?? undefined : undefined),
    retry: (n, e) => !(e instanceof ApiClientError && e.status < 500) && n < 2,
  });
}

export function useSavedPosts() {
  return useInfiniteQuery({
    queryKey: communityKeys.saved,
    queryFn: ({ pageParam }) => community.listSavedPosts(pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last: CursorPage<Post>) => (last.hasNext ? last.next ?? undefined : undefined),
  });
}

export function usePost(id: string) {
  return useQuery({ queryKey: communityKeys.post(id), queryFn: () => community.getPost(id), enabled: Boolean(id), retry: (n, e) => !isNotFound(e) && n < 2 });
}

/** Replaces one post inside every cached feed/saved page and the detail cache (no refetch needed after a toggle). */
export function patchPost(qc: QueryClient, id: string, change: (p: Post) => Post) {
  qc.setQueriesData<InfiniteData<CursorPage<Post>>>({ queryKey: [...communityKeys.all, 'feed'] }, (data) => patchPages(data, id, change));
  qc.setQueryData<InfiniteData<CursorPage<Post>>>(communityKeys.saved, (data) => patchPages(data, id, change));
  qc.setQueryData<Post>(communityKeys.post(id), (p) => (p ? change(p) : p));
}

function patchPages(data: InfiniteData<CursorPage<Post>> | undefined, id: string, change: (p: Post) => Post) {
  if (!data) return data;
  return { ...data, pages: data.pages.map((pg) => ({ ...pg, items: pg.items.map((p) => (p.id === id ? change(p) : p)) })) };
}

export function useCreatePost() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (body: CreatePostRequest) => community.createPost(body), onSuccess: () => { void qc.invalidateQueries({ queryKey: [...communityKeys.all, 'feed'] }); void qc.invalidateQueries({ queryKey: communityKeys.tags }); } });
}

export function useEditPost(id: string) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (body: EditPostRequest) => community.editPost(id, body), onSuccess: (post) => { patchPost(qc, id, () => post); } });
}

export function useDeletePost() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => community.deletePost(id), onSuccess: (_d, id) => { qc.removeQueries({ queryKey: communityKeys.post(id) }); void qc.invalidateQueries({ queryKey: communityKeys.all }); } });
}

/** Like / save / follow / share toggles for one post. Counters update from the known server semantics (idempotent set/unset). */
export function usePostActions(post: Post) {
  const qc = useQueryClient();
  const flip = (field: 'liked' | 'saved', counter: 'likes' | 'saves', on: boolean) => (p: Post): Post => {
    if (p.viewer[field] === on) return p;
    return { ...p, viewer: { ...p.viewer, [field]: on }, counts: { ...p.counts, [counter]: Math.max(0, p.counts[counter] + (on ? 1 : -1)) } };
  };
  const like = useMutation({
    mutationFn: (on: boolean) => (on ? community.likePost(post.id) : community.unlikePost(post.id)),
    onSuccess: (_d, on) => patchPost(qc, post.id, flip('liked', 'likes', on)),
  });
  const save = useMutation({
    mutationFn: (on: boolean) => (on ? community.savePost(post.id) : community.unsavePost(post.id)),
    onSuccess: (_d, on) => { patchPost(qc, post.id, flip('saved', 'saves', on)); void qc.invalidateQueries({ queryKey: communityKeys.saved }); },
  });
  const follow = useMutation({
    mutationFn: (on: boolean) => (on ? community.followUser(post.author.id) : community.unfollowUser(post.author.id)),
    onSuccess: (_d, on) => {
      qc.setQueriesData<InfiniteData<CursorPage<Post>>>({ queryKey: communityKeys.all }, (data) => data && 'pages' in data
        ? { ...data, pages: data.pages.map((pg) => ({ ...pg, items: pg.items.map((p) => (p.author.id === post.author.id ? { ...p, viewer: { ...p.viewer, following: on } } : p)) })) } : data);
      qc.setQueryData<Post>(communityKeys.post(post.id), (p) => (p ? { ...p, viewer: { ...p.viewer, following: on } } : p));
    },
  });
  const share = useMutation({
    mutationFn: () => community.sharePost(post.id, 'COPY_LINK'),
    onSuccess: () => patchPost(qc, post.id, (p) => ({ ...p, counts: { ...p.counts, shares: p.counts.shares + 1 } })),
  });
  return { like, save, follow, share };
}

// ---- comments
export function useComments(postId: string, parentId?: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: communityKeys.comments(postId, parentId),
    queryFn: ({ pageParam }) => community.listComments(postId, parentId, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last: CursorPage<CommunityComment>) => (last.hasNext ? last.next ?? undefined : undefined),
    enabled,
  });
}

export function useCommentMutations(postId: string) {
  const qc = useQueryClient();
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['community', 'comments', postId] });
    void qc.invalidateQueries({ queryKey: communityKeys.post(postId) });
    void qc.invalidateQueries({ queryKey: [...communityKeys.all, 'feed'] });
  };
  return {
    create: useMutation({ mutationFn: (v: { body: string; parentId?: string }) => community.createComment(postId, v.body, v.parentId), onSuccess: refresh }),
    edit: useMutation({ mutationFn: (v: { id: string; body: string }) => community.editComment(v.id, v.body), onSuccess: refresh }),
    remove: useMutation({ mutationFn: (id: string) => community.deleteComment(id), onSuccess: refresh }),
    like: useMutation({ mutationFn: (v: { id: string; on: boolean }) => (v.on ? community.likeComment(v.id) : community.unlikeComment(v.id)), onSuccess: () => { void qc.invalidateQueries({ queryKey: ['community', 'comments', postId] }); } }),
  };
}

export function useTrendingTags() {
  return useQuery({ queryKey: [...communityKeys.tags, 'trending'], queryFn: community.trendingTags, staleTime: 60_000 });
}

export function useTagFollow() {
  return useMutation({ mutationFn: (v: { slug: string; on: boolean }) => (v.on ? community.followTag(v.slug) : community.unfollowTag(v.slug)) });
}
