import NextAuth from 'next-auth';
import { authOptions } from '@/lib/authOptions';

// Deliberately NOT at /api/auth/[...nextauth] -- that path is a catch-all that
// would intercept the existing admin POST /api/auth/login and
// /api/auth/emergency-reset requests (which reach the Spring backend only via
// next.config.js's rewrite proxy today, since no Next.js route exists there).
const handler = NextAuth(authOptions);

export { handler as GET, handler as POST };
