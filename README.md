# Anchor

A behaviour intervention system for Android. Anchor watches your day, identifies idle and avoidance windows, and interrupts you with a decision point: start a focus session, delay with friction, or skip with a stated reason. The goal is asymmetric friction — starting is easy, avoiding is costly.

A work thought of by Joe, made in relation with Jele Sphere.

---

## Architecture

Anchor is a Kotlin Multiplatform project with a single shared module containing all business logic and UI, and thin platform entry points for Android and iOS.

```
shared/
  src/commonMain/   — business logic, Compose UI, domain models, repositories, view models
  src/androidMain/  — Android platform implementations (data stores, services, notifications)
  src/iosMain/      — iOS platform implementations (NSUserDefaults, UNUserNotifications)
androidApp/         — Android manifest, entry point, accessibility service, foreground services
iosApp/             — iOS entry point (SwiftUI shell wrapping Compose)
```

The architectural layers within `commonMain`:

```
domain/model/       — pure data classes: Action, Session, Trigger, Goal, Sensitivity, etc.
domain/repository/  — repository interfaces (no Android/iOS types)
domain/usecase/     — business logic: StartSession, CompleteSession, RecordSkip, RecordSnooze, etc.
data/repository/    — in-memory implementations + TimeBasedTriggerSource
presentation/       — ViewModels and Compose screens
di/                 — Koin module wiring
platform/           — expect/actual declarations for permissions, notifications, overlay
```

Data flow through every feature: `UI event -> ViewModel -> UseCase -> Repository (StateFlow) -> ViewModel -> UI`

---

## Full System Flow

### Session Lifecycle

1. The user taps "Start" on a focus action (from Home or the Intervention screen).
2. `StartSession` use case creates a `Session` with `outcome = InProgress`, stores it in `SessionRepository`, and logs `SESSION_STARTED` to history.
3. `SessionViewModel` runs a coroutine ticker updating `elapsedSeconds` every second.
4. The Android `SessionTimerService` (foreground service) keeps the process alive and shows a persistent "Session in progress" notification. It is started when `SessionScreen` is composed and stopped when the session ends.
5. The Complete button becomes enabled when `elapsedSeconds >= minDurationMinutes * 60`. If `minDurationMinutes` is 0, it is enabled immediately.
6. On complete: `CompleteSession` sets `completedAt` and `outcome = Completed`, logs `SESSION_COMPLETED`, updates the daily streak.
7. On Android back press during a session: a confirmation dialog is shown. If confirmed, `AbandonSession` sets `outcome = Abandoned`, logs `SESSION_ABANDONED`, resets the streak. Abandoned sessions are excluded from follow-through rate and daily progress counts.
8. After completion, the reward screen shows the elapsed duration formatted as hours and minutes (e.g., "Focused for 1h 23m").

### Trigger System

`TimeBasedTriggerSource` emits `Trigger` objects that drive the intervention flow.

Cold start (fewer than 5 completed sessions): uses fixed empirical drift windows — 08:00-10:00, 12:00-14:00, 18:00-21:00.

Warm state (5 or more completed sessions): builds an hourly histogram of when sessions have been completed. For each hour, confidence = `1 - (sessionsAtHour / maxSessionsAtAnyHour)`. A high count at an hour means low confidence (the user is already active there); a low count means high confidence the hour is a drift window.

The sensitivity setting controls the confidence threshold:

- Low: 0.8 — only fires for very strong drift signals
- Medium: 0.6 — balanced
- High: 0.4 — fires more readily

At most one trigger fires per hour per day. Day boundaries reset at midnight so triggers can fire again each new calendar day.

When a trigger fires:
- `InterventionLevel.Low`: a notification is sent
- `InterventionLevel.Medium` or `High`: the overlay launches `InterventionScreen` directly

### Intervention Screen

When the screen opens the user sees:
- A gold "Start [action]" button for each configured focus action — single tap, no friction.
- "Delay once" — opens a hold-to-confirm panel. The user must hold a button continuously for 15 seconds. On completion, `RecordSnooze` logs the event and the screen closes.
- "Skip with reason" — opens a text field requiring a minimum number of characters (10 for Medium sensitivity, 20 for High, 1 for Low). On confirm, `RecordSkip` logs the reason and closes the screen.

If no focus actions have been configured, the screen shows a prompt to go to Settings.

### Lock Mode (Android)

Lock Mode adds a blocking layer on top of the trigger system. The user selects specific apps to block via `AppSelectionScreen`.

`AnchorAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED` accessibility events. On each event it checks:
1. Lock Mode is on.
2. The foreground package is in the blocked list.
3. Daily usage of that package is at or above the configured minute limit.

`blockAfterMinutes = 0` means "Always" — the app is blocked on every open regardless of time spent.

When all three conditions are met, `Interrupter.interrupt()` fires the intervention overlay.

`UsageTimerService` is a foreground service that holds a `PARTIAL_WAKE_LOCK` and queries `UsageStatsManager` via `queryEvents` (real-time events, not the cached `queryUsageStats` which has a 30-second delay) to get accurate per-app foreground time for the current calendar day. Usage is tracked in milliseconds throughout to prevent integer-division precision loss.

The Settings screen shows a warning when Lock Mode is on but the Accessibility Service is not enabled, with a direct link to Android Accessibility Settings. Lock Mode cannot function without both toggles active.

Blocked app list is viewable directly from the Settings screen without navigating away — a "View" button opens a dialog listing all blocked apps by name and package identifier, with a "Manage" action to open the full selection screen.

### Streak and Follow-Through Rate

The follow-through rate is the ratio of completed sessions to total sessions started (including abandoned). It is computed across all sessions in the repository.

The streak counts consecutive calendar days on which at least one session was completed. The streak resets to 0 when a Goal deadline is missed, and `STREAK_RESET` is logged to history.

All duration calculations use `completedAt - startedAt` in whole seconds. Abandoned sessions are excluded from every positive metric but are visible in the history log as penalty entries.

### Goals

One goal can be active at a time. A goal has a target session count and a duration in days.

Lifecycle:
1. On creation, `startedAt` is recorded and `status = Active`.
2. Each completed session increments `sessionsCompleted`. If the target is reached before the deadline, `status = Completed`, `completedAt` is set, and 1 Exemption Card is awarded.
3. On each ViewModel state emission, if the current date is past `startedAt + durationDays` and the target was not met, the goal transitions to `status = Failed`, the streak resets, and `STREAK_RESET` is logged.
4. Goals cannot be deleted once created.

An Exemption Card lets the user skip one intervention without any penalty. The skipped session still counts toward daily progress and streak as if it were completed.

### Export and Import (HMAC-SHA-256 Signed)

`AnchorExport` is a versioned container (current: v2) holding: actions, goals, sessions, history logs, and settings snapshot. Serialized to JSON via `kotlinx.serialization`.

Signing on export:
1. Serialize the export with `signature = ""`.
2. Canonicalize JSON (deterministic key ordering).
3. Compute HMAC-SHA-256 of the canonical string using a fixed app key stored as a hex constant in `AnchorSigner`.
4. Re-serialize with the signature embedded.

Verification on import:
1. Deserialize the incoming JSON.
2. Re-serialize with `signature = ""` to reproduce the canonical form.
3. Recompute HMAC-SHA-256 and compare byte-by-byte against the embedded signature.
4. Reject if they do not match.

Legacy v1 exports (empty signature) are accepted without verification for backward compatibility.

### Reminders

`ReminderScheduler.start()` is called from `AnchorApp.onCreate()` and from `BootReceiver.onReceive()` (device restart).

Each reminder is a one-shot `AlarmManager` exact alarm. When the alarm fires:
1. `ReminderReceiver.onReceive()` calls `Interrupter.interrupt(forceNotification = true)`.
2. Immediately after, it reschedules the alarm for the same clock time the next day.

This one-shot-then-reschedule pattern keeps alarms active indefinitely. `BootReceiver` ensures they survive device reboots (Android cancels all alarms on restart).

---

## Domain Models

| Model | Key Fields |
|---|---|
| `Session` | id, actionId, startedAt, completedAt?, outcome: InProgress / Completed / Abandoned |
| `Action` | id, name, icon, isCustom |
| `Trigger` | id, predictedAt, confidence, reason |
| `Goal` | id, title, targetSessions, durationDays, startedAt, status, sessionsCompleted, completedAt? |
| `GoalStatus` | Active, Completed, Failed |
| `Sensitivity` | Low (0.8), Medium (0.6), High (0.4) |
| `InterventionLevel` | Low, Medium, High |
| `HistoryLog` | id, timestamp, type: LogType, note? |
| `LogType` | SESSION_STARTED, SESSION_COMPLETED, SESSION_ABANDONED, STREAK_RESET, EXEMPTION_USED, GOAL_CREATED, GOAL_COMPLETED, GOAL_FAILED |

---

## Navigation Stack

| Screen | Description |
|---|---|
| `Welcome` | Onboarding entry — project introduction |
| `PickActions` | Choose initial focus activities |
| `Preview` | Onboarding summary before permission request |
| `Permissions` | Step-by-step grants: Usage Access, Overlay, Notifications, Accessibility |
| `Main` | Tab container — Home / Stats / Settings with floating dock |
| `Intervention` | Decision point: start a session, delay with hold, or skip with reason |
| `Session` | Active session timer with complete and abandon |
| `AppSelection` | Choose apps to block in Lock Mode |
| `History` | Chronological activity log |

The `FloatingDock` is a three-tab bar (Home, Stats, Settings) with a gold accent and glassmorphism style. It does not include a FAB.

After the first onboarding completion, a 11-step coach mark tour runs once across all three tabs. Each step highlights a specific UI element with a spotlight cutout. Steps that require a tab switch carry a `requiresTab` field; the tour waits 280 ms for the tab to settle before showing the card. Cards are placed above the spotlight when the target is in the bottom 48% of the screen, below it otherwise.

---

## Android Permissions

| Permission | Required For |
|---|---|
| `PACKAGE_USAGE_STATS` | Usage pattern analysis for trigger timing and Lock Mode enforcement |
| `SYSTEM_ALERT_WINDOW` | Drawing the intervention overlay above other apps |
| `POST_NOTIFICATIONS` | Intervention and reminder notifications |
| `FOREGROUND_SERVICE` | UsageTimerService and SessionTimerService |
| `WAKE_LOCK` | Keeping the usage monitor alive with screen off |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Precise daily reminder alarms |
| `RECEIVE_BOOT_COMPLETED` | Re-scheduling alarms after device restart |
| `QUERY_ALL_PACKAGES` | Listing installed apps for Lock Mode app selection |
| `BIND_ACCESSIBILITY_SERVICE` | App blocking via AnchorAccessibilityService |

---

## Tech Stack

| Component | Version |
|---|---|
| Kotlin | 2.0.20 |
| Compose Multiplatform | 1.7.0 |
| Koin | 3.5.6 |
| kotlinx.datetime | 0.6+ |
| kotlinx.serialization | 1.7+ |
| kotlinx.coroutines | 1.9+ |
| Material3 | Compose M3 dark scheme |
| Android minSdk | 26 (Oreo) |
| Android targetSdk | 34 |
| JDK | 17 |

---

## Build Instructions

### Debug build

```bash
./gradlew :androidApp:assembleDebug
```

Output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

### Local release build

Add keystore details to `local.properties` in the project root:

```
anchor.storeFile=/absolute/path/to/your-keystore.jks
anchor.storePassword=your_store_password
anchor.keyAlias=your_key_alias
anchor.keyPassword=your_key_password
```

Then run:

```bash
./gradlew :androidApp:assembleRelease
```

### CI release build (GitHub Actions)

Pushing a tag matching `v*` triggers `.github/workflows/release.yml`. The workflow decodes a base64-encoded keystore from secrets, builds a signed release APK, and publishes it as a GitHub Release attachment.

Required repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
|---|---|
| `ANCHOR_KEYSTORE_BASE64` | Your `.jks` encoded with `base64 -w 0 your-keystore.jks` |
| `ANCHOR_STORE_PASSWORD` | Keystore password |
| `ANCHOR_KEY_ALIAS` | Key alias inside the keystore |
| `ANCHOR_KEY_PASSWORD` | Key password |

To generate a keystore:

```bash
keytool -genkey -v -keystore anchor-release.jks \
  -alias anchor -keyalg RSA -keysize 2048 -validity 10000
```

To trigger a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The workflow produces `Anchor-v1.0.0.apk` and attaches it to a GitHub Release with installation instructions.

---

## Installing from GitHub Releases

1. Open the Releases page and download the latest `Anchor-vX.Y.Z.apk`.
2. On your Android device: Settings > Apps > Special app access > Install unknown apps. Enable it for your browser or file manager.
3. Open the downloaded APK and follow the install prompt.
4. On first launch, grant the permissions Anchor requests: Usage Access, Display over other apps, Notifications. Enable Accessibility Service separately if you intend to use Lock Mode.

Minimum Android version: 8.0 (Oreo, API 26).

---

## Project Origin

A work thought of by Joe, made in relation with Jele Sphere.
