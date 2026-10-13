import { expect, test, type Page, type Route } from '@playwright/test';

/**
 * Reports & moderation flows against a mocked API (page.route): only the Next.js dev server is needed.
 * Session bootstrap is GET /api/v1/auth/me.
 */
const API = '**/api/v1';
const json = (route: Route, body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
const pageMeta = (n: number) => ({ page: { number: 0, size: 20, totalElements: n, totalPages: n ? 1 : 0 } });

const USER_ID = '11111111-1111-4111-8111-111111111111';
const REPORT_ID = '22222222-2222-4222-8222-222222222222';
const JOB_ID = '33333333-3333-4333-8333-333333333333';

const detail = {
  id: REPORT_ID, targetType: 'JOB', targetId: JOB_ID, reason: 'SCAM', details: 'Asks candidates to pay a fee.', status: 'OPEN',
  reporter: { id: 'u9', displayName: 'Sam Lee' },
  target: { type: 'JOB', id: JOB_ID, label: 'Backend Engineer', status: 'PUBLISHED', available: true },
  actions: [], createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z',
};

async function mock(page: Page, role: 'ADMIN' | 'JOB_SEEKER') {
  let report: Record<string, unknown> = { ...detail };
  const calls: { method: string; path: string; body: unknown }[] = [];
  await page.route(`${API}/**`, async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = req.method();
    const body = req.postData() ? JSON.parse(req.postData() as string) : undefined;
    if (path === '/auth/me') return json(route, { data: { id: role === 'ADMIN' ? 'a1' : 's1', email: 'x@example.test', role, handle: 'u', firstName: 'Test', lastName: 'User' } });
    if (path.startsWith('/notifications')) return json(route, { data: [], meta: pageMeta(0) });
    if (path === `/users/${USER_ID}/public`) return json(route, { data: { id: USER_ID, firstName: 'Pat', lastName: 'Kim', handle: 'pat', role: 'RECRUITER', headline: 'Hiring' } });
    if (path === '/reports' && method === 'POST') {
      calls.push({ method, path, body });
      return json(route, { data: { id: REPORT_ID, targetType: 'USER', targetId: USER_ID, reason: (body as { reason: string }).reason, status: 'OPEN', createdAt: new Date().toISOString() } }, 201);
    }
    if (path === '/admin/reports' && method === 'GET') {
      calls.push({ method, path: path + url.search, body });
      return json(route, { data: [{ id: REPORT_ID, targetType: 'JOB', targetId: JOB_ID, reason: 'SCAM', status: report.status, reporterId: 'u9', createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z' }], meta: pageMeta(1) });
    }
    if (path === `/admin/reports/${REPORT_ID}` && method === 'GET') return json(route, { data: report });
    if (path === `/admin/reports/${REPORT_ID}/resolve` && method === 'POST') {
      calls.push({ method, path, body });
      const b = body as { action: string; reason: string };
      report = { ...report, status: b.action === 'DISMISS' ? 'DISMISSED' : 'RESOLVED', actions: [{ id: 'm1', action: b.action, moderatorId: 'a1', reason: b.reason, createdAt: new Date().toISOString() }] };
      return json(route, { data: report });
    }
    return json(route, { error: { code: 'RESOURCE_NOT_FOUND', message: 'Not mocked' } }, 404);
  });
  return calls;
}

test('a signed-in user reports another user', async ({ page }) => {
  const calls = await mock(page, 'JOB_SEEKER');
  await page.goto(`/users/${USER_ID}`);
  await page.getByRole('button', { name: 'Report this user' }).click();
  await page.getByLabel('Reason').selectOption('HARASSMENT');
  await page.getByRole('button', { name: 'Send report' }).click();
  await expect(page.getByRole('status')).toContainText('your report was sent');
  expect(calls).toEqual([{ method: 'POST', path: '/reports', body: { targetType: 'USER', targetId: USER_ID, reason: 'HARASSMENT' } }]);
});

test('report form validates before calling the API', async ({ page }) => {
  const calls = await mock(page, 'JOB_SEEKER');
  await page.goto(`/users/${USER_ID}`);
  await page.getByRole('button', { name: 'Report this user' }).click();
  await page.getByRole('button', { name: 'Send report' }).click();
  await expect(page.getByText('Choose a reason')).toBeVisible();
  expect(calls).toEqual([]);
});

test('admin sees the queue, opens a report and dismisses it', async ({ page }) => {
  const calls = await mock(page, 'ADMIN');
  await page.goto('/admin/reports');
  await expect(page.getByRole('heading', { name: 'Reports' })).toBeVisible();
  await page.getByRole('link', { name: /Job report/ }).click();
  await expect(page.getByText('Backend Engineer')).toBeVisible();
  await expect(page.getByText('Asks candidates to pay a fee.')).toBeVisible();

  await page.getByLabel('Decision').selectOption('DISMISS');
  await page.getByLabel(/Reason/).fill('Listing is legitimate after review.');
  await page.getByRole('button', { name: 'Save decision' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Decision saved' })).toBeVisible();
  expect(calls.at(-1)).toMatchObject({ path: `/admin/reports/${REPORT_ID}/resolve`, body: { action: 'DISMISS', reason: 'Listing is legitimate after review.' } });
});

test('non-admins cannot open the moderation queue', async ({ page }) => {
  await mock(page, 'JOB_SEEKER');
  await page.goto('/admin/reports');
  await expect(page.getByText('You do not have access to this area.')).toBeVisible();
});
