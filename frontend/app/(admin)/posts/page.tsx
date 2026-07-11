import Link from 'next/link';
import { api } from '@/lib/api';
import { Post } from '@/types/post';
import { formatDate } from '@/lib/utils';

export default async function AdminPostsPage() {
  let posts: Post[] = [];
  try {
    posts = await api.get<Post[]>('/api/posts?status=all');
  } catch {}

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Posts</h1>
        <Link
          href="/posts/create"
          className="px-4 py-2 bg-black text-white rounded-lg hover:bg-gray-800 text-sm transition-colors"
        >
          New Post
        </Link>
      </div>

      <div className="bg-white rounded-xl shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Title</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Author</th>
              <th className="text-left px-6 py-3 font-medium text-gray-500">Created</th>
              <th className="px-6 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {posts.map(post => (
              <tr key={post.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 font-medium">{post.title}</td>
                <td className="px-6 py-4">
                  <span className={`px-2 py-1 rounded text-xs font-medium ${
                    post.status === 'PUBLISHED'
                      ? 'bg-green-100 text-green-700'
                      : 'bg-yellow-100 text-yellow-700'
                  }`}>
                    {post.status}
                  </span>
                </td>
                <td className="px-6 py-4 text-gray-500">{post.authorName}</td>
                <td className="px-6 py-4 text-gray-500">{formatDate(post.createdAt)}</td>
                <td className="px-6 py-4 text-right">
                  <Link
                    href={`/posts/${post.id}/edit`}
                    className="text-blue-600 hover:underline"
                  >
                    Edit
                  </Link>
                </td>
              </tr>
            ))}
            {posts.length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-12 text-center text-gray-400">
                  No posts yet.{' '}
                  <Link href="/posts/create" className="underline">
                    Create your first post
                  </Link>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
