'use client';

import { useEffect, useState } from 'react';
import MailProviderFields from '@/components/admin/MailProviderFields';
import { getMailSettings, updateMailSettings } from '@/lib/mailSettings';
import { DEFAULT_MAIL_FORM_VALUES, toFormValues, type MailSettingsResponse } from '@/types/mailSettings';

export default function MailSettingsPage() {
  const [values, setValues] = useState(DEFAULT_MAIL_FORM_VALUES);
  const [current, setCurrent] = useState<MailSettingsResponse | null>(null);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    getMailSettings()
      .then(settings => {
        setCurrent(settings);
        setValues(toFormValues(settings));
      })
      .catch(() => setLoadError('Failed to load mail settings.'))
      .finally(() => setReady(true));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);
    setLoading(true);
    try {
      const updated = await updateMailSettings(values);
      setCurrent(updated);
      setValues(toFormValues(updated));
      setSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update mail settings.');
    } finally {
      setLoading(false);
    }
  };

  if (!ready) return <p className="text-gray-400 text-sm">Loading…</p>;
  if (loadError) return <p className="text-sm text-red-500">{loadError}</p>;

  return (
    <div className="bg-white rounded-xl shadow-sm p-6">
      <h2 className="text-lg font-semibold mb-1">Mail Delivery</h2>
      <p className="text-xs text-gray-400 mb-4">
        Controls where password resets, comment mentions, and newsletter emails are actually sent.
        Leave as &quot;Log only&quot; if you don&apos;t need real email delivery yet.
      </p>
      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}
      {success && <p className="mb-4 text-sm text-green-600">Mail settings updated successfully.</p>}
      <form onSubmit={handleSubmit} className="space-y-4">
        <MailProviderFields
          values={values}
          onChange={setValues}
          secretPlaceholders={{
            smtpPassword: current?.hasSmtpPassword ? '••••••••' : undefined,
            resendApiKey: current?.hasResendApiKey ? '••••••••' : undefined,
            sendgridApiKey: current?.hasSendgridApiKey ? '••••••••' : undefined,
          }}
        />
        <button
          type="submit"
          disabled={loading}
          className="w-full py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
        >
          {loading ? 'Saving…' : 'Save Mail Settings'}
        </button>
      </form>
    </div>
  );
}
