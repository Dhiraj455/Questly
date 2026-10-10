# Questly — Manual Test Plan

_Last updated: 2026-10-10. Covers the whole app through v5 Milestones E/F/H._

Automated coverage already exists (backend `./gradlew test` is green; an API scenario sweep passed
31/31). This document is the **manual, on-device** pass: tap through every feature and confirm the
expected result. Check each box as you go.

---

## 0. Setup (do this first)

### 0.1 Run the v5 backend locally
The v5 endpoints (events, registration, chat) only exist in **this** backend build — the version on
Render is older. So point the app at a local backend.

1. Start Docker Desktop.
2. Start Postgres: `cd backend && docker compose up -d` (container `questly-postgres` on host port 5433).
3. Start the server on **port 8081** (the app's default; note host port 8080 is taken by Apache on this machine):
   ```bash
   cd backend && PORT=8081 JWT_SECRET=local-dev-secret AUTH_RATE_LIMIT_PER_MINUTE=1000 ./gradlew run
   ```
   (Add `GOOGLE_WEB_CLIENT_ID=<your web client id>` to that line if you'll test Google Sign-In, and
   `FIREBASE_CREDENTIALS=<service-account JSON>` if you'll test push.)
4. Confirm it's up: open `http://localhost:8081/health` → `{"status":"ok",...}`. Migrations V1–V7 apply automatically.

- [ ] Backend healthy on :8081

### 0.2 Point the app at the backend
- **Emulator:** nothing to do — the app defaults to `http://10.0.2.2:8081/v1` (emulator's alias for host :8081).
- **Physical device:** in `local.properties` set `QUESTLY_API_BASE_URL=http://<your-PC-LAN-IP>:8081/v1`, and make sure the phone is on the same Wi‑Fi. Rebuild the app.
- Make sure `local.properties` is **not** pointing at Render for this pass.

- [ ] App build installed and pointing at the local backend

### 0.3 Two accounts / two devices
Friends, chat, and registration are multi-user. Use **two emulators** (or one emulator + one phone),
each signed into a different account (call them **A** and **B**). A third account **C** helps test
"stranger/non-friend" cases.

### 0.4 Creating accounts (email verification without a mail server)
The dev backend logs the verification token instead of emailing it.
1. In the app: **Create account** (display name + email + password ≥ 8 chars) → banner "check your inbox to verify".
2. In the **backend console**, find the line: `[DEV EMAIL] Verify <email> -> ... token=<TOKEN>`.
3. Verify by opening in a browser on your PC: `http://localhost:8081/v1/auth/verify-email?token=<TOKEN>` → 200.
4. Back in the app: **Sign in** with the same email/password.

_Alternative:_ **Continue with Google** (no verification step) if you configured `GOOGLE_WEB_CLIENT_ID`
on both the app build and the backend.

- [ ] Account A created + verified + signed in
- [ ] Account B created + verified + signed in
- [ ] (optional) Account C

---

## 1. Auth & session
- [ ] **Sign-up validation**: Create-account button stays disabled until email looks valid, password ≥ 8, name non-empty.
- [ ] **Unverified login blocked**: register, then try to sign in *before* verifying → error banner ("…email is verified").
- [ ] **Verify + login** works (per 0.4).
- [ ] **Wrong password** → error banner, no sign-in.
- [ ] **Show/hide password** toggle works.
- [ ] **Google Sign-In** (if configured): completes and lands on the app; cancelling the Google sheet shows no error.
- [ ] **Session persists**: kill and reopen the app → still signed in (no re-login).
- [ ] **Sign out** (Profile → sign out) → returns to the auth screen.
- [ ] **Notification permission** (Android 13+): prompted once after first sign-in.

## 2. Explore / Map & check-in (core loop)
- [ ] **Location permission gate**: first open of Explore asks for location; granting shows the map at your location.
- [ ] **Checkpoints load**: nearby parks/beaches/viewpoints/landmarks appear as markers (from OpenStreetMap).
- [ ] **Radius slider** changes how far out checkpoints are fetched; list/markers update.
- [ ] **Check-in when close**: at/near a checkpoint (within 150 m), the check-in control is enabled; checking in succeeds and awards points.
- [ ] **Too far**: a distant checkpoint rejects check-in ("need to be closer").
- [ ] **Cooldown**: immediately checking in again at the same checkpoint is blocked (1-hour cooldown).
  - _Emulator tip:_ set a mock location (Extended controls → Location) near a real OSM POI to exercise distance rules.

## 3. Profile, points & achievements
- [ ] **Points total** on Rewards/Profile increases after a check-in.
- [ ] **Streak** reflects consecutive-day check-ins (current/longest).
- [ ] **Achievements** show progress and flip to earned at their thresholds.
- [ ] **Friend code** is visible on Profile and is shareable.

## 4. Friends & activity feed
- [ ] **A gets B's code** (or vice-versa) and **sends a friend request** by code.
- [ ] Invalid code → clear error.
- [ ] Sending your own code → rejected ("that's your own code").
- [ ] **B sees the incoming request** and **accepts** → both now list each other as friends.
- [ ] **Decline** path also works (use a throwaway request).
- [ ] **Unfriend** removes the friendship on both sides.
- [ ] **Activity feed** shows your and your friends' recent check-ins; pull-to-refresh updates it.

## 5. Leaderboard (realtime)
- [ ] Ranks tab shows users ordered by points.
- [ ] With the app open on A, have B check in → A's leaderboard updates **live** (WebSocket), no manual refresh.

## 6. Discover & events (Milestone E)
- [ ] **Discover lists events** near you in "Starting soon / Within radius / Nearby" sections with date + distance.
- [ ] **Radius filter** (Tune icon) re-buckets the sections.
- [ ] **Search** filters by title/venue.
- [ ] **Create event** (＋ FAB): fill title, category, venue, pick date/time, set location (defaults to current), capacity (optional), visibility, registration type; **Publish**. New event appears in Discover and in **My events**.
- [ ] **Save as draft** (toggle off Publish) → shows in My events as **Draft**, not in public Discover.
- [ ] **Event detail**: shows host, date/time, venue, capacity line, description, visibility chip, **Open in Maps** (launches a maps app at the location).
- [ ] **Visibility**:
  - PUBLIC event visible to B in Discover.
  - FRIENDS event visible to B **only if A and B are friends**.
  - PRIVATE event **not** visible to B (and B opening its id → "no such event").
- [ ] **Edit** (host): change title/details → saved and reflected.
- [ ] **Cancel** (host): event marked Cancelled; drops out of public Discover; registrants are notified (push, if enabled).
- [ ] Non-host does **not** see Edit/Cancel on someone else's event.

## 7. Registration & participation (Milestone F)
- [ ] **Free event — Register**: B opens A's free event → "Register — free" → becomes **Going**; detail shows "1 going"; a **Going** badge appears on the Discover card.
- [ ] **Leave**: B leaves → count drops, badge clears.
- [ ] **Capacity + waitlist**: A creates an event with capacity 1. B registers (Going). C registers → **Waitlisted** (button reads "Join the waitlist"; status shows waitlist).
- [ ] **Auto-promote**: B leaves → C is **auto-promoted** to Going (re-open the event to see it).
- [ ] **Host roster**: on the host's own event detail → **View attendees (N)** lists Going / Waitlist / Attended.
- [ ] **No registration**: a NONE-type event shows "Just show up — no registration needed" (no join button).
- [ ] **Paid event**: tapping "Get ticket …" shows "Paid tickets are coming in a future update" (Milestone G not built).
- [ ] **Attended tie-in**: register for an event whose location you can reach, then **check in there** (Explore) using the event as the checkpoint → your roster status becomes **Attended**. _(Requires the event's coordinates to match a place you can check in at; may need a mock location.)_

## 8. Chat & moderation (Milestone H)
- [ ] **New DM**: on the **Chat** tab, ＋ → pick a friend (B) → opens a conversation. (Only friends are listed.)
- [ ] **Send/receive**: A sends a message; **B receives it live** (Chat open on B) without refresh.
- [ ] **Inbox**: B's Chat inbox shows the conversation with an **unread badge**; opening it clears the unread.
- [ ] **Non-friend DM blocked**: there's no way to DM a non-friend; (API returns "you can only message friends").
- [ ] **Event group chat**: B registers for A's event → an **event conversation appears in B's inbox**; A (host) posts a message → B sees it. Group messages show the **sender's name**.
- [ ] **Stranger excluded**: C (not registered, not host) cannot access that event's chat.
- [ ] **Mute**: in a conversation → ⋮ → **Mute** → you stop getting push for it (unmute restores).
- [ ] **Block** (DM): ⋮ → **Block** the other person → their messages disappear from your view and new ones don't arrive; **Unblock** restores future messages.
- [ ] **Report**: long-press a message you didn't send → **Report message** → confirmation.
- [ ] **Reconnect**: toggle airplane mode on and off → the chat reconnects and new messages flow again.

## 9. Notifications (FCM) — optional (needs google-services.json + backend FIREBASE_CREDENTIALS + 2 devices)
- [ ] **Friend check-in**: B checks in → A gets a push.
- [ ] **New registration**: B registers for A's event → A (host) gets a push; A's friends get "a friend is going".
- [ ] **New message**: A messages B while B is backgrounded → B gets a push (unless B muted the conversation).
- [ ] **Event cancelled**: A cancels an event B registered for → B gets a push.
- [ ] _Not implemented yet:_ "event starts soon" reminder (needs a scheduler — don't flag as a bug).

## 10. Branding & app lifecycle
- [ ] **Splash screen** shows the Questly compass logo on launch (light and dark).
- [ ] **App icon**: the launcher icon is the compass mark (note: pre-Android 8 devices still show the old icon — legacy PNGs are a known to-do).
- [ ] **Dark mode**: toggle system dark theme → the app's colors adapt.
- [ ] **Crash recovery**: reinstall the app over an existing install → it launches to sign-in (no crash) — the encrypted-token self-heal from earlier.
- [ ] **Delete account** (Profile) → account removed; app returns to sign-in; signing in again fails (account gone).

## 11. Edge cases & resilience
- [ ] **Backend down**: stop the server, do an action → friendly error (e.g. "can't reach the server"), no crash.
- [ ] **Cold start / server waking**: first call after idle may be slow; app shows loading, then recovers.
- [ ] **Token refresh**: stay signed in long enough for the access token to expire, then act → it refreshes silently (no forced logout). _(Hard to force manually; low priority.)_
- [ ] **Rotation**: rotate the device on each main screen → no crash, state preserved.

---

## What is NOT built yet (expected gaps — don't log as bugs)
- **Paid registration** (Stripe/Razorpay) — Milestone G.
- **"Event starts soon"** push — needs a scheduler.
- **Launch hardening** (Milestone I): Crashlytics, analytics, R8/minify, certificate pinning, privacy
  policy + Play Data Safety, legacy launcher PNGs + monochrome themed icon.
- **Embedded map** inside event detail (currently a location card + "Open in Maps").
- **Multi-node chat** (WebSocket fan-out is single-process for now).
