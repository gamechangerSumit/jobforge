'use client';
import { useEffect, useState } from 'react';

/**
 * Starts MSW in the browser only when NEXT_PUBLIC_API_MOCKING=enabled (run `pnpm mock:init` once to create
 * public/mockServiceWorker.js). Children render only after the worker is ready so no request escapes unmocked.
 */
export function MockBootstrap({ children }: { children: React.ReactNode }) {
  const enabled = process.env.NEXT_PUBLIC_API_MOCKING === 'enabled';
  const [ready, setReady] = useState(!enabled);
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    void import('@/mocks/browser')
      .then(({ worker }) => worker.start({ onUnhandledRequest: 'bypass' }))
      .then(() => { if (active) setReady(true); });
    return () => { active = false; };
  }, [enabled]);
  return ready ? <>{children}</> : null;
}
