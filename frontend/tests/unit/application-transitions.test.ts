import type { ApplicationStatus } from '@/types/api';
const transitions: Record<ApplicationStatus, ApplicationStatus[]> = { SUBMITTED: ['UNDER_REVIEW','REJECTED'], UNDER_REVIEW: ['SHORTLISTED','REJECTED'], SHORTLISTED: ['INTERVIEW','REJECTED'], INTERVIEW: ['OFFERED','REJECTED'], OFFERED: ['HIRED','REJECTED'], HIRED: [], REJECTED: [], WITHDRAWN: [] };
test('terminal application states have no recruiter transitions', () => { expect(transitions.HIRED).toEqual([]); expect(transitions.REJECTED).toEqual([]); expect(transitions.WITHDRAWN).toEqual([]); });
test('recruiter cannot skip documented states', () => { expect(transitions.SUBMITTED).not.toContain('SHORTLISTED'); expect(transitions.INTERVIEW).not.toContain('HIRED'); });
