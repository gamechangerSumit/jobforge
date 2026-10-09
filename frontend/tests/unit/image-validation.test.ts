import { expect, test } from 'vitest';
import { MAX_IMAGE_BYTES, validateImageFile } from '@/lib/validation/image';

test('accepts png, jpeg and webp within 2 MB', () => {
  for (const type of ['image/png', 'image/jpeg', 'image/webp']) {
    expect(validateImageFile({ type, size: 1000, name: 'a' })).toBeNull();
  }
});
test('rejects other types, empty and oversized files', () => {
  expect(validateImageFile({ type: 'image/gif', size: 1000, name: 'a.gif' })).toMatch(/PNG, JPEG or WebP/);
  expect(validateImageFile({ type: 'image/svg+xml', size: 1000, name: 'a.svg' })).toMatch(/PNG, JPEG or WebP/);
  expect(validateImageFile({ type: 'image/png', size: MAX_IMAGE_BYTES + 1, name: 'a.png' })).toMatch(/2 MB/);
  expect(validateImageFile({ type: 'image/png', size: 0, name: 'a.png' })).toMatch(/empty/);
});
