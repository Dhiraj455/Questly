# Questly — Version Roadmap & Production-Readiness

_Last updated: 2026-10-04_

This document lists every planned version, what each contains, and everything
required to take Questly from a portfolio project to a **production-ready,
shippable app**. Companion docs: `docs/HANDOFF.md` (context), the specs/plans
under `docs/superpowers/`.

---

## Current status

- **v1–v2 — DONE.** Walking skeleton + real-world Overpass quests (radius, type
  filter, refresh, check-in history title snapshot). Committed and green.
- **v3 — DONE, live on Render.** Ktor + PostgreSQL backend, OpenAPI contract,
  self-hosted email + Google OAuth (real email verification), server-owned points
  ledger + check-in history, idempotent server-validated check-ins, rate limiting,
  encrypted token storage on-device. Base URL `https://questly-d4ws.onrender.com/v1`.
- **v4 — DONE, live on Render.** Streaks + 10-achievement catalog, friends via
  shareable friend code + requests + activity feed, real-time leaderboard over
  WebSocket, FCM push (a friend's check-in notifies their friends), profile screen
  with account deletion. All milestones verified live.
- **v5 — NEXT (scoped below).** Turn the Discover screen's placeholder events into
  **real, user-created events with registration (free + paid) and participation**,
  add **friends chat**, and do the **launch-readiness** hardening a real social app
  with payments and user-generated content requires.

> Effort sizing, sequencing, and provider choices below are **[inference]** — a
> recommended plan, not a committed estimate. Play-policy and payment-provider
> specifics are marked **[unverified]**; confirm against current official docs
> before building.

---

## Version roadmap

### v1 ✅ Walking skeleton (done)
Local, single-device proof of the core loop.
- MapLibre map, foreground geofenced check-in, points ledger (derived sum)
- Room, MVVM + Hilt, multi-module, coroutines/Flow, unit tests, GitHub Actions CI
- Brand theme, bottom sheet, detail card

### v2 ✅ Real-world quests (done)
Real places become quests, live, by location & distance.
- Overpass API POIs (free, no key), radius control 1–20 km, distance sort
- Refresh-on-move + manual refresh, type filter, loading/error, recenter
- Points by POI type; checkpoints cached in Room

### v3 ✅ Accounts & backend (done, live)
Progress lives on a server, not one device.
- **Ktor + PostgreSQL** backend; OpenAPI contract; app talks REST
- **Auth / accounts** (self-hosted email + Google OAuth, real email verification);
  points ledger + check-in history server-side
- **Server-validated check-ins** (idempotent; location + time checked server-side)
- FCM push plumbing; rate limiting; encrypted token storage on-device

### v4 ✅ Social & competition (done, live)
- **Real-time leaderboard over WebSocket** (global)
- Streaks, badges, achievements; friends (friend code), activity feed
- Push notifications (a friend's check-in notifies their friends)

### v5 🔨 Events, registration & chat — the "social life" release (NEXT)
Turn Discover's placeholder events into a real, user-driven event layer, and let
friends talk. See the full milestone breakdown under **"v5 plan (detailed)"** below.
- **User-created & managed events** — any user can host: create, edit, cancel; set
  category, place/time, capacity, visibility (public / friends-only / private).
- **Registration & participation** — RSVP with capacity + waitlist; attendee roster
  for the host; attending an event + checking in there awards points (ties events
  into the existing core loop).
- **Free and paid registration** — free RSVP, plus **paid tickets** via a payment
  provider (Stripe primary; Razorpay as a regional alternative). Card data never
  touches our servers; refunds on cancellation. Platform-collected first; host
  payouts (marketplace / Stripe Connect) deferred.
- **Friends chat** — 1:1 DMs between friends and per-event group chat for attendees,
  reusing the existing WebSocket hub + FCM; with block / report / mute (required
  for a social app). Can be trimmed to event-only chat to keep v5 lean.
- **Launch readiness** — the production hardening a real app with payments + UGC
  needs: Crashlytics, R8 + log stripping, cert pinning, privacy policy + Play Data
  Safety, moderation surfaces, UI/instrumented tests in CI.

### v6 Partner rewards & marketplace (deferred)
- **Partner venues/businesses**: offers as rewards; **sponsored challenges**
- **Redemption** (points → gift cards / perks); **attribution tracking**
- **Host payouts** (Stripe Connect + KYC/tax) — turns paid events two-sided
- Optional partner/merchant portal

### v7 AI personalization (the "AI-forward" differentiator)
- AI-generated / personalized challenges from history + location + time
- "Quests you'll like nearby" recommendations; natural-language quest search

### v8 Scale & ongoing hardening
- Offline-first sync, baseline profiles, macrobenchmarks
- Engagement/analytics dashboards deeper, localization, device-farm smoke tests
- (Most app-store/release hardening is pulled forward into v5 launch readiness)

> **Portfolio note:** through **v4** already demonstrates almost the entire Fetch
> JD (Compose, Hilt, Room, real backend, real-time, geospatial, testing,
> AI-adjacent). v5–v6 make it a real product rather than a strong sample.

---

## v5 plan (detailed)

**Theme:** Questly is an app for real-world social life — people roaming to places
together. v5 makes that literal: anyone can host an event, others find and join it
(free or paid), and friends can talk. Built contract-first like v3/v4 (OpenAPI seam
first, then backend + app behind the existing repository interfaces). Milestones are
independently shippable and ordered so each is useful on its own.

**Where we start from:** Discover events are currently placeholder data — a local
`Event(id, title, category, distanceMeters, recommended)` with no backend, no
registration, no participation (`feature/discover/.../Event.kt`). Friends, FCM push,
and a live WebSocket hub already exist from v4 and are **reused** heavily below.

### Milestone E — Real, user-created events (replace placeholder data) — ✅ DONE (2026-10-08)
Make events first-class server objects that any user can create and manage.

**Shipped:** backend (`V5__events.sql`, `events/` service + routes, visibility enforced,
OpenAPI, `EventsFlowTest` — written, run blocked only by Docker being down) **and** the
Android side: `core:model` `Event`/`EventInput`, `core:network` event DTOs + endpoints,
`RemoteEventsRepository` (`core:data`), a repo-backed `DiscoverViewModel`, and new
**Event detail**, **Create/Manage event**, and **My events** screens in `:feature:discover`
(placeholder `Event`/`DummyEvents` removed). App assembles green; discover unit tests pass.
Two deferred bits, both tied to later milestones: the detail "map" is a location card +
"Open in Maps" intent (an embedded MapLibre view can come later), and the Join/Register
button surfaces a "coming soon" message until **Milestone F** wires registration.
- **Backend (migration `V5__events.sql`):** `events` table — id, host_id (FK users),
  title, description, category, venue_name, lat, lng, starts_at, ends_at, capacity
  (nullable = unlimited), visibility (`PUBLIC` / `FRIENDS` / `PRIVATE`),
  registration_type (`NONE` / `FREE` / `PAID`), price_cents + currency (paid only),
  status (`DRAFT` / `PUBLISHED` / `CANCELLED`), created_at. Indexes on (lat,lng) area
  + starts_at for discovery.
- **Routes (`/v1/events`):** create / update / cancel (host only), get one, list by
  area + radius + category + time window (feeds Discover), `GET /events/mine`
  (hosting + attending). Visibility enforced server-side (friends-only events only
  surface to friends).
- **Core-loop tie-in:** an event has a location, so it becomes a checkpoint — checking
  in at an event you're attending awards points and marks you `attended`. This is the
  key design choice: events ride the existing check-in/points machinery instead of a
  parallel system.
- **App:** replace the dummy `Event` with a domain model fed from the backend;
  `DiscoverRepository` hits `/events`; new **Event detail** screen (map, host, time,
  capacity, join button) and **Create/Manage event** screen for hosts; "My events" view.

### Milestone F — Registration & participation (free) — ✅ DONE (2026-10-09)
RSVP, capacity, waitlist, roster — the non-money half of joining.

**Shipped:** backend `event_registrations` (`V6__event_registrations.sql`), `RegistrationsService`
(register/unregister with capacity→waitlist and auto-promote, host roster, `markAttended` tie-in),
routes `POST/DELETE /events/{id}/register` + `GET /events/{id}/roster`, EventDto enriched with
`registeredCount`/`spotsLeft`/`viewerStatus`, FCM pushes (host "new registration", friends "a friend
is going", waitlist promotion, host-cancelled), OpenAPI, and `EventRegistrationFlowTest`. Android:
`RegistrationStatus` + roster models, repo `register`/`unregister`/`roster`, real Join/Leave +
waitlist UI on the detail, "Going"/"Waitlisted" badge on Discover cards, and a host **roster** screen.
Check-in callback now carries `checkpointId` so a check-in at an event flips the registration to
ATTENDED. _"Event starts soon" push is deferred — it needs a scheduler the backend doesn't have yet._
- **Backend:** `event_registrations` table — event_id, user_id, status
  (`REGISTERED` / `WAITLISTED` / `CANCELLED` / `ATTENDED`), registered_at,
  unique(event_id, user_id). Register / unregister endpoints enforce capacity and
  auto-promote the waitlist when a spot frees. Host sees the attendee roster.
- **Push (reuses FCM):** "you're registered", "event starts soon", "the host updated /
  cancelled the event", "a friend is going".
- **App:** Join / Leave on the event detail + a "Going" badge in Discover; host sees
  the roster and a cancel control.

### Milestone G — Paid registration (payments)
Paid tickets, done without ever handling raw card data.
- **Provider:** **Stripe** primary (Payment Sheet on Android; backend creates a
  PaymentIntent, app confirms via Stripe's SDK, a **webhook** confirms payment →
  marks the registration paid). **Razorpay** as a regional (India) alternative with
  the same shape. Card details go **directly to the provider's SDK** — never to our
  backend or the repo (keeps us out of PCI scope). **[unverified]** — confirm each
  provider's current SDK + webhook flow before building.
- **Money rules:** refund automatically when a host cancels or an attendee cancels
  within policy; store only provider payment IDs; start in **test mode** with test
  keys, real keys via Render env / Play signing (never committed).
- **Play policy note [unverified]:** event tickets are a real-world service, which
  Google Play generally allows to be sold with an external payment method (Google
  Play Billing is for in-app *digital* content). Verify against current Play policy
  before release — this materially affects the payment design.
- **Scope boundary:** v5 collects payments to the **platform** only. Paying hosts out
  (a two-sided marketplace → Stripe Connect, KYC, tax/1099) is **v6**, not here — it
  roughly triples the compliance surface.
- **Safety note:** per account + assistant policy, I can wire the integration and test
  flows against the provider's **test** mode/cards, but I won't enter real financial
  credentials or live keys — you set those in the provider dashboard / Render env.

### Milestone H — Friends chat (+ event chat) — ✅ DONE (2026-10-09)
The "should friends be able to chat?" piece — yes, and it pairs naturally with events.

**Shipped:** backend `V7__chat.sql` (conversations / conversation_members / messages / user_blocks /
message_reports), `ChatService` + `ChatHub` (in-memory WS fan-out) + `chatRoutes` (REST send/history/
list/read/mute + WebSocket `/ws/chat` receive). 1:1 DMs between friends and per-event group chat
(registering auto-joins the event chat). **Moderation:** block (hides + stops delivery), mute (no push),
report. FCM for offline/unmuted recipients. OpenAPI + `ChatFlowTest` (all green). Android: `:feature:chat`
module — inbox, new-DM friend picker, conversation thread (live via WS + REST send), block/mute/report UI,
and a **Chat tab** in the shell. _Single-process WS fan-out; a multi-node deploy needs shared pub/sub (noted)._
- **Shape:** 1:1 DMs between friends, plus a **group chat per event** for its
  attendees (coordinating a meetup is the real use case for a "roam together" app).
- **Tech (reuses v4 infra):** Postgres for history (`conversations`,
  `conversation_members`, `messages`), the existing **WebSocket hub** for live
  delivery, **FCM** for offline push. No new transport needed.
- **Moderation (required, not optional):** block, report, and mute. A social app with
  open messaging + user-created events needs these for Play's UGC policy and basic
  safety. Consent/recording reminders don't apply (text only), but reporting/abuse
  handling does.
- **Lean option:** if v5 is getting heavy, ship **event-only group chat** first and
  defer 1:1 DMs — most of the coordination value with less surface area.

### Milestone I — Launch readiness (ship-grade hardening)
What a real app with payments + user-generated content must have before a public
rollout (see the checklist below for the full list; these are the v5-critical ones):
- **Crashlytics + product analytics** (currently neither is wired).
- **R8 / minify + resource shrinking + log stripping** (`isMinifyEnabled = false`
  today) and a tested ProGuard config.
- **Certificate pinning** app→backend (not present today; tokens are already stored in
  `EncryptedSharedPreferences` ✅).
- **Privacy policy + Play Data Safety** — now mandatory: the app handles location,
  accounts, payments, and messages.
- **Moderation surfaces** (report/block UI) and a basic takedown path for events.
- **Compose UI tests + instrumented tests in CI** — long-standing gaps.
- **String externalization + accessibility pass** before store submission.

### Suggested sequencing
E → F give a complete free-events experience and are the safe first ship. G (payments)
and H (chat) are independent of each other — do whichever adds more value first (chat
is lighter and reuses more existing infra; payments unlock the marketplace path). I
(launch readiness) runs partly in parallel and must land before any public release.
**Leanest viable v5:** E + F + event-only chat + the I items that touch payments-free
launch. Add G and 1:1 DMs when ready.

---

## Production-readiness requirements

Everything below is what "production ready" actually means. Items are tagged with
the version that most naturally delivers them. Many are **not built yet**.

### 1. Architecture & code quality
- [ ] Domain/use-case layer if logic grows beyond repositories (v3+)
- [ ] Consistent error model across layers (sealed results, not raw exceptions)
- [ ] Detekt + ktlint (or Spotless) wired into Gradle + CI
- [ ] Android Lint clean (no suppressed-without-reason warnings)
- [ ] Dependency version catalog kept current; Renovate/Dependabot (v7)
- [ ] Convention plugins cover all module types (already have android-library)

### 2. Backend & data (v3)
- [x] Ktor + PostgreSQL service; migrations (Flyway/Exposed) — done, live on Render
- [x] OpenAPI contract as the client/server seam; hand-written client — done
- [x] Server owns points ledger, check-in history — done
- [ ] Overpass proxied + cached server-side (rate limits, ToS, resilience) — still
      app-direct; move behind the backend before scale
- [x] Idempotent check-in endpoint; pagination for feed/lists — done
- [~] Data retention / deletion policy — account deletion shipped (v4); write the
      formal retention policy for the privacy doc (v5)

### 3. Security
- [x] HTTPS everywhere (Render TLS) — done. [ ] Certificate pinning app→backend — **gap (v5)**
- [x] Auth tokens in `EncryptedSharedPreferences`, never plain prefs — done
- [x] No secrets in the repo or APK; secrets via Render env (Firebase creds, etc.) — done
- [x] Input validation + rate limiting server-side — done (v3)
- [x] Server-validated check-ins (location/time) — done (v3)
- [ ] R8/ProGuard obfuscation for release; strip logs — **gap (v5)**, `isMinifyEnabled=false`
- [ ] Dependency vulnerability scanning in CI (v8)

### 4. Auth & accounts (v3)
- [x] Sign-up / sign-in (email + Google OAuth), email verification, session refresh — done
- [x] Account deletion (Play requirement if accounts exist) — done (v4 profile)
- [ ] Guest → account upgrade path (preserve local progress) — not built

### 5. Testing
- [ ] Unit tests for all ViewModels, repositories, mappers (have core coverage)
- [ ] Compose UI tests for each screen (map, rewards, permission gate) — **gap**
- [ ] Room instrumented DAO tests run in CI with an emulator — **gap** (compiles only)
- [ ] Network layer tests incl. error/timeout/malformed JSON (have query parse test)
- [ ] Backend integration + contract tests (v3)
- [ ] End-to-end / smoke test on device farm (Firebase Test Lab or similar) (v7)
- [ ] Coverage reporting (Kover) gate in CI

### 6. CI/CD & release engineering
- [ ] CI runs lint + unit tests on PR (have build + unit tests)
- [ ] Instrumented tests on an emulator in CI — **gap**
- [ ] Signed release build; upload keystore/Play signing configured (v7)
- [ ] `versionCode`/`versionName` strategy; release notes automation (v7)
- [ ] Play Console: internal → closed → open testing tracks (v7)
- [ ] Staged rollout + rollback plan (v7)

### 7. Observability
- [ ] Crash reporting (Firebase Crashlytics / Sentry) (v7)
- [ ] Structured logging with no PII; log levels; release log stripping (v7)
- [ ] Product analytics for engagement metrics (check-ins, DAU, retention) (v5–v7)
- [ ] Backend metrics/alerting (latency, error rate, Overpass failures) (v3)

### 8. Performance
- [ ] Baseline Profiles for startup + map/scroll jank (v7)
- [ ] Macrobenchmark for the map + list; frame-timing budget (v7)
- [ ] Marker clustering when POI count is high (v4/v7)
- [ ] Debounced/cancelled Overpass queries (radius query-on-release done; add cancel)
- [ ] APK/AAB size review; R8 shrinking; 16 KB alignment ✅ already done

### 9. Accessibility & i18n
- [ ] Content descriptions on all icons/controls (mostly present — audit)
- [ ] Touch target sizes ≥ 48dp; TalkBack pass on every screen (v7)
- [ ] Dynamic type / font scaling doesn't break layouts (v7)
- [ ] String externalization to `strings.xml`; at least one localization (v7)
- [ ] Colour-contrast check on the brand palette (light + dark) (v7)

### 10. Legal, privacy & Play policy
- [ ] Privacy policy (required — app uses location, accounts, payments, messaging) (v5)
- [ ] Play Data Safety form; declare location + account + payment + message data (v5)
- [ ] Terms of service + event host terms + refund policy (v5, once paid events exist)
- [ ] UGC policy + in-app report/block (Play requirement for social/messaging apps) (v5)
- [ ] Runtime location rationale UI (have a permission gate — refine copy)
- [ ] Background location ONLY if truly needed + separate consent + Play review (v4+)
- [ ] GDPR/CCPA: data export + deletion; consent for analytics (v3–v7)
- [ ] Foreground-service disclosure if location runs in a service (v4+)
- [ ] Age rating / content rating questionnaire (v7)

### 11. Location & maps specifics
- [ ] Handle permission denied / "only while using" / precise-vs-approximate (refine)
- [ ] GPS off / no-fix / mock-location handling (anti-spoofing ties to anti-cheat)
- [ ] Tile provider at scale: OpenFreeMap is fine free; evaluate SLA / self-host or
      a keyed provider (MapTiler/Stadia) for guaranteed uptime (v5+)
- [ ] Overpass at scale: MUST move behind own cached backend proxy (v3)
- [ ] Battery: sensible location update interval; stop updates when backgrounded

### 12. Offline & resilience
- [ ] Cached quests shown offline (Room cache exists — surface an offline banner)
- [ ] Retry/backoff on transient network errors
- [ ] Graceful empty/error/loading states on every screen (map has them — audit others)

### 14. Payments & commerce (v5, once paid events exist)
- [ ] Payment provider integrated (Stripe primary / Razorpay regional) via their SDK;
      raw card data never reaches our backend (stay out of PCI scope)
- [ ] Backend PaymentIntent + **webhook** confirmation; store only provider payment IDs
- [ ] Refund path (host cancels / attendee cancels within policy)
- [ ] Test mode end-to-end before go-live; live keys via Render env, never committed
- [ ] Confirm Play policy for real-world-service payments **[unverified]** before release
- [ ] Host payouts (Stripe Connect + KYC/tax) — **deferred to v6**, not v5

### 15. User-generated content, chat & safety (v5)
- [ ] Report / block / mute across chat and events (Play UGC requirement)
- [ ] Event takedown path + host cancellation handling (notify + refund registrants)
- [ ] Message history retention + deletion honoring account deletion
- [ ] Basic abuse/rate limits on messaging and event creation (anti-spam)

### 16. Release checklist (v5, pre-launch)
- [~] App icon + adaptive icon + splash screen — done as **vector** (brand compass
      logo: `ic_logo` / `ic_splash_logo`, adaptive `ic_launcher_*`, AndroidX
      SplashScreen API). Remaining: export legacy density **PNGs** for API 24–25
      (pre-adaptive devices still show the old bitmap) and a monochrome silhouette
      layer for themed icons.
- [ ] Store listing: screenshots, description, feature graphic
- [ ] Signed AAB; Play App Signing enabled
- [ ] ProGuard mapping upload for deobfuscated crash reports
- [ ] Legal: privacy policy URL, data safety, permissions declarations
- [ ] Monitoring live (Crashlytics + analytics) before staged rollout

---

## Cost & scaling notes
- **Now (through v4):** near-zero — OpenFreeMap tiles + Overpass (no keys), backend on
  Render's free tier (cold starts), Postgres free tier, FCM free. Overpass is still
  app-direct and must move behind the cached backend proxy before any real scale.
- **v5 paid events:** real money movement → payment-provider fees + compliance scope
  (refunds, receipts, tax if hosts are ever paid out). Stripe/Razorpay charge per
  transaction; no fixed cost until money flows.
- **v5 chat + events:** more rows + WebSocket connections; the free tiers hold for a
  portfolio/early-user scale, but watch Render connection limits for live chat.
- **v6+:** host payouts (Stripe Connect) add KYC/tax/1099 and a materially larger
  compliance surface.

## Suggested next actions (v5)
1. **Contract first:** extend the OpenAPI spec with `events` + `event_registrations`
   (and later chat), so app + backend proceed in parallel behind the repository
   interfaces — same approach that worked for v3/v4.
2. **Milestone E then F:** real user-created events + free registration/participation,
   tying events into the existing check-in/points loop. This is the safe first ship.
3. **Pick the next lever:** chat (lighter, reuses v4 WS+FCM) or paid registration
   (unlocks the marketplace path) — they're independent.
4. **Launch readiness in parallel:** wire Crashlytics + analytics, turn on R8 + log
   stripping, add cert pinning, and draft the privacy policy / Data Safety entries —
   all needed before any public rollout with payments + messaging.
