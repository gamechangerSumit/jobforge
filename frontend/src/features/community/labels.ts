import type { PostType } from '@/types/community';

export const POST_TYPE_LABEL: Record<PostType, string> = {
  DISCUSSION: 'Discussion', HIRING: 'Hiring', REFERRAL: 'Referral', PROJECT: 'Project',
  CAREER_ADVICE: 'Career advice', TECHNICAL: 'Technical', EVENT: 'Event',
};

export const fullName = (a: { firstName: string; lastName: string }) => `${a.firstName} ${a.lastName}`.trim();

export const when = (iso: string) => new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
