# Anton for Android

A minimal, linear Android client for [Anton](https://github.com/Ramgopalbhat10/anton-v2) — the personal coding agent. Kotlin + Jetpack Compose, designed to Anton's own design language (canvas `#0A0A0B`, accent `#3FB6D8`, Geist-scale typography, hairline borders, small radii).

## Screens

- **Tasks** — one list grouped by state (Needs you / Working / Queued / Review / Idle / Done), real state glyphs, segmented filter.
- **Thread** — user bubbles, markdown replies, collapsed step trace, inline ask/plan cards, composer whose send button becomes Stop while the sandbox is live.
- **New task** — bottom sheet: repo, branch, prompt, model, Plan-first.
- **Project** — coordinator card + threads in the same row anatomy.
- **Settings** — server URL, Cloudflare Access service-token pair, notification toggles, defaults.

Deliberately out of scope on mobile: terminal, live browser/computer, editor pane, automations editor, connectors/subscriptions/plugins.

## Server side

The app reads through `GET /api/mobile/*` on anton-v2 (branch `devin/mobile-api-facade`):

| Endpoint | Returns |
|---|---|
| `GET /api/mobile/sessions` | Trimmed session rows for the list |
| `GET /api/mobile/sessions/:id` | Session + waiting + subagent runs + PR + now, one call |
| `GET /api/mobile/sessions/:id/messages` | Transcript normalized from the flue agent router |
| `GET /api/mobile/spaces[/:id]` | Projects and their threads |
| `GET /api/mobile/models` | Model picker data |

Sending stays on `POST /api/agents/coder/:id` (budget checks + priming run there); live updates ride the same `GET /api/events` SSE stream; attachments upload to `POST /api/sessions/:id/attachments`.

## Auth

The server sits behind Cloudflare Access. The app sends a **Service Token** on every request — including the SSE stream and uploads:

```
CF-Access-Client-Id: …
CF-Access-Client-Secret: …
```

Create the token in Zero Trust → Access → Service Auth → Service Tokens, and add a Service Auth policy allowing it on the application's Access app. Enter the pair once in the app's Settings (stored in DataStore; rotate from the dashboard if the phone is lost). Browsers keep their email+OTP flow untouched.

## Build

Requires Android Studio (AGP 8.7+, Kotlin 2.0) or `gradle` with an Android SDK on PATH:

```
./gradlew :app:assembleDebug
```

Set `local.properties` → `sdk.dir=` if the SDK isn't detected.

## Not yet wired

- Push notifications: the `PushSender` port exists server-side; add an FCM or self-hosted ntfy implementation next. Notification toggles already persist.
- The `anton:task://` deep link in the manifest is registered for future notice routing.
- Room offline cache — currently the list refetches on SSE signals; cache lands when the DTOs settle.
