import { api } from './api';

export interface SiteSettings {
  siteName: string;
}

const DEFAULT_SETTINGS: SiteSettings = { siteName: 'Blog CMS' };

// Falls back to the default rather than throwing — every page that shows the
// site name (including the root layout itself) needs this to never hard-fail.
export async function getSiteSettings(): Promise<SiteSettings> {
  try {
    return await api.get<SiteSettings>('/api/settings');
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export async function updateSiteSettings(siteName: string): Promise<SiteSettings> {
  return api.put<SiteSettings>('/api/admin/settings', { siteName });
}
