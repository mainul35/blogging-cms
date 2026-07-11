const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

export async function uploadImage(file: File): Promise<string> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('blog_token') : null;

  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${BACKEND_URL}/api/admin/uploads`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });

  if (!res.ok) {
    const message = await res.text().catch(() => `HTTP ${res.status}`);
    throw new Error(message);
  }

  const data: { url: string } = await res.json();
  return `${BACKEND_URL}${data.url}`;
}
