'use client';

import { useState, useEffect, useCallback } from 'react';
import { CommentResponse } from '@/types/comment';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/utils';
import CommentForm from './CommentForm';

// Highlight @mentions in comment body as blue spans
function renderBody(body: string): React.ReactNode {
  return body.split(/(@[a-zA-Z0-9_]+)/g).map((part, i) =>
    /^@[a-zA-Z0-9_]+$/.test(part)
      ? <span key={i} className="text-blue-600 font-medium">{part}</span>
      : <span key={i}>{part}</span>
  );
}

function countAll(comments: CommentResponse[]): number {
  return comments.reduce((acc, c) => acc + 1 + countAll(c.replies), 0);
}

// ---

interface CommentItemProps {
  comment: CommentResponse;
  slug: string;
  onRefresh: () => void;
  depth?: number;
}

function CommentItem({ comment, slug, onRefresh, depth = 0 }: CommentItemProps) {
  const [replying, setReplying] = useState(false);

  return (
    <div>
      <div className="flex gap-3">
        {/* Avatar initial */}
        <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center shrink-0 text-sm font-semibold text-gray-600 select-none">
          {comment.authorName[0].toUpperCase()}
        </div>

        <div className="flex-1 min-w-0">
          {/* Header */}
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <span className="text-sm font-semibold">{comment.authorName}</span>
            <span className="text-xs text-gray-400">{formatDate(comment.createdAt)}</span>
          </div>

          {/* Body with @mention highlighting */}
          <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
            {renderBody(comment.body)}
          </p>

          {/* Reply trigger — cap nesting at depth 3 to keep layout tidy */}
          {depth < 3 && (
            <button
              onClick={() => setReplying(v => !v)}
              className="mt-1.5 text-xs text-gray-400 hover:text-gray-700 transition-colors"
            >
              {replying ? 'Cancel reply' : 'Reply'}
            </button>
          )}

          {replying && (
            <div className="mt-3">
              <CommentForm
                slug={slug}
                parentId={comment.id}
                onSuccess={() => { setReplying(false); onRefresh(); }}
                onCancel={() => setReplying(false)}
              />
            </div>
          )}
        </div>
      </div>

      {/* Nested replies */}
      {comment.replies.length > 0 && (
        <div className="mt-4 ml-11 pl-4 border-l-2 border-gray-100 space-y-4">
          {comment.replies.map(reply => (
            <CommentItem
              key={reply.id}
              comment={reply}
              slug={slug}
              onRefresh={onRefresh}
              depth={depth + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ---

export default function CommentSection({ slug }: { slug: string }) {
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');

  const fetchComments = useCallback(async () => {
    setError('');
    try {
      const data = await api.get<CommentResponse[]>(`/api/posts/${slug}/comments`);
      setComments(data);
    } catch {
      setError('Failed to load comments.');
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => { fetchComments(); }, [fetchComments]);

  const total = countAll(comments);

  return (
    <section className="mt-16 pt-10 border-t border-gray-100">
      <h2 className="text-xl font-bold mb-6">
        {loading ? 'Comments' : total > 0 ? `${total} Comment${total !== 1 ? 's' : ''}` : 'Comments'}
      </h2>

      {/* Top-level comment form */}
      <div className="bg-gray-50 rounded-xl p-5 mb-8">
        <p className="text-sm font-medium text-gray-700 mb-3">Leave a comment</p>
        <CommentForm slug={slug} onSuccess={fetchComments} />
      </div>

      {/* Comment list */}
      {loading && (
        <p className="text-sm text-gray-400">Loading comments…</p>
      )}

      {error && (
        <p className="text-sm text-red-500">{error}</p>
      )}

      {!loading && !error && comments.length === 0 && (
        <p className="text-sm text-gray-400">
          No comments yet — be the first to share your thoughts!
        </p>
      )}

      {!loading && !error && comments.length > 0 && (
        <div className="space-y-6">
          {comments.map(comment => (
            <CommentItem
              key={comment.id}
              comment={comment}
              slug={slug}
              onRefresh={fetchComments}
            />
          ))}
        </div>
      )}
    </section>
  );
}
