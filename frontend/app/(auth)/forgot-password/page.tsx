'use client';

import { useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { useMailConfigured } from '@/lib/useMailConfigured';

export default function ForgotPasswordPage() {
  const mailConfigured = useMailConfigured();
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState<'idle' | 'loading' | 'sent'>('idle');
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatus('loading');
    setError('');
    try {
      await api.post('/api/auth/forgot-password', { email });
      setStatus('sent');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
      setStatus('idle');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="w-full max-w-md p-8 bg-white rounded-xl shadow-sm">
        <h1 className="text-2xl font-bold mb-6">Forgot password</h1>

        {mailConfigured === false ? (
          <div className="space-y-4">
            <p className="text-sm text-gray-600">
              Password reset via email isn&apos;t set up for this site yet. Contact the site administrator.
            </p>
            <Link href="/login" className="block text-center text-sm text-gray-600 hover:underline">
              Back to sign in
            </Link>
          </div>
        ) : status === 'sent' ? (
          <div className="space-y-4">
            <p className="text-sm text-gray-600">
              If <span className="font-medium">{email}</span> is registered, we&apos;ve sent a password
              reset link. Check your inbox — the link expires in 1 hour.
            </p>
            <Link href="/login" className="block text-center text-sm text-gray-600 hover:underline">
              Back to sign in
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <p className="text-sm text-gray-500">
              Enter your admin email and we&apos;ll send you a link to reset your password.
            </p>
            {error && <p className="text-sm text-red-500">{error}</p>}
            <div>
              <label className="block text-sm font-medium mb-1">Email</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
              />
            </div>
            <button
              type="submit"
              disabled={status === 'loading'}
              className="w-full py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
            >
              {status === 'loading' ? 'Sending…' : 'Send reset link'}
            </button>
            <Link href="/login" className="block text-center text-sm text-gray-600 hover:underline">
              Back to sign in
            </Link>
          </form>
        )}
      </div>
    </div>
  );
}
