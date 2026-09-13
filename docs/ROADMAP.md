# Questly — Version Roadmap & Production-Readiness

_Last updated: 2026-09-06_

This document lists every planned version, what each contains, and everything
required to take Questly from a portfolio project to a **production-ready,
shippable app**. Companion docs: `docs/HANDOFF.md` (context), the specs/plans
under `docs/superpowers/`.

---

## Current status

- **v1 — DONE & committed.** Local walking skeleton (discover → geofenced
  check-in → points), Room, MVVM+Hilt, multi-module, tests, CI, brand UI.
- **v2 — BUILT & GREEN, but UNCOMMITTED.** Real-world quests via Overpass
  (parks/beaches/viewpoints/landmarks), radius slider, type filter, refresh,
  loading/error states, recenter FAB. All unit tests pass, app assembles.

### What remains in v2 before it's "closed"
1. **Commit the work** (currently all working-tree changes).
2. **Fix check-in history naming (bug):** `CheckIn` stores only `checkpointId`;
   Overpass checkpoints are an ephemeral cache (wiped on refresh), so the Rewards
   history can't resolve a title and shows raw IDs (`node/123…`). Snapshot the
   quest **title** (and points) onto the `CheckIn` row at check-in time.
3. **On-device verification:** real Overpass results render, radius re-queries on
   release, type filter works, check-in succeeds (set emulator to a real place).
4. **Minor polish:** map markers are one uniform red dot; optionally vary marker
   colour/icon by category (list already shows per-type icons).
5. **Run the Room DAO instrumented test** on an emulator (compiles; not yet run).

---

## Version roadmap

### v1 ✅ Walking skeleton (done)
Local, single-device proof of the core loop.
- MapLibre map, foreground geofenced check-in, points ledger (derived sum)
- Room, MVVM + Hilt, multi-module, coroutines/Flow, unit tests, GitHub Actions CI
- Brand theme, bottom sheet, detail card

### v2 🔨 Real-world quests (built, uncommitted)
Real places become quests, live, by location & distance.
- Overpass API POIs (free, no key), radius control 1–20 km, distance sort
- Refresh-on-move + manual refresh, type filter, loading/error, recenter
- Points by POI type; checkpoints cached in Room

### v3 🎯 Accounts & backend (biggest architectural step)
Progress lives on a server, not one device.
- **Ktor + PostgreSQL** backend; OpenAPI contract; app talks REST
- **Auth / accounts** (email/OAuth); points ledger + check-in history server-side
- **Server-validated check-ins** (anti-fraud: verify location + time server-side)
- FCM push plumbing; sync across devices
- Own Overpass **proxy + cache** on the backend (removes client rate-limit risk)

### v4 Social & competition (real-time)
- **Real-time leaderboard over WebSocket** (global / local / friends)
- Streaks, badges, achievements; friends, sharing, activity feed
- Push notifications (nearby quests, friend activity)

### v5 Events & partner rewards (two-sided marketplace)
- Real **events** (concerts, festivals, club nights) as quest types
- **Partner venues/businesses**: offers as rewards; **sponsored challenges**
- **Redemption** (points → gift cards / perks); **attribution tracking**
- Optional partner/merchant portal

### v6 AI personalization (the "AI-forward" differentiator)
- AI-generated / personalized challenges from history + location + time
- "Quests you'll like nearby" recommendations; natural-language quest search

### v7 Production hardening & scale
- Offline-first sync, baseline profiles, R8, crash reporting
- Engagement/analytics dashboards, accessibility, localization, app-store polish

> **Portfolio note:** through **v4** already demonstrates almost the entire Fetch
> JD (Compose, Hilt, Room, real backend, real-time, geospatial, testing,
> AI-adjacent). v5–v6 make it a real product rather than a strong sample.

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
- [ ] Ktor + PostgreSQL service; migrations (Flyway/Exposed)
- [ ] OpenAPI contract as the client/server seam; generated or hand-written client
- [ ] Server owns points ledger, check-in history, quest catalog
- [ ] Overpass proxied + cached server-side (rate limits, ToS, resilience)
- [ ] Idempotent check-in endpoint; conflict handling; pagination for lists
- [ ] Data retention / deletion policy (ties to privacy)

### 3. Security
- [ ] HTTPS everywhere; certificate pinning for the app→backend API (v3)
- [ ] Auth tokens in `EncryptedSharedPreferences`/Keystore, never plain prefs (v3)
- [ ] No secrets in the repo or APK; secrets via CI env / Play signing (now/v3)
- [ ] Input validation + rate limiting server-side (v3)
- [ ] Anti-cheat: server-validated location/time, plausibility checks (v3)
- [ ] R8/ProGuard obfuscation for release; strip logs (v7)
- [ ] Dependency vulnerability scanning in CI (v7)

### 4. Auth & accounts (v3)
- [ ] Sign-up / sign-in (email + OAuth), password reset, session refresh
- [ ] Account deletion (Play requirement if accounts exist)
- [ ] Guest → account upgrade path (preserve local progress)

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
- [ ] Privacy policy (required — app uses location) (v3/v7)
- [ ] Play Data Safety form; declare location + account data (v7)
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

### 13. Release checklist (v7, pre-launch)
- [ ] App icon + adaptive icon finalized; splash screen
- [ ] Store listing: screenshots, description, feature graphic
- [ ] Signed AAB; Play App Signing enabled
- [ ] ProGuard mapping upload for deobfuscated crash reports
- [ ] Legal: privacy policy URL, data safety, permissions declarations
- [ ] Monitoring live (Crashlytics + analytics) before staged rollout

---

## Cost & scaling notes
- **Now (v1–v2):** zero cost — OpenFreeMap tiles + Overpass, no keys, no backend.
- **v3+:** a backend introduces hosting cost (a small VPS / managed Postgres free
  tiers exist). Overpass must be proxied+cached to avoid client-side rate limits
  and respect its usage policy.
- **v5+:** partner rewards imply real money movement → payment/compliance scope.

## Suggested next actions
1. Commit v2, then fix the check-in-history naming bug (small, high-value).
2. Add Compose UI tests + wire instrumented tests into CI (closes big testing gaps).
3. Start v3 (backend) with an OpenAPI contract first, so client & server proceed
   in parallel behind the existing repository interfaces.
