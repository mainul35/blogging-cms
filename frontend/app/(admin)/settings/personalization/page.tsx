'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import { getSiteSettings, updateSiteSettings, type SiteSettings } from '@/lib/settings';
import {
  THEME_OPTIONS, CONTRAST_OPTIONS, FONT_OPTIONS, ACCENT_COLOR_OPTIONS,
} from '@/lib/personalization';

const DEFAULTS: SiteSettings = {
  siteName: '', theme: 'system', contrast: 'normal', font: 'inter', accentColor: 'blue',
};

export default function PersonalizationSettingsPage() {
  const router = useRouter();
  const [values, setValues] = useState<SiteSettings>(DEFAULTS);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    getSiteSettings()
      .then(setValues)
      .catch(() => setLoadError('Failed to load site settings.'))
      .finally(() => setReady(true));
  }, []);

  const set = <K extends keyof SiteSettings>(key: K, value: SiteSettings[K]) =>
    setValues(v => ({ ...v, [key]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess(false);
    setLoading(true);
    try {
      const updated = await updateSiteSettings(values);
      setValues(updated);
      setSuccess(true);
      // Theme/contrast/font/accent are all applied by the root layout, a
      // Server Component that only re-runs on navigation -- without this, a
      // successful save wouldn't show up anywhere until a hard reload.
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update site settings.');
    } finally {
      setLoading(false);
    }
  };

  if (!ready) return <p className="text-gray-400 text-sm">Loading…</p>;
  if (loadError) return <p className="text-sm text-red-500">{loadError}</p>;

  return (
    <div className="bg-white rounded-xl shadow-sm p-6">
      <h2 className="text-lg font-semibold mb-4">Site Settings</h2>
      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}
      {success && <p className="mb-4 text-sm text-green-600">Site settings updated successfully.</p>}
      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium mb-1">Site Name</label>
          <input
            value={values.siteName}
            onChange={e => set('siteName', e.target.value)}
            required
            maxLength={100}
            placeholder="Blog CMS"
            className="w-full border rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-black"
          />
          <p className="text-xs text-gray-400 mt-1">Shown in the header, browser tab, and sidebar across the site.</p>
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">Theme</label>
          <div className="grid grid-cols-3 gap-2">
            {THEME_OPTIONS.map(opt => (
              <button
                key={opt.value}
                type="button"
                onClick={() => set('theme', opt.value)}
                className={cn(
                  'py-2 text-sm rounded-lg border-2 transition-colors',
                  values.theme === opt.value ? 'border-black bg-gray-50 font-medium' : 'border-gray-200 hover:border-gray-300'
                )}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">Contrast</label>
          <div className="grid grid-cols-2 gap-2">
            {CONTRAST_OPTIONS.map(opt => (
              <button
                key={opt.value}
                type="button"
                onClick={() => set('contrast', opt.value)}
                className={cn(
                  'py-2 text-sm rounded-lg border-2 transition-colors',
                  values.contrast === opt.value ? 'border-black bg-gray-50 font-medium' : 'border-gray-200 hover:border-gray-300'
                )}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">Font</label>
          <div className="grid grid-cols-3 gap-2">
            {FONT_OPTIONS.map(opt => (
              <button
                key={opt.value}
                type="button"
                onClick={() => set('font', opt.value)}
                style={{ fontFamily: opt.cssVar }}
                className={cn(
                  'py-2 text-sm rounded-lg border-2 transition-colors',
                  values.font === opt.value ? 'border-black bg-gray-50 font-medium' : 'border-gray-200 hover:border-gray-300'
                )}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">Accent color</label>
          <div className="flex gap-3">
            {ACCENT_COLOR_OPTIONS.map(opt => (
              <button
                key={opt.value}
                type="button"
                onClick={() => set('accentColor', opt.value)}
                title={opt.label}
                aria-label={opt.label}
                style={{ backgroundColor: opt.swatch }}
                className="w-8 h-8 rounded-full flex items-center justify-center ring-offset-2 transition-shadow"
              >
                {values.accentColor === opt.value && <Check size={16} className="text-white" strokeWidth={3} />}
              </button>
            ))}
          </div>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full py-2 bg-black text-white rounded-lg hover:bg-gray-800 disabled:opacity-50 transition-colors"
        >
          {loading ? 'Saving…' : 'Save Site Settings'}
        </button>
      </form>
    </div>
  );
}
