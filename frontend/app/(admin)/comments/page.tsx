import CommentModerationTable from '@/components/admin/CommentModerationTable';

export const metadata = { title: 'Comment Moderation' };

export default function AdminCommentsPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Comment Moderation</h1>
      <CommentModerationTable />
    </div>
  );
}
