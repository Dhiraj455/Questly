# Questly v1 — Design Spec

**Date:** 2026-09-02
**Status:** Approved for planning
**Scope:** v1 walking skeleton only. Backend, real-time, AI, and real partner data are explicitly deferred to later cycles.

## Goal

A live, location-based challenge & rewards app. Long-term it's a real, shippable
product with a Ktor backend, real-time leaderboard, AI-generated challenges, and
partner-funded rewards. **v1 proves the core loop end-to-end on a single device,
at zero cost, with no backend.**

## v1 Core Loop

Open app → see nearby challenges/events on a map → walk to one → geofenced
check-in → earn points → see your points total & check-in history.

Everything runs on-device. Data is seeded (dummy events + real park coordinates).

## Constraints

- **Zero cost, no card, no accounts.** Rules out Google Maps (requires a billing
  account) → use **MapLibre + OpenStreetMap** (no key, no card).
- **No backend in v1.** Local-first; a repository interface is the seam a real
  backend replaces later without touching UI/ViewModels.
- **Ponytail:** simplest code that works. No speculative abstractions beyond the
  two seams we KNOW we'll swap (reward source, data source).

## Architecture

MVVM + Hilt, unidirectional data flow, multi-module. ViewModels expose
`StateFlow<UiState>`; Compose observes.

### Modules (kept minimal for v1)

| Module | Responsibility |
|---|---|
| `:app` | Application, DI setup, navigation, MainActivity |
| `:core:model` | Plain Kotlin domain models (`Checkpoint`, `CheckIn`, `Points`) |
| `:core:database` | Room: entities, DAOs, DB (KSP) |
| `:core:data` | Repository interfaces + local implementations, seed data |
| `:core:location` | FusedLocation wrapper + foreground proximity check |
| `:feature:map` | Map screen (MapLibre) + ViewModel: nearby checkpoints |
| `:feature:checkin` | Check-in action + points/history screen + ViewModel |

`ponytail: 7 modules is already more than v1 strictly needs, but it's the whole
point of the exercise (demonstrate modularization). Not adding more until a
feature forces it.`

### Data model (v1)

- **Checkpoint** — a place you can check into. One type now; `kind` field
  (CHALLENGE | EVENT) so events slot in later with no schema change. Fields:
  id, title, description, lat, lng, radiusMeters, points, kind.
- **CheckIn** — id, checkpointId, timestamp. Points derived by summing check-ins
  joined to checkpoints (single source of truth; no separate mutable balance).

### The two deliberate seams

1. **`CheckpointRepository` / `CheckInRepository`** interfaces in `:core:data`.
   v1 impl reads Room + seed data. Later: a remote impl hitting Ktor.
2. **Reward model** — points are just an `Int` sum for now. Redemption is a
   stub. The `kind`/points fields leave room for partner-paid / gift-card /
   sponsored sources later. No reward-source interface built yet (YAGNI until a
   second source actually exists).

## Data flow (check-in)

1. `:core:location` emits current location (Flow). Proximity = distance from
   current location to each checkpoint, computed in the ViewModel.
   `ponytail: foreground distance check, not the background Geofencing API.
   Upgrade to real geofences when background/offline check-ins are needed.`
2. `feature:map` ViewModel combines location + checkpoints → renders pins +
   "check in" enabled only when inside a checkpoint's radius.
3. User taps check in → `CheckInRepository.recordCheckIn(checkpointId)` (validates
   distance locally, writes Room row).
4. Points/history screen observes the check-ins Flow → shows total + list.

## Error / edge handling (v1 scope)

- Location permission denied → screen explains and links to settings; map still
  loads, check-in disabled.
- Location unavailable / GPS off → banner, check-in disabled.
- Check-in attempted outside radius → rejected in repository (defense in depth,
  even though UI gates it).
- Duplicate check-in (same checkpoint within cooldown) → repository rejects.

## Testing

- Unit: repository check-in validation (inside/outside radius, duplicate
  cooldown), points summation. `runTest` + Turbine for Flows.
- Compose UI: points screen renders total + history; check-in button
  enabled/disabled by proximity state.
- CI: GitHub Actions — assemble + unit tests on push (free).

## Explicitly deferred (later cycles, each its own spec)

- Ktor backend + Postgres, OpenAPI contract
- Real-time WebSocket leaderboard
- Multi-user auth + FCM push
- AI-generated / personalized challenges
- Real events & partner-funded rewards, redemption
- Anti-fraud server-side check-in validation
