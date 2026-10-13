'use client';
import { Button } from '@/components/ui/Button';
import { ApiClientError } from '@/lib/api/client';
import { errorMessage } from '@/lib/api/errors';

/** 404 means "missing or not yours" (the API never distinguishes), so it is shown as not found without a retry. */
export const isNotFound = (error: unknown) => error instanceof ApiClientError && error.status === 404;

export function InterviewLoading({ label }: { label: string }) {
  return <p role="status" aria-live="polite" className="mt-6 text-slate-600">{label}</p>;
}

/** Error panel with a retry button (network failures, 5xx, rate limits). */
export function InterviewLoadError({ error, fallback, onRetry, retrying = false }: {
  error: unknown; fallback: string; onRetry: () => void; retrying?: boolean;
}) {
  return (
    <div role="alert" className="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      <p>{errorMessage(error, fallback)}</p>
      <Button className="mt-3" onClick={onRetry} disabled={retrying}>{retrying ? 'Retrying…' : 'Try again'}</Button>
    </div>
  );
}
