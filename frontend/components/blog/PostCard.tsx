import Link from 'next/link';
import { Post } from '@/types/post';
import { formatDate, truncate } from '@/lib/utils';

export default function PostCard({ post }: { post: Post }) {
  return (
    <article className="bg-white rounded-xl shadow-sm p-6 hover:shadow-md transition-shadow flex flex-col">
      {post.coverImageUrl && (
        <img
          src={post.coverImageUrl}
          alt={post.title}
          className="w-full h-44 object-cover rounded-lg mb-4"
        />
      )}

      {post.tags.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-2">
          {post.tags.map(tag => (
            <span key={tag} className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded">
              {tag}
            </span>
          ))}
        </div>
      )}

      <h2 className="text-lg font-bold mb-2 leading-snug">
        <Link href={`/post/${post.slug}`} className="hover:underline">
          {post.title}
        </Link>
      </h2>

      {post.excerpt && (
        <p className="text-sm text-gray-600 mb-4 flex-1">{truncate(post.excerpt, 120)}</p>
      )}

      <p className="text-xs text-gray-500 mt-auto">
        {post.publishedAt ? formatDate(post.publishedAt) : formatDate(post.createdAt)}
        {post.authorName && ` · ${post.authorName}`}
      </p>
    </article>
  );
}
