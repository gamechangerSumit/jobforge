import { http, HttpResponse } from 'msw';
import { freshState, ids, meta, page, type MockState } from './data';

const B = '/api/v1';
let state: MockState = freshState();
/** Test/dev helper: restore fixtures. */
export const resetMockState = () => { state = freshState(); };

const ok = (data: unknown, extra: Record<string, unknown> = {}, init?: ResponseInit) => HttpResponse.json({ data, meta: meta(extra) }, init);
const fail = (status: number, code: string, message: string, details?: { field: string; code: string; message: string }[]) =>
  HttpResponse.json({ error: { code, message, details, requestId: 'mock' } }, { status });
const staleCheck = (request: Request, version: number) => {
  const header = request.headers.get('If-Match');
  return header !== null && header.replace(/"/g, '') !== String(version);
};

const jobs = [{
  id: ids.job, title: 'Backend Engineer', company: { id: ids.company, name: 'Acme', slug: 'acme', logoUrl: null, verified: true },
  location: { city: 'Nagpur', state: 'Maharashtra', country: 'IN' }, workMode: 'HYBRID', employmentType: 'FULL_TIME', experienceLevel: 'MID',
  salary: { min: 1200000, max: 1800000, currency: 'INR', period: 'YEAR' }, skills: ['java', 'spring-boot'], postedAt: new Date().toISOString(), saved: false, applied: false,
}];

const profileHandlers = [
  http.get(`${B}/seekers/me/profile`, () => ok({ ...state.profile, skills: state.skills })),
  http.put(`${B}/seekers/me/profile`, async ({ request }) => {
    if (staleCheck(request, state.profile.version)) return fail(409, 'STALE_VERSION', 'The profile was modified by someone else.');
    const body = (await request.json()) as Record<string, unknown>;
    state.profile = { ...state.profile, ...body, version: state.profile.version + 1, updatedAt: new Date().toISOString() };
    return ok({ ...state.profile, skills: state.skills });
  }),
  http.get(`${B}/seekers/me/skills`, () => ok(state.skills)),
  http.put(`${B}/seekers/me/skills`, async ({ request }) => {
    state.skills = ((await request.json()) as { skills: MockState['skills'] }).skills;
    return ok(state.skills);
  }),
  http.get(`${B}/seekers/me/education`, () => ok(state.education)),
  http.post(`${B}/seekers/me/education`, async ({ request }) => {
    const item = { id: crypto.randomUUID(), ...((await request.json()) as object) };
    state.education.push(item);
    return ok(item, {}, { status: 201 });
  }),
  http.patch(`${B}/seekers/me/education/:id`, async ({ params, request }) => {
    const i = state.education.findIndex((e) => e.id === params.id);
    if (i < 0) return fail(404, 'RESOURCE_NOT_FOUND', 'Education entry not found.');
    const patch = (await request.json()) as Record<string, unknown>;
    // Mirrors the backend: null/absent keeps, "" clears optional text fields.
    const { clearEndDate, ...rest } = patch;
    Object.entries(rest).forEach(([k, v]) => { if (v === '') delete state.education[i][k]; else if (v !== null) state.education[i][k] = v; });
    if (clearEndDate === true) delete state.education[i].endDate;
    return ok(state.education[i]);
  }),
  http.delete(`${B}/seekers/me/education/:id`, ({ params }) => { state.education = state.education.filter((e) => e.id !== params.id); return new HttpResponse(null, { status: 204 }); }),
  http.get(`${B}/seekers/me/experience`, () => ok(state.experience)),
  http.post(`${B}/seekers/me/experience`, async ({ request }) => {
    const item = { id: crypto.randomUUID(), current: false, ...((await request.json()) as object) };
    state.experience.push(item);
    return ok(item, {}, { status: 201 });
  }),
  http.patch(`${B}/seekers/me/experience/:id`, async ({ params, request }) => {
    const i = state.experience.findIndex((e) => e.id === params.id);
    if (i < 0) return fail(404, 'RESOURCE_NOT_FOUND', 'Experience entry not found.');
    const patch = (await request.json()) as Record<string, unknown>;
    const { clearEndDate, ...rest } = patch;
    Object.entries(rest).forEach(([k, v]) => { if (v === '') delete state.experience[i][k]; else if (v !== null) state.experience[i][k] = v; });
    if (clearEndDate === true) delete state.experience[i].endDate;
    if (state.experience[i].current) delete state.experience[i].endDate;
    return ok(state.experience[i]);
  }),
  http.delete(`${B}/seekers/me/experience/:id`, ({ params }) => { state.experience = state.experience.filter((e) => e.id !== params.id); return new HttpResponse(null, { status: 204 }); }),
  http.get(`${B}/seekers/me/resumes`, () => ok(state.resumes)),
];

const accountHandlers = [
  http.get(`${B}/auth/me`, () => ok(state.account)),
  http.get(`${B}/users/me`, () => ok(state.account)),
  http.patch(`${B}/users/me`, async ({ request }) => { state.account = { ...state.account, ...((await request.json()) as object) }; return ok(state.account); }),
  http.put(`${B}/users/me/avatar`, () => { state.account.avatarUrl = `${B}/users/${state.account.id}/avatar`; return ok(state.account); }),
  http.delete(`${B}/users/me`, async ({ request }) => {
    const body = (await request.json()) as { password?: string };
    if (!body.password) return fail(400, 'VALIDATION_FAILED', 'Password is required.', [{ field: 'password', code: 'NOT_BLANK', message: 'is required' }]);
    return new HttpResponse(null, { status: 204 });
  }),
  http.get(`${B}/users/search`, ({ request }) => {
    const q = (new URL(request.url).searchParams.get('q') ?? '').toLowerCase();
    return ok([{ id: ids.recruiter, firstName: 'Ravi', lastName: 'Rao', handle: 'ravi_r' }, { id: ids.seeker, firstName: 'Asha', lastName: 'Kulkarni', handle: 'asha_k' }].filter((u) => u.handle.includes(q)));
  }),
  http.get(`${B}/users/:id/public`, ({ params }) => params.id === ids.seeker
    ? ok({ id: ids.seeker, firstName: 'Asha', lastName: 'Kulkarni', handle: 'asha_k', role: 'JOB_SEEKER', headline: 'Backend engineer' })
    : fail(404, 'RESOURCE_NOT_FOUND', 'User not found.')),
];

const companyHandlers = [
  http.get(`${B}/companies/me`, () => ok({ company: state.company, memberRole: 'OWNER', members: [{ userId: ids.recruiter, firstName: 'Ravi', lastName: 'Rao', email: 'ravi@acme.example', memberRole: 'OWNER' }] })),
  http.get(`${B}/companies/:id`, ({ params }) => params.id === ids.company ? ok(state.company) : fail(404, 'RESOURCE_NOT_FOUND', 'Company not found.')),
  http.patch(`${B}/companies/:id`, async ({ request }) => {
    if (staleCheck(request, state.company.version)) return fail(409, 'STALE_VERSION', 'The company was modified by someone else.');
    state.company = { ...state.company, ...((await request.json()) as object), version: state.company.version + 1 };
    return ok(state.company);
  }),
  http.put(`${B}/companies/:id/logo`, () => { state.company.logoUrl = `${B}/companies/${ids.company}/logo`; return ok(state.company); }),
];

const notificationHandlers = [
  http.get(`${B}/notifications`, ({ request }) => {
    const unread = new URL(request.url).searchParams.get('unread') === 'true';
    return ok(state.notifications.filter((n) => !unread || !n.readAt));
  }),
  http.get(`${B}/notifications/unread-count`, () => ok({ count: state.notifications.filter((n) => !n.readAt).length })),
  http.post(`${B}/notifications/read-all`, () => { state.notifications.forEach((n) => { n.readAt ??= new Date().toISOString(); }); return new HttpResponse(null, { status: 204 }); }),
  http.post(`${B}/notifications/:id/read`, ({ params }) => { const n = state.notifications.find((x) => x.id === params.id); if (n) n.readAt ??= new Date().toISOString(); return new HttpResponse(null, { status: 204 }); }),
];

const adminHandlers = [
  http.get(`${B}/admin/recruiters/:userId`, ({ params }) => ok({
    userId: params.userId, email: 'rhea@acme.test', firstName: 'Rhea', lastName: 'Patil', jobTitle: 'Talent Lead', phone: '+91 90000 00000',
    approvalStatus: 'PENDING', rejectionReason: null, companyName: 'Acme', companyId: ids.company, createdAt: new Date().toISOString(),
  })),
  http.get(`${B}/admin/users`, () => {
    const rows = [{ id: ids.seeker, email: 'asha@jobforge.local', firstName: 'Asha', lastName: 'Kulkarni', handle: 'asha_k', role: 'JOB_SEEKER', status: 'ACTIVE', emailVerified: true, createdAt: new Date().toISOString(), lastLoginAt: null }];
    return ok(rows, page(0, 20, rows.length));
  }),
  http.get(`${B}/admin/audit-logs`, () => {
    const rows = [{ id: 'a-1', occurredAt: new Date().toISOString(), actorUserId: ids.admin, actorRole: 'ADMIN', source: 'API', action: 'COMPANY_VERIFIED', entityType: 'COMPANY', entityId: ids.company, outcome: 'SUCCESS', metadata: { note: 'mock' }, ip: null, requestId: 'mock' }];
    return ok(rows, page(0, 20, rows.length));
  }),
  http.get(`${B}/admin/jobs`, () => {
    const rows = [{ id: ids.job, title: 'Backend Engineer', slug: 'backend-engineer', status: 'PUBLISHED', company: { id: ids.company, name: 'Acme' }, publishedAt: new Date().toISOString(), expiresAt: null }];
    return ok(rows, page(0, 20, rows.length));
  }),
];

export const handlers = [
  http.get(`${B}/jobs`, () => ok(jobs, page(0, 20, jobs.length))),
  ...accountHandlers, ...profileHandlers, ...companyHandlers, ...notificationHandlers, ...adminHandlers,
];
