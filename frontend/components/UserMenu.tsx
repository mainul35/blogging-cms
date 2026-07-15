'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState, useRef, useEffect } from 'react';
import { authLib } from '@/lib/auth';

export default function UserMenu() {
  const [isOpen, setIsOpen] = useState(false);
  const [name, setName] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const menuRef = useRef<HTMLDivElement>(null);
  const pathname = usePathname();

  useEffect(() => {
    const loadProfile = () => {
      authLib.getProfile()
        .then(profile => {
          setName(profile.name || profile.email);
          setAvatarUrl(profile.avatarUrl ?? '');
        })
        .catch(() => {});
    };
    loadProfile();
    // The Settings page's ProfileForm is a sibling client component with its
    // own independent state -- it has no direct reference to this one, so a
    // successful save there dispatches this event to let us know our
    // already-fetched name/avatar are stale without a full page reload.
    window.addEventListener('profile-updated', loadProfile);
    return () => window.removeEventListener('profile-updated', loadProfile);
  }, []);

  // Close menu when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleLogout = () => {
    authLib.logout();
    // Full navigation, not router.push() -- the header's isAuthenticated
    // check runs server-side off the auth cookie, and Next's Router Cache
    // keeps serving the shared layout's old (signed-in) render across a soft
    // client-side navigation even after router.refresh(). See login page.
    window.location.href = '/login';
  };

  const menuItems = [
    { label: 'Dashboard', href: '/dashboard' },
    { label: 'Settings', href: '/settings' },
  ];

  return (
    <div className="relative" ref={menuRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-3 rounded-full hover:bg-gray-100 px-3 py-2 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
        aria-label="User menu"
      >
        <span className="text-sm font-medium text-gray-700">{name || 'User'}</span>
        {avatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={avatarUrl} alt="Your avatar" className="w-9 h-9 rounded-full object-cover bg-gray-200" />
        ) : (
          <div className="w-9 h-9 rounded-full bg-gray-200 flex items-center justify-center text-xs font-medium text-gray-500">
            {(name || 'U').charAt(0).toUpperCase()}
          </div>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-50 animate-in fade-in slide-in-from-top-2">
          {menuItems.map((item) => (
            <Link
              key={item.label}
              href={item.href}
              onClick={() => setIsOpen(false)}
              className={`block px-4 py-2 text-sm ${
                pathname === item.href
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              {item.label}
            </Link>
          ))}
          <hr className="my-1" />
          <button
            onClick={() => {
              setIsOpen(false);
              handleLogout();
            }}
            className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-gray-100"
          >
            Logout
          </button>
        </div>
      )}
    </div>
  );
}