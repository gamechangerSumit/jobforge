import { expect, test, type Page, type Route } from '@playwright/test';

/**
 * Interview flows against a mocked API (page.route), so they need only the Next.js dev server, not the backend.
 * Session bootstrap is GET /api/v1/auth/me; every other call is answered by the stateful stub below.
 */
const API = '**/api/v1';
const json = (route: Route, body: unknown, status = 200) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

const company = { id: 'c1', name: 'Acme', slug: 'acme', verified: true };
const baseInterview = {
  id: 'i1', applicationId: 'a1', applicationStatus: 'INTERVIEW',
  job: { id: 'j1', title: 'Backend Engineer', company },
  seeker: { id: 's1', handle: 'sam', firstName: 'Sam', lastName: 'Lee' },
  scheduledBy: { id: 'r1', handle: 'rhea', firstName: 'Rhea', lastName: 'Rao' },
  type: 'VIDEO', scheduledAt: new Date(Date.now() + 3 * 86_400_000).toISOString(), durationMinutes: 45, timezone: 'Asia/Kolkata',
  locationOrLink: 'https://meet.example.com/room', status: 'SCHEDULED', seekerResponse: 'PENDING',
  createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
};

async function mockApi(page: Page, role: 'JOB_SEEKER' | 'RECRUITER') {
  let interview: Record<string, unknown> = { ...baseInterview };
  const calls: { method: string; path: string; body: unknown }[] = [];
  await page.route(`${API}/**`, async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    const method = request.method();
    const body = request.postData() ? JSON.parse(request.postData() as string) : undefined;
    if (path === '/auth/me') {
      return json(route, { data: { id: role === 'RECRUITER' ? 'r1' : 's1', email: 'x@example.test', role, handle: 'u', firstName: 'Test', lastName: 'User' } });
    }
    if (path === '/notifications' || path.startsWith('/notifications/')) return json(route, { data: [], meta: { page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } } });
    if (path === '/interviews' && method === 'GET') {
      return json(route, { data: [interview], meta: { page: { number: 0, size: 20, totalElements: 1, totalPages: 1 } } });
    }
    if (path === '/interviews/i1' && method === 'GET') return json(route, { data: interview });
    if (path === '/interviews/i1/respond' && method === 'POST') {
      calls.push({ method, path, body });
      const confirm = (body as { response: string }).response === 'CONFIRM';
      interview = { ...interview, status: confirm ? 'CONFIRMED' : 'DECLINED', seekerResponse: confirm ? 'CONFIRMED' : 'DECLINED' };
      return json(route, { data: interview });
    }
    if (path === '/interviews/i1/cancel' && method === 'POST') {
      calls.push({ method, path, body });
      interview = { ...interview, status: 'CANCELLED', cancelledReason: (body as { reason: string }).reason };
      return json(route, { data: interview });
    }
    return json(route, { error: { code: 'RESOURCE_NOT_FOUND', message: 'Not mocked' } }, 404);
  });
  return calls;
}

test('seeker sees an interview and confirms it', async ({ page }) => {
  const calls = await mockApi(page, 'JOB_SEEKER');
  await page.goto('/interviews');
  await expect(page.getByRole('heading', { name: 'My interviews' })).toBeVisible();
  await expect(page.getByText('Backend Engineer')).toBeVisible();
  await expect(page.getByRole('link', { name: 'https://meet.example.com/room' })).toHaveAttribute('rel', /noopener/);
  await expect(page.getByText('Internal notes')).toHaveCount(0);

  await page.getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('CONFIRMED').first()).toBeVisible();
  expect(calls).toEqual([{ method: 'POST', path: '/interviews/i1/respond', body: { response: 'CONFIRM' } }]);
});

test('seeker can decline with a note', async ({ page }) => {
  const calls = await mockApi(page, 'JOB_SEEKER');
  await page.goto('/interviews/i1');
  await page.getByLabel(/message to the recruiter/i).fill('Away that day');
  await page.getByRole('button', { name: 'Decline' }).click();
  await expect(page.getByText('DECLINED').first()).toBeVisible();
  expect(calls[0].body).toEqual({ response: 'DECLINE', note: 'Away that day' });
});

test('recruiter cancels an interview with a reason', async ({ page }) => {
  const calls = await mockApi(page, 'RECRUITER');
  await page.goto('/recruiter/interviews');
  await expect(page.getByRole('heading', { name: 'Interviews' })).toBeVisible();
  await expect(page.getByText('Candidate: Sam Lee')).toBeVisible();

  await page.getByRole('button', { name: 'Cancel interview' }).click();
  await page.getByRole('dialog').getByRole('textbox').fill('Position on hold');
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('CANCELLED').first()).toBeVisible();
  expect(calls[0]).toMatchObject({ path: '/interviews/i1/cancel', body: { reason: 'Position on hold' } });
});

test('recruiter reschedule form validates before calling the API', async ({ page }) => {
  await mockApi(page, 'RECRUITER');
  await page.goto('/recruiter/interviews/i1');
  await page.getByRole('button', { name: /reschedule/i }).click();
  await page.getByLabel('Date').fill('2020-01-01');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Choose a time in the future')).toBeVisible();
});
