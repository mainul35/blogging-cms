import { cookies } from 'next/headers';
import { api } from '@/lib/api';
import Link from 'next/link';

interface Stats {
  totalPosts: number;
  publishedPosts: number;
  draftPosts: number;
}

export default async function DashboardPage() {
  let stats: Stats = { totalPosts: 0, publishedPosts: 0, draftPosts: 0 };
  try {
    // This runs server-side, where lib/api.ts's localStorage-based token lookup
    // can't find anything (localStorage doesn't exist outside the browser) —
    // /api/admin/stats requires auth, so that always silently fell back to
    // zeros. Forward the same JWT from the auth cookie instead.
    const token = (await cookies()).get('token')?.value;
    stats = await api.get<Stats>('/api/admin/stats', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
  } catch {}

  return (
    <div>
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <Link
          href="/posts/create"
          className="px-4 py-2 bg-black text-white rounded-lg hover:bg-gray-800 text-sm transition-colors"
        >
          New Post
        </Link>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
        <StatCard label="Total Posts"  value={stats.totalPosts} />
        <StatCard label="Published"    value={stats.publishedPosts} color="text-green-600" />
        <StatCard label="Drafts"       value={stats.draftPosts}     color="text-yellow-600" />
      </div>
    </div>
  );
}

function StatCard({ label, value, color = 'text-black' }: { label: string; value: number; color?: string }) {
  return (
    <div className="bg-white rounded-xl p-6 shadow-sm">
      <p className="text-sm text-gray-500">{label}</p>
      <p className={`text-4xl font-bold mt-2 ${color}`}>{value}</p>
    </div>
  );
}
