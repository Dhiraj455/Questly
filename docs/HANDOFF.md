# Questly — Project Handoff / Context

Paste this into a new chat to continue work. It captures the goal, decisions,
architecture, current state, known issues, and the next planned cycle.

## 1. What this is & why

**Questly** — a live, location-based challenge & rewards Android app. Users see
challenges/quests pinned to real places nearby, check in (geofenced), earn points,
and (later) climb a real-time leaderboard.

**Why it exists:** portfolio project targeting an **Android Software Engineer role
at Fetch** (rewards/gameplay app). It deliberately exercises Fetch's stack:
Kotlin, Jetpack Compose, MVVM + Hilt, Room, Coroutines/Flow, multi-module,
convention plugins, testing, CI. Intent is a **real, shippable product** (not a toy),
built **at zero cost / no API keys / no billing accounts**.

## 2. Where it lives

- Path: `C:\Users\dshelke\AndroidStudioProjects\Questly`
- Git: local repo, branch `main`, ~15 commits. No remote yet.
- Design spec: `docs/superpowers/specs/2026-09-02-questly-v1-design.md`
- v1 plan: `docs/superpowers/plans/2026-09-02-questly-v1.md`
- Windows 11, Android Studio, PowerShell. Build via `.\gradlew.bat`.

## 3. Tech stack (bleeding-edge 2026 versions — these gotchas are already solved)

- Kotlin 2.2.10, AGP 9.2.1, Gradle 9.4.1, Compose BOM 2026.02.01
- **compileSdk = 37** (required by androidx libs; targetSdk 36, minSdk 24)
- Hilt **2.60.1** (2.52 failed: AGP 9 removed `BaseExtension`)
- Room 2.7.1 + KSP; Coroutines 1.9.0
- MapLibre SDK **11.13.5** + annotation plugin 3.0.2 (16 KB-aligned native libs)
- MapLibre style: **OpenFreeMap** (`https://tiles.openfreemap.org/styles/liberty`) —
  free, no key. (OSM's own tile servers 403-block app traffic — do NOT use them.)
- material-icons-extended, navigation not used (2-tab Int state)
- Tests: JUnit + Turbine + coroutines-test. CI: GitHub Actions (`.github/workflows/ci.yml`).

### AGP 9 / toolchain fixes already applied (don't re-hit these)
- `android.disallowKotlinSourceSets=false` in `gradle.properties` (KSP + built-in Kotlin)
- Convention plugin uses **new DSL** `com.android.build.api.dsl.LibraryExtension`
- AGP 9 auto-applies Kotlin for android modules — do NOT apply `org.jetbrains.kotlin.android`
- graphics-path bumped to 1.1.0 for 16 KB alignment

## 4. Architecture (multi-module, MVVM + Hilt, unidirectional)

```
:app                — Application (@HiltAndroidApp), MainActivity, nav (2-tab), permission gate, theme
:core:model         — Checkpoint, CheckIn, CheckpointKind; distanceMeters() haversine
:core:database      — Room: CheckpointEntity, CheckInEntity, DAOs, QuestlyDatabase (KSP)
:core:data          — CheckpointRepository / CheckInRepository (interfaces = the backend seam),
                      Local*Repository impls, SeedData, Hilt DataModule, CheckInResult, check-in rules
:core:location      — LocationProvider (interface) + FusedLocationProviderImpl (Flow<UserLocation?>)
:feature:map        — MapViewModel, MapUiState (CheckpointUi w/ distanceMeters + FocusTarget),
                      MapScreen (BottomSheetScaffold + MapLibre map), MapLibreMap composable
:feature:checkin    — PointsViewModel, PointsScreen (hero card + history)
build-logic         — `questly.android.library` convention plugin
```

Convention plugin id: `questly.android.library` (sets compileSdk 37, minSdk 24, Java 11).

## 5. Core behavior / logic

- **Loop:** discover checkpoints on map → geofenced (foreground distance) check-in → earn points.
- **Check-in rules** (`LocalCheckInRepository`, unit-tested): within `radiusMeters`,
  1-hour cooldown per checkpoint, points = sum of check-ins joined to checkpoints
  (single source of truth, no mutable balance). Returns `CheckInResult`
  (Success/TooFar/OnCooldown/UnknownCheckpoint).
- **Proximity** computed in `MapViewModel` (foreground distance, NOT the background
  Geofencing API — deliberate ponytail simplification).
- **Location:** `FusedLocationProviderImpl` emits `Flow<UserLocation?>`; permission
  gated by `LocationPermissionGate` in :app.
- **Seeding** is authoritative: `ensureSeeded()` deletes all checkpoints then inserts.

## 6. Current data state (IMPORTANT)

- **Only ONE seed checkpoint:** "Bollywood Beach Party" @ North Avenue Beach,
  Chicago (41.9109, -87.6267), EVENT, 100 pts, radius 150 m.
- All previous seeds (London parks, near-me demo generation) were **removed** by request.
- To test: set emulator location to Chicago: `adb emu geo fix -87.6267 41.9109`
  (longitude latitude order), then grant location — marker reads "You're here" → check in.

## 7. UI (Material 3, branded)

- **Theme:** teal (exploration) + amber (rewards) palette, light+dark, dynamic color OFF.
- **Explore tab:** full-screen MapLibre map; floating "Questly · N nearby" pill;
  **BottomSheetScaffold** peek shows only the "Nearby challenges" header, drag up for the
  distance-sorted list (each row: name, distance, points, Check-in button; row tap flies
  map to it). Tapping a **red marker** shows a detail card (title, desc, points/range
  chips, check-in, close).
- **Rewards tab:** hero points card (total + trophy), styled check-in history with
  relative time, empty state.
- Bottom nav: Explore (compass) / Rewards (trophy), filled/outlined states.

## 8. What's DONE

- Full v1 (9-task plan) built, all unit tests green, app assembles & runs on emulator.
- Real MapLibre map with OpenFreeMap tiles + markers, verified 16 KB-aligned.
- UI polish + brand theme.
- Bottom-sheet discovery list + marker detail card + single Chicago seed.

## 9. Known issues / not done

- **UI bug:** bottom-sheet peek's first row is half-hidden behind the bottom nav bar
  (needs peek height + nav-bar inset fix).
- Only 1 hardcoded seed — no real place data; map is empty unless emulator is in Chicago.
- No radius/distance control.
- Room DAO instrumented test needs an emulator to run (compiles; not run in CI).
- No backend, no real-time leaderboard, no auth, no AI, no real events — all deferred.
- Some `.idea/` files are tracked (harmless).

## 10. NEXT CYCLE (agreed direction — not yet built)

**Real-time, location-based quests from real places, with a distance control.**

- Replace seed data with a **live places feed via OpenStreetMap Overpass API**
  (free, no key — fits zero-cost rule). Query e.g. `leisure=park`, `natural=beach`,
  landmarks within a radius of the user; map each POI → a quest/checkpoint.
- Add a **radius selector** (chips/slider: 1 / 5 / 20 km) — the missing "increase
  distance to see quests" control. Query + list use it.
- Flow: location → Overpass query in radius → POIs → quests (points by type/distance)
  → cache in Room → markers + distance-sorted list. Re-query on big location change
  or radius change.
- Slots behind the existing `CheckpointRepository` interface (a remote impl) — UI unchanged.
- **Trade-offs:** Overpass is rate-limited/slow (cache results; a backend can proxy
  later). Need rules for which POI types count and points per type.
- Also fix the sheet/nav-bar overlap.

**Open design decisions for next cycle:**
- Which POI types become quests, and points per type/distance?
- Default + max radius; how many quests to show.
- When to re-query (distance moved threshold? manual refresh? on radius change?).
- Google Places (richer, needs billing) vs Overpass (free) — currently Overpass.

## 11. Working style / conventions

- Uses **Superpowers skills** (brainstorming → writing-plans → executing-plans) and
  **Ponytail** (laziest solution that works; deliberate simplifications marked with
  `ponytail:` comments naming the ceiling + upgrade path).
- Repository interfaces are the seam for a future Ktor backend (client/backend split
  planned via an OpenAPI contract; not started).
- Commit per task; tests before claiming done.
