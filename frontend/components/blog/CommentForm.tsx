'use client';

import { useState, useEffect, useRef } from 'react';
import { useSession, signIn } from 'next-auth/react';
import { api } from '@/lib/api';
import { authLib } from '@/lib/auth';
import { CommentRequest } from '@/types/comment';

export interface MentionCandidate {
  handle: string;
  displayName: string;
}

interface CommentFormProps {
  slug: string;
  parentId?: number;
  onSuccess: () => void;
  onCancel?: () => void;
  mentionCandidates?: MentionCandidate[];
}

export default function CommentForm({ slug, parentId, onSuccess, onCancel, mentionCandidates = [] }: CommentFormProps) {
  const { data: session, status: sessionStatus } = useSession();
  const [body, setBody]               = useState('');
  const [authorName, setAuthorName]   = useState('');
  const [authorEmail, setAuthorEmail] = useState('');
  const [isAdmin, setIsAdmin]         = useState(false);
  const [loading, setLoading]         = useState(false);
  const [error, setError]             = useState('');
  const [mentionQuery, setMentionQuery] = useState<string | null>(null);
  const [cursorPos, setCursorPos]     = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Auth state is only available client-side (localStorage)
  useEffect(() => { setIsAdmin(authLib.isAuthenticated()); }, []);

  const isReader = !isAdmin && sessionStatus === 'authenticated' && !!session?.readerToken;
  const isGuest = !isAdmin && !isReader;

  const suggestions = mentionQuery !== null && mentionQuery.length > 0
    ? mentionCandidates.filter(c => c.handle.toLowerCase().startsWith(mentionQuery.toLowerCase())).slice(0, 5)
    : [];

  const handleBodyChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value;
    const pos = e.target.selectionStart;
    setBody(value);
    setCursorPos(pos);
    const match = value.slice(0, pos).match(/@([a-zA-Z0-9_]*)$/);
    setMentionQuery(match ? match[1] : null);
  };

  const selectMention = (handle: string) => {
    const upToCursor = body.slice(0, cursorPos);
    const atIndex = upToCursor.lastIndexOf('@');
    if (atIndex === -1) return;
    const newBody = body.slice(0, atIndex) + '@' + handle + ' ' + body.slice(cursorPos);
    setBody(newBody);
    setMentionQuery(null);
    requestAnimationFrame(() => {
      const newPos = atIndex + handle.length + 2;
      textareaRef.current?.focus();
      textareaRef.current?.setSelectionRange(newPos, newPos);
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const payload: CommentRequest = {
      body,
      parentId,
      ...(isGuest ? { authorName, authorEmail } : {}),
    };

    try {
      await api.post(`/api/posts/${slug}/comments`, payload,
        isReader ? { headers: { Authorization: `Bearer ${session!.readerToken}` } } : undefined);
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
      {isReader && (
        <div className="flex items-center gap-2 text-sm text-gray-600">
          {session?.readerAvatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={session.readerAvatarUrl} alt="" className="w-6 h-6 rounded-full object-cover" />
          ) : (
            <div className="w-6 h-6 rounded-full bg-gray-200 flex items-center justify-center text-xs font-medium text-gray-500">
              {(session?.readerDisplayName ?? 'R').charAt(0).toUpperCase()}
            </div>
          )}
          Commenting as <span className="font-medium">{session?.readerDisplayName}</span>
        </div>
      )}

      {/* Guest-only fields and sign-in options — hidden for admin and signed-in readers */}
      {isGuest && (
        <>
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
          <div className="flex items-center gap-2 text-xs text-gray-400">
            <span>Or</span>
            <button
              type="button"
              onClick={() => signIn('google', { callbackUrl: window.location.href })}
              className="px-3 py-1.5 border rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
            >
              Continue with Google
            </button>
            <button
              type="button"
              onClick={() => signIn('github', { callbackUrl: window.location.href })}
              className="px-3 py-1.5 border rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
            >
              Continue with GitHub
            </button>
          </div>
        </>
      )}

      <div className="relative">
        <label className="block text-xs font-medium text-gray-600 mb-1">
          {parentId ? 'Reply' : 'Comment'}
          <span className="text-gray-400 font-normal ml-1">
            — use @username to mention someone
          </span>
        </label>
        <textarea
          ref={textareaRef}
          value={body}
          onChange={handleBodyChange}
          required
          rows={parentId ? 2 : 4}
          placeholder={
            parentId
              ? 'Write a reply… Use @username to mention someone.'
              : 'Share your thoughts… Use @username to mention someone.'
          }
          className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-black resize-none"
        />
        {suggestions.length > 0 && (
          <div className="absolute z-10 mt-1 w-56 bg-white border rounded-lg shadow-lg overflow-hidden">
            {suggestions.map(s => (
              <button
                key={s.handle}
                type="button"
                onMouseDown={e => e.preventDefault()}
                onClick={() => selectMention(s.handle)}
                className="w-full text-left px-3 py-1.5 text-sm hover:bg-gray-100 transition-colors"
              >
                <span className="font-medium">@{s.handle}</span>
                {s.displayName !== s.handle && (
                  <span className="text-gray-400 ml-1">{s.displayName}</span>
                )}
              </button>
            ))}
          </div>
        )}
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
