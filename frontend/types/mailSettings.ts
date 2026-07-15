export type MailProvider = 'log' | 'smtp' | 'resend' | 'sendgrid';

export interface MailSettingsResponse {
  provider: MailProvider;
  fromAddress: string;
  replyTo: string | null;
  smtpHost: string | null;
  smtpPort: number;
  smtpUsername: string | null;
  smtpAuth: boolean;
  smtpStarttls: boolean;
  hasSmtpPassword: boolean;
  hasResendApiKey: boolean;
  hasSendgridApiKey: boolean;
}

// Secrets (smtpPassword/resendApiKey/sendgridApiKey) are always blank in form
// state -- a saved secret is never sent back down from the server (see
// MailSettingsResponse), so "blank" always means "unchanged" on submit,
// matching the backend's own keep-existing-if-blank semantics.
export interface MailFormValues {
  provider: MailProvider;
  fromAddress: string;
  replyTo: string;
  smtpHost: string;
  smtpPort: number;
  smtpUsername: string;
  smtpPassword: string;
  smtpAuth: boolean;
  smtpStarttls: boolean;
  resendApiKey: string;
  sendgridApiKey: string;
}

export const DEFAULT_MAIL_FORM_VALUES: MailFormValues = {
  provider: 'log',
  fromAddress: 'noreply@example.com',
  replyTo: '',
  smtpHost: '',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  smtpAuth: true,
  smtpStarttls: true,
  resendApiKey: '',
  sendgridApiKey: '',
};

export function toFormValues(response: MailSettingsResponse): MailFormValues {
  return {
    provider: response.provider,
    fromAddress: response.fromAddress,
    replyTo: response.replyTo ?? '',
    smtpHost: response.smtpHost ?? '',
    smtpPort: response.smtpPort,
    smtpUsername: response.smtpUsername ?? '',
    smtpPassword: '',
    smtpAuth: response.smtpAuth,
    smtpStarttls: response.smtpStarttls,
    resendApiKey: '',
    sendgridApiKey: '',
  };
}
