export interface CommentResponse {
  id: number;
  body: string;
  authorName: string;
  parentId: number | null;
  mentionedUsernames: string[];
  createdAt: string;
  replies: CommentResponse[];
  // Populated by GET /api/admin/comments only
  postTitle?: string;
  postSlug?: string;
}

export interface CommentRequest {
  body: string;
  authorName?: string;
  authorEmail?: string;
  parentId?: number;
}
