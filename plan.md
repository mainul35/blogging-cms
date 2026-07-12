# Full-Stack Blog/CMS System - Implementation Plan

> **Status: built and in active use.** See [Deviations From Original Plan](#deviations-from-original-plan) and
> [Post-Launch Changes](#post-launch-changes-not-in-original-scope) below for what actually shipped vs. what
> was originally scoped here — read those two sections first if you're picking this project back up.

## Context

This plan outlines the development of a modern, full-stack web-based Content Management System (CMS) for managing blog posts. The system will provide a complete solution for creating, editing, publishing, and organizing blog content with user authentication and rich text/markdown support.

**Why this change:** Building from scratch to create a production-ready CMS that leverages modern frameworks and best practices.

---

## Recommended Approach: Next.js Frontend + Spring Boot Reactive Backend

The system is split into two independent services communicating over REST/JSON:

- **Frontend** — Next.js 14+ (App Router) handles UI, SSR, and calls the backend API
- **Backend** — Spring Boot WebFlux (Reactive) handles all business logic, auth, and data access

### Technology Stack

| Layer          | Technology                        | Rationale                                                         |
|----------------|-----------------------------------|-------------------------------------------------------------------|
| Frontend       | Next.js 14+ (App Router)          | SSR/SSG support, excellent DX, seamless API consumption           |
| UI Language    | TypeScript                        | Type safety for API contracts and component props                 |
| Styling        | Tailwind CSS + shadcn/ui          | Rapid UI development, accessible components                       |
| Backend        | Spring Boot 3 + WebFlux           | Non-blocking reactive stack, high throughput under concurrent load |
| API Layer      | Spring WebFlux REST (Mono/Flux)   | Fully reactive endpoints; no thread-per-request blocking          |
| Database       | PostgreSQL + R2DBC                | Production-grade, fully reactive (non-blocking) driver; far faster than SQLite under load |
| Cache          | Redis (Spring Data Reactive Redis)| Sub-millisecond read cache for posts and sessions                 |
| Authentication | Spring Security + JWT             | Stateless auth; works naturally with reactive filter chain        |
| Build Tool     | Gradle (backend) / npm (frontend) | Standard for Spring Boot; fast incremental builds                 |

### Why PostgreSQL + R2DBC over SQLite/Prisma

- R2DBC is the reactive, non-blocking database driver — no thread blocking, scales with WebFlux
- PostgreSQL handles concurrent writes and large datasets without degradation
- Redis caches hot post reads, cutting DB round-trips for public blog pages

### Alternative Approaches Considered

1. **Django + React** — Strong Python backend, but no reactive/async-first story
2. **Express + React (separate repos)** — More boilerplate, callback-based async
3. **Static Site Generator (Astro/Next.js SSG)** — No dynamic admin panel
4. **MongoDB + Spring Reactive** — Good for schema-less content, but PostgreSQL's relational model fits CMS taxonomies (tags, categories) better

---

## Project Structure

```
blogging-cms-app/
├── backend/                               # Spring Boot WebFlux service
│   ├── src/
│   │   └── main/
│   │       ├── java/com/blog/cms/
│   │       │   ├── BlogCmsApplication.java
│   │       │   ├── config/
│   │       │   │   ├── SecurityConfig.java        # Spring Security + JWT filter chain
│   │       │   │   ├── R2dbcConfig.java            # R2DBC connection pool config
│   │       │   │   └── RedisConfig.java            # Reactive Redis template config
│   │       │   ├── controller/
│   │       │   │   ├── AuthController.java         # POST /api/auth/login, /register
│   │       │   │   ├── PostController.java         # CRUD /api/posts
│   │       │   │   ├── CategoryController.java
│   │       │   │   ├── TagController.java
│   │       │   │   ├── CommentController.java      # GET|POST /api/posts/{slug}/comments, DELETE /api/comments/{id}
│   │       │   │   ├── NewsletterController.java   # POST /api/newsletter/subscribe, GET confirm
│   │       │   │   └── AdminController.java        # GET /api/admin/stats, newsletter/subscribers, newsletter/send
│   │       │   ├── service/
│   │       │   │   ├── AuthService.java
│   │       │   │   ├── PostService.java
│   │       │   │   ├── CategoryService.java
│   │       │   │   ├── CacheService.java           # Redis cache logic
│   │       │   │   ├── CommentService.java         # Comment CRUD + @mention parsing
│   │       │   │   └── NewsletterService.java      # Subscribe, confirm, broadcast digest
│   │       │   ├── repository/                     # Spring Data R2DBC repositories
│   │       │   │   ├── UserRepository.java
│   │       │   │   ├── PostRepository.java
│   │       │   │   ├── CategoryRepository.java
│   │       │   │   ├── TagRepository.java
│   │       │   │   ├── CommentRepository.java
│   │       │   │   └── NewsletterRepository.java
│   │       │   ├── model/                          # R2DBC entity classes
│   │       │   │   ├── User.java
│   │       │   │   ├── Post.java
│   │       │   │   ├── Category.java
│   │       │   │   ├── Tag.java
│   │       │   │   ├── Comment.java
│   │       │   │   └── NewsletterSubscriber.java
│   │       │   ├── dto/                            # Request/response DTOs
│   │       │   │   ├── PostRequest.java
│   │       │   │   ├── PostResponse.java
│   │       │   │   └── AuthRequest.java
│   │       │   └── security/
│   │       │       ├── JwtUtil.java
│   │       │       └── JwtAuthFilter.java
│   │       └── resources/
│   │           ├── application.yml                 # DB, Redis, JWT config
│   │           └── db/migration/                   # Flyway SQL migrations
│   │               ├── V1__create_users.sql
│   │               ├── V2__create_posts.sql
│   │               └── V3__create_categories_tags.sql
│   └── build.gradle
│
├── frontend/                              # Next.js App Router service
│   ├── app/
│   │   ├── (auth)/
│   │   │   ├── login/
│   │   │   │   └── page.tsx
│   │   │   └── register/
│   │   │       └── page.tsx
│   │   ├── (admin)/                       # Protected admin routes
│   │   │   ├── dashboard/
│   │   │   │   └── page.tsx
│   │   │   ├── posts/
│   │   │   │   ├── create/
│   │   │   │   │   └── page.tsx
│   │   │   │   ├── [id]/edit/
│   │   │   │   │   └── page.tsx
│   │   │   │   └── page.tsx
│   │   │   └── layout.tsx                 # Admin sidebar/navigation
│   │   ├── blog/
│   │   │   └── page.tsx                   # Public post listing
│   │   ├── post/
│   │   │   └── [slug]/
│   │   │       └── page.tsx               # Individual post viewer
│   │   ├── layout.tsx
│   │   ├── page.tsx                       # Homepage
│   │   └── globals.css
│   ├── components/
│   │   ├── ui/                            # shadcn/ui primitives
│   │   ├── blog/
│   │   │   ├── PostCard.tsx
│   │   │   ├── PostList.tsx
│   │   │   ├── MarkdownRenderer.tsx
│   │   │   ├── CommentSection.tsx       # Threaded comment list + submit form per post
│   │   │   ├── CommentForm.tsx          # Guest/auth input with @mention highlighting
│   │   │   └── NewsletterForm.tsx       # Email subscribe widget (embedded in blog pages)
│   │   └── admin/
│   │       ├── Sidebar.tsx
│   │       ├── Editor.tsx                 # Markdown editor with live preview
│   │       └── PostForm.tsx
│   ├── lib/
│   │   ├── api.ts                         # Typed fetch wrappers to backend API
│   │   ├── auth.ts                        # JWT token storage/refresh logic
│   │   └── utils.ts
│   ├── types/
│   │   ├── post.ts
│   │   └── user.ts
│   ├── middleware.ts                      # Route protection via JWT check
│   ├── next.config.js
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   └── package.json
│
└── docker-compose.yml                     # PostgreSQL + Redis local dev setup
```

---

## Key Features to Implement

### 1. Authentication & Authorization

- **Backend:** `AuthController.java`, `SecurityConfig.java`, `JwtUtil.java`
- **Frontend:** `app/(auth)/login/page.tsx`, `lib/auth.ts`, `middleware.ts`
- Spring Security reactive filter chain with JWT (stateless)
- Role-based access: `ADMIN`, `AUTHOR`, `VIEWER`
- Frontend middleware validates JWT on every protected route navigation

### 2. Blog Post Management (CRUD)

- **Backend:** `PostController.java`, `PostService.java`, `PostRepository.java`
- **Frontend:** `app/(admin)/posts/`
- Fully reactive endpoints returning `Mono<PostResponse>` / `Flux<PostResponse>`
- Markdown content stored as text in PostgreSQL
- Metadata: title, slug, excerpt, cover image URL, tags, publish status

### 3. Content Storage

- **Primary:** PostgreSQL via R2DBC — reactive, non-blocking, strongly typed
- **Cache layer:** Redis caches published post reads (TTL-based invalidation on edit/publish)
- **Schema migrations:** Flyway manages versioned SQL migrations
- Models: `User`, `Post`, `Category`, `Tag`, `PostTag` (join table), `Comment`, `NewsletterSubscriber`

### 4. Public Blog Display

- **Frontend:** `app/blog/page.tsx`, `app/post/[slug]/page.tsx`
- Next.js fetches from backend API; published posts use ISR (Incremental Static Regeneration)
- Pagination, category/tag filtering, and slug-based routing
- SEO: `generateMetadata()` per post using backend data

### 5. Admin Dashboard

- **Frontend:** `app/(admin)/dashboard/page.tsx`
- Calls backend stats endpoint (`GET /api/admin/stats`)
- Displays: total posts, published vs drafts, recent activity

### 6. Comments & Mentions

- **Backend:** `CommentController.java`, `CommentService.java`, `CommentRepository.java`, `model/Comment.java`
- **Frontend:** `components/blog/CommentSection.tsx`, `components/blog/CommentForm.tsx`
- Readers can leave comments on published posts (authenticated or guest with name + email)
- `@username` mentions inside comment body notify the mentioned user
  - Mention detection via regex on save: extract `@handle` tokens, resolve to users, store in `comment_mentions` table
  - Notification stub: log to console initially; plug in email/WebSocket later
- Comment fields: `id`, `postId`, `authorId` (nullable for guests), `authorName`, `authorEmail`, `body`, `parentId` (threaded replies), `createdAt`
- Admin can delete any comment; authenticated authors can delete their own
- Endpoints:
  - `GET  /api/posts/{slug}/comments` — public, returns threaded comment tree
  - `POST /api/posts/{slug}/comments` — open (guest) or authenticated
  - `DELETE /api/comments/{id}` — owner or admin

### 7. Newsletter Subscription

- **Backend:** `NewsletterController.java`, `NewsletterService.java`, `NewsletterRepository.java`, `model/NewsletterSubscriber.java`
- **Frontend:** `components/blog/NewsletterForm.tsx` — embedded in `/blog` listing and each post page
- Visitors enter their email to subscribe; double opt-in via a confirmation email (UUID token link)
- Subscriber fields: `id`, `email`, `confirmed`, `token`, `subscribedAt`
- On post publish, admin can trigger a digest broadcast to all confirmed subscribers
- Endpoints:
  - `POST /api/newsletter/subscribe` — public; saves subscriber and sends confirmation email
  - `GET  /api/newsletter/confirm?token={token}` — public; marks subscriber as confirmed
  - `GET  /api/admin/newsletter/subscribers` — admin only; returns subscriber list
  - `POST /api/admin/newsletter/send` — admin only; broadcasts digest for a given post

---

## Implementation Steps

### Phase 1: Infrastructure Setup

- [x] Create `docker-compose.yml` with PostgreSQL 16 and Redis 7 services
- [x] Bootstrap Spring Boot project (Spring Initializr: WebFlux, R2DBC, Security, Redis, Flyway)
- [x] Bootstrap Next.js frontend with TypeScript and Tailwind
- [ ] ~~Install shadcn/ui components~~ — **not done**; frontend uses plain Tailwind utility classes throughout, no `components/ui/` was ever added

### Phase 2: Database Schema & Migrations

- [x] Write Flyway migrations: `users`, `posts`, `categories`, `tags`, `post_tags`, `comments`, `comment_mentions`, `newsletter_subscribers` (V1–V5), plus V6 `avatar_url` added later
- [x] Define R2DBC entity classes and Spring Data repositories
- [x] Write seed SQL for initial categories and an admin user

### Phase 3: Backend — Authentication

- [x] ~~Implement `AuthController`: `POST /api/auth/register`~~ — **removed entirely**, see Deviations below
- [x] `POST /api/auth/login`
- [x] Configure Spring Security reactive filter chain
- [x] Implement `JwtUtil` for token generation and `JwtAuthFilter` for validation
- [x] Role-based method security on admin endpoints

### Phase 4: Backend — Post & Content APIs

- [x] `PostController`: `GET /api/posts`, `GET /api/posts/{slug}`, `GET /api/posts/id/{id}` (added later), `POST`, `PUT`, `DELETE`
- [x] `PostService` with reactive R2DBC queries and Redis cache integration
- [x] `CategoryController` and `TagController` — **backend only**, no frontend filter UI was ever built for these
- [x] Admin stats endpoint: `GET /api/admin/stats`
- [x] `CommentController` + `CommentService`: threaded comments with `@mention` extraction
- [x] `NewsletterController` + `NewsletterService`: subscribe, confirm (token), and broadcast digest

### Phase 5: Frontend — Authentication Flow

- [x] ~~Build login/register pages~~ — login only; no register page (removed, see Deviations)
- [ ] ~~Store JWT in `httpOnly` cookie via Next.js route handler~~ — **not done**; JWT lives in `localStorage` + a plain (non-httpOnly) `document.cookie`, see Deviations
- [x] `middleware.ts` to protect admin routes (protects bare paths like `/dashboard`, `/posts` — not `/admin/**`, see Deviations)
- [ ] ~~Session refresh logic~~ — not implemented; token just expires after 24h, user re-logs-in

### Phase 6: Frontend — Admin Panel

- [x] Post list view (calls `GET /api/posts?status=all`) — no search/filter UI was added
- [x] Post creation and edit forms with markdown editor and live preview
- [x] Delete functionality with confirmation
- [x] Image upload — local-disk backend endpoint (not Cloudinary), see Post-Launch Changes

### Phase 7: Frontend — Public Blog Pages

- [x] Homepage with post listing — no pagination was built
- [x] Individual post page with markdown rendering (react-markdown)
- [ ] ~~Category/tag filter pages~~ — not built (backend supports it, frontend doesn't surface it)
- [x] SEO metadata via `generateMetadata()` using backend post data
- [x] `CommentSection` + `CommentForm` embedded below each post (guest name + email, threaded replies, `@mention` highlight)
- [x] `NewsletterForm` widget embedded in `/blog` listing footer and each post page

### Phase 8: Polish & Deployment Prep

- [ ] Loading states / error boundaries / toast notifications — minimal only (inline error text, no toast system)
- [x] Responsive design verification — sidebar collapse + toolbar wrapping added, see Post-Launch Changes
- [x] Configure CORS on Spring Boot for the Next.js origin
- [x] Environment variable documentation — `frontend/.env.example` added (site name, backend URLs). Backend secrets are still inline defaults in `application.yml` with env-var overrides for prod (e.g. `ADMIN_RESET_SECRET`); no backend `.env.example`.
- [ ] Production build verification (`./gradlew build` + `npm run build`) — not run; only ever exercised via `bootRun` / `next dev`

---

## Verification Plan

### Backend (Spring Boot)

1. **Start services:** `docker-compose up -d` then `./gradlew bootRun`
2. **Auth endpoints:**
   - `POST /api/auth/register` — returns JWT
   - `POST /api/auth/login` — returns JWT
3. **Protected endpoints:**
   - Call `GET /api/posts` without token — expect `401`
   - Call with valid JWT — expect post list
4. **CRUD flow:**
   - Create post via `POST /api/posts` (admin JWT)
   - Fetch by slug via `GET /api/posts/{slug}`
   - Edit via `PUT /api/posts/{id}`
   - Delete via `DELETE /api/posts/{id}`

### Frontend (Next.js)

1. **Start:** `npm run dev`
   - Navigate to `/login` — should show login page
   - Log in and verify JWT cookie is set
2. **Admin Panel:**
   - Access `/admin/posts/create` — should show post editor
   - Create a post and verify it appears at `/admin/posts`
3. **Public Blog:**
   - Navigate to `/blog` — displays published posts
   - Click post to view at `/post/[slug]`; verify markdown renders
4. **Auth guards:**
   - Access `/admin` without login — should redirect to `/login`
   - Log out and verify JWT cookie is cleared
5. **Comments:**
   - Navigate to a published post — comment form should appear below content
   - Post a guest comment (name + email + body) — should appear in the thread
   - Post a comment with `@admin` mention — verify mention is stored and logged
   - Delete a comment as admin — should disappear from thread
6. **Newsletter:**
   - Submit email via the subscribe form — `POST /api/newsletter/subscribe` returns 200
   - Click confirmation link (`/api/newsletter/confirm?token=...`) — subscriber marked confirmed
   - `GET /api/admin/newsletter/subscribers` with admin JWT — returns confirmed list
7. **Production build:** `npm run build && npm run start`

---

## Existing Patterns to Reuse

No existing codebase — new implementation from scratch. The following patterns will be adopted:

- **Spring WebFlux reactive chain** — all service/repository methods return `Mono<T>` or `Flux<T>`; never block
- **R2DBC repository pattern** — extend `ReactiveCrudRepository` for zero-boilerplate CRUD
- **Flyway migrations** — versioned, incremental SQL scripts; no schema auto-generate in production
- **Next.js App Router conventions** — server components for data fetching, client components for interactivity

---

## Deviations From Original Plan

Things that shipped differently than originally scoped above:

- **No shadcn/ui.** Plain Tailwind utility classes throughout; `components/ui/` was never created.
- **No public registration.** `POST /api/auth/register` was built per the original plan, then **removed entirely** — this is a single-admin CMS, not a multi-user platform. The one admin account is Flyway-seeded (`admin@blog.com` / `Admin@1234`), and the "AUTHOR" role registration path never got a use case. See Post-Launch Changes for the replacement (self-service password/profile + emergency reset).
- **JWT storage isn't `httpOnly`.** The plan called for an httpOnly cookie via a Next.js route handler; what actually shipped is `localStorage` + a plain `document.cookie` (readable by JS), written directly in `lib/auth.ts`. Middleware reads the cookie for route protection; `api.ts` reads localStorage for the `Authorization` header. This is simpler but more XSS-exposed than the original plan — worth hardening if this ever handles real user data.
- **No session refresh.** Token just expires after 24h (`app.jwt.expiration-ms`); user has to log in again. No silent refresh was built.
- **Admin routes aren't under `/admin/**`.** `(admin)` is a Next.js *route group* (parentheses = no path segment), so the real URLs are `/dashboard`, `/posts`, `/comments`, etc. This caused a real bug (see Post-Launch Changes) — `middleware.ts`'s matcher and internal links both originally assumed an `/admin/` prefix that never existed on disk.
- **No Category/Tag frontend.** Backend CRUD exists (`CategoryController`, `TagController`) and is fully reactive, but no admin UI or public filter pages were ever built against them — they're unused from the frontend's perspective.
- **No ISR, no pagination, no search/filter UI, no toast system.** All scoped in Phase 7/8 but never built; error states are inline text, not toasts.
- **Backend has no `.env.example`.** Secrets are inline defaults in `application.yml`, overridable via env vars (e.g. `ADMIN_RESET_SECRET`) for anything meant to change in prod. (The frontend got one — see Post-Launch Changes.)

---

## Post-Launch Changes (Not in Original Scope)

Everything below was built in follow-up sessions after the initial implementation above, driven by direct user requests rather than this plan. Grouped by theme; see [blogging-cms-app.md](blogging-cms-app.md) memory for full technical detail on each.

### Admin auth overhaul
- Removed public registration.
- `PUT /api/admin/account/password` — authenticated self-service password change.
- `GET/PUT /api/admin/account/profile` — self-service email/name/avatar change. Reissues a fresh JWT on change since the token subject is the email.
- `POST /api/auth/emergency-reset` — not linked from the frontend; gated by a shared secret (`X-Admin-Reset-Secret` header / `ADMIN_RESET_SECRET` env var), resets the admin's email + password back to Flyway-seeded defaults. Keyed by role (`findFirstByRole`), not email, so it still works after the admin changes their email.

### Image upload + editor
- Local-disk upload endpoint (`POST /api/admin/uploads`, admin-only), served publicly at `/uploads/**`. Configurable allowed types and max size (`app.upload.*`).
- Editor toolbar: Bold, Italic, Strikethrough, Inline code, Code block, Quote, Bullet/Numbered list, Link, Upload image — wrap-selection or insert-with-cursor-positioned-for-typing.
- **Text style + Alignment dropdowns** (replaced a single H2-only button): "Style" select offers Paragraph/Heading 1-6, replacing any existing heading prefix rather than stacking one on top. "Align" select offers Left/Center/Right/Justify, wrapping the block in `<div align="...">` (existing wrapper replaced, not nested, on realignment; "Left" unwraps back to plain text). Verified nested markdown (bold, links) inside an aligned block still renders correctly — the blank lines around the content mean CommonMark treats the div tags as separate raw-HTML nodes while the enclosed text still gets full markdown parsing. `align` is restricted to those four literal values in the sanitize schema (not an open `style` attribute) so it can't smuggle arbitrary CSS. Selects can't use the buttons' `onMouseDown preventDefault` trick (would block the dropdown from opening) — selection is captured on mousedown instead and applied in `onChange`.
  - **Considered and explicitly declined**: font-family/font-size controls. Markdown has no font syntax; the closest available things are already in the toolbar (bold/italic/code). Flagged to the user rather than guessed at.
- Paste-an-image-directly and toolbar-upload both insert `<img>` tags (not plain `![]()`) so they can later be resized.
- **Resizable images**: 8 drag handles in the editor preview (corners = proportional, edges = single-axis), committing the final pixel size back into the markdown source. Requires `rehype-raw` + a restricted `rehype-sanitize` schema (img `src`/`alt`/`width`/`height`/`id` only) — shared between the editor preview and the public post renderer via `lib/markdown.ts`.
- **Cover image** is now an upload+preview widget (`CoverImageUpload.tsx`) instead of a raw URL text field, cropped with `object-cover` to match how it displays to readers.
- **Orphan cleanup**: updating or deleting a post diffs which uploaded images it references (content + cover) before/after, and deletes files no longer referenced — but only if no *other* post still uses that file (`PostRepository.countOtherPostsReferencingFile`). Known gap: an uploaded-but-never-saved image is never cleaned up (no scheduled sweep).

### Layout / responsiveness
- Sidebar is collapsible (224px ↔ 64px icon rail), toggle persisted to `localStorage`, icons added to every nav item.
- Editor toolbar wraps (`flex-wrap`) onto additional lines on narrow viewports instead of being clipped or requiring horizontal scroll. The admin content panel itself is fully fluid (`min-w-0`, no forced minimum width) — an earlier attempt at a fixed `min-w-[640px]` + horizontal scroll was tried and reverted because the scrollbar ended up unreachable.
- **App-shell layout** (final form, after two earlier attempts each had their own bug — see history below): root layout (`app/layout.tsx`) is `body { h-screen flex flex-col overflow-hidden }` with the `<header>` as a `shrink-0` flex item, and `{children}` wrapped in a `flex-1 min-h-0 overflow-y-auto` div — so the header never scrolls out of view, and that wrapper is the scroll region for ordinary (non-admin) pages. `app/(admin)/layout.tsx` sits inside that wrapper as `h-full` (exactly filling the space the wrapper hands it, never more), with `Sidebar` staying visually pinned and only `main` (`flex-1 min-w-0 min-h-0 overflow-y-auto`) scrolling internally — so the sidebar never scrolls away either, only the actual content does.
  - *History, for context if this breaks again*: (1) first cut let the whole page scroll via `min-h-screen` with no fixed height anywhere — worked, but the sidebar scrolled away with long content, which the app-shell version above fixes; (2) an attempt at `h-screen` + `main overflow-auto` *without* first fixing the root layout's header caused two vertical scrollbars (body's and main's), because the header's real height was double-counted against a hardcoded 100vh. The current version fixes that by deriving the bounded height from `flex-1 min-h-0` in the root layout instead of guessing a fixed value.

### Open-source distribution readiness
- **Configurable site name**: "Blog CMS" was hardcoded in 3 user-facing places (root layout's header + `<title>` metadata, admin `Sidebar` brand link, `/blog` hero heading). Centralized into `frontend/lib/config.ts`'s `SITE_NAME`, sourced from `NEXT_PUBLIC_SITE_NAME` (falls back to "Blog CMS" so it works unconfigured). Verified end-to-end with a custom value across all 3 locations plus the browser tab title, then reverted.
- Added `frontend/.env.example` documenting `NEXT_PUBLIC_SITE_NAME`, `NEXT_PUBLIC_BACKEND_URL`, and `BACKEND_URL` — the project had no environment documentation at all before this.

### Reader identity: Google/GitHub OAuth for comments, mentions, newsletter
- **Motivation**: this is a single-admin CMS by design (see Deviations above — public registration was removed entirely), but `@mention` in comments could only ever resolve to the one admin user, since visitors had no persistent identity. Guest commenting (typed name + email, no verification) needed to keep working unchanged alongside a real, optional sign-in.
- **Architecture decision**: the OAuth2 authorization-code dance is handled entirely by NextAuth v4 on the Next.js frontend rather than adding `spring-boot-starter-oauth2-client` to the backend — the backend's single `SecurityWebFilterChain` already had its one authentication-filter slot claimed by the admin JWT filter, and NextAuth already verifies the Google/GitHub identity before the backend ever needs to trust it. On first sign-in, NextAuth's server-side `jwt` callback calls a new `POST /api/readers/oauth-login`, gated by a shared secret header (mirroring the existing admin emergency-reset pattern) rather than Spring Security, and mints a reader-scoped JWT reusing the *existing* `JwtUtil`/`JwtAuthFilter` unchanged (`role: READER` instead of `ADMIN`, same signing secret) — zero changes to the security filter chain.
- **New schema**: `readers` (stable, deduped, mention-safe `handle` slugified from the OAuth display name) + `reader_oauth_identities` (one row per provider identity — same email via Google *and* GitHub deliberately stays two separate reader rows, not merged, since GitHub emails can be noreply-proxy addresses that make cross-provider email matching unreliable). `comments.reader_id` and `comment_mentions.mentioned_reader_id` added alongside the existing admin-only columns.
- **Route collision caught during planning, not after shipping**: NextAuth must not mount at its default `/api/auth/[...nextauth]` — that would silently intercept the existing admin `POST /api/auth/login`/`emergency-reset` (which only work today via `next.config.js`'s rewrite proxy, since no Next.js route exists at those exact paths). Mounted at `/api/reader-auth/[...nextauth]` instead.
- **Frontend**: `CommentForm` gained three states (guest/signed-in-reader/admin) plus "Continue with Google/GitHub" buttons and a `@mention` autocomplete scoped to the current post's commenters (no new endpoint — derived client-side from the already-loaded comment tree). `CommentSection` shows real avatars and visually distinguishes resolved mentions from unresolved ones. A `NewsletterOfferBanner` offers (doesn't force) newsletter signup using the reader's OAuth email right after sign-in.
- **Setup**: see [blogging-cms-app.md](blogging-cms-app.md)'s "Reader OAuth & Mail Delivery" section for the exact Google Cloud Console / GitHub Developer Settings steps and required env vars.

### Pluggable mail delivery
- **Motivation**: newsletter emails (confirmation link, digest broadcast) were stubbed since the original implementation — `log.info`, nothing ever actually sent. Since this CMS is meant to be forked/self-hosted, mail sending needed to work with whatever provider each self-hoster already has, via configuration only.
- **Design**: a `MailSender` interface with four `@ConditionalOnProperty`-gated implementations selected by `app.mail.provider` — `log` (default, today's exact stub behavior, zero config), `smtp` (covers Gmail, self-hosted servers, *and* AWS SES/Mailgun/Postmark via their SMTP relay — no dedicated SDK needed for those three), `resend` and `sendgrid` (HTTP APIs via a self-built `WebClient` each, no extra dependency). This is the first pluggable-backend / conditional-bean pattern in the codebase, but it reuses the existing `app.<feature>.<key>` config convention and the blocking-call-wrapping idiom (`Mono.fromRunnable(...).subscribeOn(Schedulers.boundedElastic())`) already used twice in `UploadService` — `JavaMailSender` is blocking, unlike the WebClient-based providers.
- **`NewsletterService`** now builds real clickable links (confirmation → backend `/api/newsletter/confirm`, digest → the actual frontend `/post/{slug}` route) and sends through the injected `MailSender`, with per-send `.onErrorResume` (matching `CommentService`'s existing mention-resolution resilience pattern) so one bad address or a provider hiccup never fails the subscribe/digest HTTP response itself.
- **Local sandbox**: `docker-compose.yml` gained an opt-in `mailpit` service (SMTP catch-all + web UI, `http://localhost:8025`) so anyone can see real emails render without a real provider account. Not started by default, doesn't change the code default (`log`).
- **Setup**: see [blogging-cms-app.md](blogging-cms-app.md)'s "Reader OAuth & Mail Delivery" section for exact env vars per provider (SMTP/Resend/SendGrid) and the Mailpit walkthrough.
- **Incidental bug found and fixed**: `lib/api.ts`'s `request()` unconditionally called `res.json()` on every successful response, but `subscribe`/`confirm`/`send` all return plain text (`Content-Type: text/plain`) — this had been silently breaking newsletter signup (both the public form and the admin "Send Digest" button) with a confusing "Unexpected token... is not valid JSON" error since those endpoints were first built. Fixed by checking `Content-Type` and falling back to `res.text()` for non-JSON responses.

### Pre-existing bugs found and fixed along the way
These were latent since the original implementation, surfaced while working on the above — not related to whatever feature was being built at the time:
- **`/admin/*` routing mismatch** — see Deviations above. Fixed all internal links + `middleware.ts`'s matcher to use the real bare paths.
- **`GET /api/posts/{id}` didn't exist** — only slug lookup existed, but the edit page fetched by numeric ID. Editing any post was completely broken. Added `GET /api/posts/id/{id}`.
- **Redis cache serialization was broken for every post view** — `GenericJackson2JsonRedisSerializer` used a bare `ObjectMapper` with no `JavaTimeModule`, so caching a `PostResponse` (which has `LocalDateTime` fields) threw on every single published-post page view. This meant **no post had ever actually been viewable** on the public site. Fixed by reusing Spring Boot's own auto-configured `ObjectMapper` bean in `RedisConfig`.
- **Next.js server-side `fetch()` cached indefinitely** — default `force-cache` behavior, persisted to disk (`​.next/cache/fetch-cache`), survived even dev-server restarts. Any publish/edit was invisible to visitors until someone manually wiped the cache. Fixed with `cache: 'no-store'` in `lib/api.ts`.
- **`@tailwindcss/typography` was never installed or registered** (`plugins: []` in `tailwind.config.ts` despite `theme.extend.typography: {}` being present as a no-op). This meant **every** `prose`/`prose-*:` class across the whole app — headings, blockquotes, code blocks, inline code, lists — had been silently inert since the project's inception, not just in the one place it was noticed (the editor's code blocks looking undistinguished from body text). Installed the package and registered it.
- **Global `a:hover` CSS rule silently overrode intentional hover text colors app-wide.** `globals.css` had `a { color: #1d4ed8 } a:hover { color: #1e40af }`. `a:hover` (element + pseudo-class) has *higher specificity* than a single Tailwind utility class like `.text-white`, so on hover it overrode any `<Link>`-rendered button/nav-item's own color — e.g. the black "New Post" button and the sidebar's active blue nav item both had their white text force-changed to blue on hover, landing on illegible blue-on-black / blue-on-blue. Fixed by removing just the `:hover` variant (kept the base `a` rule, since a few unstyled links — the sidebar logo, post title links, "Create your first post" — rely on it for their default blue color and have no explicit color class of their own).

---

## Next Steps (Historical — Phase 1 Kickoff)

Upon approval, proceed with:

1. Running `docker-compose up -d` to start PostgreSQL and Redis
2. Generating the Spring Boot project via Spring Initializr
3. Creating the Next.js scaffold with `npx create-next-app`
4. Implementing each phase sequentially with verification at each stage
