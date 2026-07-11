'use client';

import { useState, useEffect } from 'react';
import { api } from '@/lib/api';
import { authLib } from '@/lib/auth';
import { CommentRequest } from '@/types/comment';

interface CommentFormProps {
  slug: string;
  parentId?: number;
  onSuccess: () => void;
  onCancel?: () => void;
}

export default function CommentForm({ slug, parentId, onSuccess, onCancel }: CommentFormProps) {
  const [body, setBody]               = useState('');
  const [authorName, setAuthorName]   = useState('');
  const [authorEmail, setAuthorEmail] = useState('');
  const [isLoggedIn, setIsLoggedIn]   = useState(false);
  const [loading, setLoading]         = useState(false);
  const [error, setError]             = useState('');

  // Auth state is only available client-side (localStorage)
  useEffect(() => { setIsLoggedIn(authLib.isAuthenticated()); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const payload: CommentRequest = {
      body,
      parentId,
      ...(isLoggedIn ? {} : { authorName, authorEmail }),
    };

    try {
      await api.post(`/api/posts/${slug}/comments`, payload);
      setBody('');
      setAuthorName('');
      setAuthorEmail('');
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to post comment. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      {/* Guest-only fields — hidden when the user is signed in */}
      {!isLoggedIn && (
        <div className="grid sm:grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Name *</label>
            <input
              value={authorName}
              onChange={e => setAuthorName(e.target.value)}
              required
              placeholder="Your name"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-black"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Email *</label>
            <input
              type="email"
              value={authorEmail}
              onChange={e => setAuthorEmail(e.target.value)}
              required
              placeholder="your@email.com"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-black"
            />
          </div>
        </div>
      )}

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">
          {parentId ? 'Reply' : 'Comment'}
          <span className="text-gray-400 font-normal ml-1">
            — use @username to mention someone
          </span>
        </label>
        <textarea
          value={body}
          onChange={e => setBody(e.target.value)}
          required
          rows={parentId ? 2 : 4}
          placeholder={
            parentId
              ? 'Write a reply… Use @username to mention someone.'
              : 'Share your thoughts… Use @username to mention someone.'
          }
          className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-black resize-none"
        />
      </div>

      {error && <p className="text-xs text-red-500">{error}</p>}

      <div className="flex items-center gap-2">
        <button
          type="submit"
          disabled={loading || !body.trim()}
          className="px-4 py-1.5 bg-black text-white rounded-lg text-sm hover:bg-gray-800 disabled:opacity-50 transition-colors"
        >
          {loading ? 'Posting…' : parentId ? 'Post reply' : 'Post comment'}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-1.5 border rounded-lg text-sm text-gray-600 hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
