import { api } from './api';
import type { MailSettingsResponse, MailFormValues } from '@/types/mailSettings';

// Public — safe to call from any page, no admin session needed. Falls back to
// "not configured" on any failure so a backend hiccup hides outward-facing
// mail features rather than exposing ones that can't actually work.
export async function getMailStatus(): Promise<{ configured: boolean }> {
  try {
    return await api.get<{ configured: boolean }>('/api/mail-settings/status');
  } catch {
    return { configured: false };
  }
}

export async function getMailSettings(): Promise<MailSettingsResponse> {
  return api.get<MailSettingsResponse>('/api/admin/mail-settings');
}

export async function updateMailSettings(values: MailFormValues): Promise<MailSettingsResponse> {
  return api.put<MailSettingsResponse>('/api/admin/mail-settings', values);
}
