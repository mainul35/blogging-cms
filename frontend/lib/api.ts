const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

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
    const message = await res.text().catch(() => `HTTP ${res.status}`);
    throw new Error(message);
  }

  if (res.status === 204) return undefined as T;
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
