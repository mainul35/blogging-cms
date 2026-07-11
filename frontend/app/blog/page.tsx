import Link from 'next/link';
import { api } from '@/lib/api';
import { Post } from '@/types/post';
import PostList from '@/components/blog/PostList';
import NewsletterForm from '@/components/blog/NewsletterForm';
import { getSiteSettings } from '@/lib/settings';

export const metadata = { title: 'Blog' };

export default async function BlogPage() {
  let posts: Post[] = [];
  try {
    posts = await api.get<Post[]>('/api/posts?status=PUBLISHED');
  } catch {}
  const { siteName } = await getSiteSettings();

  return (
    <main className="max-w-6xl mx-auto px-4 py-12">
      {/* Hero Banner */}
      <section className="text-center mb-12">
        <h1 className="text-5xl font-bold text-gray-900 mb-4">Welcome to {siteName}</h1>
        <p className="text-lg text-gray-600 mb-8 max-w-2xl mx-auto">
          Discover insightful stories, tutorials, and ideas from our community of writers.
        </p>

        {/* Search Bar */}
        <form action="/blog" method="get" className="max-w-md mx-auto flex gap-2">
          <input
            type="search"
            name="q"
            placeholder="Search articles..."
            aria-label="Search articles"
            className="flex-1 px-4 py-3 border border-gray-300 rounded-lg text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 text-sm"
          />
          <button
            type="submit"
            className="px-6 py-3 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition-colors text-sm"
          >
            Search
          </button>
        </form>
      </section>

      {/* Blog Posts Grid */}
      <PostList posts={posts} />

      {/* Newsletter subscribe widget below the post grid */}
      <div className="mt-12">
        <NewsletterForm />
      </div>
    </main>
  );
}
