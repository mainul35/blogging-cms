import Sidebar from '@/components/admin/Sidebar';
import { getSiteSettings } from '@/lib/settings';

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const { siteName } = await getSiteSettings();

  return (
    // h-full fills exactly the space the root layout hands it (viewport minus
    // header, via that layout's own flex-1/min-h-0) — never more, so this div
    // itself never overflows its parent. Only `main` scrolls internally, and
    // the sidebar stays visible the whole time instead of scrolling away.
    <div className="flex h-full bg-gray-100">
      <Sidebar siteName={siteName} />
      {/* min-w-0 lets this shrink to fit any width instead of forcing horizontal
          scroll — content that needs room (e.g. the editor toolbar) wraps instead.
          min-h-0 is the vertical equivalent, needed for overflow-y-auto to work. */}
      <main className="flex-1 min-w-0 min-h-0 overflow-y-auto p-8">{children}</main>
    </div>
  );
}
