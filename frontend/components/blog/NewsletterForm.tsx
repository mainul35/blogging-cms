'use client';

import { useState } from 'react';
import { api } from '@/lib/api';

type Status = 'idle' | 'loading' | 'success' | 'error';

export default function NewsletterForm() {
  const [email, setEmail]     = useState('');
  const [status, setStatus]   = useState<Status>('idle');
  const [message, setMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatus('loading');
    try {
      const msg = await api.post<string>('/api/newsletter/subscribe', { email });
      setMessage(typeof msg === 'string' ? msg : 'Check your inbox to confirm your subscription.');
      setStatus('success');
    } catch (err) {
      setMessage(
        err instanceof Error && err.message
          ? err.message
          : 'Something went wrong. Please try again.'
      );
      setStatus('error');
    }
  };

  if (status === 'success') {
    return (
      <div className="bg-green-50 border border-green-200 rounded-xl p-6 text-center">
        <p className="font-semibold text-green-800">You&apos;re almost subscribed!</p>
        <p className="text-sm text-green-700 mt-1">{message}</p>
      </div>
    );
  }

  return (
    <div className="bg-gray-50 border border-gray-200 rounded-xl p-8">
      <h3 className="text-xl font-bold mb-1">Stay in the loop</h3>
      <p className="text-gray-600 text-sm mb-5">
        Get new posts delivered straight to your inbox. No spam, unsubscribe any time.
      </p>

      {status === 'error' && (
        <p className="text-sm text-red-500 mb-3">{message}</p>
      )}

      <form onSubmit={handleSubmit} className="flex gap-3 flex-col sm:flex-row">
        <input
          type="email"
          value={email}
          onChange={e => setEmail(e.target.value)}
          required
          placeholder="your@email.com"
          className="flex-1 border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-black"
        />
        <button
          type="submit"
          disabled={status === 'loading'}
          className="px-5 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 disabled:opacity-50 transition-colors whitespace-nowrap font-medium"
        >
          {status === 'loading' ? 'Subscribing…' : 'Subscribe'}
        </button>
      </form>
    </div>
  );
}
