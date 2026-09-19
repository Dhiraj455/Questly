# Questly v3 — Accounts & Backend (plan)

_Created 2026-09-19. Approach: **contract-first**. Companion: `docs/api/openapi.yaml`._

## Goal

Move progress off the single device onto a server: real accounts, server-owned
points/history, server-validated check-ins, and an Overpass proxy+cache — so
progress syncs across devices and the app no longer trusts the client for scoring.

The app keeps its existing `CheckpointRepository` / `CheckInRepository` interfaces
and gains **remote implementations** that call the REST API. The UI does not change.

## Decisions

- **Auth: self-hosted** in the Ktor backend — email+password **with real email
  verification** (account inactive until an emailed token is verified; no dummy
  emails) **plus Google OAuth** (verify the Google ID token server-side).
- **Contract-first:** `docs/api/openapi.yaml` is the source of truth; client and
  server are built against it in parallel.
- **Tokens:** short-lived JWT access token + rotating refresh token. On the app,
  store tokens in EncryptedSharedPreferences/Keystore (never plain prefs).

## Open decisions (pick as we go)

- **Email sending** provider (needed for real verification): Brevo / Mailgun /
  SendGrid free tier, or Gmail SMTP. TBD.
- **Hosting:** small VPS (Docker) vs a managed platform (Railway/Render/Fly) with a
  free/managed Postgres. TBD.
- **Client HTTP + JSON codegen:** Ktor Client + kotlinx.serialization, hand-written
  models vs OpenAPI-generated. Leaning hand-written (small surface, full control).

## Architecture

```
backend/                      # new standalone JVM (Ktor) project — NOT an Android module
  api/        Ktor routes matching openapi.yaml
  auth/       registration, email verification, login, Google verify, JWT, refresh rotation
  overpass/   server-side Overpass client + cache (moved off the app)
  checkins/   server-validated check-in rules (distance + time + plausibility), points ledger
  db/         PostgreSQL via Exposed; Flyway migrations
app side:
  :core:network        add a typed API client (Ktor Client) for the endpoints
  :core:data           Remote*Repository impls behind existing interfaces; token store
  :app                 auth screens (sign in / sign up / verify), session gate
```

## Server-side data model (first cut)

- `users` (id, email, display_name, password_hash, email_verified, created_at)
- `email_tokens` (token, user_id, purpose {VERIFY, RESET}, expires_at, used_at)
- `refresh_tokens` (token, user_id, expires_at, revoked_at) — rotation + revocation
- `checkins` (id, user_id, checkpoint_id, title, points, created_at) — server ledger
- `checkpoint_cache` (query key → POIs, fetched_at) — Overpass proxy cache

## Milestones

1. **Contract** — `openapi.yaml` (DONE). Review + freeze the shapes.
2. **Backend skeleton** — Ktor app boots, `/health`, Postgres wired, Flyway baseline.
3. **Auth vertical slice** — register → verify email (real send) → login → refresh;
   `GET /auth/me`. Integration-tested.
4. **Google sign-in** — verify Google ID token; link/create account.
5. **Overpass proxy + cache** — move the Overpass client server-side; `GET /checkpoints`.
6. **Check-ins + points** — server-validated `POST /checkins` (idempotent), history,
   `GET /points`; anti-cheat distance/time checks.
7. **App integration** — API client + remote repositories behind the interfaces;
   token store; auth screens + session gate; guest→account upgrade preserving local
   progress.
8. **Security & CI** — HTTPS + cert pinning, rate limiting, input validation, backend
   integration/contract tests in CI.

## Security checklist (v3 scope, from ROADMAP §3)

- HTTPS everywhere; certificate pinning app→API
- Tokens in EncryptedSharedPreferences/Keystore
- No secrets in repo/APK (CI env / signing)
- Server-side input validation + rate limiting
- Anti-cheat: server-validated location/time + plausibility
- Idempotent check-in endpoint; conflict handling; pagination for lists
