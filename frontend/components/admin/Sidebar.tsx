'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard, FileText, MessageSquare, Mail, Settings as SettingsIcon,
  Globe, PanelLeftClose, PanelLeftOpen,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useMailConfigured } from '@/lib/useMailConfigured';
import packageJson from '../../package.json';

const baseNavItems = [
  { href: '/dashboard',  label: 'Dashboard',  icon: LayoutDashboard },
  { href: '/posts',      label: 'Posts',      icon: FileText },
  { href: '/comments',   label: 'Comments',   icon: MessageSquare },
  { href: '/newsletter', label: 'Newsletter', icon: Mail },
  { href: '/settings',   label: 'Settings',   icon: SettingsIcon },
];

const COLLAPSED_KEY = 'admin_sidebar_collapsed';

export default function Sidebar({ siteName }: { siteName: string }) {
  const pathname = usePathname();
  const mailConfigured = useMailConfigured();
  const [collapsed, setCollapsed] = useState(false);

  // Hidden rather than disabled -- with no mail gateway, there's nothing this
  // page can do (no subscribers will ever confirm), so it's not just "greyed
  // out," it's not a relevant feature of the site yet.
  const navItems = baseNavItems.filter(item => item.href !== '/newsletter' || mailConfigured === true);

  useEffect(() => {
    setCollapsed(localStorage.getItem(COLLAPSED_KEY) === 'true');
  }, []);

  const toggleCollapsed = () => {
    setCollapsed(prev => {
      const next = !prev;
      localStorage.setItem(COLLAPSED_KEY, String(next));
      return next;
    });
  };

  const linkClasses = (active: boolean) => cn(
    'flex items-center gap-3 py-2 rounded-lg text-sm transition-colors',
    collapsed ? 'justify-center px-0' : 'px-3',
    active ? 'bg-blue-600 text-white font-medium' : 'text-gray-600 hover:bg-gray-100'
  );

  return (
    <aside className={cn(
      'bg-white shadow-sm flex flex-col shrink-0 transition-[width] duration-200',
      collapsed ? 'w-16' : 'w-56'
    )}>
      <div className="p-4 border-b flex items-center justify-between">
        {!collapsed && (
          <Link href="/" className="text-lg font-bold tracking-tight truncate">
            {siteName}
          </Link>
        )}
        <button
          onClick={toggleCollapsed}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className="p-1.5 rounded text-gray-500 hover:bg-gray-100 hover:text-black transition-colors shrink-0"
        >
          {collapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
        </button>
      </div>

      <nav className="flex-1 p-2 space-y-1">
        {navItems.map(item => (
          <Link
            key={item.href}
            href={item.href}
            title={collapsed ? item.label : undefined}
            className={linkClasses(pathname.startsWith(item.href))}
          >
            <item.icon size={18} className="shrink-0" />
            {!collapsed && <span className="truncate">{item.label}</span>}
          </Link>
        ))}

        <Link
          href="/blog"
          title={collapsed ? 'View Blog' : undefined}
          className={cn(
            'flex items-center gap-3 py-2 rounded-lg text-sm text-blue-600 hover:bg-blue-50 transition-colors font-medium',
            collapsed ? 'justify-center px-0' : 'px-3'
          )}
        >
          <Globe size={18} className="shrink-0" />
          {!collapsed && <span className="truncate">View Blog</span>}
        </Link>
      </nav>

      <div className="p-2 border-t">
        <p
          title={collapsed ? `v${packageJson.version}` : undefined}
          className={cn(
            'text-xs text-gray-400 py-2',
            collapsed ? 'text-center' : 'px-3'
          )}
        >
          {collapsed ? `v${packageJson.version}` : `Version ${packageJson.version}`}
        </p>
      </div>
    </aside>
  );
}
