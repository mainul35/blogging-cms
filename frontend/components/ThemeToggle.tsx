'use client';

import { useEffect, useState } from 'react';
import { Sun, Moon } from 'lucide-react';

// Lets any visitor override the site owner's configured theme for their own
// browser, independent of Settings > Personalization -- stored client-side
// only (no reader account needed), read back by the inline script in
// app/layout.tsx on every subsequent page load.
export const READER_THEME_STORAGE_KEY = 'reader-theme';

export default function ThemeToggle() {
  // null until mounted -- avoids rendering the wrong icon for a frame before
  // we can read the theme the page actually resolved to (site default,
  // system preference, or an earlier override), which isn't knowable from
  // the server side of this client component.
  const [theme, setTheme] = useState<'light' | 'dark' | null>(null);

  useEffect(() => {
    setTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');
  }, []);

  if (theme === null) return null;

  const toggle = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    localStorage.setItem(READER_THEME_STORAGE_KEY, next);
    document.documentElement.setAttribute('data-theme', next);
  };

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      className="p-2 rounded-lg text-gray-500 hover:bg-gray-100 hover:text-gray-700 transition-colors"
    >
      {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
    </button>
  );
}
