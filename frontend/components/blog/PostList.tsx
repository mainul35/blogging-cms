import { Post } from '@/types/post';
import PostCard from './PostCard';
import Link from 'next/link';

export default function PostList({ posts }: { posts: Post[] }) {
  if (posts.length === 0) {
    return (
      <div className="text-center py-20 text-gray-400">
        <p className="text-lg mb-2">No posts published yet.</p>
        <p className="text-sm">Check back soon!</p>
      </div>
    );
  }

  return (
    <div className="grid gap-6 sm:grid-cols-2">
      {posts.map(post => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  );
}
