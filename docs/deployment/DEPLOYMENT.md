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

A Harbor **robot account** scoped to the `blogging-cms` project, with
**both push and pull** — the build jobs push, and the deploy jobs' tag
picker calls Harbor's API to list existing tags (needs at least read
access) — created in the Harbor UI, see
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

Four jobs, grouped under one **Folder** named `blog.mainul35.dev` — build
and deploy are separate concerns per service, so a deploy never triggers a
rebuild and a build never touches the running app:

| Job | Jenkinsfile | What it does |
|---|---|---|
| `build-backend` | `jenkins/build-backend.Jenkinsfile` | Checkout → build backend image → push to Harbor (tagged with the short git SHA, plus `latest`) |
| `build-frontend` | `jenkins/build-frontend.Jenkinsfile` | Same, for the frontend image |
| `deploy-backend` | `jenkins/deploy-backend.Jenkinsfile` | Checkout → **pause and let you pick a tag** from Harbor's actual tag list for `blogging-cms/backend` → dependency health check → pull + deploy *only* the backend container with that tag → smoke test |
| `deploy-frontend` | `jenkins/deploy-frontend.Jenkinsfile` | Same, for the frontend container |

The deploy jobs' tag picker is a plain Jenkins `input` step (no extra
plugin) — it queries Harbor's API for the repository's current tags via
`curl`/`grep` and pauses the pipeline with a dropdown of whatever it found,
newest first. This is also how you pick which image is currently live —
there's no separate "which version is deployed" tracking beyond whichever
tag you last selected.

Setup, once:

1. **Manage Jenkins → Credentials** — add the two credentials above (needed by both build and deploy jobs).
2. **New Item → Folder**, name it `blog.mainul35.dev`.
3. Inside that folder, **New Item → Pipeline** four times, one per job name above.
4. For each: Pipeline definition → **Pipeline script from SCM** → Git → this repo's URL → branch `main` → script path set to that job's Jenkinsfile from the table above.
5. Save.

First release: run `build-backend` and `build-frontend` (produces the
first images in Harbor), then run `deploy-backend` and `deploy-frontend`
(each pauses on its tag picker — pick the tag the build job just pushed).

## Rollback

Every build tags images with the short git SHA, and Harbor keeps every
tag ever pushed (until a retention policy prunes it — see
[harbor-registry-setup.md](harbor-registry-setup.md#maintenance)). To roll
back, there's no separate procedure — just re-run `deploy-backend` and/or
`deploy-frontend`, and pick the previous good SHA from the tag picker
instead of the latest one.

Postgres/redis are untouched by any deploy — only the one app container
being deployed changes. If a Flyway migration shipped in the bad build
already ran, a code-only rollback won't undo the schema change; that needs
a manual Flyway `undo`/manual fix, same as any other Flyway-based project.

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
