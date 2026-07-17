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
# the same public origin the edge reverse proxy serves the frontend on --
# see deploy/nginx/edge.conf, which forwards that origin's /api/* to backend
# and everything else to this container. Keeping both the same origin is what
# makes the browser's calls same-origin: no CORS, and the Bearer token in
# localStorage just works without any cross-site cookie/credentials handling.
ARG BACKEND_URL=http://backend:8080
ARG NEXT_PUBLIC_BACKEND_URL=http://localhost
ENV BACKEND_URL=$BACKEND_URL
ENV NEXT_PUBLIC_BACKEND_URL=$NEXT_PUBLIC_BACKEND_URL
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
