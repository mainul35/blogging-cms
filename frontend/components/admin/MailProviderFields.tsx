'use client';

import type { MailFormValues } from '@/types/mailSettings';

interface Props {
  values: MailFormValues;
  onChange: (values: MailFormValues) => void;
  // Shown as the secret input's placeholder when a value is already saved
  // server-side -- lets the user see "something is configured" without the
  // actual secret ever being sent back down. Omit entirely in contexts (like
  // the setup wizard) where nothing could possibly be configured yet.
  secretPlaceholders?: {
    smtpPassword?: string;
    resendApiKey?: string;
    sendgridApiKey?: string;
  };
}

const inputClasses = 'w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black';

export default function MailProviderFields({ values, onChange, secretPlaceholders }: Props) {
  const set = <K extends keyof MailFormValues>(key: K, value: MailFormValues[K]) =>
    onChange({ ...values, [key]: value });

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-sm font-medium mb-1">Provider</label>
        <select
          value={values.provider}
          onChange={e => set('provider', e.target.value as MailFormValues['provider'])}
          className={inputClasses}
        >
          <option value="log">Log only (no real email sent)</option>
          <option value="smtp">SMTP (Gmail, self-hosted, SES/Mailgun/Postmark relay)</option>
          <option value="resend">Resend</option>
          <option value="sendgrid">SendGrid</option>
        </select>
      </div>

      {values.provider !== 'log' && (
        <>
          <div>
            <label className="block text-sm font-medium mb-1">From address</label>
            <input
              type="email"
              value={values.fromAddress}
              onChange={e => set('fromAddress', e.target.value)}
              required
              className={inputClasses}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">Reply-to (optional)</label>
            <input
              type="email"
              value={values.replyTo}
              onChange={e => set('replyTo', e.target.value)}
              className={inputClasses}
            />
          </div>
        </>
      )}

      {values.provider === 'smtp' && (
        <>
          <div>
            <label className="block text-sm font-medium mb-1">SMTP host</label>
            <input
              value={values.smtpHost}
              onChange={e => set('smtpHost', e.target.value)}
              placeholder="smtp.gmail.com"
              className={inputClasses}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">SMTP port</label>
            <input
              type="number"
              value={values.smtpPort}
              onChange={e => set('smtpPort', Number(e.target.value))}
              className={inputClasses}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">SMTP username</label>
            <input
              value={values.smtpUsername}
              onChange={e => set('smtpUsername', e.target.value)}
              className={inputClasses}
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1">SMTP password</label>
            <input
              type="password"
              value={values.smtpPassword}
              onChange={e => set('smtpPassword', e.target.value)}
              placeholder={secretPlaceholders?.smtpPassword ?? ''}
              className={inputClasses}
            />
            {secretPlaceholders?.smtpPassword && (
              <p className="text-xs text-gray-400 mt-1">Leave blank to keep the currently configured password.</p>
            )}
          </div>
          <div className="flex gap-6">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={values.smtpAuth} onChange={e => set('smtpAuth', e.target.checked)} />
              Auth
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={values.smtpStarttls} onChange={e => set('smtpStarttls', e.target.checked)} />
              STARTTLS
            </label>
          </div>
        </>
      )}

      {values.provider === 'resend' && (
        <div>
          <label className="block text-sm font-medium mb-1">Resend API key</label>
          <input
            type="password"
            value={values.resendApiKey}
            onChange={e => set('resendApiKey', e.target.value)}
            placeholder={secretPlaceholders?.resendApiKey ?? ''}
            className={inputClasses}
          />
          {secretPlaceholders?.resendApiKey && (
            <p className="text-xs text-gray-400 mt-1">Leave blank to keep the currently configured key.</p>
          )}
        </div>
      )}

      {values.provider === 'sendgrid' && (
        <div>
          <label className="block text-sm font-medium mb-1">SendGrid API key</label>
          <input
            type="password"
            value={values.sendgridApiKey}
            onChange={e => set('sendgridApiKey', e.target.value)}
            placeholder={secretPlaceholders?.sendgridApiKey ?? ''}
            className={inputClasses}
          />
          {secretPlaceholders?.sendgridApiKey && (
            <p className="text-xs text-gray-400 mt-1">Leave blank to keep the currently configured key.</p>
          )}
        </div>
      )}
    </div>
  );
}
