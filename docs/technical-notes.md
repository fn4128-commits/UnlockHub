# UnlockHub

Android-first personal utility hub built around how you use your phone. It
brings together three features that people usually juggle across separate
apps:

1. **Daily check-in (SafePing)** — low-friction family safety check-ins.
2. **Memos** — quick notes with scheduled reminders.
3. **Automations (routines)** — actions triggered by everyday phone events.

> Package: `com.jinxin.unlockhub` · min SDK 26 · target SDK 36

---

## Modules

### 1. Daily check-in (SafePing)

The recorder phone logs the first unlock event of each day, syncs it to a
backend, and schedules a 72-hour inactivity check. A contact you choose
receives the signal.

- Record the first phone unlock per local date.
- Store records locally in SQLite.
- Sync unsent records to a configurable backend over HTTPS.
- Schedule a local 72-hour inactivity check after each activity as a fallback.
- A Cloudflare scheduled monitor runs periodically to detect 72 hours without
  a new first-unlock record even if the recorder phone is offline.
- Create inactivity alert messages when no new first-unlock record has arrived
  for 72 hours.
- Generate weekly in-app report messages once the weekly window is reached.

### 2. Memos

- Create memos with scheduled reminders (local notifications).
- Optional sync so notes are backed up to the same backend.

### 3. Automations (routines)

Trigger an action when something happens on the phone:

- **Triggers**: a set time, your first unlock of the day, plugging in / out
  the charger, low battery, connecting to a Wi-Fi or Bluetooth device, or
  arriving at a place you choose (geofence).
- **Actions**: toggling Do Not Disturb and other quick device tasks.

Place-arrival triggers use background location; see `play-store-docs/` and the
desktop publishing guide for the Google Play background-location declaration.

---

## Backend

Two backend options power check-in and memo sync:

- `backend/`: local Python backend for quick development (standard library only).
- `cloudflare-worker/`: free-cloud backend target using Cloudflare Workers + D1.

For the no-cost hosted path, use `cloudflare-worker/`. The app's default
backend URL is the HTTPS Cloudflare Worker.

### Local Python backend

```powershell
python backend\safeping_server.py --host 0.0.0.0 --port 8080
```

Then set the app backend URL to your computer's LAN address, e.g.
`http://192.168.1.23:8080`. Android emulators can usually use
`http://10.0.2.2:8080`. Open the same backend URL in a browser for the
temporary web inbox; for a receiver, enter the same `guardianHandle`
configured in the app (e.g. `mom`).

## Backend endpoints

`GET /health` — service health.

`POST /api/unlock-events`

```json
{
  "deviceId": "local-install-id",
  "displayName": "Me",
  "guardianHandle": "+15551234567 or user id",
  "localDate": "2026-05-31",
  "firstUnlockAt": "2026-05-31T08:12:00+08:00"
}
```

Stores a first-unlock event and may create a weekly report message.

`POST /api/inactivity-alerts`

```json
{
  "deviceId": "local-install-id",
  "displayName": "Me",
  "guardianHandle": "+15551234567 or user id",
  "lastActivityAt": "2026-05-28T08:12:00+08:00",
  "inactiveHours": 72
}
```

Creates a high-priority inactivity message for the guardian handle.

`GET /api/messages?guardianHandle=mom` — latest messages for a receiver handle.

`POST /api/messages/{id}/read` — marks a message as read.

---

## Android build

Open the folder in Android Studio, or build with Gradle and a local Android
SDK (Platform 36 required for `compileSdk 36`).

```powershell
# Debug build
.\gradlew assembleDebug

# Release App Bundle for Google Play (requires keystore.properties, see below)
.\gradlew bundleRelease
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

### Signing for release

Create an upload key and a `keystore.properties` in the project root (already
git-ignored):

```properties
storeFile=C:\\Users\\<you>\\keys\\unlockhub-upload.jks
storePassword=...
keyAlias=unlockhub
keyPassword=...
```

Google Play publishing docs and checklists live in `play-store-docs/`.

## Android runtime notes

After installing:

1. Open UnlockHub at least once.
2. Fill in display name, receiver ID, and backend URL. Save settings.
3. Allow notifications if Android asks.
4. Use `Check backend connection` to verify the Cloudflare URL.
5. Use `Record one unlock now` to create the first test record.
6. Open Android app settings and avoid restricting the app's battery /
   background activity.

Automatic behavior (check-in):

- Listens for Android's user-present unlock event; only the first unlock per
  local date is stored.
- A local notification is shown when a new daily record is captured (if
  permission granted).
- Failed syncs remain local and are retried on the next unlock and via a
  one-hour background retry.
- Reboot restores the local 72-hour fallback check and pending-sync retry.
- Cloudflare also checks for missing first-unlock records periodically.

## Current identity model

The backend currently uses `guardianHandle` as a plain receiver handle so the
MVP can be tested without SMS verification or account setup. Before production
this should become phone/email login, verified user IDs, guardian invite and
acceptance, and delivery only to accepted guardian relationships.

For the no-cost MVP path, use a receiver handle such as `mom` first. Do not use
a real phone number until account verification and consent are implemented.
