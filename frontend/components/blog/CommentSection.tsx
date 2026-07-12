'use client';

import { useState, useEffect, useCallback } from 'react';
import { CommentResponse } from '@/types/comment';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/utils';
import CommentForm, { MentionCandidate } from './CommentForm';
import NewsletterOfferBanner from './NewsletterOfferBanner';

// Highlight @mentions in comment body — resolved mentions (present in this
// comment's mentionedUsernames, i.e. the handle actually exists) get a
// stronger style than an unresolved @word (typo, or the mentioned handle
// doesn't exist), which keeps today's plain decorative highlighting.
function renderBody(body: string, mentionedUsernames: string[]): React.ReactNode {
  return body.split(/(@[a-zA-Z0-9_]+)/g).map((part, i) => {
    if (!/^@[a-zA-Z0-9_]+$/.test(part)) return <span key={i}>{part}</span>;
    const resolved = mentionedUsernames.includes(part.slice(1));
    return (
      <span
        key={i}
        className={resolved ? 'text-blue-700 font-medium bg-blue-50 rounded px-0.5' : 'text-blue-600 font-medium'}
      >
        {part}
      </span>
    );
  });
}

function countAll(comments: CommentResponse[]): number {
  return comments.reduce((acc, c) => acc + 1 + countAll(c.replies), 0);
}

// Scoped to this post only — the people who've already commented here, so
// mention-autocomplete can suggest them without needing a site-wide directory.
function collectMentionCandidates(comments: CommentResponse[], acc: Map<string, MentionCandidate> = new Map()): Map<string, MentionCandidate> {
  for (const c of comments) {
    if (c.authorHandle) {
      acc.set(c.authorHandle, { handle: c.authorHandle, displayName: c.authorName });
    }
    collectMentionCandidates(c.replies, acc);
  }
  return acc;
}

// ---

interface CommentItemProps {
  comment: CommentResponse;
  slug: string;
  onRefresh: () => void;
  mentionCandidates: MentionCandidate[];
  depth?: number;
}

function CommentItem({ comment, slug, onRefresh, mentionCandidates, depth = 0 }: CommentItemProps) {
  const [replying, setReplying] = useState(false);

  return (
    <div>
      <div className="flex gap-3">
        {/* Avatar — real photo when available, else the initial letter */}
        {comment.authorAvatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={comment.authorAvatarUrl}
            alt=""
            className="w-8 h-8 rounded-full object-cover shrink-0"
          />
        ) : (
          <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center shrink-0 text-sm font-semibold text-gray-600 select-none">
            {comment.authorName[0].toUpperCase()}
          </div>
        )}

        <div className="flex-1 min-w-0">
          {/* Header */}
          <div className="flex items-center gap-2 flex-wrap mb-1">
            <span className="text-sm font-semibold">{comment.authorName}</span>
            <span className="text-xs text-gray-400">{formatDate(comment.createdAt)}</span>
          </div>

          {/* Body with @mention highlighting */}
          <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
            {renderBody(comment.body, comment.mentionedUsernames)}
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
                mentionCandidates={mentionCandidates}
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
              mentionCandidates={mentionCandidates}
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
  const mentionCandidates = Array.from(collectMentionCandidates(comments).values());

  return (
    <section className="mt-16 pt-10 border-t border-gray-100">
      <h2 className="text-xl font-bold mb-6">
        {loading ? 'Comments' : total > 0 ? `${total} Comment${total !== 1 ? 's' : ''}` : 'Comments'}
      </h2>

      <NewsletterOfferBanner />

      {/* Top-level comment form */}
      <div className="bg-gray-50 rounded-xl p-5 mb-8">
        <p className="text-sm font-medium text-gray-700 mb-3">Leave a comment</p>
        <CommentForm slug={slug} onSuccess={fetchComments} mentionCandidates={mentionCandidates} />
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
              mentionCandidates={mentionCandidates}
            />
          ))}
        </div>
      )}
    </section>
  );
}
