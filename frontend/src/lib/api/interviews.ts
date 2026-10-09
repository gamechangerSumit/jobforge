import { api } from './client';
import type {
  CancelInterviewRequest, CompleteInterviewRequest, Interview, InterviewListParams, RespondInterviewRequest,
  ScheduleInterviewRequest, UpdateInterviewRequest,
} from '@/types/interviews';

function query(params: InterviewListParams) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    q.set(key, String(value));
  });
  const result = q.toString();
  return result ? `?${result}` : '';
}

export const listInterviews = (params: InterviewListParams = {}) => api.getPage<Interview>(`/interviews${query(params)}`);

export const getInterview = (id: string) => api.get<Interview>(`/interviews/${id}`);

export const scheduleInterview = (applicationId: string, body: ScheduleInterviewRequest) =>
  api.post<Interview>(`/applications/${applicationId}/interviews`, body);

export const updateInterview = (id: string, body: UpdateInterviewRequest) => api.patch<Interview>(`/interviews/${id}`, body);

export const cancelInterview = (id: string, body: CancelInterviewRequest) => api.post<Interview>(`/interviews/${id}/cancel`, body);

export const respondToInterview = (id: string, body: RespondInterviewRequest) =>
  api.post<Interview>(`/interviews/${id}/respond`, body);

export const completeInterview = (id: string, body: CompleteInterviewRequest) =>
  api.post<Interview>(`/interviews/${id}/complete`, body);
