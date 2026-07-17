# --- Dependencies stage ---------------------------------------------------
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# --- Build stage -----------------------------------------------------------
FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Both of these get resolved at `next build` time, not at request time --
# next.config.js's rewrites() bakes BACKEND_URL into routes-manifest.json, and
# NEXT_PUBLIC_* vars are inlined straight into the client JS bundle. Neither
# can be changed by a runtime `docker run -e` later; a new value means a
# rebuild. BACKEND_URL (server-to-server, e.g. lib/api.ts's server branch,
# middleware.ts, authOptions.ts) should point at the backend's docker-compose
# service name. NEXT_PUBLIC_BACKEND_URL (browser-to-server) should point at
# the same public origin this container is reached on directly -- there's no
# separate reverse-proxy container in front of it. Keeping both the same
# origin is what makes the browser's calls same-origin: no CORS, and the
# Bearer token in localStorage just works without cross-site handling.
ARG BACKEND_URL=http://backend:8080
ARG NEXT_PUBLIC_BACKEND_URL=http://localhost
ENV BACKEND_URL=$BACKEND_URL
ENV NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL
# `next/font/google` (used in app/layout.tsx) fetches font CSS from Google at
# build time via Next's bundled node-fetch, which -- unlike Node's native
# fetch -- doesn't fall back cleanly when a host resolves to an AAAA record
# but the build environment has no real IPv6 route (exactly the case on
# proxy-vm's Docker network): the connection attempt fails near-instantly
# instead of retrying over IPv4, and the whole build fails. Forcing IPv4-first
# resolution for this one build step sidesteps it without needing real IPv6
# connectivity or self-hosting the fonts.
ENV NODE_OPTIONS=--dns-result-order=ipv4first
RUN npm run build

# --- Runtime stage -----------------------------------------------------------
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

# output: 'standalone' traces only the node_modules the app actually needs
# into .next/standalone -- static assets and public/ aren't included in that
# trace and must be copied in separately.
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nextjs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
