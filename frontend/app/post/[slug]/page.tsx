import { notFound } from 'next/navigation';
import Link from 'next/link';
import { api } from '@/lib/api';
import { Post } from '@/types/post';
import MarkdownRenderer from '@/components/blog/MarkdownRenderer';
import CommentSection from '@/components/blog/CommentSection';
import NewsletterForm from '@/components/blog/NewsletterForm';
import { formatDate } from '@/lib/utils';

export async function generateMetadata({ params }: { params: { slug: string } }) {
  try {
    const post = await api.get<Post>(`/api/posts/${params.slug}`);
    return { title: post.title, description: post.excerpt };
  } catch {
    return { title: 'Post not found' };
  }
}

export default async function PostPage({ params }: { params: { slug: string } }) {
  let post: Post;
  try {
    post = await api.get<Post>(`/api/posts/${params.slug}`);
  } catch {
    notFound();
  }

  return (
    <>
      <main className="max-w-3xl mx-auto px-4 py-12">
        <Link href="/blog" className="text-sm text-gray-400 hover:underline mb-6 block">
          ← Back to Blog
        </Link>

        <h1 className="text-4xl font-bold mb-3">{post.title}</h1>

        {post.coverImageUrl && (
          <img
            src={post.coverImageUrl}
            alt={post.title}
            className="w-full h-64 object-cover rounded-xl mb-8"
          />
        )}

        <p className="text-sm text-gray-400 mb-8">
          {post.publishedAt ? formatDate(post.publishedAt) : formatDate(post.createdAt)}
          {post.authorName && ` · ${post.authorName}`}
          {post.categoryName && ` · ${post.categoryName}`}
        </p>

        <MarkdownRenderer content={post.content} />

        {post.tags.length > 0 && (
          <div className="mt-10 flex flex-wrap gap-2">
            {post.tags.map(tag => (
              <span key={tag} className="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded">
                {tag}
              </span>
            ))}
          </div>
        )}

        {/* Comments — client component, fetches its own data */}
        <CommentSection slug={params.slug} />

        {/* Newsletter subscribe widget */}
        <div className="mt-12">
          <NewsletterForm />
        </div>
      </main>
    </>
  );
}
