'use client';

import { SessionProvider } from 'next-auth/react';

// basePath must match the non-default route location in
// app/api/reader-auth/[...nextauth]/route.ts -- this is what redirects every
// next-auth/react client call (signIn, signOut, useSession's own polling
// fetch) away from the library's default /api/auth/* path.
export default function ReaderSessionProvider({ children }: { children: React.ReactNode }) {
  return <SessionProvider basePath="/api/reader-auth">{children}</SessionProvider>;
}
