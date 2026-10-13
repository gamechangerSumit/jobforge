import { expect, test } from 'vitest';
import { emptyPostForm, isHttpsUrl, metadataFor, parseTags, postFormSchema, commentSchema, toCreateRequest } from '@/lib/validation/community';

const base = { ...emptyPostForm, body: 'Hello community' };

test('parseTags lower-cases, strips #, splits on commas/spaces and removes duplicates', () => {
  expect(parseTags('#Java, spring-boot  SQL java')).toEqual(['java', 'spring-boot', 'sql']);
  expect(parseTags('')).toEqual([]);
});

test('only https URLs without credentials are accepted', () => {
  expect(isHttpsUrl('https://example.com/a')).toBe(true);
  expect(isHttpsUrl('http://example.com')).toBe(false);
  expect(isHttpsUrl('javascript:alert(1)')).toBe(false);
  expect(isHttpsUrl('https://user:pw@example.com')).toBe(false);
});

test('a plain discussion needs only a body', () => {
  expect(postFormSchema.safeParse(base).success).toBe(true);
  expect(postFormSchema.safeParse({ ...base, body: '   ' }).success).toBe(false);
  expect(postFormSchema.safeParse({ ...base, body: 'x'.repeat(10_001) }).success).toBe(false);
});

test('tags are limited to five valid slugs', () => {
  expect(postFormSchema.safeParse({ ...base, tags: 'a1 b2 c3 d4 e5 f6' }).success).toBe(false);
  expect(postFormSchema.safeParse({ ...base, tags: 'bad_tag' }).success).toBe(false);
  expect(postFormSchema.safeParse({ ...base, tags: 'java spring' }).success).toBe(true);
});

test('hiring and referral posts require company and role; referral also needs details', () => {
  expect(postFormSchema.safeParse({ ...base, type: 'HIRING' }).success).toBe(false);
  expect(postFormSchema.safeParse({ ...base, type: 'HIRING', companyName: 'Acme', roleTitle: 'Dev', applyUrl: 'https://acme.example' }).success).toBe(true);
  expect(postFormSchema.safeParse({ ...base, type: 'HIRING', companyName: 'Acme', roleTitle: 'Dev', applyUrl: 'http://acme.example' }).success).toBe(false);
  expect(postFormSchema.safeParse({ ...base, type: 'REFERRAL', companyName: 'Acme', roleTitle: 'Dev' }).success).toBe(false);
});

test('events need a start time and an end that is not before it', () => {
  expect(postFormSchema.safeParse({ ...base, type: 'EVENT' }).success).toBe(false);
  expect(postFormSchema.safeParse({ ...base, type: 'EVENT', startsAt: '2026-11-02T10:00', endsAt: '2026-11-01T10:00' }).success).toBe(false);
  expect(postFormSchema.safeParse({ ...base, type: 'EVENT', startsAt: '2026-11-01T10:00' }).success).toBe(true);
});

test('only the metadata keys of the chosen type are sent', () => {
  const parsed = postFormSchema.parse({ ...base, type: 'PROJECT', repoUrl: 'https://github.com/x/y', techStack: 'java, sql', companyName: 'ignored' });
  expect(metadataFor(parsed)).toEqual({ repoUrl: 'https://github.com/x/y', techStack: ['java', 'sql'] });
  const request = toCreateRequest(postFormSchema.parse({ ...base, tags: '#Java', title: 'Hi' }));
  expect(request).toEqual({ type: 'DISCUSSION', title: 'Hi', body: 'Hello community', tags: ['java'], typeMetadata: {} });
});

test('comments are 1–2000 characters', () => {
  expect(commentSchema.safeParse({ body: ' ' }).success).toBe(false);
  expect(commentSchema.safeParse({ body: 'x'.repeat(2001) }).success).toBe(false);
  expect(commentSchema.safeParse({ body: 'nice' }).success).toBe(true);
});
