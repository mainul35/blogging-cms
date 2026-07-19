'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { importMediumArticle } from '@/lib/mediumImport';

export default function MediumImportPage() {
  const router = useRouter();
  const [fetchUrl, setFetchUrl] = useState('');
  const [ownershipConfirmed, setOwnershipConfirmed] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const result = await importMediumArticle({ fetchUrl, ownershipConfirmed });
      router.push(`/posts/${result.postId}/edit`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not import article.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm p-6">
      <h2 className="text-lg font-semibold mb-1">Import from Medium</h2>
      <p className="text-xs text-gray-400 mb-4">
        Import one of your own Medium articles as a draft you can review and edit here. Nothing is
        published automatically.
      </p>

      <div className="bg-gray-50 border border-gray-200 rounded-lg p-3 mb-4 text-xs text-gray-600 space-y-1">
        <p className="font-medium text-gray-700">How to get the fetch URL</p>
        <p>
          Open your article on Medium and copy its URL — either straight from your browser&apos;s
          address bar, or from DevTools (F12) → Network tab: reload the page, find the top-level
          document request (the one matching the page itself, e.g.{' '}
          <code className="bg-gray-200 px-1 rounded">https://yourname.medium.com/your-article-title-abc123</code>
          ), right-click → Copy → Copy URL. Paste it below either way.
        </p>
      </div>

      <div className="border border-amber-300 bg-amber-50 rounded-lg p-3 mb-4">
        <p className="text-xs font-medium text-amber-800 mb-1">Ownership responsibility</p>
        <p className="text-xs text-amber-700">
          This tool does not verify that you are the author of the article at this URL. Importing
          content you do not own or do not have the right to republish is your responsibility. The
          developers and operators of this system accept no responsibility for content imported
          through this feature — only import articles you personally wrote.
        </p>
      </div>

      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Medium fetch URL</label>
          <input
            value={fetchUrl}
            onChange={e => setFetchUrl(e.target.value)}
            required
            placeholder="https://medium.com/_/api/posts/xxxxxxxxxxxx"
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
        </div>

        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            checked={ownershipConfirmed}
            onChange={e => setOwnershipConfirmed(e.target.checked)}
            required
            className="mt-0.5"
          />
          <span>
            I confirm this is my own original article and I have the right to republish it here.
          </span>
        </label>

        <button
          type="submit"
          disabled={loading || !ownershipConfirmed || !fetchUrl}
          className="w-full py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
        >
          {loading ? 'Importing…' : 'Import Article'}
        </button>
      </form>
    </div>
  );
}
