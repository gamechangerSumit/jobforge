import { api } from './client';

export interface Account {
  id: string; email: string; role: string; firstName: string; lastName: string; handle: string;
  avatarUrl?: string | null; emailVerified: boolean;
}
export const getAccount = () => api.get<Account>('/users/me');
export const uploadAvatar = (file: File) => { const form = new FormData(); form.append('file', file); return api.uploadPut<Account>('/users/me/avatar', form); };
export const uploadCompanyLogo = (companyId: string, file: File) => {
  const form = new FormData(); form.append('file', file);
  return api.uploadPut<unknown>(`/companies/${companyId}/logo`, form);
};
export const deleteAccount = (password: string) => api.deleteWithBody<void>('/users/me', { password });
export interface AccountUpdate { firstName?: string; lastName?: string; handle?: string }
export const updateAccount = (input: AccountUpdate) => api.patch<Account>('/users/me', input);
