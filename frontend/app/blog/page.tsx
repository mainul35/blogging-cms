import { api } from '@/lib/api';
import { Post } from '@/types/post';
import BlogSearch from '@/components/blog/BlogSearch';
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
    <>
      <main className="max-w-6xl mx-auto px-4 py-12">
        {/* Hero Banner */}
        <section className="text-center mb-12">
          <h1 className="text-5xl font-bold text-gray-900 mb-4">Welcome to {siteName}</h1>
        </section>

        {/* Realtime search + results grid -- client component, filters the
            already-fetched posts on every keystroke, no round-trip. */}
        <BlogSearch posts={posts} />

        {/* Newsletter subscribe widget below the post grid */}
        <div className="mt-12">
          <NewsletterForm />
        </div>
      </main>
    </>
  );
}