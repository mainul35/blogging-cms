# blogging-cms-app

A full-stack Blog/CMS — Next.js 14 frontend + Spring Boot 3 WebFlux backend.

## Quick start (local, via Docker)

Requires Docker, Java 21, and Node 18+.

```bash
# 1. Start the backend (applies DB migrations automatically on boot).
#    spring-boot-docker-compose (developmentOnly, see backend/build.gradle)
#    runs `docker compose up` against the root docker-compose.yml for you on
#    startup, and stops those containers again on shutdown -- no separate
#    `docker-compose up -d` step needed. If you'd rather manage the
#    containers yourself (e.g. keep them running across restarts), run
#    `docker-compose up -d` manually first; Spring Boot detects they're
#    already up and leaves them alone.
cd backend
./gradlew bootRun          # Windows: .\gradlew.bat bootRun

# 2. Start the frontend, in a separate terminal
cd frontend
npm install
npm run dev
```

Then open `http://localhost:3000`. First run redirects to `/setup` to create your own admin account — the app has no default login exposed anywhere in the UI.

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`
- Email sandbox (optional, already running alongside postgres/redis from step 1): set provider **smtp**, host `localhost`, port `1025`, auth/STARTTLS off in Settings → Mail — every email shows up at `http://localhost:8025` instead of a real inbox. Not used unless you configure it (`mail_settings` seeds provider "log").

## Configuration

Copy `frontend/.env.example` to `frontend/.env.local` and adjust if needed — all values have working defaults for local dev. Everything else (mail provider, site theme, personalization) is configured at runtime from the admin UI, not env vars or config files.

## Production deployment

This repo doesn't include production deployment tooling — no CI/CD pipeline, no production compose file, no registry setup. That's operator-specific infrastructure, kept in a separate `blogging-cms-ops` repo and documented separately by whoever runs the deployment.
