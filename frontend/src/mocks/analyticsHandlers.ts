import { http, HttpResponse } from 'msw';
import { ids, meta } from './data';
import { validateRange } from '@/lib/analytics/range';

/**
 * MSW handlers for the proposed, unmerged Session 5 contract change (§12.10.1 / §12.11.2; analytics). Development mocks and test fixtures only: production
 * UI never imports this file. Numbers are derived deterministically from the requested range, so changing the filter
 * visibly changes the charts. The mock enforces the same query rules as the backend (unknown parameter, invalid or
 * oversized range → 400 VALIDATION_FAILED) and the role split (RECRUITER / ADMIN; others → 403).
 */
type Role = 'JOB_SEEKER' | 'RECRUITER' | 'ADMIN';
const B = '/api/v1';
const DAY = 86_400_000;

const ok = (data: unknown) => HttpResponse.json({ data, meta: meta() });
const fail = (status: number, code: string, message: string, details?: { field: string; code: string; message: string }[]) =>
  HttpResponse.json({ error: { code, message, details, requestId: 'mock' } }, { status });

function parseRange(url: URL, allowed: string[]) {
  const unknown = [...url.searchParams.keys()].filter((k) => !allowed.includes(k));
  if (unknown.length) {
    return { error: fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', unknown.map((field) => ({ field, code: 'UNKNOWN_FIELD', message: 'unknown query parameter' }))) };
  }
  const today = new Date().toISOString().slice(0, 10);
  const to = url.searchParams.get('to') ?? today;
  const from = url.searchParams.get('from') ?? new Date(Date.parse(`${to}T00:00:00Z`) - 29 * DAY).toISOString().slice(0, 10);
  const message = validateRange({ from, to });
  if (message) return { error: fail(400, 'VALIDATION_FAILED', message, [{ field: 'from', code: 'PATTERN', message }]) };
  const days: string[] = [];
  for (let t = Date.parse(`${from}T00:00:00Z`); t <= Date.parse(`${to}T00:00:00Z`); t += DAY) days.push(new Date(t).toISOString().slice(0, 10));
  return { from, to, days };
}

const perDay = (index: number) => index % 4; // 0,1,2,3,0,…

export function analyticsHandlers(getRole: () => Role) {
  const requireRole = (role: Role) => (getRole() === role ? null : fail(403, 'ACCESS_DENIED', 'You do not have access to this resource.'));

  return [
    http.get(`${B}/recruiters/me/analytics/overview`, ({ request }) => {
      const denied = requireRole('RECRUITER');
      if (denied) return denied;
      const r = parseRange(new URL(request.url), ['from', 'to']);
      if ('error' in r) return r.error;
      const applications = r.days.reduce((sum, _d, i) => sum + perDay(i), 0);
      const hired = Math.floor(applications / 10);
      const rejected = Math.floor(applications / 4);
      const withdrawn = Math.floor(applications / 12);
      const shortlisted = Math.floor(applications / 5);
      const submitted = applications - hired - rejected - withdrawn - shortlisted;
      return ok({
        from: r.from, to: r.to, applications,
        pipeline: { SUBMITTED: submitted, UNDER_REVIEW: 0, SHORTLISTED: shortlisted, INTERVIEW: 0, OFFERED: 0, HIRED: hired, REJECTED: rejected, WITHDRAWN: withdrawn },
        conversion: { hired, rate: applications === 0 ? null : Math.round((hired / applications) * 10_000) / 10_000 },
        timeToHire: { hired, averageDays: hired === 0 ? null : 12.4, medianDays: hired === 0 ? null : 11 },
        jobs: { published: 2, open: 1 },
      });
    }),

    http.get(`${B}/recruiters/me/analytics/jobs/:jobId`, ({ request, params }) => {
      const denied = requireRole('RECRUITER');
      if (denied) return denied;
      if (params.jobId !== ids.job) return fail(404, 'RESOURCE_NOT_FOUND', 'Job not found.');
      const r = parseRange(new URL(request.url), ['from', 'to']);
      if ('error' in r) return r.error;
      const series = r.days.map((date, i) => ({ date, applications: perDay(i) }));
      const total = series.reduce((sum, p) => sum + p.applications, 0);
      const reached = [total, Math.floor(total * 0.7), Math.floor(total * 0.4), Math.floor(total * 0.25), Math.floor(total * 0.1), Math.floor(total * 0.05)];
      const stages = ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED'];
      return ok({
        jobId: ids.job, from: r.from, to: r.to, applications: total,
        funnel: stages.map((stage, i) => ({ stage, count: reached[i] })),
        outcomes: { REJECTED: Math.floor(total / 4), WITHDRAWN: Math.floor(total / 12) },
        series,
      });
    }),

    http.get(`${B}/admin/analytics/overview`, ({ request }) => {
      const denied = requireRole('ADMIN');
      if (denied) return denied;
      const r = parseRange(new URL(request.url), ['from', 'to']);
      if ('error' in r) return r.error;
      const n = r.days.length;
      return ok({
        from: r.from, to: r.to,
        users: { registered: n * 3, jobSeekers: n * 2, recruiters: n, admins: 0 },
        jobs: { published: n, currentlyOpen: 31 },
        applications: { submitted: n * 8, hired: n },
        reports: { filed: Math.floor(n / 3), open: 3 },
      });
    }),

    http.get(`${B}/admin/analytics/timeseries`, ({ request }) => {
      const denied = requireRole('ADMIN');
      if (denied) return denied;
      const url = new URL(request.url);
      const metric = url.searchParams.get('metric');
      const interval = url.searchParams.get('interval') ?? 'DAY';
      if (!metric || !['USERS_REGISTERED', 'JOBS_PUBLISHED', 'APPLICATIONS_SUBMITTED'].includes(metric)) {
        return fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', [{ field: 'metric', code: metric ? 'INVALID_ENUM' : 'NOT_BLANK', message: 'must be one of the allowed values' }]);
      }
      if (!['DAY', 'WEEK', 'MONTH'].includes(interval)) return fail(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', [{ field: 'interval', code: 'INVALID_ENUM', message: 'must be one of the allowed values' }]);
      const r = parseRange(new URL(request.url), ['metric', 'from', 'to', 'interval']);
      if ('error' in r) return r.error;
      const step = interval === 'DAY' ? 1 : interval === 'WEEK' ? 7 : 30;
      const points = r.days.filter((_d, i) => i % step === 0).map((date, i) => ({ date, value: (i % 5) * step }));
      return ok({ metric, interval, from: r.from, to: r.to, points });
    }),
  ];
}
