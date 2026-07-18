# blogging-cms-app

A full-stack Blog/CMS — Next.js 14 frontend + Spring Boot 3 WebFlux backend.

## Quick start (local, via Docker)

Requires Docker, Java 21, and Node 18+.

```bash
# 1. Start infra (PostgreSQL + Redis; Mailpit is optional, see below)
docker-compose up -d

# 2. Start the backend (applies DB migrations automatically on boot)
cd backend
./gradlew bootRun          # Windows: .\gradlew.bat bootRun

# 3. Start the frontend, in a separate terminal
cd frontend
npm install
npm run dev
```

Then open `http://localhost:3000`. First run redirects to `/setup` to create your own admin account — the app has no default login exposed anywhere in the UI.

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`
- Email sandbox (optional): `docker-compose up -d mailpit`, then set provider **smtp**, host `localhost`, port `1025`, auth/STARTTLS off in Settings → Mail — every email shows up at `http://localhost:8025` instead of a real inbox.

## Configuration

Copy `frontend/.env.example` to `frontend/.env.local` and adjust if needed — all values have working defaults for local dev. Everything else (mail provider, site theme, personalization) is configured at runtime from the admin UI, not env vars or config files.

## Production deployment

This repo doesn't include production deployment tooling — no CI/CD pipeline, no production compose file, no registry setup. That's operator-specific infrastructure, kept in a separate `blogging-cms-ops` repo and documented separately by whoever runs the deployment.
