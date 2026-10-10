import { expect, test } from 'vitest';
import { codePointLength, reportFormSchema, resolveFormSchema } from '@/lib/validation/reports';

test('report form needs a valid reason and caps details at 1000', () => {
  expect(reportFormSchema.safeParse({ reason: 'SPAM', details: '' }).success).toBe(true);
  expect(reportFormSchema.safeParse({ reason: 'OTHER', details: 'x'.repeat(1000) }).success).toBe(true);
  expect(reportFormSchema.safeParse({ reason: 'OTHER', details: 'x'.repeat(1001) }).success).toBe(false);
  expect(reportFormSchema.safeParse({ reason: undefined, details: '' }).success).toBe(false);
  expect(reportFormSchema.safeParse({ reason: 'BOGUS', details: '' }).success).toBe(false);
  expect(reportFormSchema.parse({ reason: 'SPAM', details: '  hi  ' }).details).toBe('hi');
});

const reason = 'This listing is a clear scam.';

test('resolve reason must be 10-500 characters', () => {
  expect(resolveFormSchema.safeParse({ action: 'DISMISS', reason, confirmed: false }).success).toBe(true);
  expect(resolveFormSchema.safeParse({ action: 'DISMISS', reason: 'too short', confirmed: false }).success).toBe(false);
  expect(resolveFormSchema.safeParse({ action: 'DISMISS', reason: '         x         ', confirmed: false }).success).toBe(false);
  expect(resolveFormSchema.safeParse({ action: 'DISMISS', reason: 'x'.repeat(501), confirmed: false }).success).toBe(false);
});

const parseReason = (value: string) => resolveFormSchema.safeParse({ action: 'DISMISS', reason: value, confirmed: false });
const emoji = '\u{1F600}'; // 1 code point, 2 UTF-16 units

test('resolve reason boundaries: 9 / 10 / 500 / 501 characters after trimming', () => {
  expect(parseReason('x'.repeat(9)).success).toBe(false);
  expect(parseReason('x'.repeat(10)).success).toBe(true);
  expect(parseReason('x'.repeat(500)).success).toBe(true);
  expect(parseReason('x'.repeat(501)).success).toBe(false);
  expect(parseReason(`   ${'x'.repeat(9)}   `).success).toBe(false);
  expect(parseReason(`   ${'x'.repeat(10)}   `).success).toBe(true);
  // padding beyond 500 raw characters does not count against the maximum
  expect(parseReason(`  ${'x'.repeat(500)}  `).success).toBe(true);
  expect(parseReason('').success).toBe(false);
});

test('resolve reason counts Unicode code points, not UTF-16 units (matches the backend)', () => {
  expect(codePointLength(emoji)).toBe(1);
  expect(emoji.length).toBe(2);
  expect(parseReason(emoji.repeat(9)).success).toBe(false);   // 18 UTF-16 units, 9 code points
  expect(parseReason(emoji.repeat(10)).success).toBe(true);   // 20 units, 10 code points
  expect(parseReason(emoji.repeat(500)).success).toBe(true);  // 1000 units, 500 code points
  expect(parseReason(emoji.repeat(501)).success).toBe(false);
  expect(parseReason(`${emoji.repeat(250)}${'x'.repeat(250)}`).success).toBe(true);
  // a ZWJ sequence is several code points (3 emoji + 2 joiners = 5), exactly like the backend counts it
  expect(codePointLength('\u{1F468}\u200D\u{1F469}\u200D\u{1F467}')).toBe(5);
});

test('resolve reason trims the same whitespace as the backend (NBSP, BOM, ideographic space)', () => {
  expect(parseReason(`a${'\u00A0'.repeat(30)}`).success).toBe(false);
  expect(parseReason(`\uFEFF\u3000${'x'.repeat(9)}\u00A0`).success).toBe(false);
  const ok = resolveFormSchema.parse({ action: 'DISMISS', reason: `\u00A0\uFEFF ${'x'.repeat(10)}\u3000`, confirmed: false });
  expect(ok.reason).toBe('x'.repeat(10));
});

test('destructive decisions need an explicit confirmation, harmless ones do not', () => {
  for (const action of ['REMOVE_CONTENT', 'SUSPEND_USER'] as const) {
    const res = resolveFormSchema.safeParse({ action, reason, confirmed: false });
    expect(res.success).toBe(false);
    expect(res.error?.issues.some((i) => i.path[0] === 'confirmed')).toBe(true);
    expect(resolveFormSchema.safeParse({ action, reason, confirmed: true }).success).toBe(true);
  }
  expect(resolveFormSchema.safeParse({ action: 'WARN_USER', reason, confirmed: false }).success).toBe(true);
  expect(resolveFormSchema.safeParse({ action: 'NUKE', reason, confirmed: true }).success).toBe(false);
});

const parseDetails = (details: string) => reportFormSchema.safeParse({ reason: 'SPAM', details });

test('details: exactly 1000 UTF-16 code units accepted, 1001 rejected', () => {
  expect(parseDetails('x'.repeat(1000)).success).toBe(true);
  expect(parseDetails('x'.repeat(1001)).success).toBe(false);
});

test('details: the raw length is checked before trimming', () => {
  // 1000 meaningful characters + padding: raw length 1002 > 1000, rejected although trim() would give 1000
  expect(parseDetails(`${'x'.repeat(1000)}  `).success).toBe(false);
  expect(parseDetails(`\u00A0${'x'.repeat(1000)}`).success).toBe(false);
  expect(parseDetails(' '.repeat(1001)).success).toBe(false);
  // padding inside the budget is accepted and removed
  const ok = reportFormSchema.parse({ reason: 'SPAM', details: ` ${'x'.repeat(998)} ` });
  expect(ok.details).toBe('x'.repeat(998));
});

test('details: supplementary characters count as two UTF-16 code units', () => {
  const emoji = '\u{1F600}';
  expect(emoji.length).toBe(2);
  expect(parseDetails(emoji.repeat(500)).success).toBe(true);   // 1000 units
  expect(parseDetails(emoji.repeat(501)).success).toBe(false);  // 1002 units
  expect(parseDetails(`${emoji.repeat(499)}xx`).success).toBe(true);   // 1000 units
  expect(parseDetails(`${emoji.repeat(499)}xxx`).success).toBe(false); // 1001 units
});

test('details: whitespace-only values (incl. NBSP, BOM, ideographic space) trim to blank, like the backend', () => {
  for (const blank of ['', '   ', '\u00A0\u00A0', '\uFEFF', '\u3000\u3000', '\u2003\u202F\u205F\u2028\u2029\u1680']) {
    const parsed = reportFormSchema.parse({ reason: 'SPAM', details: blank });
    expect(parsed.details).toBe('');
  }
});

test('details: leading/trailing whitespace is removed, inner whitespace kept', () => {
  expect(reportFormSchema.parse({ reason: 'SPAM', details: '\u00A0\uFEFF\u3000 hi \u00A0\n' }).details).toBe('hi');
  expect(reportFormSchema.parse({ reason: 'SPAM', details: ' a \u00A0 b ' }).details).toBe('a \u00A0 b');
});
