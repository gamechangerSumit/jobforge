import type { UserRole } from './api';

export type PostType = 'DISCUSSION' | 'HIRING' | 'REFERRAL' | 'PROJECT' | 'CAREER_ADVICE' | 'TECHNICAL' | 'EVENT';
export const POST_TYPES: PostType[] = ['DISCUSSION', 'HIRING', 'REFERRAL', 'PROJECT', 'CAREER_ADVICE', 'TECHNICAL', 'EVENT'];

export type ModerationState = 'ACTIVE' | 'HIDDEN' | 'REMOVED';
export type FeedKind = 'latest' | 'following' | 'trending';
export type ShareChannel = 'COPY_LINK' | 'LINKEDIN' | 'X' | 'EMAIL';

/** UserSummary (API_CONTRACT §11). A deleted account is returned anonymised ("Deleted User"). */
export interface PostAuthor {
  id: string; handle: string; firstName: string; lastName: string; avatarUrl: string | null; role: UserRole | null;
}

/** Per-type metadata (API_CONTRACT §12.9). Only the keys allowed for the post type are ever present. */
export interface TypeMetadata {
  companyName?: string; roleTitle?: string; jobId?: string; location?: string; applyUrl?: string;
  referralDetails?: string; repoUrl?: string; liveUrl?: string; techStack?: string[];
  startsAt?: string; endsAt?: string; onlineUrl?: string; registrationUrl?: string; codeLanguage?: string;
}

export interface Post {
  id: string; type: PostType; title: string | null; body: string; tags: string[]; typeMetadata: TypeMetadata;
  author: PostAuthor;
  counts: { likes: number; comments: number; saves: number; shares: number };
  viewer: { liked: boolean; saved: boolean; following: boolean };
  status: ModerationState; createdAt: string; editedAt: string | null;
}

export interface CommunityComment {
  id: string; postId: string; parentId: string | null; body: string; author: PostAuthor;
  counts: { likes: number; replies: number }; viewer: { liked: boolean };
  status: ModerationState; createdAt: string; editedAt: string | null;
}

export interface TagSummary { slug: string; name: string; postCount?: number }

export interface FeedFilter { feed: FeedKind; type?: PostType; tag?: string; authorId?: string; q?: string }

export interface CreatePostRequest { type: PostType; title?: string; body: string; tags: string[]; typeMetadata?: TypeMetadata }
export interface EditPostRequest { title?: string; body?: string; tags?: string[]; typeMetadata?: TypeMetadata }
