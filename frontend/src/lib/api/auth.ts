import { api, setAccessToken } from './client';
import type { AuthSession, AuthUser } from '@/types/api';

export async function login(email: string, password: string) {
  const session = await api.post<AuthSession>('/auth/login', { email, password });
  setAccessToken(session.accessToken);
  return session;
}

export async function register(body: { email: string; password: string; firstName: string; lastName: string; handle?: string; role: 'JOB_SEEKER' | 'RECRUITER' }) {
  return api.post<AuthUser>('/auth/register', body);
}

export async function me() { return api.get<AuthUser>('/auth/me'); }
export async function logout() { try { await api.post<void>('/auth/logout'); } finally { setAccessToken(null); } }
