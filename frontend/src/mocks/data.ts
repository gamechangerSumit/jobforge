/** In-memory fixtures for MSW handlers. Shapes mirror API_CONTRACT; keep in sync with docs when the contract changes. */
export const ids = {
  seeker: '11111111-1111-4111-8111-111111111111',
  recruiter: '22222222-2222-4222-8222-222222222222',
  admin: '33333333-3333-4333-8333-333333333333',
  company: '44444444-4444-4444-8444-444444444444',
  job: '55555555-5555-4555-8555-555555555555',
  application: '66666666-6666-4666-8666-666666666666',
  resume: '77777777-7777-4777-8777-777777777777',
};

export const meta = (extra: Record<string, unknown> = {}) => ({ requestId: 'mock', timestamp: new Date().toISOString(), ...extra });
export const page = (n: number, size: number, total: number) => ({ page: { number: n, size, totalElements: total, totalPages: Math.max(1, Math.ceil(total / size)), hasNext: (n + 1) * size < total } });

export interface MockState {
  account: { id: string; email: string; role: 'JOB_SEEKER' | 'RECRUITER' | 'ADMIN'; firstName: string; lastName: string; handle: string; avatarUrl: string | null; emailVerified: boolean };
  profile: Record<string, unknown> & { version: number };
  skills: { skill: string; proficiency: string; years?: number }[];
  education: Record<string, unknown>[];
  experience: Record<string, unknown>[];
  resumes: { id: string; fileName: string; contentType: string; sizeBytes: number; primary: boolean; createdAt: string }[];
  company: Record<string, unknown> & { version: number };
  notifications: { id: string; type: string; title: string; body: string; data: Record<string, unknown>; readAt: string | null; createdAt: string }[];
}

export function freshState(): MockState {
  return {
    account: { id: ids.seeker, email: 'asha@jobforge.local', role: 'JOB_SEEKER', firstName: 'Asha', lastName: 'Kulkarni', handle: 'asha_k', avatarUrl: null, emailVerified: true },
    profile: {
      id: 'p-1', userId: ids.seeker, headline: 'Backend engineer', summary: null, phone: null, location: { city: 'Nagpur', state: 'Maharashtra', country: 'IN' },
      currentTitle: null, yearsExperience: 3, expectedSalary: null, noticePeriodDays: 30, openToWork: true, visibility: 'RECRUITERS_ONLY',
      links: null, completenessScore: 25, skills: [], version: 1, updatedAt: new Date().toISOString(),
    },
    skills: [{ skill: 'java', proficiency: 'ADVANCED', years: 4 }],
    education: [],
    experience: [],
    resumes: [{ id: ids.resume, fileName: 'asha-resume.pdf', contentType: 'application/pdf', sizeBytes: 120_000, primary: true, createdAt: new Date().toISOString() }],
    company: {
      id: ids.company, name: 'Acme', slug: 'acme', description: 'We build things.', industry: 'Software', sizeBand: '51_200', websiteUrl: 'https://acme.example',
      hqCity: 'Nagpur', hqState: 'Maharashtra', hqCountry: 'IN', foundedYear: 2015, verificationStatus: 'VERIFIED', verified: true, logoUrl: null, openJobCount: 1, version: 1,
    },
    notifications: [
      { id: 'n-1', type: 'APPLICATION_STATUS_CHANGED', title: 'Application status updated', body: 'Your application status changed to SHORTLISTED.', data: { applicationId: ids.application, status: 'SHORTLISTED' }, readAt: null, createdAt: new Date().toISOString() },
      { id: 'n-2', type: 'APPLICATION_SUBMITTED', title: 'Application submitted', body: 'Your application was submitted successfully.', data: { jobId: ids.job }, readAt: new Date().toISOString(), createdAt: new Date(Date.now() - 86_400_000).toISOString() },
    ],
  };
}
