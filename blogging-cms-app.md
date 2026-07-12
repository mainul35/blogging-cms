---
name: blogging-cms-app
description: "Full-stack Blog/CMS project — Next.js frontend + Spring Boot WebFlux backend. Location, stack, what's built, and architecture decisions."
metadata: 
  node_type: memory
  type: project
  originSessionId: 911f61ce-937f-4042-adf1-c5a377cf2b27
---

## Location

`C:\Users\mainu\Documents\codes\blogging-cms-app`

See [plan.md](plan.md) in the project root for the original implementation plan, what deviated from it, and a
fuller changelog of everything below — this memory is the quick-reference version.

## Architecture

Two independent services + Docker infra:

```
blogging-cms-app/
├── backend/      Spring Boot 3 WebFlux (port 8080)
├── frontend/     Next.js 14 App Router  (port 3000)
├── plan.md       Original plan + deviations + full changelog
└── docker-compose.yml   PostgreSQL 16 + Redis 7 + Mailpit (local email sandbox, opt-in)
```

Git repo, pushed to `https://github.com/mainul35/blogging-cms.git` on branch `development/blogging-cms-v2` (the repo's `main` and other branches hold an unrelated older project — deliberately left untouched, see repo history for why this branch exists).

## Technology Stack

| Layer | Technology |
|---|---|
| Frontend framework | Next.js 14+ App Router, TypeScript, Tailwind CSS (plain utility classes — **no shadcn/ui**, despite the original plan) |
| Backend framework | Spring Boot 3 + WebFlux (fully reactive, non-blocking) |
| Database | PostgreSQL 16 via R2DBC (reactive driver) |
| Schema migrations | Flyway (uses JDBC separately from R2DBC runtime) |
| Cache | Redis 7 via Spring Data Reactive Redis |
| Auth | Spring Security + JWT (stateless); frontend stores token in `localStorage` + a plain (non-httpOnly) cookie |
| Image storage | Local disk (`backend/uploads/`), no cloud/S3 |
| Build | Gradle (backend), npm (frontend) |

**Why:** SQLite/Prisma was replaced with PostgreSQL + R2DBC for production-grade reactive throughput. Redis caches hot post reads with TTL-based eviction on edit/publish.

## Backend Package Structure

`com.blog.cms` — root package at `backend/src/main/java/com/blog/cms/`

| Sub-package | Contents |
|---|---|
| `config/` | SecurityConfig, R2dbcConfig, RedisConfig, WebConfig (serves `/uploads/**`), GlobalExceptionHandler (clean 413 for oversized uploads) |
| `controller/` | AuthController, AdminController, PostController, CategoryController, TagController, CommentController, NewsletterController, UploadController, ReaderController |
| `service/` | AuthService, PostService, CategoryService, CacheService, CommentService, NewsletterService, UploadService, ReaderService |
| `repository/` | UserRepository, PostRepository, CategoryRepository, TagRepository, CommentRepository, CommentMentionRepository, NewsletterRepository, ReaderRepository, ReaderOAuthIdentityRepository |
| `model/` | User (has `username` reused as display name + `avatarUrl`), Post, Category, Tag, Comment (+`readerId`), CommentMention (+`mentionedReaderId`), NewsletterSubscriber, Reader, ReaderOAuthIdentity |
| `dto/` | AuthRequest, AuthResponse (now incl. `name`/`avatarUrl`), ChangePasswordRequest, ProfileResponse, UpdateProfileRequest, PostRequest, PostResponse, CommentRequest, CommentResponse (+`authorType`/`authorHandle`/`authorAvatarUrl`), NewsletterSubscribeRequest, OAuthLoginRequest, ReaderAuthResponse |
| `security/` | JwtUtil, JwtAuthFilter (WebFilter) — also validates reader JWTs (`role: READER`), same secret, no changes needed |
| `mail/` | `MailSender` interface + `MailMessage` DTO; `LogMailSender` (default), `SmtpMailSender`, `ResendMailSender`, `SendGridMailSender` — selected via `app.mail.provider`, see Newsletter/Mail section below |

## Database Migrations (Flyway)

All under `backend/src/main/resources/db/migration/`:

| File | Creates |
|---|---|
| V1 | `users` table + default admin seed |
| V2 | `posts` table + indexes |
| V3 | `categories`, `tags`, `post_tags` + seed data (backend CRUD exists; **no frontend UI ever built for these**) |
| V4 | `comments`, `comment_mentions` + indexes |
| V5 | `newsletter_subscribers` + indexes |
| V6 | `avatar_url` column on `users` (for profile self-service) |

Default seeded admin: `admin@blog.com` / `Admin@1234` (BCrypt hashed) — also the target of the emergency-reset endpoint (see below), configured via `app.admin.default-email` / `app.admin.default-password`.

## Security Rules (SecurityConfig)

```
/api/auth/**                                          → permitAll (login + emergency-reset only; register removed)
GET /api/posts/**, /api/categories/**, /api/tags/**    → permitAll
GET /uploads/**                                        → permitAll (public post images)
POST /api/posts/*/comments                             → permitAll  (guest commenting)
/api/newsletter/subscribe, /newsletter/confirm         → permitAll
POST /api/readers/oauth-login                          → permitAll (real gate is the internal-secret header check
                                                          inside ReaderService, not Spring Security)
/api/admin/**                                          → hasRole("ADMIN")  (incl. account/profile, account/password, uploads)
anyExchange                                            → authenticated
```

## Key API Endpoints

```
POST /api/auth/login                    → AuthResponse { token, email, role, name, avatarUrl }
POST /api/auth/emergency-reset          → 204; header X-Admin-Reset-Secret must match ADMIN_RESET_SECRET;
                                           NOT linked from frontend; resets admin email+password to defaults
                                           (keyed by role, so it finds the admin even if email was changed)

GET  /api/admin/account/profile         → ProfileResponse (auth required)
PUT  /api/admin/account/profile         → AuthResponse (fresh token — subject/email may have changed)
PUT  /api/admin/account/password        → 204 (auth required; body { currentPassword, newPassword })

GET  /api/posts?status=PUBLISHED        → Flux<PostResponse>
GET  /api/posts/{slug}                  → Mono<PostResponse>  (Redis cached, 10min TTL)
GET  /api/posts/id/{id}                 → Mono<PostResponse>  (added later — admin edit page needs by-id, not by-slug)
POST /api/posts                         → Mono<PostResponse>  (auth required)
PUT  /api/posts/{id}                    → Mono<PostResponse>  (evicts Redis cache; diffs+cleans up orphaned uploaded images)
DELETE /api/posts/{id}                  → 204 (also cleans up orphaned uploaded images)

POST /api/admin/uploads                 → 201 { url } (auth required; multipart; type/size limited by app.upload.*)

GET  /api/posts/{slug}/comments         → Flux<CommentResponse> (threaded tree)
POST /api/posts/{slug}/comments         → Mono<CommentResponse> (guest or auth)
DELETE /api/comments/{id}               → 204 (owner or ADMIN)

POST /api/newsletter/subscribe          → String (sends a real confirmation email via MailSender — see below)
GET  /api/newsletter/confirm?token=     → String
GET  /api/admin/comments                → Flux<CommentResponse> (flat, newest first, with postTitle/postSlug)
GET  /api/admin/stats                   → { totalPosts, publishedPosts, draftPosts }
GET  /api/admin/newsletter/subscribers  → Flux<NewsletterSubscriber>
POST /api/admin/newsletter/send?postId= → String (sends a real digest email to every confirmed subscriber)

POST /api/readers/oauth-login           → ReaderAuthResponse (internal only — server-to-server from the
                                           frontend's NextAuth callback, gated by X-Internal-Auth-Secret,
                                           not by Spring Security; see Reader OAuth section below)
```

## Frontend Structure

`frontend/` — Next.js 14 App Router

```
app/
  page.tsx                    Homepage
  (auth)/login/               Login page (no register page — removed)
  (admin)/layout.tsx          Admin shell (collapsible Sidebar + fluid content panel)
  (admin)/dashboard/          Stats cards
  (admin)/posts/              Post list + create/edit/delete
  (admin)/comments/           Comment moderation table
  (admin)/newsletter/         Newsletter management (subscribers + send digest)
  (admin)/settings/           Profile (email/name/avatar) + change-password forms
  blog/                       Public post listing + NewsletterForm
  post/[slug]/                Individual post + CommentSection + NewsletterForm

  NOTE: (admin) and (auth) are Next.js *route groups* — parentheses mean no path
  segment, so real URLs are /dashboard, /posts, /login etc., NOT /admin/dashboard.
  middleware.ts's matcher and all internal links must use the bare paths.

components/blog/
  PostCard, PostList, MarkdownRenderer (shares lib/markdown.ts plugins with the editor)
  CommentForm       — guest (name+email) or auth, @mention hint
  CommentSection    — threaded tree with inline reply forms, @mention highlighting
  NewsletterForm    — double opt-in subscribe widget (idle/loading/success/error)

components/admin/
  Sidebar           — collapsible (224px↔64px), icons on every item, localStorage-persisted
  Editor            — markdown textarea + live preview, formatting toolbar, image upload/paste
  ResizableImage    — 8-handle drag-resize for images in the editor preview
  CoverImageUpload  — upload+preview widget (object-cover), replaces old URL text field
  PostForm
  CommentModerationTable    — flat table, search, delete, reply badge, @mention pills
  NewsletterSubscriberTable — confirmed subscriber list with email search filter
  SendDigestForm            — post selector dropdown + post preview card + send button

lib/
  api.ts       — typed fetch wrapper; cache: 'no-store' on every request (see bug note below)
  auth.ts      — login/logout/changePassword/updateProfile/getProfile; writes token to localStorage + cookie
  upload.ts    — uploadImage(file) → absolute backend URL
  markdown.ts  — shared remark/rehype plugin config (rehype-raw + restricted rehype-sanitize schema),
                 used by both Editor's preview and the public MarkdownRenderer
  config.ts    — SITE_NAME (from NEXT_PUBLIC_SITE_NAME, falls back to "Blog CMS") — the one place to
                 rebrand; used in root layout (header + <title>), admin Sidebar, and /blog hero heading
  utils.ts     — cn(), formatDate(), truncate()

frontend/.env.example — NEXT_PUBLIC_SITE_NAME, NEXT_PUBLIC_BACKEND_URL, BACKEND_URL (all optional, defaults work out of the box)

types/
  post.ts, user.ts (now incl. Profile/UpdateProfileRequest), comment.ts, newsletter.ts
```

## Key Frontend Behaviours

- **Auth detection in client components:** use `useEffect(() => setIsLoggedIn(authLib.isAuthenticated()), [])` — never read localStorage during SSR.
- **JWT cookie for middleware:** `authLib.login()`/`updateProfile()` write both `localStorage` (for `api.ts` request headers) and a `document.cookie` (for Next.js `middleware.ts`). `updateProfile` specifically re-issues a fresh token because the JWT subject is the email, which may have just changed.
- **CommentSection** is a `'use client'` component so it fetches its own data after hydration — the parent post page stays a server component.
- **@mention highlighting** in `CommentSection`: client-side regex split on `@[a-zA-Z0-9_]+`, rendered as `<span className="text-blue-600">`.
- **Redis eviction:** PostService evicts a post's Redis key on `updatePost` and `deletePost` via `CacheService.evictPost(slug)`.
- **Newsletter emails:** real, sent through the pluggable `MailSender` abstraction — see "Reader OAuth & Mail Delivery" below.
- **Editor images**: uploaded/pasted images are inserted as `<img src id>` (not `![]()`) so `ResizableImage` can target them precisely for resize commits. Resizing writes `width`/`height`/`id` back into the raw markdown. `rehype-sanitize` prefixes all `id` attributes with `user-content-` (DOM-clobbering protection) — strip that prefix before matching against the raw source.
- **Editor text style / alignment**: toolbar has a "Style" dropdown (Paragraph, Heading 1-6 — replaces any existing heading prefix rather than stacking) and an "Align" dropdown (Left/Center/Right/Justify — wraps the block in `<div align="...">`, restricted to those 4 literal values in the sanitize schema, not an open `style` attribute). Nested markdown formatting inside an aligned block still works correctly (verified). These two use a captured-selection-on-mousedown pattern instead of the toolbar buttons' `onMouseDown preventDefault` trick, since preventing default on a `<select>` would block it from opening. No font-family/size controls — markdown has no such syntax; flagged to the user rather than guessed at.
- **Orphaned image cleanup** happens in `PostService` (backend), not the frontend — see API section above.
- **Sidebar/layout (app-shell pattern)**: collapsed state is `localStorage`-persisted (`admin_sidebar_collapsed`). Admin content panel is fully fluid (no forced min-width) — the Editor toolbar wraps via `flex-wrap` rather than being clipped or needing horizontal scroll.
  - Root layout (`app/layout.tsx`): `body` is `h-screen flex flex-col overflow-hidden`; `<header>` is `shrink-0` (fixed, never scrolls away); `{children}` is wrapped in `flex-1 min-h-0 overflow-y-auto` (the scroll region for ordinary pages).
  - Admin layout (`app/(admin)/layout.tsx`): `h-full` (fills exactly what the root wrapper gives it), `Sidebar` stays visually pinned, only `main` (`flex-1 min-w-0 min-h-0 overflow-y-auto`) scrolls.
  - `min-h-0`/`min-w-0` on the flex children are load-bearing — without them the flex item won't actually shrink to the bounded size, and `overflow-y-auto`/`overflow-x` won't kick in (content just overflows the parent instead). This tripped up two earlier iterations of this layout — see plan.md's Post-Launch Changes for the failure history if this area breaks again.

## Reader OAuth & Mail Delivery

**Reader identity (Google/GitHub sign-in for comments/mentions/newsletter):** entirely separate from admin auth. NextAuth v4 handles the OAuth dance on the frontend, mounted at `/api/reader-auth/[...nextauth]` — **not** the default `/api/auth/[...nextauth]`, which would collide with the existing admin `/api/auth/login`/`emergency-reset` routes (no Next.js route exists there today; they only work via `next.config.js`'s rewrite proxy, which the plain-array `rewrites()` form checks *before* dynamic catch-all routes — this exact collision broke reader sign-in once already, fixed by moving the backend-proxy rewrite into the `fallback` bucket). On first sign-in, NextAuth's `jwt` callback calls the backend's `POST /api/readers/oauth-login` (secret-gated, not Spring-Security-gated) to upsert a `readers` row and mint a reader-scoped JWT (`role: READER`, subject = reader's slugified+deduped `handle`, not email — a reader can have two rows if they sign in via Google and GitHub with the same email, deliberately not merged). That JWT flows through the *same* `JwtAuthFilter`/`JwtUtil` as admin tokens, no filter-chain changes. Guest commenting (no login) still works unchanged.

**Mail delivery:** see the `mail/` package above. Default is `log` (just logs, nothing sent — zero config). To send real email, set `app.mail.provider` to `smtp`/`resend`/`sendgrid` plus that provider's config block in `application.yml`. For local testing without a real provider account, `docker-compose.yml` includes an opt-in `mailpit` service (`docker compose up -d mailpit`, SMTP on `1025`, web UI on `http://localhost:8025`) — point `app.mail.provider=smtp` at `MAIL_SMTP_HOST=localhost MAIL_SMTP_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false` and every confirmation/digest email shows up in the Mailpit UI instead of a real inbox.

### Setup guide — Google/GitHub sign-in

1. **Google:** [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials → *Create Credentials* → OAuth client ID → Application type "Web application". Authorized redirect URI: `http://localhost:3000/api/reader-auth/callback/google` (swap the origin for your real domain in production — note the path is `/api/reader-auth/...`, **not** the NextAuth default `/api/auth/...`). Copy the Client ID/Secret into `frontend/.env.local`:
   ```
   GOOGLE_CLIENT_ID=...
   GOOGLE_CLIENT_SECRET=...
   ```
2. **GitHub:** GitHub → Settings → Developer settings → OAuth Apps → *New OAuth App*. Homepage URL `http://localhost:3000`, Authorization callback URL `http://localhost:3000/api/reader-auth/callback/github` (same path caveat as above). Generate a client secret, put both into `.env.local`:
   ```
   GITHUB_CLIENT_ID=...
   GITHUB_CLIENT_SECRET=...
   ```
3. Also required in `.env.local` regardless of provider: `NEXTAUTH_SECRET` (generate with `openssl rand -base64 32`), `NEXTAUTH_URL=http://localhost:3000`, and `INTERNAL_AUTH_SECRET` — must be the *literal same value* as the backend's `app.internal.auth-secret` / `INTERNAL_AUTH_SECRET` env var, or every reader sign-in 403s at the bridge call with no visible error beyond a generic sign-in failure.
4. Sign-in buttons already exist in `CommentForm.tsx` ("Continue with Google"/"Continue with GitHub") — nothing else to wire up once the four env vars above are set. Guest commenting keeps working with zero config either way.

### Setup guide — mail providers

Set `app.mail.provider` (or `MAIL_PROVIDER` if you prefer overriding via env — the yml key doesn't currently read from one, so edit `application.yml` directly or pass `--app.mail.provider=...` as a run arg) to one of:

- **`log`** (default) — nothing to configure. Emails are logged, not sent.
- **`smtp`** — works with Gmail, any self-hosted server, and AWS SES/Mailgun/Postmark via their SMTP relay credentials:
  ```
  MAIL_SMTP_HOST=smtp.gmail.com        # or your provider's SMTP host
  MAIL_SMTP_PORT=587
  MAIL_SMTP_USERNAME=you@gmail.com
  MAIL_SMTP_PASSWORD=<an App Password, not your real Gmail password>
  MAIL_SMTP_AUTH=true
  MAIL_SMTP_STARTTLS=true
  ```
  Gmail specifically requires an [App Password](https://myaccount.google.com/apppasswords) (2FA must be on) — a normal account password will be rejected.
- **`resend`** — sign up at resend.com, verify a sending domain (or use their shared test domain for dev), create an API key:
  ```
  RESEND_API_KEY=re_...
  ```
- **`sendgrid`** — sign up, verify a sender identity, create a full-access (or Mail Send scoped) API key:
  ```
  SENDGRID_API_KEY=SG...
  ```
- All providers also read `app.mail.from` (the sender address) and optionally `MAIL_REPLY_TO`.
- **Local sandbox, no account needed:** `docker compose up -d mailpit`, then `provider=smtp` with `MAIL_SMTP_HOST=localhost MAIL_SMTP_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false` — every email shows up at `http://localhost:8025` instead of anyone's real inbox. This is what was used to verify the SMTP path end-to-end (see plan.md's Post-Launch Changes).

## Known Pre-Existing Bugs Fixed (worth knowing if something looks newly broken)

These were latent since the original build, found incidentally while working on unrelated features:

- **Redis cache had no `LocalDateTime` support** → every single published-post page view 500'd on the cache-write. Fixed in `RedisConfig` by reusing Spring Boot's auto-configured `ObjectMapper` (has `JavaTimeModule`) instead of a bare `new GenericJackson2JsonRedisSerializer()`.
- **Next.js `fetch()` defaults to indefinite disk-persisted caching** (`.next/cache/fetch-cache`, survives dev-server restarts) → publishing/editing a post was invisible on the public site until the cache dir was manually wiped. Fixed with `cache: 'no-store'` in `lib/api.ts` (used by every page).
- **`@tailwindcss/typography` was never installed/registered** → every `prose` class app-wide was inert since inception (headings, blockquotes, code blocks, lists all unstyled). Installed + registered in `tailwind.config.ts`.
- **`/admin/*` link/middleware mismatch** — see Architecture note above re: route groups.
- **`GET /api/posts/{id}` didn't exist** — edit page fetched by ID against a slug-only endpoint; editing any post was completely broken until `GET /api/posts/id/{id}` was added.
- **Global `a:hover` CSS rule overrode intentional hover text colors app-wide** — `globals.css` had `a:hover { color: #1e40af }`, and `a:hover` (element+pseudo) has higher specificity than a single Tailwind class like `.text-white`, so it silently forced blue text on hover over ANY `<Link>`-rendered button/nav-item, regardless of that component's own color class (e.g. white text on the black "New Post" button, or on the sidebar's active blue item, both turning illegible same-color-on-same-color). Fixed by removing just the `:hover` variant; kept the base `a { color }` rule since a few unstyled links (sidebar logo, post title links) rely on it for their default blue.

If you hit something that looks like it "should just work," check this list before assuming it's a new regression.

## Local Dev Startup

```powershell
# 1 — infra
docker-compose up -d

# 2 — backend (Java 21 required)
cd C:\Users\mainu\Documents\codes\blogging-cms-app\backend; .\gradlew.bat bootRun

# 3 — frontend
cd C:\Users\mainu\Documents\codes\blogging-cms-app\frontend; npm run dev
```

**Why:** PowerShell on Windows, so use `.\gradlew.bat` (not `./gradlew`) and `;` to chain `cd` with the run command. No MSVC — user's Java/Gradle setup uses standard JDK 21. Flyway runs on startup and applies all migrations automatically. Backend serves port 8080, frontend port 3000.
