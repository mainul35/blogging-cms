'use client';

import { useState, useEffect, useMemo } from 'react';
import Link from 'next/link';
import { CommentResponse } from '@/types/comment';
import { api } from '@/lib/api';
import { formatDate, truncate } from '@/lib/utils';

export default function CommentModerationTable() {
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [search, setSearch]     = useState('');
  const [deleting, setDeleting] = useState<number | null>(null);

  useEffect(() => {
    api.get<CommentResponse[]>('/api/admin/comments')
      .then(setComments)
      .catch(() => setError('Failed to load comments.'))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    if (!q) return comments;
    return comments.filter(c =>
      c.authorName.toLowerCase().includes(q) ||
      c.body.toLowerCase().includes(q) ||
      (c.postTitle ?? '').toLowerCase().includes(q)
    );
  }, [comments, search]);

  const handleDelete = async (id: number) => {
    if (!confirm('Delete this comment? This cannot be undone.')) return;
    setDeleting(id);
    try {
      await api.delete(`/api/comments/${id}`);
      setComments(prev => prev.filter(c => c.id !== id));
    } catch {
      alert('Failed to delete comment. Please try again.');
    } finally {
      setDeleting(null);
    }
  };

  if (loading) return <LoadingSkeleton />;

  if (error) return <p className="text-sm text-red-500">{error}</p>;

  return (
    <div className="space-y-4">
      {/* Toolbar */}
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <input
          type="search"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Filter by author, content, or post…"
          className="border rounded-lg px-3 py-2 text-sm w-80 focus:outline-none focus:ring-2 focus:ring-black"
        />
        <span className="text-sm text-gray-500 shrink-0">
          {filtered.length === comments.length
            ? `${comments.length} comment${comments.length !== 1 ? 's' : ''}`
            : `${filtered.length} of ${comments.length} comments`}
        </span>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        {filtered.length === 0 ? (
          <p className="px-6 py-12 text-center text-sm text-gray-400">
            {comments.length === 0
              ? 'No comments yet.'
              : 'No comments match your filter.'}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-6 py-3 font-medium text-gray-500 whitespace-nowrap">Author</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500">Comment</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500 hidden md:table-cell whitespace-nowrap">Post</th>
                  <th className="text-left px-6 py-3 font-medium text-gray-500 hidden lg:table-cell whitespace-nowrap">Date</th>
                  <th className="px-6 py-3 w-20" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map(comment => (
                  <CommentRow
                    key={comment.id}
                    comment={comment}
                    deleting={deleting === comment.id}
                    onDelete={() => handleDelete(comment.id)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

// ---

function CommentRow({
  comment,
  deleting,
  onDelete,
}: {
  comment: CommentResponse;
  deleting: boolean;
  onDelete: () => void;
}) {
  return (
    <tr className="hover:bg-gray-50 align-top">
      {/* Author */}
      <td className="px-6 py-4">
        <div className="flex items-center gap-2">
          <Avatar name={comment.authorName} />
          <div>
            <p className="font-medium leading-tight">{comment.authorName}</p>
            {comment.parentId !== null && (
              <span className="text-xs text-blue-500">↩ reply</span>
            )}
          </div>
        </div>
      </td>

      {/* Comment body + mentions */}
      <td className="px-6 py-4 max-w-xs">
        <p className="text-gray-700 leading-snug" title={comment.body}>
          {truncate(comment.body, 100)}
        </p>
        {comment.mentionedUsernames.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1.5">
            {comment.mentionedUsernames.map(u => (
              <span
                key={u}
                className="text-xs bg-blue-50 text-blue-600 px-1.5 py-0.5 rounded"
              >
                @{u}
              </span>
            ))}
          </div>
        )}
      </td>

      {/* Post link */}
      <td className="px-6 py-4 hidden md:table-cell">
        {comment.postSlug ? (
          <Link
            href={`/post/${comment.postSlug}`}
            target="_blank"
            rel="noopener noreferrer"
            className="text-blue-600 hover:underline leading-snug"
            title={comment.postTitle}
          >
            {truncate(comment.postTitle ?? comment.postSlug, 32)}
          </Link>
        ) : (
          <span className="text-gray-300">—</span>
        )}
      </td>

      {/* Date */}
      <td className="px-6 py-4 text-gray-400 whitespace-nowrap hidden lg:table-cell">
        {formatDate(comment.createdAt)}
      </td>

      {/* Delete */}
      <td className="px-6 py-4 text-right">
        <button
          onClick={onDelete}
          disabled={deleting}
          className="text-sm text-red-500 hover:text-red-700 disabled:opacity-40 transition-colors"
        >
          {deleting ? 'Deleting…' : 'Delete'}
        </button>
      </td>
    </tr>
  );
}

function Avatar({ name }: { name: string }) {
  return (
    <div className="w-7 h-7 rounded-full bg-gray-200 flex items-center justify-center text-xs font-semibold text-gray-600 shrink-0 select-none">
      {name[0].toUpperCase()}
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="h-14 bg-gray-100 rounded-lg animate-pulse" />
      ))}
    </div>
  );
}
