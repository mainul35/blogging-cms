'use client';

import { useRouter } from 'next/navigation';
import PostForm from '@/components/admin/PostForm';
import { PostRequest } from '@/types/post';
import { api } from '@/lib/api';

export default function CreatePostPage() {
  const router = useRouter();

  const handleSubmit = async (data: PostRequest) => {
    await api.post('/api/posts', data);
    router.push('/posts');
  };

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">New Post</h1>
      <PostForm onSubmit={handleSubmit} />
    </div>
  );
}
