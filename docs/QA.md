# SpotAmp QA

Use `scripts/qa.ps1` for bounded, report-producing QA tiers:

```powershell
.\scripts\qa.ps1 quick
.\scripts\qa.ps1 companion
.\scripts\qa.ps1 storage
.\scripts\qa.ps1 qa
.\scripts\qa.ps1 qa-backend
.\scripts\qa.ps1 signing
.\scripts\qa.ps1 audit
.\scripts\qa.ps1 sync
.\scripts\qa.ps1 playback
.\scripts\qa.ps1 downloads
.\scripts\qa.ps1 ui
.\scripts\qa.ps1 stress -Seed 847229173 -Actions 500
.\scripts\qa.ps1 benchmark
.\scripts\qa.ps1 release
.\scripts\qa.ps1 full
.\scripts\qa-device-performance.ps1
```

Generate disposable deterministic audio inputs for playback/download validation with:

```powershell
.\scripts\generate-audio-fixtures.ps1
```

The generator creates short 440 Hz WAV, 880 Hz MP3, and stereo-silence WAV fixtures under `qa-fixtures/audio`. The directory is disposable and is not connected to Plex, SpotCore, or the production database.

Host-only CI validation is defined in [`.github/workflows/host-qa.yml`](../.github/workflows/host-qa.yml). It runs the non-device tiers, including loopback backend, unsigned release, signing-policy, stress, and framework-audit checks, on push and pull request without production credentials or external services; the `full` gate still belongs to the local/device lab because UI and real companion playback require the connected environment. CI uploads the latest report plus archived QA and companion evidence.

Reports are written to `qa-results/latest/QA_REPORT.md` and `QA_REPORT.json`, with logs and reserved artifact directories. Each invocation assigns a UTC `runId`; the prior `latest` tree is preserved under `qa-results/runs/<runId>/` before the new run starts. The `quick` tier runs `lintDebug` before `testQaUnitTest`; host tests use the QA variant so Compose/Robolectric and device tests share the isolated QA application configuration. Use it as the frequent compile/static-analysis/regression gate. The `full` tier includes release assembly and `verify-release.ps1`; use `release` for that gate alone. `BLOCKED` and `NOT_IMPLEMENTED` are not passes. UI requires a connected emulator/device with the display awake and the keyguard dismissed; a secure locked handset is reported as BLOCKED before Gradle UI tests start. Stress covers deterministic playback-state and download-collection invariants; endurance runs the playback state machine for at least 10,000 actions; benchmark is a host-side smoke baseline, not a device performance claim. For a separate bounded device diagnostic campaign, run `scripts/qa-device-performance.ps1 -Samples 3 -SettleSeconds 2`; it records cold/warm startup, memory, frame/jank, and thermal metadata without imposing a threshold. Reports include the selected tier, commit, device, Android version, Java runtime, project path, and UTC generation time for comparison context.
QA wrapper runs are serialized by a host-wide, fail-fast mutex shared by `qa.ps1` and `qa-companion-real.ps1`. The lock is acquired before rotating `qa-results/latest` or installing/running connected instrumentation. If another QA wrapper is active, the new invocation prints a `BLOCKED` message, returns exit code 2, and starts no tests; retry after the active run finishes. This prevents concurrent app/test APK installation from terminating another run and keeps shared reports together. Direct Gradle or ADB commands bypass this guard and should be run sequentially when they target the QA package or device.
The Gradle wrapper is used by default. If wrapper bootstrap is unavailable but a compatible Gradle distribution is already installed, pass `-GradleExecutable <path-to-gradle.bat>` to `qa.ps1` or `qa-companion-real.ps1`; both scripts keep the same task/report behavior with that executable.
The `audit` tier validates the existing canonical report without archiving or replacing it; its own result is written to `qa-results/latest/QA_AUDIT_REPORT.json` and `.md`.
The framework audit scans the current QA report tree and newest companion run for credential markers using bounded text chunks; it skips binary artifacts. To rescan every archived QA and companion run, invoke `scripts/qa-audit.ps1 -IncludeArchivedArtifacts` directly.
The runner also writes summary.json with aggregated suite/test counts, ingests host JUnit XML plus connected Android-test XML, and copies both into qa-results/latest/junit/; totals and per-tier functional/infrastructure classifications are embedded in QA_REPORT.json. The UI tier first assembles the QA target app (`:app:assembleQa`) and then its matching Android-test APK (`:app:assembleQaAndroidTest`), installs both with `adb install -r`, and launches the installed `AndroidJUnitRunner` with `adb shell am instrument`; it avoids Gradle's connected-test task because that task's teardown uninstalls the QA app. The runner records whether `.qa` existed before installation, requires it to remain installed afterward, and treats unexpected removal as a failure. AndroidJUnitRunner instrumentation has a 180-second watchdog (override process-locally with `SPOTAMP_CONNECTED_TIMEOUT_SECONDS`); a timeout is reported as `BLOCKED` infrastructure. On build or instrumentation failure/timeout, the runner records a tier context file and, when a device is connected, captures logcat, window state, a UI hierarchy, and a best-effort screenshot under qa-results/latest/failure-context/.

The `signing` tier verifies unsigned release-validation behavior with cleared signing environment variables, QA/benchmark local signing, and—only when the caller supplies `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`—explicit production signing. It never creates or embeds credentials.

The qa tier assembles and installs the isolated `com.aistudio.plexmusicplayer.vctplx.qa` / SpotAmp QA variant; the full tier includes this check before benchmark and release validation. It also verifies the device-registered package identity (QAIdentity) after installation. Connected UI and real-companion tests target this isolated QA package, keeping fixture sync and stale-cache pruning away from the main app's saved library. Both test paths install or replace the QA APK with `adb install -r`, then execute instrumentation directly so test teardown cannot uninstall the QA app. The generic `ui` tier excludes setup-dependent instrumentation by default: `CompanionRealPlaybackTest` (requires a configured real SpotCore companion), `BluetoothPlaybackRouteDeviceTest` (requires a connected headset route), `LockScreenTransportDeviceTest` (requires the phone to remain locked), and `PlaybackMotionDeviceTest` (requires zero animation scales). Use the separately scoped `companion` tier for real SpotCore. Run Bluetooth, lock-screen, and reduced-motion checks through their focused flows with the required route/state prepared; the broad UI harness wakes and dismisses keyguard and does not change global motion scales. Bluetooth/headset checks remain deferred per the active user direction. The UI runner grants `POST_NOTIFICATIONS` only to the QA package for instrumentation, then explicitly revokes it in a `finally` cleanup and records `UINotificationPermissionCleanup`; cleanup is not a pass if `.qa` disappears before revocation.
The full tier includes the same `QuickLint` static-analysis step as the standalone quick tier before the broader host, device, and release checks.

The standalone sync tier runs policy, safety, generation, pagination, backend, shared-playlist, and companion-isolation contract tests. The standalone playback tier runs restoration, service/session, URI-policy, and companion-origin contract tests. The current runner uses the existing host test suite and does not configure real Plex, SpotCore, Last.fm, or other production services. The explicit `companion` tier is the integration exception: it imports the real adjacent SpotCore companion implementation, uses an isolated QA library, and passes a temporary pairing secret only to the connected QA test process. It remains separate from `quick` and `full` so ordinary validation is independent of desktop availability. The dedicated QA application variant and device journeys are operational, including offline search-mode persistence, download-mode navigation, and a loopback companion-stream journey through the real PlaybackManager and Media3 path; the disposable local backend and generated audio-fixture tools support contract and media-input validation without production services.

### Playback prerequisite

SpotAmp product music playback is companion-only: a real audio journey requires the phone to be paired with the desktop SpotCore companion and to receive a companion stream URL. Automated device coverage includes a loopback companion-stream infrastructure journey through the real PlaybackManager and Media3 HTTP boundary. Run `qa.ps1 companion` for the separate Level-B real-desktop-companion journey; it covers pairing, library fetch, stream responses, ExoPlayer readiness/progression, pause/resume, seek, next-track, background behavior, and state coherence. Generated WAV/MP3 fixtures validate disposable media inputs; neither tier claims physical audible observation.


See [BUILD_SIGNING.md](BUILD_SIGNING.md) for the explicit distinction between debug, QA, unsigned release-validation, and production-signed artifacts.

For the real product audio journey, use [QA_COMPANION_PLAYBACK.md](QA_COMPANION_PLAYBACK.md). Level B is automated by `qa.ps1 companion` when the adjacent SpotCore source and connected device are available; Level C remains the separate manual physical listening gate for a paired desktop SpotCore session and is `BLOCKED`, not a local-fixture PASS, when no reliable audible observation is available.
Use docs/QA_COMPANION_OBSERVATION_TEMPLATE.json as the redacted observation starting point. The guarded evidence gate can be run with scripts/qa-companion.ps1. With no observation file it writes COMPANION_PLAYBACK_REPORT.json and COMPANION_PLAYBACK_REPORT.md and exits 2 (BLOCKED). A manually collected, redacted observation may be supplied with -ObservationPath; the script requires pairing, audible observation, a SpotCore build, a run ID, and all eight journey steps before it can return PASS.


## QA Lab

Debug and QA builds expose a hidden QA Lab entry from Settings. It reports bounded runtime state (queue, downloads, sync) and carries no production credentials or destructive production controls. Release builds compile this surface out via `BuildConfig.DEBUG`; device-only journeys remain under the `ui` tier.

Backup safety is explicit: `plex_settings_secure.xml` and the legacy `plex_settings.xml` preference files are excluded from cloud backup and device transfer because they contain device-bound credentials. Database, download, and other non-credential app data remain eligible for the configured backup behavior.

The QA Lab fixture controls include 100, 1K, 10K, 50K, and 100K in-memory queues plus Unicode and duplicate-ID cases. Use the larger fixtures only on a disposable debug/QA installation; they do not touch the production database.


Start the disposable local backend contract service with:

```powershell
.\scripts\qa-backend.ps1
.\scripts\qa-backend-check.ps1
```

The service binds only to `127.0.0.1`, accepts only the sentinel `SPOTCORE_TEST_TOKEN_DO_NOT_USE`, serves disposable generated fixtures, and exits after the contract check; it is not a proxy to Plex, SpotCore, Last.fm, Gemini, or any production endpoint. `qa-audit.ps1` verifies the loopback binding and required service scripts.

Run the signing-policy gate with:

```powershell
.\scripts\qa-signing.ps1
```

Run the framework-configuration audit with:

```powershell
.\scripts\qa-audit.ps1
```

It listens only on 127.0.0.1:8765, requires the exact `Bearer SPOTCORE_TEST_TOKEN_DO_NOT_USE` sentinel for library data, and exposes /healthz, the normalized library endpoint, lyrics, and an in-memory-only QA reset endpoint. Stop it with Ctrl+C; it never contacts or mutates a remote service. The `qa-backend-check.ps1` command starts a disposable instance on loopback and verifies eight checks including missing/non-sentinel authorization rejection, authorized library response, fixture-name validation, generated audio fixture delivery, lyrics, and in-memory reset behavior; the `qa-backend` runner tier executes that check and `full` includes it.

QA network confinement is enforced by the QA-only QA_BUILD flag and shared OkHttp/Coil/Media3 boundary interceptors. Backend, Plex, Last.fm, artwork, download, lyrics, and playback HTTP clients reject non-loopback origins in the QA APK unless the exact HTTPS companion origin was explicitly approved by a scanned QR payload for the current pairing flow. Debug and release builds retain their normal configured-network behavior.


The post-signing-path revalidation completed every non-UI tier and verified the unsigned release artifact. A subsequent focused UI run passed 12/12 after the QA-Lab fixture stress fix.
### Latest integrated gate

The latest 2026-09-05 full run (`.\scripts\qa.ps1 full -Seed 847229173 -Actions 500`) completed 27 tiers with 167 executed tests, 0 failures, 0 errors, and 0 skips on SM-S926U / Android 16, including 12/12 connected-UI tests. Release validation assembled and inspected `app-release-unsigned.apk` as an unsigned local-validation artifact; no production signing credentials were supplied. The runner selects the installed Android Studio JBR when available and applies a process-local conservative compiler/code-cache setting for stable repeated Kotlin invocations. The generated evidence is in `qa-results/latest/QA_REPORT.json` and `qa-results/latest/QA_REPORT.md`.
