import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import { cookies } from 'next/headers';
import { getSiteSettings } from '@/lib/settings';
import './globals.css';
import UserMenu from '@/components/UserMenu';
import ReaderSessionProvider from '@/components/ReaderSessionProvider';

const inter = Inter({ subsets: ['latin'] });

export async function generateMetadata(): Promise<Metadata> {
  const { siteName } = await getSiteSettings();
  return {
    title: { default: siteName, template: `%s | ${siteName}` },
    description: 'A modern full-stack content management system',
  };
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const { siteName } = await getSiteSettings();
  const isAuthenticated = !!(await cookies()).get('token')?.value;

  return (
    <html lang="en">
      <body className={`${inter.className} h-screen flex flex-col overflow-hidden bg-gray-50 text-gray-900`}>
        {/* Top Navigation Bar — shrink-0 keeps it fixed height, never scrolls out of view */}
        <header className="shrink-0 bg-white border-b border-gray-200 shadow-sm">
          <nav className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
            <a href="/blog" className="text-xl font-bold text-blue-700 hover:text-blue-800 transition-colors">{siteName}</a>
            <div className="flex items-center gap-6">
              <a href="/blog" className="text-sm text-gray-700 hover:text-blue-700 transition-colors font-medium">Home</a>
              <a href="/blog" className="text-sm text-gray-700 hover:text-blue-700 transition-colors font-medium">Blog</a>
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
