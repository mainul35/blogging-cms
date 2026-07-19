'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import PostForm from '@/components/admin/PostForm';
import { Post, PostRequest } from '@/types/post';
import { api } from '@/lib/api';

export default function EditPostPage() {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const [post, setPost] = useState<Post | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get<Post>(`/api/posts/id/${id}`)
      .then(setPost)
      .catch(() => setError('Failed to load post.'));
  }, [id]);

  const handleSubmit = async (data: PostRequest) => {
    await api.put(`/api/posts/${id}`, data);
    // See create/page.tsx's handleSubmit for why refresh() is needed here too.
    router.push('/posts');
    router.refresh();
  };

  if (error) return <p className="text-red-500">{error}</p>;
  if (!post)  return <p className="text-gray-400">Loading…</p>;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Edit Post</h1>
      <PostForm initialData={post} onSubmit={handleSubmit} />
    </div>
  );
}
