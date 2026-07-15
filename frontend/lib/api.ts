const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

// Error responses are JSON ({ status, error, message }, see GlobalExceptionHandler) --
// extracting .message surfaces the actual validation reason (e.g. "Image must be
// 5MB or smaller") instead of the raw JSON blob or a generic fallback. Exported so
// lib/upload.ts's separate fetch call (not routed through request() below) gets the
// same treatment.
export async function extractErrorMessage(res: Response): Promise<string> {
  const contentType = res.headers.get('content-type') ?? '';
  try {
    if (contentType.includes('application/json')) {
      const body = await res.json();
      return typeof body?.message === 'string' ? body.message : `HTTP ${res.status}`;
    }
    const text = await res.text();
    return text || `HTTP ${res.status}`;
  } catch {
    return `HTTP ${res.status}`;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token =
    typeof window !== 'undefined' ? localStorage.getItem('blog_token') : null;

  const res = await fetch(`${BACKEND_URL}${path}`, {
    // Content is admin-editable and expected to change at any time — Next.js's
    // default fetch caching (persisted to disk, surviving dev-server restarts)
    // would otherwise serve stale data indefinitely until manually cleared.
    cache: 'no-store',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!res.ok) {
    throw new Error(await extractErrorMessage(res));
  }

  if (res.status === 204) return undefined as T;

  // Some endpoints (e.g. newsletter subscribe/confirm/send) return a plain
  // text message rather than JSON — calling res.json() on those throws a
  // confusing "Unexpected token" error instead of the actual response.
  const contentType = res.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    return res.text() as unknown as T;
  }
  return res.json();
}

export const api = {
  get:    <T>(path: string, init?: RequestInit) => request<T>(path, init),
  post:   <T>(path: string, body: unknown, init?: RequestInit) =>
            request<T>(path, { ...init, method: 'POST', body: JSON.stringify(body) }),
  put:    <T>(path: string, body: unknown, init?: RequestInit) =>
            request<T>(path, { ...init, method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string, init?: RequestInit) =>
            request<T>(path, { ...init, method: 'DELETE' }),
};
