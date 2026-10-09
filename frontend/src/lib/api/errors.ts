import { ApiClientError } from './client';

export const errorMessage = (e: unknown, fallback: string) => (e instanceof ApiClientError ? e.error.message : fallback);

/** Maps API `details[]` ({field, code, message}) to a field→message record for form libraries. */
export function fieldErrors(e: unknown): Record<string, string> {
  if (!(e instanceof ApiClientError)) return {};
  const details = (e.error as { details?: { field?: string; message?: string; code?: string }[] }).details ?? [];
  return Object.fromEntries(details.filter((d) => d.field).map((d) => [d.field as string, d.message ?? d.code ?? 'Invalid value']));
}
