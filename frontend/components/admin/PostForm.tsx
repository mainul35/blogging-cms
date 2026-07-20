'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Post, PostRequest, PostStatus } from '@/types/post';
import Editor from './Editor';
import CoverImageUpload from './CoverImageUpload';
import { api } from '@/lib/api';

interface PostFormProps {
  initialData?: Post;
  onSubmit: (data: PostRequest) => Promise<void>;
}

export default function PostForm({ initialData, onSubmit }: PostFormProps) {
  const router = useRouter();
  const [title, setTitle]               = useState(initialData?.title          ?? '');
  const [content, setContent]           = useState(initialData?.content        ?? '');
  const [excerpt, setExcerpt]           = useState(initialData?.excerpt        ?? '');
  const [coverImageUrl, setCoverImageUrl] = useState(initialData?.coverImageUrl ?? '');
  const [status, setStatus]             = useState<PostStatus>(initialData?.status ?? 'DRAFT');
  const [saving, setSaving]             = useState(false);
  const [error, setError]               = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      await onSubmit({ title, content, excerpt, coverImageUrl, status });
    } catch {
      setError('Failed to save post. Please try again.');
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!initialData || !confirm('Delete this post? This cannot be undone.')) return;
    await api.delete(`/api/posts/${initialData.id}`);
    // See app/(admin)/posts/create/page.tsx's handleSubmit for why refresh()
    // is needed alongside push() here.
    router.push('/posts');
    router.refresh();
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {error && <p className="text-sm text-red-500">{error}</p>}

      <div>
        <label className="block text-sm font-medium mb-1">Title</label>
        <input
          value={title}
          onChange={e => setTitle(e.target.value)}
          required
          placeholder="Post title"
          className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">Excerpt</label>
        <textarea
          value={excerpt}
          onChange={e => setExcerpt(e.target.value)}
          rows={2}
          placeholder="Short description shown in post listings…"
          className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black resize-none"
        />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">Cover Image</label>
        <CoverImageUpload value={coverImageUrl} onChange={setCoverImageUrl} />
      </div>

      <div>
        <label className="block text-sm font-medium mb-1">Content</label>
        <Editor value={content} onChange={setContent} />
      </div>

      <div className="flex items-center justify-between pt-2">
        <div className="flex items-center gap-3">
          <label className="text-sm font-medium">Status</label>
          <select
            value={status}
            onChange={e => setStatus(e.target.value as PostStatus)}
            className="border rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-black"
          >
            <option value="DRAFT">Draft</option>
            <option value="PUBLISHED">Published</option>
          </select>
        </div>

        <div className="flex gap-3">
          {initialData && (
            <button
              type="button"
              onClick={handleDelete}
              className="px-4 py-2 text-sm border border-red-300 text-red-600 rounded-lg hover:bg-red-50 transition-colors"
            >
              Delete
            </button>
          )}
          <button
            type="submit"
            disabled={saving}
            className="px-6 py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
          >
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </form>
  );
}
