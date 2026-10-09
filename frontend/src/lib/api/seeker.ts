import { api } from './client';

export interface Resume { id: string; fileName: string; contentType: string; sizeBytes: number; primary: boolean; createdAt: string }
export interface SeekerSkill { skill: string; proficiency: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'; years?: number | null }
export interface Education { id: string; institution: string; degree: string; fieldOfStudy?: string | null; startDate: string; endDate?: string | null; grade?: string | null; description?: string | null }
export interface Experience { id: string; title: string; companyName: string; location?: string | null; employmentType?: string | null; startDate: string; endDate?: string | null; current: boolean; description?: string | null }
export interface EducationInput { institution: string; degree: string; fieldOfStudy?: string; startDate: string; endDate?: string; grade?: string; description?: string }
export interface ExperienceInput { title: string; companyName: string; location?: string; employmentType?: string; startDate: string; endDate?: string; current?: boolean; description?: string }

export const listResumes = () => api.get<Resume[]>('/seekers/me/resumes');
export const uploadResume = (file: File) => { const form = new FormData(); form.append('file', file); return api.upload<Resume>('/seekers/me/resumes', form); };
export const setPrimaryResume = (id: string) => api.put<void>(`/seekers/me/resumes/${id}/primary`);
export const deleteResume = (id: string) => api.delete<void>(`/seekers/me/resumes/${id}`);
export const downloadResume = (id: string) => api.blob(`/seekers/me/resumes/${id}/download`);

export const listSkills = () => api.get<SeekerSkill[]>('/seekers/me/skills');
export const replaceSkills = (skills: SeekerSkill[]) => api.put<SeekerSkill[]>('/seekers/me/skills', { skills });

export const listEducation = () => api.get<Education[]>('/seekers/me/education');
export const addEducation = (input: EducationInput) => api.post<Education>('/seekers/me/education', input);
export const deleteEducation = (id: string) => api.delete<void>(`/seekers/me/education/${id}`);

export const listExperience = () => api.get<Experience[]>('/seekers/me/experience');
export const addExperience = (input: ExperienceInput) => api.post<Experience>('/seekers/me/experience', input);
export const deleteExperience = (id: string) => api.delete<void>(`/seekers/me/experience/${id}`);

/** clearEndDate: true removes a stored end date (omitting endDate alone means unchanged). */
export type EducationPatch = Partial<EducationInput> & { clearEndDate?: boolean };
export type ExperiencePatch = Partial<ExperienceInput> & { clearEndDate?: boolean };
export const patchEducation = (id: string, patch: EducationPatch) => api.patch<Education>(`/seekers/me/education/${id}`, patch);
export const patchExperience = (id: string, patch: ExperiencePatch) => api.patch<Experience>(`/seekers/me/experience/${id}`, patch);

export type ProfileVisibility = 'PUBLIC' | 'RECRUITERS_ONLY' | 'PRIVATE';
export type SalaryPeriod = 'YEAR' | 'MONTH' | 'HOUR';
export interface SeekerProfile {
  id: string; userId: string; headline?: string | null; summary?: string | null; phone?: string | null;
  location?: { city?: string | null; state?: string | null; country?: string | null } | null;
  currentTitle?: string | null; yearsExperience?: number | null;
  expectedSalary?: { min?: number | null; max?: number | null; currency?: string | null; period?: SalaryPeriod | null } | null;
  noticePeriodDays?: number | null; openToWork: boolean; visibility: ProfileVisibility;
  links?: { linkedin?: string | null; github?: string | null; portfolio?: string | null } | null;
  completenessScore: number; skills: SeekerSkill[]; version: number; updatedAt: string;
}
/** PUT = replace: omitted optional fields are cleared server-side (API_CONTRACT 12.2). */
export interface SeekerProfileInput {
  headline?: string; summary?: string; phone?: string;
  location?: { city?: string; state?: string; country?: string };
  currentTitle?: string; yearsExperience?: number;
  expectedSalary?: { min?: number; max?: number; currency?: string; period?: SalaryPeriod };
  noticePeriodDays?: number; openToWork: boolean; visibility: ProfileVisibility;
  links?: { linkedin?: string; github?: string; portfolio?: string };
}
export const getSeekerProfile = () => api.get<SeekerProfile>('/seekers/me/profile');
export const saveSeekerProfile = (version: number, input: SeekerProfileInput) =>
  api.put<SeekerProfile>('/seekers/me/profile', input, { 'If-Match': `"${version}"` });
