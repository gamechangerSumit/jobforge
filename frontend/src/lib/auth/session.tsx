'use client';
import { createContext, useContext, useEffect, useState } from 'react';
import { me, logout } from '@/lib/api/auth';
import { setAccessToken } from '@/lib/api/client';
import type { AuthUser } from '@/types/api';

interface SessionContextValue { user: AuthUser | null; loading: boolean; refresh: () => Promise<void>; signOut: () => Promise<void>; }
const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null); const [loading, setLoading] = useState(true);
  const refresh = async () => { try { const current = await me(); setUser(current); } catch { setAccessToken(null); setUser(null); } finally { setLoading(false); } };
  useEffect(() => { void refresh(); }, []);
  const signOut = async () => { await logout(); setUser(null); };
  return <SessionContext.Provider value={{ user, loading, refresh, signOut }}>{children}</SessionContext.Provider>;
}
export function useSession() { const value = useContext(SessionContext); if (!value) throw new Error('useSession must be used within SessionProvider'); return value; }
