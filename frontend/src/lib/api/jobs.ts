import { api } from './client';

import type {
  JobDetail,
  JobListParams,
  JobSummary,
  JobUpsert,
  RecruiterJobSummary,
} from '@/types/api';

function query(params: JobListParams) {
  const q = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === '' || value === null) {
      return;
    }

    if (Array.isArray(value)) {
      value.forEach((v) => {
        q.append(key, String(v));
      });
    } else {
      q.set(key, String(value));
    }
  });

  const result = q.toString();

  return result ? `?${result}` : '';
}

export async function listJobsPage(params: JobListParams = {}) {
  return api.getPage<JobSummary>(`/jobs${query(params)}`);
}

export async function listJobs(
  params: JobListParams = {},
) {
  return api.get<JobSummary[]>(
    `/jobs${query(params)}`,
  );
}

export const getJob = (id: string) =>
  api.get<JobDetail>(`/jobs/${id}`);

export const getSimilarJobs = (id: string) =>
  api.get<JobSummary[]>(
    `/jobs/${id}/similar`,
  );

export const saveJob = (id: string) =>
  api.put<void>(
    `/seekers/me/saved-jobs/${id}`,
  );

export const unsaveJob = (id: string) =>
  api.delete<void>(
    `/seekers/me/saved-jobs/${id}`,
  );

export const listSavedJobs = (
  page = 0,
  size = 20,
) =>
  api.get<JobSummary[]>(
    `/seekers/me/saved-jobs?page=${page}&size=${size}`,
  );

export const createJob = (
  body: JobUpsert,
) =>
  api.post<JobDetail>(
    '/jobs',
    body,
  );

/** PATCH body (JSON merge): omitted = unchanged, '' / null = clear. aiRequestId is not patchable. */
export type JobPatch = Partial<Omit<JobUpsert, 'location' | 'salary' | 'aiRequestId'>> & {
  location?: JobUpsert['location'] | null;
  salary?: JobUpsert['salary'] | null;
};

export const updateJob = (
  id: string,
  body: JobPatch,
  version: number,
) =>
  api.patch<JobDetail>(
    `/jobs/${id}`,
    body,
    {
      'If-Match': String(version),
    },
  );

export const publishJob = (
  id: string,
  version: number,
) =>
  api.post<JobDetail>(
    `/jobs/${id}/publish`,
    undefined,
    {
      'If-Match': String(version),
    },
  );

export const unpublishJob = (
  id: string,
  version: number,
) =>
  api.post<JobDetail>(
    `/jobs/${id}/unpublish`,
    undefined,
    {
      'If-Match': String(version),
    },
  );

export const closeJob = (
  id: string,
  version: number,
) =>
  api.post<JobDetail>(
    `/jobs/${id}/close`,
    undefined,
    {
      'If-Match': String(version),
    },
  );

export const deleteJob = (
  id: string,
) =>
  api.delete<void>(
    `/jobs/${id}`,
  );

export interface RecruiterJobListParams {
  status?: string;
  q?: string;
  page?: number;
  size?: number;
}

export const listRecruiterJobs = (
  params: RecruiterJobListParams = {},
) => {
  const q = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      q.set(key, String(value));
    }
  });

  const queryString = q.toString();

  return api.get<RecruiterJobSummary[]>(
    `/recruiters/me/jobs${
      queryString ? `?${queryString}` : ''
    }`,
  );
};