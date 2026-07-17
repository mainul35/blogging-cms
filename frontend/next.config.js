/** @type {import('next').NextConfig} */
const nextConfig = {
  // Produces .next/standalone -- a minimal server bundle with only the
  // node_modules it actually needs, traced from the app's imports. Docker
  // (frontend/Dockerfile) copies just that + .next/static + public/ into the
  // final image instead of the full node_modules tree.
  output: 'standalone',
  async headers() {
    return [
      {
        // The reset token lives in this page's URL query string. A referrer
        // header sent from here (e.g. to any external resource the page ever
        // loads) would leak it to that third party; browser history and
        // server/proxy access logs already capture the URL regardless, which
        // is a separate risk the token's short TTL + single-use + hashed-at-
        // rest storage are meant to bound.
        source: '/reset-password',
        headers: [{ key: 'Referrer-Policy', value: 'no-referrer' }],
      },
    ];
  },
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
