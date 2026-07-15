import { api } from './api';
import type { Theme, Contrast, Font, AccentColor } from './personalization';

export interface SiteSettings {
  siteName: string;
  theme: Theme;
  contrast: Contrast;
  font: Font;
  accentColor: AccentColor;
}

const DEFAULT_SETTINGS: SiteSettings = {
  siteName: 'Blog CMS',
  theme: 'system',
  contrast: 'normal',
  font: 'inter',
  accentColor: 'blue',
};

// Falls back to the default rather than throwing — every page that shows the
// site name (including the root layout itself) needs this to never hard-fail.
export async function getSiteSettings(): Promise<SiteSettings> {
  try {
    return await api.get<SiteSettings>('/api/settings');
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export async function updateSiteSettings(settings: SiteSettings): Promise<SiteSettings> {
  return api.put<SiteSettings>('/api/admin/settings', settings);
}
