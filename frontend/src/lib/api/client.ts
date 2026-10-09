import type { ApiErrorEnvelope, ApiResponse, PageMeta } from '@/types/api';

const API_BASE = '/api/v1';
let accessToken: string | null = null;
let refreshPromise: Promise<string | null> | null = null;

export class ApiClientError extends Error {
  constructor(public readonly error: ApiErrorEnvelope['error'], public readonly status = 0) { super(error.message); }
}

export interface Page<T> { items: T[]; page: PageMeta | undefined }

export function setAccessToken(token: string | null) { accessToken = token; }
export function getAccessToken() { return accessToken; }

/** Tolerates empty / non-JSON bodies (proxy errors, 502, 204) instead of throwing an uncaught SyntaxError. */
async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return undefined;
  try { return JSON.parse(text); } catch { return undefined; }
}

function isErrorEnvelope(body: unknown): body is ApiErrorEnvelope {
  return typeof body === 'object' && body !== null && 'error' in body;
}

async function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST', credentials: 'include', headers: { 'X-Requested-With': 'JobForge' },
    }).then(async (response) => {
      if (!response.ok) return null;
      const body = await readBody(response) as ApiResponse<{ accessToken: string }> | undefined;
      if (!body?.data?.accessToken) return null;
      setAccessToken(body.data.accessToken);
      return body.data.accessToken;
    }).catch(() => null).finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

const NO_REFRESH_PATHS = ['/auth/refresh', '/auth/login', '/auth/register'];

async function requestEnvelope<T>(path: string, init: RequestInit = {}, retry = true): Promise<ApiResponse<T>> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  const isForm = typeof FormData !== 'undefined' && init.body instanceof FormData;
  if (init.body && !isForm && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  headers.set('X-Request-Id', crypto.randomUUID());

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
  } catch {
    throw new ApiClientError({ code: 'NETWORK_ERROR', message: 'Cannot reach the server. Check your connection and retry.' } as ApiErrorEnvelope['error']);
  }

  if (response.status === 204) return { data: undefined as T, meta: {} as ApiResponse<T>['meta'] };
  const body = await readBody(response);

  // Any 401 on a protected call gets one silent refresh attempt (covers a page reload, where the in-memory token is gone).
  if (response.status === 401 && retry && !NO_REFRESH_PATHS.includes(path)) {
    const nextToken = await refreshAccessToken();
    if (nextToken) return requestEnvelope<T>(path, init, false);
  }

  if (!response.ok) {
    if (isErrorEnvelope(body)) throw new ApiClientError(body.error, response.status);
    throw new ApiClientError({ code: 'UNEXPECTED_ERROR', message: `Request failed (${response.status}).` } as ApiErrorEnvelope['error'], response.status);
  }
  if (body === undefined) {
    throw new ApiClientError({ code: 'UNEXPECTED_ERROR', message: 'The server returned an empty response.' } as ApiErrorEnvelope['error'], response.status);
  }
  return body as ApiResponse<T>;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  return (await requestEnvelope<T>(path, init)).data;
}

const json = (body: unknown) => (body === undefined ? undefined : JSON.stringify(body));

export const api = {
  get: <T>(path: string) => request<T>(path),
  getPage: async <T>(path: string): Promise<Page<T>> => {
    const envelope = await requestEnvelope<T[]>(path);
    return { items: envelope.data, page: envelope.meta?.page };
  },
  post: <T>(path: string, body?: unknown, headers?: HeadersInit) => request<T>(path, { method: 'POST', body: json(body), headers }),
  put: <T>(path: string, body?: unknown, headers?: HeadersInit) => request<T>(path, { method: 'PUT', body: json(body), headers }),
  patch: <T>(path: string, body?: unknown, headers?: HeadersInit) => request<T>(path, { method: 'PATCH', body: json(body), headers }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  deleteWithBody: <T>(path: string, body: unknown) => request<T>(path, { method: 'DELETE', body: json(body) }),
  /** Authenticated binary download (resume files); same refresh/error handling as JSON calls. */
  blob: async (path: string, retry = true): Promise<Blob> => {
    const headers = new Headers();
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
    let response: Response;
    try { response = await fetch(`${API_BASE}${path}`, { headers, credentials: 'include' }); }
    catch { throw new ApiClientError({ code: 'NETWORK_ERROR', message: 'Cannot reach the server.' } as ApiErrorEnvelope['error']); }
    if (response.status === 401 && retry && (await refreshAccessToken())) return api.blob(path, false);
    if (!response.ok) {
      const body = await readBody(response);
      throw new ApiClientError(isErrorEnvelope(body) ? body.error : ({ code: 'UNEXPECTED_ERROR', message: `Download failed (${response.status}).` } as ApiErrorEnvelope['error']), response.status);
    }
    return response.blob();
  },
  /** Multipart upload; the browser sets the Content-Type boundary. */
  upload: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', body: form }),
  /** Multipart PUT (avatar / company logo). */
  uploadPut: <T>(path: string, form: FormData) => request<T>(path, { method: 'PUT', body: form }),
};
