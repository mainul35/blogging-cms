'use client';

import { useRouter } from 'next/navigation';
import PostForm from '@/components/admin/PostForm';
import { PostRequest } from '@/types/post';
import { api } from '@/lib/api';

export default function CreatePostPage() {
  const router = useRouter();

  const handleSubmit = async (data: PostRequest) => {
    await api.post('/api/posts', data);
    // The /posts list is a Server Component -- push() alone can reuse Next's
    // client-side Router Cache and show the pre-mutation list until a hard
    // reload. refresh() invalidates that cache so the list re-fetches fresh.
    router.push('/posts');
    router.refresh();
  };

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">New Post</h1>
      <PostForm onSubmit={handleSubmit} />
    </div>
  );
}
