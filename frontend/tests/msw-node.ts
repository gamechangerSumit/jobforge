import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { handlers, resetMockState } from '@/mocks/handlers';

/**
 * Node MSW server for component tests that exercise the real lib/api client.
 * Handlers use relative URLs (/api/v1/...). jsdom resolves them against window.location for matching,
 * but Node's fetch rejects relative URLs, so a small shim makes them absolute first.
 */
export const server = setupServer(...handlers);

export function useMockServer() {
  const originalFetch = globalThis.fetch;
  beforeAll(() => {
    globalThis.fetch = ((input: RequestInfo | URL, init?: RequestInit) =>
      typeof input === 'string' && input.startsWith('/')
        ? originalFetch(new URL(input, window.location.origin).toString(), init)
        : originalFetch(input as RequestInfo, init)) as typeof fetch;
    server.listen({ onUnhandledRequest: 'error' });
  });
  afterEach(() => { server.resetHandlers(); resetMockState(); });
  afterAll(() => { server.close(); globalThis.fetch = originalFetch; });
}
