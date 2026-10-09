export const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
export const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

/** Client-side pre-check for avatar/logo uploads (API_CONTRACT section 10); the server re-verifies magic bytes. */
export function validateImageFile(file: Pick<File, 'size' | 'type' | 'name'>): string | null {
  if (!IMAGE_TYPES.includes(file.type)) return 'Only PNG, JPEG or WebP images are accepted.';
  if (file.size > MAX_IMAGE_BYTES) return 'The image must be at most 2 MB.';
  if (file.size === 0) return 'The file is empty.';
  return null;
}
