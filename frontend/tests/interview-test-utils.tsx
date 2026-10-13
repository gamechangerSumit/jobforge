import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import type { Interview } from '@/types/interviews';

/** Fresh query client per render (no retries, so errors show immediately). */
export function renderWithClient(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

export const emptyPage = () => HttpResponse.json({
  data: [], meta: { requestId: 'test', timestamp: '', page: { number: 0, size: 20, totalElements: 0, totalPages: 1, hasNext: false } },
});

export const errorResponse = (status: number, code: string, message: string, details?: { field: string; code: string; message: string }[]) =>
  HttpResponse.json({ error: { code, message, details, requestId: 'test' } }, { status });

const DAY = 86_400_000;
/** Whole-minute instant `days` from now (the form has minute precision). */
export const inDays = (days: number) => new Date(Math.floor((Date.now() + days * DAY) / 60_000) * 60_000).toISOString();

export const sampleInterview = (over: Partial<Interview> = {}): Interview => ({
  id: 'i-1', applicationId: 'a-1', applicationStatus: 'INTERVIEW',
  job: { id: 'j-1', title: 'Backend Engineer', company: { id: 'c-1', name: 'Acme', slug: 'acme', logoUrl: null, verified: true } },
  seeker: { id: 's-1', handle: 'asha_k', firstName: 'Asha', lastName: 'Kulkarni' },
  scheduledBy: { id: 'r-1', handle: 'ravi_r', firstName: 'Ravi', lastName: 'Rao' },
  type: 'VIDEO', scheduledAt: inDays(3), durationMinutes: 45, timezone: 'Asia/Kolkata', locationOrLink: 'https://meet.example.com/abc',
  status: 'SCHEDULED', seekerResponse: 'PENDING', notes: 'Panel: Priya', createdAt: '', updatedAt: '', ...over,
});

export const nextLinkMock = ({ href, children, ...rest }: { href: string; children: ReactNode; [key: string]: unknown }) => (
  <a href={href} {...rest}>{children}</a>
);
