import { http, HttpResponse } from 'msw';
import { ids, meta, page } from './data';
import type { Interview } from '@/types/interviews';

/**
 * MSW handlers for API_CONTRACT §12.7 (interviews). They mirror the backend rules closely enough for UI work and tests:
 * role split (recruiter / seeker), seeker view without `seeker` and `notes`, state rules, reschedule resetting the
 * answer, time rules and the query whitelist. The mock has a single role at a time, taken from `getRole()`.
 */
type Role = 'JOB_SEEKER' | 'RECRUITER' | 'ADMIN';
const B = '/api/v1';
const DAY = 86_400_000;
const OPEN = ['SCHEDULED', 'CONFIRMED', 'DECLINED'];

const ok = (data: unknown, extra: Record<string, unknown> = {}, init?: ResponseInit) => HttpResponse.json({ data, meta: meta(extra) }, init);
const fail = (status: number, code: string, message: string, details?: { field: string; code: string; message: string }[]) =>
  HttpResponse.json({ error: { code, message, details, requestId: 'mock' } }, { status });

const person = { id: ids.seeker, handle: 'asha_k', firstName: 'Asha', lastName: 'Kulkarni' };
const recruiter = { id: ids.recruiter, handle: 'ravi_r', firstName: 'Ravi', lastName: 'Rao' };
const job = { id: ids.job, title: 'Backend Engineer', company: { id: ids.company, name: 'Acme', slug: 'acme', logoUrl: null, verified: true }, location: { city: 'Nagpur', state: 'Maharashtra', country: 'IN' } };

export const interviewIds = { upcoming: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', started: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' };

function fixtures(): Interview[] {
  const now = new Date().toISOString();
  return [
    {
      id: interviewIds.upcoming, applicationId: ids.application, applicationStatus: 'INTERVIEW', job, seeker: person, scheduledBy: recruiter,
      type: 'VIDEO', scheduledAt: new Date(Date.now() + 3 * DAY).toISOString(), durationMinutes: 45, timezone: 'Asia/Kolkata',
      locationOrLink: 'https://meet.example.com/abc', status: 'SCHEDULED', seekerResponse: 'PENDING', notes: 'Panel: Priya', createdAt: now, updatedAt: now,
    },
    {
      id: interviewIds.started, applicationId: ids.application, applicationStatus: 'INTERVIEW', job, seeker: person, scheduledBy: recruiter,
      type: 'PHONE', scheduledAt: new Date(Date.now() - 2 * 3_600_000).toISOString(), durationMinutes: 30, timezone: 'Asia/Kolkata',
      locationOrLink: '+91 98765 43210', status: 'CONFIRMED', seekerResponse: 'CONFIRMED', createdAt: now, updatedAt: now,
    },
  ];
}

let store: Interview[] = fixtures();
export const resetInterviewMocks = () => { store = fixtures(); };

const view = (i: Interview, role: Role): Interview => {
  if (role === 'RECRUITER') return i;
  const { seeker: _seeker, notes: _notes, ...rest } = i; // recruiter-only parts are never sent to seekers
  void _seeker; void _notes;
  return rest;
};

const forbidden = () => fail(403, 'ACCESS_DENIED', 'You do not have permission to do this.');
const notFound = () => fail(404, 'RESOURCE_NOT_FOUND', 'Interview not found.');
const invalid = (status: string) => fail(409, 'INVALID_STATE_TRANSITION', `An interview in status ${status} cannot be changed that way.`);
const started = (i: Interview) => new Date(i.scheduledAt).getTime() <= Date.now();

export function interviewHandlers(getRole: () => Role) {
  return [
    http.get(`${B}/interviews`, ({ request }) => {
      const role = getRole();
      if (role === 'ADMIN') return forbidden();
      const url = new URL(request.url);
      const unknown = [...url.searchParams.keys()].filter((k) => !['applicationId', 'from', 'to', 'status', 'page', 'size'].includes(k));
      if (unknown.length) return fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', unknown.map((field) => ({ field, code: 'UNKNOWN_FIELD', message: 'unknown query parameter' })));
      const status = url.searchParams.get('status');
      const applicationId = url.searchParams.get('applicationId');
      const size = Number(url.searchParams.get('size') ?? 20);
      const number = Number(url.searchParams.get('page') ?? 0);
      const items = store
        .filter((i) => (!status || i.status === status) && (!applicationId || i.applicationId === applicationId))
        .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
      return ok(items.slice(number * size, number * size + size).map((i) => view(i, role)), page(number, size, items.length));
    }),

    http.get(`${B}/interviews/:id`, ({ params }) => {
      const role = getRole();
      if (role === 'ADMIN') return forbidden();
      const found = store.find((i) => i.id === params.id);
      return found ? ok(view(found, role)) : notFound();
    }),

    http.post(`${B}/applications/:id/interviews`, async ({ params, request }) => {
      if (getRole() !== 'RECRUITER') return forbidden();
      const body = (await request.json()) as Record<string, unknown>;
      const problems: { field: string; code: string; message: string }[] = [];
      const duration = Number(body.durationMinutes);
      if (!['PHONE', 'VIDEO', 'ONSITE'].includes(String(body.type))) problems.push({ field: 'type', code: 'INVALID_ENUM', message: 'must be PHONE, VIDEO or ONSITE' });
      if (!Number.isInteger(duration) || duration < 15 || duration > 480) problems.push({ field: 'durationMinutes', code: 'MIN', message: 'must be between 15 and 480' });
      if (!body.locationOrLink || String(body.locationOrLink).trim() === '') problems.push({ field: 'locationOrLink', code: 'NOT_BLANK', message: 'must not be blank' });
      if (!body.timezone) problems.push({ field: 'timezone', code: 'NOT_BLANK', message: 'must not be blank' });
      if (problems.length) return fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', problems);
      if (new Date(String(body.scheduledAt)).getTime() <= Date.now()) return fail(422, 'BUSINESS_RULE_VIOLATED', 'The interview must be scheduled in the future.', [{ field: 'scheduledAt', code: 'BUSINESS_RULE', message: 'must be in the future' }]);
      const now = new Date().toISOString();
      const created: Interview = {
        id: crypto.randomUUID(), applicationId: String(params.id), applicationStatus: 'INTERVIEW', job, seeker: person, scheduledBy: recruiter,
        type: body.type as Interview['type'], scheduledAt: String(body.scheduledAt), durationMinutes: duration, timezone: String(body.timezone),
        locationOrLink: String(body.locationOrLink), status: 'SCHEDULED', seekerResponse: 'PENDING',
        ...(body.notes ? { notes: String(body.notes) } : {}), createdAt: now, updatedAt: now,
      };
      store.push(created);
      return ok(created, {}, { status: 201, headers: { Location: `${B}/interviews/${created.id}` } });
    }),

    http.patch(`${B}/interviews/:id`, async ({ params, request }) => {
      if (getRole() !== 'RECRUITER') return forbidden();
      const i = store.find((x) => x.id === params.id);
      if (!i) return notFound();
      if (!OPEN.includes(i.status)) return invalid(i.status);
      const patch = (await request.json()) as Partial<Interview> & { notes?: string };
      const visible = (['type', 'scheduledAt', 'durationMinutes', 'timezone', 'locationOrLink'] as const)
        .some((k) => patch[k] !== undefined && String(patch[k]) !== String(i[k]));
      if (visible && patch.scheduledAt && new Date(patch.scheduledAt).getTime() <= Date.now()) {
        return fail(422, 'BUSINESS_RULE_VIOLATED', 'The interview must be scheduled in the future.', [{ field: 'scheduledAt', code: 'BUSINESS_RULE', message: 'must be in the future' }]);
      }
      Object.assign(i, Object.fromEntries(Object.entries(patch).filter(([k, v]) => k !== 'notes' && v !== undefined)));
      if (patch.notes !== undefined) { if (patch.notes.trim() === '') delete i.notes; else i.notes = patch.notes; }
      if (visible) { i.status = 'SCHEDULED'; i.seekerResponse = 'PENDING'; delete i.seekerResponseNote; } // reschedule resets the answer
      i.updatedAt = new Date().toISOString();
      return ok(i);
    }),

    http.post(`${B}/interviews/:id/cancel`, async ({ params, request }) => {
      if (getRole() !== 'RECRUITER') return forbidden();
      const i = store.find((x) => x.id === params.id);
      if (!i) return notFound();
      const { reason } = (await request.json()) as { reason?: string };
      if (!reason || reason.trim() === '') return fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', [{ field: 'reason', code: 'NOT_BLANK', message: 'must not be blank' }]);
      if (!OPEN.includes(i.status)) return invalid(i.status);
      i.status = 'CANCELLED'; i.cancelledReason = reason; i.updatedAt = new Date().toISOString();
      return ok(i);
    }),

    http.post(`${B}/interviews/:id/respond`, async ({ params, request }) => {
      if (getRole() !== 'JOB_SEEKER') return forbidden();
      const i = store.find((x) => x.id === params.id);
      if (!i) return notFound();
      const body = (await request.json()) as { response?: string; note?: string };
      if (body.response !== 'CONFIRM' && body.response !== 'DECLINE') return fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', [{ field: 'response', code: 'INVALID_ENUM', message: 'must be CONFIRM or DECLINE' }]);
      if (!['SCHEDULED', 'CONFIRMED'].includes(i.status)) return invalid(i.status);
      if (started(i)) return fail(422, 'BUSINESS_RULE_VIOLATED', 'This interview has already started.');
      i.status = body.response === 'CONFIRM' ? 'CONFIRMED' : 'DECLINED';
      i.seekerResponse = body.response === 'CONFIRM' ? 'CONFIRMED' : 'DECLINED';
      if (body.note) i.seekerResponseNote = body.note; else delete i.seekerResponseNote;
      i.updatedAt = new Date().toISOString();
      return ok(view(i, 'JOB_SEEKER'));
    }),

    http.post(`${B}/interviews/:id/complete`, async ({ params, request }) => {
      if (getRole() !== 'RECRUITER') return forbidden();
      const i = store.find((x) => x.id === params.id);
      if (!i) return notFound();
      const { outcome } = (await request.json()) as { outcome?: string };
      if (outcome !== 'COMPLETED' && outcome !== 'NO_SHOW') return fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', [{ field: 'outcome', code: 'INVALID_ENUM', message: 'must be COMPLETED or NO_SHOW' }]);
      if (!['SCHEDULED', 'CONFIRMED'].includes(i.status)) return invalid(i.status);
      if (!started(i)) return fail(422, 'BUSINESS_RULE_VIOLATED', 'The interview has not started yet.');
      i.status = outcome; i.updatedAt = new Date().toISOString();
      return ok(i);
    }),
  ];
}
