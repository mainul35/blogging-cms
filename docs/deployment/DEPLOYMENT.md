# Deployment & Release Guide

How blogging-cms-app ships to `proxy-vm` as Docker containers, driven by a
Jenkins pipeline that already runs on that same host.

## Architecture

```
   Internet                                proxy-vm
      │                          ┌──────────────────────────────────────────────┐
      │ (outbound-only            │                                                │
      │  connection from           │  cloudflared ───▶ edge (nginx, 127.0.0.1:80)  │
      │  cloudflared, no           │                      /api/*  ───▶ backend  :8080│
      ▼  inbound ports open)       │                      /*      ───▶ frontend :3000│
  Cloudflare Tunnel ───────────────┘                          │            │        │
                                    │                          ▼            ▼        │
                                    │                   postgres :5432   redis :6379  │
                                    │                                                │
                                    │  jenkins (existing container, builds+deploys)  │
                                    │  harbor  (separate stack, its own port)        │
                                    └──────────────────────────────────────────────┘
```

Public access goes through [Cloudflare Tunnel](cloudflare-tunnel-setup.md) —
`cloudflared` runs on proxy-vm and dials *out* to Cloudflare, so nothing
inbound needs to be opened on the VM's firewall/router at all, and
Cloudflare terminates TLS for you. `edge` only binds to `127.0.0.1`
(see `docker-compose.prod.yml`), reachable exclusively from `cloudflared`
on the same host — not from the VM's public/LAN interface directly.

Everything public goes through the **edge** reverse proxy on one origin.
That's deliberate, not incidental: the frontend calls the backend directly
from the browser (`lib/api.ts`, using `NEXT_PUBLIC_BACKEND_URL`), and the
admin auth token travels as an `Authorization: Bearer` header out of
`localStorage`, not a cookie. Routing both frontend and backend under the
same public origin means those calls are same-origin from the browser's
point of view — no CORS, no cross-site cookie/credentials handling to get
wrong. Server-side code (`getSiteSettings()`, `middleware.ts`,
`authOptions.ts`) instead talks to the backend over the internal Docker
network (`BACKEND_URL=http://backend:8080`), never leaving the host.

Harbor (your private image registry — see
[harbor-registry-setup.md](harbor-registry-setup.md)) and Jenkins are
separate concerns from the app stack itself; they're referenced here only
because the pipeline pushes to Harbor and runs inside Jenkins.

## Prerequisites checklist

- [ ] Docker + Compose plugin on proxy-vm (confirmed already installed)
- [ ] Harbor deployed on proxy-vm — see [harbor-registry-setup.md](harbor-registry-setup.md), do this first if you haven't
- [ ] Jenkins container on proxy-vm is healthy (see [Jenkins troubleshooting](#jenkins-container-troubleshooting) below — it was reported unhealthy)
- [ ] Jenkins container has the **docker CLI installed and the host's docker socket mounted** (`-v /var/run/docker.sock:/var/run/docker.sock`), since every pipeline stage runs `docker`/`docker compose` directly against proxy-vm's own daemon — no SSH hop mid-pipeline
- [ ] Cloudflare Tunnel set up and routing your chosen `PUBLIC_DOMAIN` to `127.0.0.1:80` on proxy-vm — see [cloudflare-tunnel-setup.md](cloudflare-tunnel-setup.md)

## Jenkins container troubleshooting

Since Jenkins is what runs the pipeline, get it healthy first. From your PC:

```bash
ssh proxy-vm
docker ps -a --filter name=jenkins          # confirm the container name/state
docker inspect --format='{{json .State.Health}}' <jenkins-container> | jq .
docker logs --tail 200 <jenkins-container>   # look for the actual startup error
df -h; free -h                                # rule out disk/memory exhaustion on the volume
docker restart <jenkins-container>
```

Common causes worth checking first: the `jenkins_home` volume ran out of
disk space, a plugin failed to load after an update, or the container was
never given the docker socket mount it needs (symptoms: Jenkins itself
comes up fine, but every pipeline job fails at the first `docker` command
with `permission denied` or `command not found`).

## Required secrets & environment variables

Two Jenkins credentials drive everything; nothing else needs to exist on
proxy-vm outside of Jenkins/Harbor/Docker themselves.

### 1. `harbor-robot-blogging-cms` (Username with password)

A Harbor **robot account** scoped to the `blogging-cms` project (push-only
is enough) — created in the Harbor UI, see
[harbor-registry-setup.md](harbor-registry-setup.md). Store its account
name as the username and its token as the password.

### 2. `blogging-cms-prod-env` (Secret file)

A `.env` file with every variable `docker-compose.prod.yml` requires. Build
it once, keep a copy somewhere safe (password manager / vault), upload it as
this Jenkins credential:

```bash
# Generate strong random secrets for anything marked (random) below:
openssl rand -base64 48   # run once per secret needed

cat > blogging-cms.env <<'EOF'
# --- Registry (matches your Harbor project; port 8443, not 443 -- Harbor's
#     own bundled proxy stays off 80/443 so it doesn't clash with edge) ---
REGISTRY=harbor.proxy-vm.local:8443
HARBOR_PROJECT=blogging-cms

# --- Public origin the edge proxy serves this app on ---
# The real domain routed through Cloudflare Tunnel (see
# cloudflare-tunnel-setup.md) -- must match that tunnel's ingress hostname
# exactly. Changing this always requires a frontend rebuild (baked into the
# client JS bundle at build time) -- see NEXT_PUBLIC_BACKEND_URL in frontend/Dockerfile.
PUBLIC_DOMAIN=blog.yourdomain.com

# --- Database ---
DB_NAME=blogcms
DB_USER=bloguser
DB_PASSWORD=<random>

# --- Backend secrets (application.yml property -> env var mapping noted for reference) ---
JWT_SECRET=<random>                 # app.jwt.secret
ADMIN_RESET_SECRET=<random>         # app.admin.reset-secret (emergency admin reset only)
INTERNAL_AUTH_SECRET=<random>       # app.internal.auth-secret -- MUST match frontend's INTERNAL_AUTH_SECRET below

# --- Frontend (NextAuth reader sign-in) ---
NEXTAUTH_SECRET=<random>
INTERNAL_AUTH_SECRET=<same value as above>
# Optional -- leave blank to leave reader OAuth sign-in disabled:
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
EOF
```

Upload `blogging-cms.env` as the `blogging-cms-prod-env` secret file
credential, then delete your local copy (or keep it only in a password
manager — never commit it).

> **Mail gateway** (SMTP/Resend/SendGrid) is *not* an env var here — it's
> configured at runtime from **Settings → Mail** in the admin UI (stored in
> the `mail_settings` DB table), same as any other environment.

## Jenkins job setup (one-time)

1. **Manage Jenkins → Credentials** — add the two credentials above.
2. **New Item → Pipeline**, name it `blogging-cms-deploy`.
3. Pipeline definition: **Pipeline script from SCM** → Git → this repo's URL
   → branch `main` → script path `Jenkinsfile`.
4. Save, then **Build Now** for the first run.

The `Jenkinsfile` at the repo root does everything from there: checks
postgres/redis are up (starting them if not), builds the backend and
frontend images, pushes both to Harbor, deploys via
`docker-compose.prod.yml`, and smoke-tests the result through the edge
proxy. Re-running the job is the release process — there's no separate
"release" step.

## Rollback

Every build tags images with the short git SHA (`IMAGE_TAG`), and Jenkins
keeps the last 20 builds' logs (`buildDiscarder`) so you can find the
previous good SHA. To roll back:

```bash
ssh proxy-vm
cd <jenkins-workspace-for-blogging-cms-deploy>   # or wherever docker-compose.prod.yml lives
export IMAGE_TAG=<previous-good-sha>
docker compose --env-file <path-to-prod.env> -f docker-compose.prod.yml \
  up -d --no-deps backend frontend
```

Postgres/redis are untouched by a rollback — only the app images change.
If a Flyway migration shipped in the bad build already ran, a code-only
rollback won't undo the schema change; that needs a manual Flyway
`undo`/manual fix, same as any other Flyway-based project.

## Day-2 notes

- **Changing `PUBLIC_DOMAIN`**: requires a frontend rebuild (it's baked into
  the client bundle) — just re-run the Jenkins job after updating the
  `blogging-cms-prod-env` secret file.
- **TLS/HTTPS**: handled by Cloudflare — it terminates TLS at its edge and
  the tunnel carries plain HTTP from there to `127.0.0.1:80`, so there's no
  certbot/Let's Encrypt to manage on proxy-vm at all. See
  [cloudflare-tunnel-setup.md](cloudflare-tunnel-setup.md).
- **Logs**: `docker logs blog_backend` / `blog_frontend` / `blog_edge` on proxy-vm.
- **Uploaded images**: persisted in the `backend_uploads` named volume, independent of container/image lifecycle.
