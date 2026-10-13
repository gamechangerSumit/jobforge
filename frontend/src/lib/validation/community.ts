import { z } from 'zod';
import { POST_TYPES, type CreatePostRequest, type PostType, type TypeMetadata } from '@/types/community';

/** Limits mirror API_CONTRACT §10: body 1–10000, comment 1–2000, ≤5 tags of [a-z0-9-]{2,30}. The server stays the authority. */
export const BODY_MAX = 10_000;
export const COMMENT_MAX = 2_000;
export const TITLE_MAX = 150;
export const TAGS_MAX = 5;
const SLUG = /^[a-z0-9-]{2,30}$/;

/** "#Java, spring-boot  sql" -> ['java','spring-boot','sql'] (lower-cased, de-duplicated). */
export function parseTags(raw: string): string[] {
  const out: string[] = [];
  for (const part of raw.split(/[\s,]+/)) {
    const tag = part.replace(/^#/, '').trim().toLowerCase();
    if (tag && !out.includes(tag)) out.push(tag);
  }
  return out;
}

export const isHttpsUrl = (value: string) => {
  try { const u = new URL(value); return u.protocol === 'https:' && !u.username && !u.password; } catch { return false; }
};

const optionalUrl = z.string().trim().max(500).refine((v) => v === '' || isHttpsUrl(v), 'Must be an https URL');
const optionalText = (max: number) => z.string().trim().max(max);

export const postFormSchema = z.object({
  type: z.enum(POST_TYPES as [PostType, ...PostType[]]),
  title: optionalText(TITLE_MAX),
  body: z.string().trim().min(1, 'Write something first').max(BODY_MAX, `At most ${BODY_MAX} characters`),
  tags: z.string().superRefine((raw, ctx) => {
    const tags = parseTags(raw);
    if (tags.length > TAGS_MAX) ctx.addIssue({ code: 'custom', message: `At most ${TAGS_MAX} tags` });
    if (tags.some((t) => !SLUG.test(t))) ctx.addIssue({ code: 'custom', message: 'Tags use 2–30 letters, digits or hyphens' });
  }),
  companyName: optionalText(200), roleTitle: optionalText(200), location: optionalText(200), applyUrl: optionalUrl,
  referralDetails: optionalText(2000), repoUrl: optionalUrl, liveUrl: optionalUrl, techStack: z.string().max(1000),
  startsAt: z.string(), endsAt: z.string(), onlineUrl: optionalUrl, registrationUrl: optionalUrl, codeLanguage: optionalText(200),
}).superRefine((v, ctx) => {
  const need = (field: keyof typeof v, label: string) => {
    if (!String(v[field] ?? '').trim()) ctx.addIssue({ code: 'custom', path: [field], message: `${label} is required` });
  };
  if (v.type === 'HIRING') { need('companyName', 'Company'); need('roleTitle', 'Role'); }
  if (v.type === 'REFERRAL') { need('companyName', 'Company'); need('roleTitle', 'Role'); need('referralDetails', 'Referral details'); }
  if (v.type === 'EVENT') {
    need('startsAt', 'Start time');
    if (v.startsAt && v.endsAt && new Date(v.endsAt) < new Date(v.startsAt)) ctx.addIssue({ code: 'custom', path: ['endsAt'], message: 'End must not be before start' });
  }
});

export type PostFormInput = z.input<typeof postFormSchema>;
export type PostFormOutput = z.output<typeof postFormSchema>;

export const emptyPostForm: PostFormInput = {
  type: 'DISCUSSION', title: '', body: '', tags: '', companyName: '', roleTitle: '', location: '', applyUrl: '', referralDetails: '',
  repoUrl: '', liveUrl: '', techStack: '', startsAt: '', endsAt: '', onlineUrl: '', registrationUrl: '', codeLanguage: '',
};

const iso = (local: string) => (local ? new Date(local).toISOString() : undefined);
const put = (meta: TypeMetadata, key: keyof TypeMetadata, value: string | undefined) => { if (value && value.trim()) (meta as Record<string, unknown>)[key] = value.trim(); };

/** Only the keys the API allows for the chosen type are sent (unknown keys are a 400). */
export function metadataFor(v: PostFormOutput): TypeMetadata {
  const m: TypeMetadata = {};
  switch (v.type) {
    case 'HIRING': put(m, 'companyName', v.companyName); put(m, 'roleTitle', v.roleTitle); put(m, 'location', v.location); put(m, 'applyUrl', v.applyUrl); break;
    case 'REFERRAL': put(m, 'companyName', v.companyName); put(m, 'roleTitle', v.roleTitle); put(m, 'referralDetails', v.referralDetails); break;
    case 'PROJECT': put(m, 'repoUrl', v.repoUrl); put(m, 'liveUrl', v.liveUrl);
      { const stack = v.techStack.split(',').map((t) => t.trim()).filter(Boolean); if (stack.length) m.techStack = stack; } break;
    case 'EVENT': put(m, 'startsAt', iso(v.startsAt)); put(m, 'endsAt', iso(v.endsAt)); put(m, 'location', v.location); put(m, 'onlineUrl', v.onlineUrl); put(m, 'registrationUrl', v.registrationUrl); break;
    case 'TECHNICAL': put(m, 'codeLanguage', v.codeLanguage); break;
    default: break;
  }
  return m;
}

export function toCreateRequest(v: PostFormOutput): CreatePostRequest {
  const request: CreatePostRequest = { type: v.type, body: v.body, tags: parseTags(v.tags), typeMetadata: metadataFor(v) };
  if (v.title) request.title = v.title;
  return request;
}

export const commentSchema = z.object({ body: z.string().trim().min(1, 'Write a comment first').max(COMMENT_MAX, `At most ${COMMENT_MAX} characters`) });
