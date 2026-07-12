'use client';

import { useState, useEffect, useRef } from 'react';
import { Post } from '@/types/post';
import { api } from '@/lib/api';

type Status = 'idle' | 'loadingPosts' | 'sending' | 'success' | 'error';

export default function SendDigestForm() {
  const [posts, setPosts]       = useState<Post[]>([]);
  const [postId, setPostId]     = useState<string>('');
  const [query, setQuery]       = useState('');
  const [isOpen, setIsOpen]     = useState(false);
  const [status, setStatus]     = useState<Status>('loadingPosts');
  const [message, setMessage]   = useState('');
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    api.get<Post[]>('/api/posts?status=PUBLISHED')
      .then(data => {
        setPosts(data);
        if (data.length > 0) {
          setPostId(String(data[0].id));
          setQuery(data[0].title);
        }
        setStatus('idle');
      })
      .catch(() => {
        setMessage('Could not load posts.');
        setStatus('error');
      });
  }, []);

  // Close the dropdown on an outside click, reverting the typed text back to
  // whatever's actually selected if they clicked away without picking anything.
  useEffect(() => {
    if (!isOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        const selected = posts.find(p => String(p.id) === postId);
        setQuery(selected ? selected.title : '');
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen, posts, postId]);

  const filteredPosts = query.trim() === ''
    ? posts
    : posts.filter(p => p.title.toLowerCase().includes(query.toLowerCase()));

  const selectPost = (post: Post) => {
    setPostId(String(post.id));
    setQuery(post.title);
    setIsOpen(false);
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!postId) return;
    if (!confirm('Send a digest email to all confirmed subscribers?')) return;

    setStatus('sending');
    setMessage('');
    try {
      const result = await api.post<string>(
        `/api/admin/newsletter/send?postId=${postId}`,
        {}
      );
      setMessage(typeof result === 'string' ? result : 'Digest sent successfully.');
      setStatus('success');
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'Failed to send digest.');
      setStatus('error');
    }
  };

  const reset = () => {
    setStatus('idle');
    setMessage('');
  };

  return (
    <div className="bg-white rounded-xl shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 border-b">
        <h2 className="font-semibold">Send Digest</h2>
        <p className="text-sm text-gray-400 mt-0.5">
          Broadcast a post to all confirmed subscribers.
        </p>
      </div>

      {/* Body */}
      <div className="px-6 py-5">
        {/* Success state */}
        {status === 'success' && (
          <div className="space-y-4">
            <div className="bg-green-50 border border-green-200 rounded-lg p-4">
              <p className="text-sm font-medium text-green-800">Digest queued!</p>
              <p className="text-sm text-green-700 mt-1">{message}</p>
            </div>
            <button
              onClick={reset}
              className="w-full py-2 border rounded-lg text-sm text-gray-600 hover:bg-gray-50 transition-colors"
            >
              Send another
            </button>
          </div>
        )}

        {/* Error loading posts */}
        {status === 'error' && posts.length === 0 && (
          <p className="text-sm text-red-500">{message}</p>
        )}

        {/* Form */}
        {status !== 'success' && (
          <form onSubmit={handleSend} className="space-y-4">
            <div ref={containerRef} className="relative">
              <label className="block text-xs font-medium text-gray-600 mb-1.5">
                Post to feature
              </label>
              {status === 'loadingPosts' ? (
                <div className="h-10 bg-gray-100 rounded-lg animate-pulse" />
              ) : posts.length === 0 ? (
                <p className="text-sm text-gray-400">
                  No published posts available.
                </p>
              ) : (
                <>
                  <input
                    value={query}
                    onChange={e => { setQuery(e.target.value); setIsOpen(true); }}
                    onFocus={() => { setIsOpen(true); setQuery(''); }}
                    placeholder="Search posts by title…"
                    autoComplete="off"
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-black"
                  />
                  {isOpen && (
                    <div className="absolute z-10 mt-1 w-full max-h-64 overflow-y-auto bg-white border rounded-lg shadow-lg">
                      {filteredPosts.length === 0 ? (
                        <p className="px-3 py-2 text-sm text-gray-400">No matching posts.</p>
                      ) : (
                        filteredPosts.map(post => (
                          <button
                            key={post.id}
                            type="button"
                            onMouseDown={e => e.preventDefault()}
                            onClick={() => selectPost(post)}
                            className={`w-full text-left px-3 py-2 text-sm hover:bg-gray-100 transition-colors ${
                              String(post.id) === postId ? 'bg-gray-50 font-medium' : ''
                            }`}
                          >
                            {post.title}
                          </button>
                        ))
                      )}
                    </div>
                  )}
                </>
              )}
            </div>

            {/* Post preview card */}
            {postId && posts.length > 0 && (
              <PostPreview post={posts.find(p => String(p.id) === postId)!} />
            )}

            {status === 'error' && message && (
              <p className="text-xs text-red-500">{message}</p>
            )}

            <button
              type="submit"
              disabled={status === 'sending' || status === 'loadingPosts' || posts.length === 0}
              className="w-full py-2 bg-black text-white rounded-lg text-sm hover:bg-gray-800 disabled:opacity-50 transition-colors"
            >
              {status === 'sending' ? 'Sending…' : 'Send to all subscribers'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}

function PostPreview({ post }: { post: Post }) {
  return (
    <div className="border rounded-lg p-3 bg-gray-50 space-y-1">
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">Preview</p>
      <p className="text-sm font-semibold leading-snug line-clamp-2">{post.title}</p>
      {post.excerpt && (
        <p className="text-xs text-gray-500 line-clamp-2">{post.excerpt}</p>
      )}
    </div>
  );
}
