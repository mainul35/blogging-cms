'use client';

import { useMemo, useState } from 'react';
import { Post } from '@/types/post';
import PostList from './PostList';

// Filters client-side over the already-fetched published posts -- no
// dedicated search endpoint needed at this scale, and it means results
// update on every keystroke with no network round-trip.
export default function BlogSearch({ posts }: { posts: Post[] }) {
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return posts;
    return posts.filter(post =>
      post.title.toLowerCase().includes(q) ||
      (post.excerpt ?? '').toLowerCase().includes(q) ||
      post.tags.some(tag => tag.toLowerCase().includes(q))
    );
  }, [posts, query]);

  return (
    <>
      <div className="max-w-md mx-auto mb-12">
        <input
          type="search"
          value={query}
          onChange={e => setQuery(e.target.value)}
          placeholder="Search articles..."
          aria-label="Search articles"
          className="w-full px-4 py-3 border border-gray-300 rounded-lg text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
        />
      </div>

      {query.trim() && filtered.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <p className="text-lg mb-2">No articles match &ldquo;{query.trim()}&rdquo;.</p>
          <p className="text-sm">Try a different search term.</p>
        </div>
      ) : (
        <PostList posts={filtered} />
      )}
    </>
  );
}
