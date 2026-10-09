'use client';
import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { makeQueryClient } from '@/lib/query/client';
import { SessionProvider } from '@/lib/auth/session';
import { MockBootstrap } from './MockBootstrap';

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(makeQueryClient);
  return <MockBootstrap><QueryClientProvider client={queryClient}><SessionProvider>{children}</SessionProvider></QueryClientProvider></MockBootstrap>;
}
