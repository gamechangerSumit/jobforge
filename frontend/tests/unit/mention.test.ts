import { expect, test } from 'vitest';
import { activeMention } from '@/components/common/MentionInput';

test('detects the @token under the caret', () => {
  expect(activeMention('hello @as', 9)).toEqual({ start: 6, query: 'as' });
  expect(activeMention('@a', 2)).toEqual({ start: 0, query: 'a' });
});
test('ignores emails, bare @ and finished words', () => {
  expect(activeMention('mail me at a@b', 14)).toBeNull();
  expect(activeMention('hi @', 4)).toBeNull();
  expect(activeMention('hi @asha thanks', 15)).toBeNull();
});
