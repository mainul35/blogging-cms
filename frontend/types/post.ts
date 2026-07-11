export type PostStatus = 'DRAFT' | 'PUBLISHED';

export interface Post {
  id: number;
  title: string;
  slug: string;
  excerpt: string;
  content: string;
  coverImageUrl?: string;
  status: PostStatus;
  authorName: string;
  categoryName?: string;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

export interface PostRequest {
  title: string;
  content: string;
  excerpt?: string;
  coverImageUrl?: string;
  status: PostStatus;
  categoryId?: number;
  tagIds?: number[];
}
