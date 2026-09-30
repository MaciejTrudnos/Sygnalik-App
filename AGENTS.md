# AGENT.md

Guidance for AI coding agents working on **Sygnalik-App**. Read this before making changes.

## Project overview

Sygnalik-App is the Android companion app for the [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device) (a vehicle-mounted hardware unit). The app:

- Connects to the device over **Bluetooth** and pushes notifications in real time.
- Forwards **SMS** and **incoming call** alerts to the device.
- Delivers **speed camera** alerts (Poland) and **police speed control** warnings, sourced from the Sygnalik Warning Gateway.
- Tracks GPS routes via **Traccar**.
- Uses **Nominatim** (OpenStreetMap) for geocoding.
- *In progress:* turn-by-turn navigation cues, backed by [Sygnalik-Directions-API](https://github.com/MaciejTrudnos/Sygnalik-Directions-API).

The app must keep working **in the background** (screen off, app not in foreground). This drives most design decisions below.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (`kotlin.compose` plugin)
- **Build:** Gradle with Kotlin DSL (`*.gradle.kts`) and a **version catalog** (`gradle/libs.versions.toml`, referenced as `libs.*`)
- **Modules:** single `app` module
- **Wrapper:** always use `./gradlew`, never a system Gradle

> Do not add a new dependency without adding it to the version catalog first. Do not hardcode versions in `build.gradle.kts`.

## Repository layout

```
Sygnalik-App/
├── app/                    # The only module: sources, resources, manifest
├── gradle/                 # Wrapper + libs.versions.toml (version catalog)
├── build.gradle.kts        # Root build file (plugins declared with apply false)
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

<!-- TODO: once the package structure is settled, list the main packages here
     (e.g. bluetooth, notifications, tracking, network, ui). -->

## Build

```bash
./gradlew assembleDebug          # build debug APK

```
Before considering a task done, at minimum run `./gradlew assembleDebug` sure pass.

Bluetooth, SMS, and call-state features **cannot be verified on an emulator**. If a change touches them, say so explicitly and list what needs a manual test on a physical phone with the device.

## Configuration and secrets

Runtime configuration comes from **system environment variables**, not from committed files:

| Key | Purpose |
| --- | --- |
| `NOMINATIM_USER_AGENT` | Identifies the app to OpenStreetMap (required by Nominatim usage policy) |
| `TRACCAR_DEVICE_ID` | Device ID from the Traccar panel |
| `TRACCAR_HOST` | Traccar instance address |
| `WARNING_GATEWAY_HOST` | Sygnalik Warning Gateway address |
| `WARNING_GATEWAY_API_KEY` | API key for the Warning Gateway |

Rules:

- **Never** commit real values, API keys, device IDs, or hosts. Never put them in source, `gradle.properties`, tests, logs, or screenshots.
- Read them through the existing configuration mechanism (e.g. `BuildConfig`) rather than inventing a new one. If you must add a variable, also update the table in `README.md` and this file.
- Do not log the API key or full request headers.

## Android-specific rules

**Permissions.** The app relies on sensitive permissions (Bluetooth, SMS, phone state/calls, location, notifications, background operation). When touching them:

- Declare in `AndroidManifest.xml` and request at runtime where required.
- Handle **denied** and **permanently denied** states gracefully; the app must not crash if a permission is missing.
- Respect API-level differences (e.g. Android 12+ Bluetooth permissions, Android 13+ notification permission, background location).
- Do not add new permissions without a clear reason, and mention any addition in your summary.

**Background work.**

- Long-running functionality (Bluetooth link, location tracking) belongs in a properly declared **foreground service** with a visible notification and the correct foreground service type.
- Do not rely on Activity/ViewModel lifetime for anything that must run while the screen is off.
- Be mindful of battery: no busy loops, no unnecessarily frequent location updates or network polling.

**Bluetooth.**

- All Bluetooth I/O off the main thread.
- Handle disconnects, reconnects, and the adapter being turned off.
- Treat the device protocol (message format sent to Sygnalik-Device) as a contract shared with the firmware repo. Do not change message formats without flagging it, since the device firmware must change in lockstep.

**Networking.**

- Nominatim: respect its usage policy (identifying User-Agent, low request rate, caching where reasonable).
- Warning Gateway requests must include the API key; fail quietly and retry sensibly when offline.
- Traccar uses its OsmAnd-style HTTP protocol on the configured host.

**Privacy.** The app handles SMS content, call info, and precise location. Do not log message bodies, phone numbers, or coordinates in release builds. Do not add analytics or third-party SDKs without asking.

## Code conventions

- Idiomatic Kotlin; follow the official Kotlin coding conventions and the style already used in the file you are editing.
- Prefer **coroutines/Flow** for async work; no `GlobalScope`, no blocking calls on the main thread.
- Compose: keep composables stateless where possible (state hoisting), keep side effects in `LaunchedEffect`/ViewModels, and provide `@Preview`s for non-trivial UI.
- Keep UI, domain logic, and I/O (Bluetooth, network, location) separated so the logic is unit-testable.
- No hardcoded user-visible strings: use `strings.xml`.
- Keep changes small and focused. Do not refactor unrelated code or reformat whole files in the same change.
- Do not commit generated files, build outputs, `local.properties`, or keystores.


## Boundaries

**Always:** use the version catalog; keep secrets out of the repo; handle missing permissions; keep the README config table in sync.

**Ask first:** adding permissions or dependencies; changing the Bluetooth message format; changing minSdk/targetSdk or Gradle/AGP/Kotlin versions; adding analytics/crash reporting; large architectural changes (e.g. splitting into modules, adding DI framework).

**Never:** commit secrets or keystores; log personal data (SMS, numbers, location) in release builds; bypass Android permission or foreground-service requirements with workarounds; edit files under `build/` or `.gradle/`.

## Related repositories

- [Sygnalik-Device](https://github.com/MaciejTrudnos/Sygnalik-Device): hardware/firmware the app talks to over Bluetooth
- [Sygnalik-Directions-API](https://github.com/MaciejTrudnos/Sygnalik-Directions-API): turn-by-turn directions backend
- Sygnalik Warning Gateway: source of speed camera and speed control data
