import 'next-auth';
import 'next-auth/jwt';

declare module 'next-auth' {
  interface Session {
    readerToken?: string;
    readerHandle?: string;
    readerDisplayName?: string;
    readerAvatarUrl?: string;
    readerEmail?: string;
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    readerToken?: string;
    readerHandle?: string;
    readerDisplayName?: string;
    readerAvatarUrl?: string;
    readerEmail?: string;
  }
}
