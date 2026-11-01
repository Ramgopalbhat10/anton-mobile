# Anton for Android

A minimal, linear Android client for [Anton](https://github.com/Ramgopalbhat10/anton-v2) — the personal coding agent. Kotlin + Jetpack Compose, designed to Anton's own design language (canvas `#0A0A0B`, accent `#3FB6D8`, Geist typography, hairline borders, small radii).

## Design kit

The UI mirrors the web app's shadcn components without pulling in a UI library (release APK ≈ 1.65 MB). The full reference (colours, type, spacing scale, components, icons, size budget) is in [docs/design/mobile-design-kit.md](docs/design/mobile-design-kit.md).

- `ui/theme/Theme.kt` — Anton tokens, the `Space` spacing scale, plus the shadcn aliases the web maps onto them (`popover`, `input`, `ring`), Geist + Geist Mono (variable TTFs, Latin subset, ~67 KB in the APK; OFL, see `licenses/`), and a press highlight that replaces Material's ripple.
- `ui/theme/Lucide.kt` — the lucide icons the web uses, as small stroke vectors (no icon library).
- `ui/components/Components.kt` — card, button variants, input with focus ring, select trigger, switch, badge, list rows, segmented control, composer.
- `ui/components/Overlays.kt` — anchored dropdown/popover with menu items, searchable select (combobox), and the bottom sheet every picker and form uses.

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

### Toolchain

- JDK **17** (`java -version`)
- Android SDK command-line tools, **platform 35**, and **build-tools 34.0.0**
- Gradle **8.11.1**, downloaded and checksum-verified by the checked-in wrapper
- AGP **8.7.3** and Kotlin **2.0.21**, pinned in `gradle/libs.versions.toml`

Android Studio is optional. For a headless Linux sandbox, install JDK 17 and extract the [Android SDK command-line tools](https://developer.android.com/tools/sdkmanager) into `$ANDROID_HOME/cmdline-tools/latest`, then run:

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 # adjust for your JDK installation
export ANDROID_HOME=/opt/android-sdk             # adjust for your SDK installation
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools"
./gradlew :app:assembleRelease --max-workers=2
```

Alternatively, set `sdk.dir=/absolute/path/to/android-sdk` in the ignored `local.properties`. The wrapper scripts and JAR are included; no system Gradle installation is needed.

### APK variants and signing

- `./gradlew :app:assembleDebug` produces a debug-signed APK at `app/build/outputs/apk/debug/app-debug.apk`. This variant includes development tooling and is **not** the small distribution artifact.
- `./gradlew :app:assembleRelease` enables R8 minification and resource shrinking, producing `app/build/outputs/apk/release/app-release-unsigned.apk`. Measure the **signed release APK** when checking the roughly **1.7 MB** size target; the exact size depends on the source and toolchain.

Release signing is deliberately not configured with a checked-in key. To create an installable sandbox build, generate a local test key outside the repository and sign the optimized APK:

```sh
# Test key only; do not use this public password/key identity for production releases.
keytool -genkeypair -keystore /tmp/anton-sandbox.p12 -storetype PKCS12 \
  -storepass android -keypass android -alias anton-sandbox -keyalg RSA \
  -keysize 2048 -validity 3650 -dname "CN=Anton Sandbox"
"$ANDROID_HOME/build-tools/34.0.0/zipalign" -f -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk /tmp/anton-aligned.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" sign \
  --ks /tmp/anton-sandbox.p12 --ks-key-alias anton-sandbox \
  --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false \
  --out /tmp/anton-release.apk /tmp/anton-aligned.apk
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --verbose /tmp/anton-release.apk
"$ANDROID_HOME/build-tools/34.0.0/zipalign" -c -v 4 /tmp/anton-release.apk
```

Keep a real release keystore securely outside the repository and reuse it for updates. A sandbox-key APK cannot update an existing installation signed with another key. The tracked `.idsig` file is not an APK and is not used by this build.

### Checks

```sh
./gradlew :app:testDebugUnitTest :app:verifyPaparazziDebug :app:lintRelease --max-workers=2
```

Paparazzi runs the UI snapshot tests on the JVM, without an emulator. Snapshot references live in `app/src/test/snapshots/`; do not overwrite them just to make a failing verification pass.

## Not yet wired

- Push notifications: the `PushSender` port exists server-side; add an FCM or self-hosted ntfy implementation next. Notification toggles already persist.
- The `anton:task://` deep link in the manifest is registered for future notice routing.
- Room offline cache — currently the list refetches on SSE signals; cache lands when the DTOs settle.
