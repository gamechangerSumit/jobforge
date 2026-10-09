import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { ApiClientError, api, setAccessToken } from '@/lib/api/client';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => setAccessToken(null));
afterEach(() => vi.unstubAllGlobals());

test('sends the bearer token and unwraps the data envelope', async () => {
  setAccessToken('tok');
  const fetchMock = vi.fn().mockResolvedValue(json(200, { data: { ok: true }, meta: {} }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(api.get<{ ok: boolean }>('/ping')).resolves.toEqual({ ok: true });
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(url).toBe('/api/v1/ping');
  expect(new Headers(init.headers).get('Authorization')).toBe('Bearer tok');
});

test('getPage returns items together with page metadata', async () => {
  const page = { number: 1, size: 12, totalElements: 30, totalPages: 3, hasNext: true };
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(200, { data: [{ id: 'a' }], meta: { page } })));
  await expect(api.getPage('/jobs')).resolves.toEqual({ items: [{ id: 'a' }], page });
});

test('refreshes once on 401 and retries the original request', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(json(401, { error: { code: 'AUTH_UNAUTHENTICATED', message: 'x' } }))
    .mockResolvedValueOnce(json(200, { data: { accessToken: 'fresh' }, meta: {} }))
    .mockResolvedValueOnce(json(200, { data: 'secret', meta: {} }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(api.get<string>('/seekers/me/resumes')).resolves.toBe('secret');
  expect(fetchMock).toHaveBeenCalledTimes(3);
  expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/auth/refresh');
  expect(new Headers((fetchMock.mock.calls[2][1] as RequestInit).headers).get('Authorization')).toBe('Bearer fresh');
});

test('does not loop when the refresh itself fails', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(json(401, { error: { code: 'AUTH_UNAUTHENTICATED', message: 'x' } }))
    .mockResolvedValueOnce(json(401, { error: { code: 'AUTH_REFRESH_INVALID', message: 'y' } }));
  vi.stubGlobal('fetch', fetchMock);

  await expect(api.get('/companies/me')).rejects.toMatchObject({ status: 401 });
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

test('maps a non-JSON error body to a typed client error instead of throwing a SyntaxError', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 })));
  const error = await api.get('/jobs').catch((e: unknown) => e);
  expect(error).toBeInstanceOf(ApiClientError);
  expect((error as ApiClientError).error.code).toBe('UNEXPECTED_ERROR');
  expect((error as ApiClientError).status).toBe(502);
});

test('maps network failures to NETWORK_ERROR', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
  await expect(api.get('/jobs')).rejects.toMatchObject({ error: { code: 'NETWORK_ERROR' } });
});

test('does not force a JSON content type on multipart uploads', async () => {
  const fetchMock = vi.fn().mockResolvedValue(json(201, { data: { id: 'r1' }, meta: {} }));
  vi.stubGlobal('fetch', fetchMock);
  const form = new FormData();
  form.append('file', new Blob(['%PDF-1.7']), 'cv.pdf');
  await api.upload('/seekers/me/resumes', form);
  expect(new Headers((fetchMock.mock.calls[0][1] as RequestInit).headers).has('Content-Type')).toBe(false);
});
