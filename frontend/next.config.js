/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return {
      beforeFiles: [],
      afterFiles: [],
      // fallback rewrites run after Next's own routes (including dynamic ones
      // like app/api/reader-auth/[...nextauth]/route.ts) have had a chance to
      // match -- an afterFiles rewrite here would win over that dynamic route
      // and silently break reader sign-in, since dynamic/catch-all routes are
      // checked after afterFiles but before fallback.
      fallback: [
        {
          source: '/api/:path*',
          destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/api/:path*`,
        },
      ],
    };
  },
  images: {
    remotePatterns: [{ protocol: 'https', hostname: '**' }],
  },
};

module.exports = nextConfig;
