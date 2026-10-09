
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { handlers, resetMockState } from '@/mocks/handlers';

/**
 * Node MSW server for component tests that exercise the real lib/api client.
 *
 * Handlers use relative URLs (/api/v1/...).
 * The fetch shim converts relative URLs into absolute URLs so Node fetch
 * can process them correctly.
 */
export const server = setupServer(...handlers);

export function setupMockServer() {
  const originalFetch = globalThis.fetch;

  beforeAll(() => {
    globalThis.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
      if (typeof input === 'string' && input.startsWith('/')) {
        return originalFetch(
          new URL(input, window.location.origin).toString(),
          init,
        );
      }

      return originalFetch(input as RequestInfo, init);
    }) as typeof fetch;

    server.listen({ onUnhandledRequest: 'error' });
  });

  afterEach(() => {
    server.resetHandlers();
    resetMockState();
  });

  afterAll(() => {
    server.close();
    globalThis.fetch = originalFetch;
  });
}
