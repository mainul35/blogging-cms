'use client';

import { useState } from 'react';
import { Mail, Clock } from 'lucide-react';
import { Eye, EyeOff } from 'lucide-react';
import { api } from '@/lib/api';
import { cn } from '@/lib/utils';
import MailProviderFields from '@/components/admin/MailProviderFields';
import { DEFAULT_MAIL_FORM_VALUES } from '@/types/mailSettings';

export default function SetupPage() {
  const [siteName, setSiteName] = useState('');
  const [adminName, setAdminName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [configureMail, setConfigureMail] = useState(false);
  const [mailValues, setMailValues] = useState(DEFAULT_MAIL_FORM_VALUES);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await api.post('/api/setup', {
        siteName,
        adminName,
        adminEmail,
        adminPassword,
        // Omitted entirely (not just defaulted) when skipped, so the backend
        // leaves the log-only default from the migration seed untouched.
        mailSettings: configureMail ? mailValues : undefined,
      });
      // Full navigation: middleware re-checks setup status on every request,
      // and a plain client-side route change wouldn't force that re-check.
      window.location.href = '/login';
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12">
      <div className="w-full max-w-md p-8 bg-white rounded-xl shadow-sm">
        <h1 className="text-2xl font-bold mb-2">Welcome — let&apos;s set up your blog</h1>
        <p className="text-sm text-gray-500 mb-6">
          This runs once. Configure your site name and your own admin account, replacing the
          default credentials this CMS ships with.
        </p>
        {error && <p className="mb-4 text-sm text-red-500">{error}</p>}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Site name</label>
            <input
              type="text"
              value={siteName}
              onChange={e => setSiteName(e.target.value)}
              required
              maxLength={100}
              className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Your name</label>
            <input
              type="text"
              value={adminName}
              onChange={e => setAdminName(e.target.value)}
              required
              maxLength={50}
              className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Your email</label>
            <input
              type="email"
              value={adminEmail}
              onChange={e => setAdminEmail(e.target.value)}
              required
              className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Choose a password</label>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={adminPassword}
                onChange={e => setAdminPassword(e.target.value)}
                required
                minLength={8}
                className="w-full border rounded-lg px-3 py-2 pr-10 focus:outline-none focus:ring-2 focus:ring-black"
              />
              <button
                type="button"
                onClick={() => setShowPassword(v => !v)}
                tabIndex={-1}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-700 transition-colors"
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            <p className="mt-1 text-xs text-gray-400">At least 8 characters.</p>
          </div>

          <div className="border-t pt-6">
            <h2 className="text-base font-semibold mb-1">Want email-powered features?</h2>
            <p className="text-sm text-gray-500 mb-4">
              Password-reset links and newsletter subscriptions only work with a mail gateway behind
              them — without one, they stay hidden from visitors rather than silently not working.
            </p>
            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setConfigureMail(true)}
                className={cn(
                  'text-left border-2 rounded-xl p-4 transition-colors',
                  configureMail ? 'border-black bg-gray-50' : 'border-gray-200 hover:border-gray-300'
                )}
              >
                <Mail size={20} className="mb-2 text-blue-600" />
                <p className="text-sm font-medium">Yes, set it up</p>
                <p className="text-xs text-gray-500 mt-0.5">
                  Enable password-reset emails and newsletter signups now.
                </p>
              </button>
              <button
                type="button"
                onClick={() => setConfigureMail(false)}
                className={cn(
                  'text-left border-2 rounded-xl p-4 transition-colors',
                  !configureMail ? 'border-black bg-gray-50' : 'border-gray-200 hover:border-gray-300'
                )}
              >
                <Clock size={20} className="mb-2 text-gray-400" />
                <p className="text-sm font-medium">Not now</p>
                <p className="text-xs text-gray-500 mt-0.5">
                  Skip for now — turn it on later from Settings whenever you're ready.
                </p>
              </button>
            </div>
            {configureMail && (
              <div className="mt-4 pt-4 border-t border-dashed">
                <MailProviderFields values={mailValues} onChange={setMailValues} />
              </div>
            )}
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
          >
            {loading ? 'Setting up…' : 'Finish setup'}
          </button>
        </form>
      </div>
    </div>
  );
}
