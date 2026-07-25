# SafePing MVP Plan

## Product position

SafePing is a low-friction family safety check-in app. It does not replace emergency services, phone calls, or medical monitoring. Its first job is to surface a long silence early enough that a trusted person can check in.

## MVP scope

### Android recorder

- Record the first device unlock per local date.
- Keep records locally if the network or backend is unavailable.
- Sync pending unlock records when the phone is unlocked again or when the user taps sync.
- Retry failed syncs in the background after one hour.
- Keep a local fallback check for the 72-hour inactivity alert.
- Let the user pause recording.
- Show a local notification when a new daily unlock is captured.

### Receiver

- Receiver starts as a web inbox so the MVP does not require an Apple Developer account.
- Receiver should eventually use an iOS or Android app if push notifications and a native inbox become necessary.
- Messages should become in-app messages plus push notifications in the native receiver phase.
- Receiver should explicitly accept a guardian relationship before receiving sensitive activity data.
- The app cannot know when the receiver opens other iOS apps.

### Backend

- Store users, guardian relationships, unlock events, weekly reports, alerts, and message read state.
- Send weekly report messages after the configured weekly window, once the recorder phone becomes active again.
- Run a scheduled monitor every 6 hours and create an alert when the recorder has no new first-unlock record for 72 hours.
- Retry or re-notify when receiver messages remain unread.

## Current implementation

This repository currently contains the Android recorder MVP, a Python local backend MVP, and a Cloudflare Workers + D1 backend target. It uses a configurable backend URL and these endpoints:

- `POST /api/unlock-events`
- `POST /api/inactivity-alerts`
- `GET /api/messages?guardianHandle=...`
- `POST /api/messages/{id}/read`

The temporary receiver is a browser inbox at `/`. The iOS receiver is not implemented yet.

## Next build step

Deploy the Cloudflare backend with:

- a D1 database
- `schema.sql` applied remotely
- a `workers.dev` URL configured inside the Android app

After deployment, the next product step is either:

- private receiver links, so a receiver does not need to type a shared handle
- lightweight login and guardian invite acceptance, so sensitive records are not visible by guessing a handle
