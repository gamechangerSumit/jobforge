import { api } from './client';

export interface UserSuggestion { id: string; firstName: string; lastName: string; handle: string }
export interface PublicCard extends UserSuggestion { role: string; headline?: string | null }

/** GET /users/search?q= : mention autocomplete (any signed-in role, max 10). */
export const searchUsers = (q: string) => api.get<UserSuggestion[]>(`/users/search?q=${encodeURIComponent(q)}`);
/** GET /users/{id}/public */
export const getPublicCard = (id: string) => api.get<PublicCard>(`/users/${id}/public`);
/** Public avatar bytes (img tags cannot send the in-memory bearer token). */
export const avatarSrc = (userId: string) => `/api/v1/users/${userId}/avatar`;
