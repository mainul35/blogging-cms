import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

// Only called for the small set of admin-adjacent routes in `matcher` below --
// never on public blog pages -- so the extra round-trip per request is fine
// at this scale.
async function isSetupCompleted(): Promise<boolean> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/setup/status`, { cache: 'no-store' });
    if (!res.ok) return true; // fail open -- don't lock the admin out over a backend hiccup
    const data = await res.json();
    return !!data.completed;
  } catch {
    return true;
  }
}

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get('token')?.value;
  const isLoginPage = pathname === '/login';
  const isSetupPage = pathname === '/setup';

  const completed = await isSetupCompleted();

  // Not completed yet: force every admin-adjacent route through the wizard first.
  if (!completed && !isSetupPage) {
    return NextResponse.redirect(new URL('/setup', request.url));
  }
  // Already completed: the wizard is one-time, so re-hitting /setup just bounces to /login.
  if (completed && isSetupPage) {
    return NextResponse.redirect(new URL('/login', request.url));
  }
  if (isSetupPage) {
    return NextResponse.next();
  }

  if (token && isLoginPage) {
    return NextResponse.redirect(new URL('/dashboard', request.url));
  }

  if (!token && !isLoginPage) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/dashboard/:path*', '/posts/:path*', '/comments/:path*', '/newsletter/:path*', '/settings/:path*', '/login', '/setup'],
};
