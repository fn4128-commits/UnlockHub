# UnlockHub

This is an app project focused on unlock status, I developed three features in this app. 
First, is a safety feature, it can recored your unlock status, and synchronise to cloud, you can set a person who can check your sates.
And that designated person can check it on website.
Second feature is a memorandum, most user command/feedback is about the reminder, the problem is the reminder cannot make sure user sees the note.
So I link the reminder with unlock action, you can use the function is "when you unlock your phone, then the app will pop-up a notification", this function can increase the probability of user sees.
Last feature actually should be remove, because this feature is me referenced from Bixby Routines of Samsung. 
Originally, I set this feature is because I can use this function at another phone, but this feature is developed by Samsung, and this also a selling point isn't?

---

## Built with AI assistance

This project was built by me working together with an AI coding assistant
(Claude). I want to be transparent about how the work was split, because
"AI-assisted" can mean very different things.

**What I (the author) did**

- Came up with the product idea and decided what the three modules should be,
  and why they should exist at all.
- Made every product decision: what "unlock popup" should mean, how often a
  reminder should fire, that the blue button means *read* and the red button
  means *keep unread and quit*, that the persistent notification should be
  reduced but never at the cost of features.
- Tested every build on my own phone (Samsung A57) and reported what actually
  happened in the real world — which is how the important bugs were found.
- Rejected the easy-but-wrong fixes. When the assistant proposed a workaround
  that only made the app *look* like it worked (recording only when the app is
  opened), I pushed back and asked for the real cause.
- Owned everything outside the code: the Play Console account, the signing key,
  the Cloudflare account and deployment approval.

**What the AI assistant did**

- Wrote and refactored most of the Java/JavaScript code from my instructions.
- Diagnosed the root cause of the main bug: on Android 14/15 a background app
  no longer receives `ACTION_USER_PRESENT`, even with a foreground service, so
  the app was never notified about unlocks. The fix (listen for `SCREEN_ON`
  and then poll the keyguard state) came from that finding, verified on an
  emulator.
- Added the reliability layer: `UsageStatsManager` backfill plus a periodic
  `JobScheduler` job, so a missed unlock can still be recovered afterwards.
- Did the full zh/en internationalisation (~900 hardcoded strings moved into
  resources) for the app and the web page.
- Ran the builds, the emulator tests and the release packaging.

**Honest note on the limits**

The assistant also introduced bugs — including one that crashed the app on
launch — which were caught by testing before release. Nothing here was
"generated once and shipped". Every behaviour in this repo was checked on a
real device or an emulator before it was accepted.

---

## Tech notes

Technical details (modules, backend endpoints, build and signing instructions)
are in [`docs/technical-notes.md`](docs/technical-notes.md).

- Android app: plain Android SDK, no third-party libraries, minSdk 26 /
  targetSdk 36.
- Backend: Cloudflare Worker + D1 (a local Python server is kept for testing).
- Languages: Chinese and English, switchable in-app and on the web page.

## Status

Work in progress, preparing for a Google Play release. The signing key and all
account credentials are kept outside this repository.
