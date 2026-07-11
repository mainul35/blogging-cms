'use client';

import { useState, useEffect, useMemo } from 'react';
import { NewsletterSubscriber } from '@/types/newsletter';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/utils';

export default function NewsletterSubscriberTable() {
  const [subscribers, setSubscribers] = useState<NewsletterSubscriber[]>([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');
  const [search, setSearch]           = useState('');

  useEffect(() => {
    api.get<NewsletterSubscriber[]>('/api/admin/newsletter/subscribers')
      .then(setSubscribers)
      .catch(() => setError('Failed to load subscribers.'))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return q ? subscribers.filter(s => s.email.toLowerCase().includes(q)) : subscribers;
  }, [subscribers, search]);

  return (
    <div className="bg-white rounded-xl shadow-sm overflow-hidden">
      {/* Header */}
      <div className="px-6 py-4 border-b flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h2 className="font-semibold">Confirmed Subscribers</h2>
          {!loading && !error && (
            <p className="text-sm text-gray-400 mt-0.5">
              {subscribers.length} subscriber{subscribers.length !== 1 ? 's' : ''}
            </p>
          )}
        </div>
        <input
          type="search"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Filter by email…"
          className="border rounded-lg px-3 py-1.5 text-sm w-56 focus:outline-none focus:ring-2 focus:ring-black"
        />
      </div>

      {/* Body */}
      {loading && <LoadingSkeleton />}

      {error && (
        <p className="px-6 py-10 text-center text-sm text-red-500">{error}</p>
      )}

      {!loading && !error && subscribers.length === 0 && (
        <p className="px-6 py-12 text-center text-sm text-gray-400">
          No confirmed subscribers yet.
        </p>
      )}

      {!loading && !error && subscribers.length > 0 && (
        <>
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-6 py-3 font-medium text-gray-500">Email</th>
                <th className="text-left px-6 py-3 font-medium text-gray-500 hidden sm:table-cell">
                  Subscribed
                </th>
                <th className="text-left px-6 py-3 font-medium text-gray-500">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered.map(sub => (
                <tr key={sub.id} className="hover:bg-gray-50">
                  <td className="px-6 py-3 font-medium">{sub.email}</td>
                  <td className="px-6 py-3 text-gray-400 hidden sm:table-cell">
                    {formatDate(sub.subscribedAt)}
                  </td>
                  <td className="px-6 py-3">
                    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-green-700 bg-green-100 px-2 py-0.5 rounded-full">
                      <span className="w-1.5 h-1.5 rounded-full bg-green-500 shrink-0" />
                      Confirmed
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Filter empty state */}
          {filtered.length === 0 && (
            <p className="px-6 py-8 text-center text-sm text-gray-400">
              No subscribers match &ldquo;{search}&rdquo;.
            </p>
          )}
        </>
      )}
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div className="divide-y divide-gray-100">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="px-6 py-3.5 flex gap-4">
          <div className="h-4 bg-gray-100 rounded animate-pulse w-48" />
          <div className="h-4 bg-gray-100 rounded animate-pulse w-24 hidden sm:block" />
          <div className="h-4 bg-gray-100 rounded animate-pulse w-16 ml-auto" />
        </div>
      ))}
    </div>
  );
}
