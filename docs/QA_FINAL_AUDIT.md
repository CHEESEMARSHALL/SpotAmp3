# SpotAmp QA Final Audit

Audit date: 2026-09-05  
Authoritative root: `D:\Documents\SpotAmp\SpotAmp`  
Baseline revision: `d6d84cb6af5e12f418805661ef08c7dedaa4f0de`

## Scope and product boundary

SpotAmp is a companion client: product music playback requires a paired desktop SpotCore session and a companion stream URL. Local WAV/MP3 fixtures and loopback streams validate media plumbing and origin policy only; they are not evidence of audible desktop-companion playback.

The checkout contains one Gradle root and one `:app` module. The verified application ID is `com.aistudio.plexmusicplayer.vctplx`; the namespace remains `com.example`. The app uses Compose, `MainActivity`, `MusicViewModel`/StateFlow, Room v16, Retrofit/OkHttp, WorkManager, Media3 ExoPlayer, and a custom `MusicPlaybackService` with platform MediaSession.

## Build and signing result

| Variant | Verified intent |
|---|---|
| `debug` | Standard Android debug signing for development and device testing |
| `qa` | Locally debug-signed, isolated `.qa` package for installable QA journeys |
| `benchmark` / profileable test variants | Local signing for profiling and performance tests |
| `release` without credentials | Unsigned `app-release-unsigned.apk` for compilation, packaging, and inspection |
| `release` with explicit credentials | Production/upload signing selected only from `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and optional `KEY_ALIAS` |

The repeatable signing-policy gate passed QA/benchmark local signing, unsigned release validation with cleared credentials, and explicit production signing with a disposable workspace-only keystore. Relative keystore paths resolve from the repository root. The release build has no automatic debug-keystore fallback and contains no hardcoded credentials. The unsigned release-validation build passed archive inspection and QA-only exclusion checks. A separate disposable PKCS#12 smoke keystore proved that explicit release credentials select the signed configuration; it was removed after the check and was not a production key.

## Integrated QA evidence

An earlier full gate was run with:

```powershell
.\scripts\qa.ps1 full -Seed 847229173 -Actions 500
```

Result: PASS across 26 tiers, 165 tests, 0 failures, 0 errors, and 0 skips. That run included 12/12 connected-UI tests on SM-S926U running Android 16; it is retained as historical device evidence, not as proof of the companion-only audible playback gate. Release validation produced the unsigned local artifact and verified 540 APK entries. The screenshot replacement was then independently verified with the focused `ProductStateScreenshotTest` and the quick tier; the current product-state snapshot is `now-playing-queue.png`. The subsequent quick gate passed with 153 tests after adding lyrics parser coverage and fixing malformed bracket-record handling.


The post-signing-path revalidation on 2026-09-05 reached all 25 non-UI tiers successfully, including release assembly and unsigned-artifact inspection, with 153 executed host tests and no failures, errors, or skips. Its UI tier was `BLOCKED` before test start because the connected handset was asleep and secure-locked; the earlier independent UI run remains the 12/12 device evidence. The subsequent lint hardening added `QuickLint` to the quick tier; `:app:lintDebug` and the updated quick gate both pass. The latest quick gate after adding the local Last.fm fake contracts executed 155 tests with no failures, errors, or skips. The current machine-readable result is in `qa-results/latest/QA_REPORT.json` and is intentionally `FAIL` when a required UI tier is blocked.
The consolidated full run after that runner correction completed `QuickLint`, all host/backend/QA-variant/benchmark/stress/endurance checks, and release validation successfully. Its only non-PASS tier was UI, reported as `BLOCKED` with failure class `infrastructure` because the handset was still securely locked; no functional tier failed. After the handset became available, the QA-Lab fixture journey was isolated, the repeated Media3 queue-rebuild issue was fixed as SPOTAMP-QA-037, and the complete connected UI tier passed all 12 tests on SM-S926U / Android 16.
The current regression ledger records SPOTAMP-QA-023 for malformed bracketed LRC records, SPOTAMP-QA-027 for the standalone-audit report-archiving defect, SPOTAMP-QA-028 for destructive Room migration fallback, SPOTAMP-QA-029 for the QA backend sentinel-credential boundary, SPOTAMP-QA-030 for preserving the canonical report during audit runs, SPOTAMP-QA-034 for stress-property leakage between full-tier phases, and SPOTAMP-QA-035 for the connected-test watchdog/reporting path. Important verified areas include release exclusion scanning, QA identity/installability, companion-origin and cross-origin credential policy, playback restoration, Media3/service contracts, downloads, sync policy/safety, deterministic stress/trace, endurance, backend contracts, and connected UI.

The subsequent dedicated benchmark rerun passed `BenchmarkAssembly` and `Benchmark` with all three benchmark suites passing. A separate QA-only device-performance campaign also completed three cold and three warm samples on SM-S926U / Android 16; its report records median/P95 startup and memory values, frame/jank samples, thermal metadata, and a matching-metadata comparability marker. These measurements remain diagnostic and do not claim a device-performance threshold.

The standalone disposable backend service check passed all eight checks (health, missing-authorization rejection, non-sentinel rejection, authorized library pagination, fixture delivery, invalid-fixture rejection, lyrics, and in-memory reset) on loopback `127.0.0.1:18765`; the service exited after the check and left no persistent QA backend process.

The latest consolidated full run (`20260905T145036801Z-full`) completed 27 functional/validation tiers with 167 tests, 0 failures, 0 errors, and 0 skips. Every host, backend, QA-variant, benchmark, stress, endurance, connected-UI, and release tier passed. Release archive inspection also rejects the documented QA sentinel credential in addition to QA-only paths, markers, and forbidden endpoints. The latest quick gate separately passed with 155 tests.

Room database construction now registers the complete known migration set without `fallbackToDestructiveMigration()` or a duplicate destructive recovery builder. The focused storage tier and subsequent quick lint/regression gate passed after this change; the framework audit also rejects any future reintroduction of destructive fallback.

## Artifacts and documentation

- [QA guide](QA.md)
- [Build and signing policy](BUILD_SIGNING.md)
- [Companion playback gate](QA_COMPANION_PLAYBACK.md)
- [Real companion Level-B runner](../scripts/qa-companion-real.ps1)
- [Companion playback evidence gate script](../scripts/qa-companion.ps1)
- [QA framework audit script](../scripts/qa-audit.ps1)
- [Companion playback observation template](QA_COMPANION_OBSERVATION_TEMPLATE.json)
- [Architecture audit](QA_ARCHITECTURE_AUDIT.md)
- [Coverage matrix](QA_COVERAGE_MATRIX.md)
- [Bug ledger](QA_BUG_LEDGER.md)
- [Product-state screenshot test](../app/src/test/java/com/example/ProductStateScreenshotTest.kt)
- [Product-state screenshot](../app/src/test/screenshots/now-playing-queue.png)

Reports are generated under `qa-results/latest/`, including JSON/Markdown summaries, JUnit copies, traces, benchmarks, and best-effort failure context. Each invocation has a UTC `runId`, and the prior latest tree is archived under `qa-results/runs/` before replacement; audit runs preserve the canonical test report and write `QA_AUDIT_REPORT.json` and `.md`. Host CI uploads the latest tree together with archived `qa-results/runs/` and `qa-results/companion-runs/` evidence. The JSON report also carries per-tier `failureClass` values (`functional`, `infrastructure`, or `none`) so blocked device/companion evidence cannot be confused with a functional test failure. It also carries an `artifacts` map for debug, QA, benchmark, and release APK paths, presence, and signing intent.

## Explicit evidence limits

- The dedicated real-companion Level-B run `20260905T154738658Z-companion` passed on SM-S926U / Android 16 using the adjacent SpotCore implementation, the exact generated QR payload and production parser, an isolated library index, generated QA tones, real pairing, authenticated library/stream requests, HTTP range seeking, Media3 playback controls, track transition, and background state checks.
- The manual Level-C observation run `20260905T-level-c-manual` passed all eight companion journey steps, including audible playback, controls, background/return, connection recovery, and event delivery/retry. The current machine-readable report is `qa-results/latest/COMPANION_PLAYBACK_REPORT.json`; the automated Level-B evidence remains linked by `levelBRunId`.
- Historical shipped Room schemas/releases are not verifiable from this checkout; synthetic migration fixtures do not establish shipped provenance.
- Gemini client implementation is absent from this checkout; its contract coverage remains unimplemented.
- Real Plex, Last.fm, production backends, and distribution/publishing were not used or started; Last.fm behavior was tested only against a disposable loopback fake.
- Robolectric cannot establish the platform MediaSession callback path; that portion remains device-level evidence.

No distribution or publishing work was started.

