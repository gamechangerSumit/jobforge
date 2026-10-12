'use client';
import { Button } from '@/components/ui/Button';
import { ApiClientError } from '@/lib/api/client';
import { errorMessage } from '@/lib/api/errors';

export const isForbidden = (error: unknown) => error instanceof ApiClientError && error.status === 403;
export const isNotFound = (error: unknown) => error instanceof ApiClientError && error.status === 404;

/** Pulsing placeholders shaped like the final layout (cards + two panels). */
export function AnalyticsSkeleton() {
  return (
    <div role="status" aria-live="polite" aria-busy="true" className="mt-6">
      <span className="sr-only">Loading analytics…</span>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-hidden="true">
        {[0, 1, 2, 3].map((i) => <div key={i} className="h-24 animate-pulse rounded-2xl bg-slate-200" />)}
      </div>
      <div className="mt-6 grid gap-4 lg:grid-cols-2" aria-hidden="true">
        <div className="h-64 animate-pulse rounded-2xl bg-slate-200" />
        <div className="h-64 animate-pulse rounded-2xl bg-slate-200" />
      </div>
    </div>
  );
}

/** Error panel with retry. 403 and 404 are access/ownership answers, not transient, so they get no retry button. */
export function AnalyticsError({ error, onRetry, retrying = false }: { error: unknown; onRetry: () => void; retrying?: boolean }) {
  if (isForbidden(error)) {
    return <div role="alert" className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">{errorMessage(error, 'You do not have access to these analytics.')}</div>;
  }
  if (isNotFound(error)) {
    return <div role="alert" className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">This job was not found.</div>;
  }
  return (
    <div role="alert" className="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      <p>{errorMessage(error, 'Analytics could not be loaded.')}</p>
      <Button className="mt-3" onClick={onRetry} disabled={retrying}>{retrying ? 'Retrying…' : 'Try again'}</Button>
    </div>
  );
}

export function AnalyticsEmpty({ children }: { children: React.ReactNode }) {
  return <p className="mt-6 rounded-xl border border-dashed border-slate-300 p-8 text-center text-slate-600">{children}</p>;
}
