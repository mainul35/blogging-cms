'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { User, Mail, Palette } from 'lucide-react';
import { cn } from '@/lib/utils';

const sections = [
  { href: '/settings/profile', label: 'Profile', icon: User },
  { href: '/settings/mail', label: 'Mail', icon: Mail },
  { href: '/settings/personalization', label: 'Personalization', icon: Palette },
];

export default function SettingsLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  return (
    <div className="max-w-4xl">
      <h1 className="text-2xl font-bold mb-6">Settings</h1>
      <div className="flex gap-8 items-start">
        <nav className="w-48 shrink-0 space-y-1">
          {sections.map(section => (
            <Link
              key={section.href}
              href={section.href}
              className={cn(
                'flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors',
                pathname === section.href
                  ? 'bg-blue-600 text-white font-medium'
                  : 'text-gray-600 hover:bg-gray-100'
              )}
            >
              <section.icon size={16} className="shrink-0" />
              {section.label}
            </Link>
          ))}
        </nav>
        <div className="flex-1 max-w-md space-y-6">{children}</div>
      </div>
    </div>
  );
}
