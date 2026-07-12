import GoogleProvider from 'next-auth/providers/google';
import GitHubProvider from 'next-auth/providers/github';
import type { NextAuthOptions } from 'next-auth';

const BACKEND_URL = process.env.BACKEND_URL || process.env.NEXT_PUBLIC_BACKEND_URL || 'http://localhost:8080';

export const authOptions: NextAuthOptions = {
  providers: [
    GoogleProvider({
      clientId: process.env.GOOGLE_CLIENT_ID ?? '',
      clientSecret: process.env.GOOGLE_CLIENT_SECRET ?? '',
    }),
    GitHubProvider({
      clientId: process.env.GITHUB_CLIENT_ID ?? '',
      clientSecret: process.env.GITHUB_CLIENT_SECRET ?? '',
    }),
  ],
  session: { strategy: 'jwt' },
  callbacks: {
    // Runs on every sign-in; `account`/`profile` are only present on the first
    // call right after the OAuth redirect, not on subsequent session reads.
    async jwt({ token, account, profile }) {
      if (account && profile) {
        const res = await fetch(`${BACKEND_URL}/api/readers/oauth-login`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Internal-Auth-Secret': process.env.INTERNAL_AUTH_SECRET ?? '',
          },
          body: JSON.stringify({
            provider: account.provider.toUpperCase(),
            providerUserId: account.providerAccountId,
            email: profile.email,
            displayName: profile.name ?? profile.email,
            avatarUrl: (profile as { picture?: string; avatar_url?: string }).picture
              ?? (profile as { picture?: string; avatar_url?: string }).avatar_url
              ?? null,
          }),
        });
        if (!res.ok) {
          throw new Error('Reader login bridge failed');
        }
        const data = await res.json();
        token.readerToken = data.token;
        token.readerHandle = data.handle;
        token.readerDisplayName = data.displayName;
        token.readerAvatarUrl = data.avatarUrl;
        token.readerEmail = data.email;
      }
      return token;
    },
    async session({ session, token }) {
      session.readerToken = token.readerToken as string;
      session.readerHandle = token.readerHandle as string;
      session.readerDisplayName = token.readerDisplayName as string;
      session.readerAvatarUrl = token.readerAvatarUrl as string | undefined;
      session.readerEmail = token.readerEmail as string;
      return session;
    },
  },
};
