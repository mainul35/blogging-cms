import type { Metadata } from 'next';
import { Inter, Merriweather, JetBrains_Mono } from 'next/font/google';
import { cookies } from 'next/headers';
import { getSiteSettings } from '@/lib/settings';
import { accentCssVars, type Font } from '@/lib/personalization';
import './globals.css';
import UserMenu from '@/components/UserMenu';
import ReaderSessionProvider from '@/components/ReaderSessionProvider';

const inter = Inter({ subsets: ['latin'] });
const merriweather = Merriweather({ subsets: ['latin'], weight: ['400', '700'] });
const jetbrainsMono = JetBrains_Mono({ subsets: ['latin'] });

const FONT_FAMILY_MAP: Record<Font, string> = {
  inter: inter.style.fontFamily,
  serif: merriweather.style.fontFamily,
  mono: jetbrainsMono.style.fontFamily,
};

export async function generateMetadata(): Promise<Metadata> {
  const { siteName } = await getSiteSettings();
  return {
    title: { default: siteName, template: `%s | ${siteName}` },
    description: 'A modern full-stack content management system',
  };
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const { siteName, theme, contrast, font, accentColor } = await getSiteSettings();
  const isAuthenticated = !!(await cookies()).get('token')?.value;

  // 'system' resolves client-side (see the inline script below) to avoid
  // needing a cookie just to remember the visitor's OS preference -- 'light'
  // is the safe default in the meantime, so only dark-preferring visitors
  // see a brief flash rather than everyone.
  const resolvedTheme = theme === 'system' ? 'light' : theme;
  const htmlStyle = {
    '--font-body': FONT_FAMILY_MAP[font],
    ...accentCssVars(accentColor),
  } as React.CSSProperties;

  return (
    // suppressHydrationWarning is required here: when theme is "system", the
    // inline script below mutates data-theme on the client before hydration
    // based on the visitor's OS preference, which will legitimately differ
    // from what the server rendered ("light", the safe default) -- without
    // this, React treats that as an error and discards the server-rendered
    // tree instead of just leaving the attribute alone (the standard fix
    // used by next-themes and other theme-switcher libraries).
    <html lang="en" data-theme={resolvedTheme} data-contrast={contrast} style={htmlStyle} suppressHydrationWarning>
      <body className="h-screen flex flex-col overflow-hidden bg-gray-50 text-gray-900">
        {theme === 'system' && (
          // A plain (not next/script) inline script, placed first in <body>,
          // executes synchronously as the parser reaches it -- before the
          // rest of the visible page -- so a dark-preferring visitor doesn't
          // see a light flash first when the site is set to "match device"
          // rather than a fixed theme.
          <script
            suppressHydrationWarning
            dangerouslySetInnerHTML={{
              __html:
                "(function(){try{if(window.matchMedia('(prefers-color-scheme: dark)').matches){document.documentElement.setAttribute('data-theme','dark');}}catch(e){}})();",
            }}
          />
        )}
        {/* Top Navigation Bar — shrink-0 keeps it fixed height, never scrolls out of view */}
        <header className="shrink-0 bg-white border-b border-gray-200 shadow-sm">
          <nav className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
            <a href="/blog" className="text-xl font-bold text-blue-700 hover:text-blue-800 transition-colors">{siteName}</a>
            <div className="flex items-center gap-6">
              <a href="/blog" className="text-sm text-gray-700 hover:text-blue-700 transition-colors font-medium">Home</a>
              <a href="/blog" className="text-sm text-gray-700 hover:text-blue-700 transition-colors font-medium">Blog</a>
              {/* No public login link, matching the reference personal-blog pattern this
                  was modeled on (e.g. WordPress themes never link /wp-admin) -- the
                  owner reaches /login directly by URL. Only rendered once signed in. */}
              {isAuthenticated && <UserMenu />}
            </div>
          </nav>
        </header>
        {/* min-h-0 lets this flex child actually shrink to the remaining space instead
            of growing with its content — that's what makes overflow-y-auto work here
            rather than pushing the body taller than the viewport. */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          <ReaderSessionProvider>{children}</ReaderSessionProvider>
        </div>
      </body>
    </html>
  );
}
