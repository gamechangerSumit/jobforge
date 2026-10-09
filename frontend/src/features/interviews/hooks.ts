'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelInterview, completeInterview, getInterview, listInterviews, respondToInterview, scheduleInterview, updateInterview,
} from '@/lib/api/interviews';
import type {
  CancelInterviewRequest, CompleteInterviewRequest, InterviewListParams, RespondInterviewRequest, ScheduleInterviewRequest,
  UpdateInterviewRequest,
} from '@/types/interviews';

export const interviewKeys = {
  all: ['interviews'] as const,
  list: (params: InterviewListParams) => ['interviews', 'list', params] as const,
  detail: (id: string) => ['interviews', 'detail', id] as const,
};

export function useInterviewList(params: InterviewListParams, enabled = true) {
  return useQuery({ queryKey: interviewKeys.list(params), queryFn: () => listInterviews(params), enabled });
}

export function useInterview(id: string) {
  return useQuery({ queryKey: interviewKeys.detail(id), queryFn: () => getInterview(id), enabled: Boolean(id) });
}

/** Every write refreshes interview lists; scheduling also moves the application (SHORTLISTED → INTERVIEW). */
function useInvalidate() {
  const qc = useQueryClient();
  return (applicationId?: string) => {
    void qc.invalidateQueries({ queryKey: interviewKeys.all });
    if (applicationId) void qc.invalidateQueries({ queryKey: ['application', applicationId] });
    void qc.invalidateQueries({ queryKey: ['applications'] });
  };
}

export function useScheduleInterview(applicationId: string) {
  const invalidate = useInvalidate();
  return useMutation({ mutationFn: (body: ScheduleInterviewRequest) => scheduleInterview(applicationId, body), onSuccess: () => invalidate(applicationId) });
}

export function useUpdateInterview(id: string) {
  const invalidate = useInvalidate();
  return useMutation({ mutationFn: (body: UpdateInterviewRequest) => updateInterview(id, body), onSuccess: (i) => invalidate(i.applicationId) });
}

export function useCancelInterview(id: string) {
  const invalidate = useInvalidate();
  return useMutation({ mutationFn: (body: CancelInterviewRequest) => cancelInterview(id, body), onSuccess: (i) => invalidate(i.applicationId) });
}

export function useCompleteInterview(id: string) {
  const invalidate = useInvalidate();
  return useMutation({ mutationFn: (body: CompleteInterviewRequest) => completeInterview(id, body), onSuccess: (i) => invalidate(i.applicationId) });
}

export function useRespondToInterview(id: string) {
  const invalidate = useInvalidate();
  return useMutation({ mutationFn: (body: RespondInterviewRequest) => respondToInterview(id, body), onSuccess: (i) => invalidate(i.applicationId) });
}
