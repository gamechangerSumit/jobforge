import { api } from './client';

import type {
  Application,
  ApplicationListParams,
  ApplicationNote,
  ApplyRequest,
  NoteRequest,
  ResumeSummary,
  StatusUpdateRequest,
} from '@/types/api';

function query(
  params: ApplicationListParams,
) {
  const q = new URLSearchParams();

  Object.entries(params).forEach(
    ([key, value]) => {
      if (
        value === undefined ||
        value === '' ||
        value === null
      ) {
        return;
      }

      q.set(key, String(value));
    },
  );

  const result = q.toString();

  return result ? `?${result}` : '';
}

export const listMyApplications = (
  params: ApplicationListParams = {},
) =>
  api.get<Application[]>(
    `/seekers/me/applications${query(params)}`,
  );

export const getApplication = (
  id: string,
) =>
  api.get<Application>(
    `/applications/${id}`,
  );

export const listResumes = () =>
  api.get<ResumeSummary[]>(
    '/seekers/me/resumes',
  );

export const applyToJob = (
  jobId: string,
  body: ApplyRequest,
) =>
  api.post<Application>(
    `/jobs/${jobId}/applications`,
    body,
    {
      'Idempotency-Key': crypto.randomUUID(),
    },
  );

export const withdrawApplication = (
  id: string,
  version: number,
) =>
  api.post<Application>(
    `/applications/${id}/withdraw`,
    undefined,
    {
      'If-Match': String(version),
    },
  );

export const listJobApplications = (
  jobId: string,
  params: ApplicationListParams = {},
) =>
  api.get<Application[]>(
    `/jobs/${jobId}/applications${query(params)}`,
  );

export const listRecruiterApplications = (
  params: ApplicationListParams = {},
) =>
  api.get<Application[]>(
    `/recruiters/me/applications${query(params)}`,
  );

export const updateApplicationStatus = (
  id: string,
  body: StatusUpdateRequest,
  version: number,
) =>
  api.patch<Application>(
    `/applications/${id}/status`,
    body,
    {
      'If-Match': String(version),
    },
  );

export const rateApplication = (
  id: string,
  rating: number,
) =>
  api.put<Application>(
    `/applications/${id}/rating`,
    { rating },
  );

/* -----------------------------
   Application Notes
------------------------------ */

export const listApplicationNotes = (
  applicationId: string,
) =>
  api.get<ApplicationNote[]>(
    `/applications/${applicationId}/notes`,
  );

export const createApplicationNote = (
  applicationId: string,
  body: NoteRequest,
) =>
  api.post<ApplicationNote>(
    `/applications/${applicationId}/notes`,
    body,
  );

export const updateApplicationNote = (
  applicationId: string,
  noteId: string,
  body: NoteRequest,
) =>
  api.patch<ApplicationNote>(
    `/applications/${applicationId}/notes/${noteId}`,
    body,
  );

export const deleteApplicationNote = (
  applicationId: string,
  noteId: string,
) =>
  api.delete<void>(
    `/applications/${applicationId}/notes/${noteId}`,
  );