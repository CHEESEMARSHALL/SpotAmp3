# QA Bug Ledger

## SPOTAMP-QA-001

- Severity: HIGH
- Date: 2026-09-03
- Source: Playback state restoration
- Status: FIXED
- Symptom: Restoring a persisted queue always selected index 0, losing the playing occurrence after restart.
- Reproduction: Persist queue A, duplicate B occurrences, D, E with current index 3 and position 42 seconds; restore.
- Root cause: `PlaybackStateRestoration` hard-coded `currentIndex = 0` after filtering unavailable tracks.
- Regression: `PlaybackStateRestorationRegressionTest.restore_preservesNonZeroCurrentQueueOccurrenceAndPosition`
- Fix: Track original queue indices through availability filtering and restore the matching occurrence, with deterministic next-available fallback.
- Verification: Focused restoration suite PASS; quick suite PASS.

## Source risks requiring reproduction

- Historical shipped schema versions remain unverified; unsupported runtime schema upgrades must fail without destructive fallback.
## SPOTAMP-QA-002

- Severity: CRITICAL
- Date: 2026-09-03
- Source: Library synchronization stale-cache pruning
- Status: FIXED
- Symptom: A zero-track remote response could authorize deletion of the existing cached library.
- Reproduction: Complete sync loop with no received tracks, then reach unconditional `deleteStaleTracks(syncId)`.
- Root cause: Pruning was unconditional after loop termination; empty/ambiguous snapshots were not distinguished from valid snapshots.
- Regression: `TrackCacheSyncSafetyTest.emptyRemoteSnapshot_doesNotAuthorizeStaleCachePruning`
- Fix: Track whether at least one non-empty page was received and gate pruning on that evidence.
- Verification: Sync safety and existing sync policy tests PASS.
## SPOTAMP-QA-003

- Severity: CRITICAL
- Date: 2026-09-03
- Source: Library synchronization generation ownership
- Status: FIXED
- Symptom: An obsolete worker could finalize a newer sync generation and prune or overwrite current state.
- Root cause: Finalization did not verify that the persisted `lastSyncId` still belonged to the worker.
- Regression: `SyncGenerationSafetyTest.obsoleteWorker_cannotFinalizeNewerGeneration`
- Fix: Gate finalization and stale pruning on the worker still owning the persisted generation.
- Verification: Sync generation, sync safety, and pagination tests PASS.
## SPOTAMP-QA-004

- Severity: CRITICAL
- Date: 2026-09-03
- Source: Account-switch synchronization isolation
- Status: FIXED
- Symptom: A worker started for an old backend account could continue committing catalog pages after credentials or endpoint changed.
- Root cause: Sync commits were guarded by section and generation, but not by the active backend identity.
- Regression: `SyncAccountIsolationPolicyTest.oldAccountCannotCommitAfterAccountSwitch`
- Fix: Compare non-secret normalized backend origin plus device/account ID before page writes and finalization; stale workers fail without finalizing or pruning.
- Verification: Account isolation and generation safety tests PASS.
## SPOTAMP-QA-005

- Severity: HIGH
- Date: 2026-09-03
- Source: Distributed playback-event retry contract
- Status: FIXED
- Symptom: Retry handling lacked an explicit tested contract for ambiguous delivery outcomes.
- Root cause: Outbox deletion and idempotency-key reuse were implicit in the worker.
- Regression: `RetryIdempotencyPolicyTest.transientAndAmbiguousFailuresKeepPendingEvent`
- Fix: Centralized and tested the policy: remove only on confirmed/permanent outcomes and always reuse the original event ID.
- Verification: Retry policy suite PASS.

## Remaining risk

Historical shipped schema versions and full real-server contracts remain unverified; those checks require controlled fixtures or dedicated integration environments.
## SPOTAMP-QA-006

- Severity: HIGH
- Date: 2026-09-03
- Source: Download retry/file integrity
- Status: FIXED
- Symptom: A failed redownload could truncate or delete an existing valid destination file.
- Root cause: The worker streamed directly into the final filename and deleted it on failure.
- Regression: `DownloadIntegrityPolicyTest.missingOrEmptyFileIsNotPlayable`
- Fix: Stream into a temporary `.part` file, validate non-empty output, then replace the destination atomically with a fallback move.
- Verification: Download integrity and existing downloads model tests PASS.
## SPOTAMP-QA-007  - Severity: HIGH - Date: 2026-09-03 - Source: Room database recovery - Status: FIXED - Symptom: Initialization failure switched to a timestamped fallback database, potentially hiding the existing library behind a new empty database. - Root cause: Recovery used a different database filename and omitted the normal migration set. - Regression: `MusicDatabaseRecoveryPolicyTest.recoveryUsesCanonicalDatabaseName` - Fix: Recovery reuses the canonical database name and registers the complete migration set. - Verification: Focused recovery test PASS; quick suite PASS. 
## SPOTAMP-QA-008

- Severity: CRITICAL
- Date: 2026-09-03
- Source: Pending companion playback outbox
- Status: FIXED
- Symptom: Pending playback rows had no originating-account identity and could be delivered after a credential switch.
- Root cause: The outbox schema and worker query were account-agnostic.
- Regression: Account identity isolation policy coverage plus migration version 16 compilation/quick-suite verification.
- Fix: Added nullable accountIdentity with migration 15→16; new rows are tagged and the worker only dispatches rows for the active identity. Legacy untagged rows are intentionally not dispatched.
- Verification: Focused policy tests PASS; quick suite PASS.


## SPOTAMP-QA-009

- Severity: HIGH
- Date: 2026-09-04
- Source: Download path safety
- Status: FIXED
- Symptom: A server-provided rating key was used directly as a local download filename.
- Root cause: Download path construction did not reject separators or control characters.
- Regression: DownloadPathPolicyTest.rejectsPathTraversalAndControlCharacters
- Fix: Reject unsafe identifiers before constructing target or temporary download paths.
- Verification: Downloads tier PASS.

## SPOTAMP-QA-010

- Severity: CRITICAL
- Date: 2026-09-04
- Source: Backend redirect credential safety
- Status: FIXED
- Symptom: A redirected backend request could expose authorization to an unintended target.
- Root cause: Redirect behavior lacked a device-independent regression contract.
- Regression: BackendApiRedirectSafetyTest.libraryRedirectIsNotFollowed
- Fix: Backend and download HTTP clients disable ordinary and TLS redirects.
- Verification: Focused redirect test PASS; sync contract tier PASS.

## SPOTAMP-QA-011

- Severity: HIGH
- Date: 2026-09-04
- Source: Room migration registration
- Status: FIXED
- Symptom: The defined 7→8 migration was omitted from the normal and recovery Room builders.
- Risk: A supported schema-7 upgrade could use destructive fallback instead of the intended no-op migration.
- Fix: Registered `MIGRATION_7_8` in both builders.
- Regression: `MusicDatabaseMigration7To8Test.migration7To8_isNoOpAndPreservesExistingRows`.
- Verification: `MusicDatabaseMigration*` focused suite PASS.


## SPOTAMP-QA-012

- Severity: MEDIUM
- Date: 2026-09-04
- Source: Windows PowerShell QA backend runner
- Status: FIXED
- Symptom: The disposable loopback contract check could not start on the supported Windows PowerShell host because it referenced an unavailable shorthand HTTP type.
- Root cause: The script assumed the System.Net.Http assembly and namespace were already loaded, and used a .NET Core-oriented type shorthand.
- Fix: Load System.Net.Http, use fully qualified HTTP types, and call SendAsync for Windows PowerShell compatibility.
- Regression: qa-backend-check.ps1 contract execution.
- Verification: Direct Windows PowerShell backend check PASS; subsequent full QA gate PASS.

## SPOTAMP-QA-013

- Severity: HIGH
- Date: 2026-09-04
- Source: Plex pagination collector
- Status: FIXED
- Symptom: A backend that repeatedly returned the same full page could keep pagination in an unbounded loop.
- Root cause: The collector stopped only on a short page and had no repeated-page or global safety bound.
- Regression: `PlexPaginationTest.repeatedFullPageFailsInsteadOfLooping`
- Fix: Detect repeated page fingerprints and fail clearly after the repeated-page threshold; enforce a 10,000-page safety cap.
- Verification: Focused pagination test PASS.
## SPOTAMP-QA-014

- Severity: MEDIUM
- Date: 2026-09-04
- Source: `scripts/qa.ps1` stress/endurance runner
- Status: FIXED
- Symptom: `-Seed` and `-Actions` could be reported in `QA_REPORT.json` while Gradle reused an up-to-date test result using the default campaign values.
- Root cause: Campaign inputs were environment-only and were not Gradle task inputs; cached test tasks could therefore skip execution.
- Regression: 5,000-action campaign output must record the requested seed and action count in JUnit system output.
- Fix: Pass campaign values as Gradle properties and force rerun only for stress/endurance invocations.
- Verification: `scripts\\qa.ps1 stress -Seed 271828182 -Actions 5000` PASS; JUnit records `seed=271828182 actions=5000`.
## SPOTAMP-QA-015

- Severity: MEDIUM
- Date: 2026-09-04
- Source: QA result aggregation
- Status: FIXED
- Symptom: Host-only tiers could count stale connected Android-test XML left by an earlier UI run.
- Root cause: `Collect-TestResults` scanned connected output unconditionally.
- Fix: Connected XML is collected only for connected-test Gradle tasks; host-only tiers scan host JUnit output.
- Verification: `quick` reports 139 host tests; `ui` reports 147 combined host/device tests after the 8 device tests pass.

## SPOTAMP-QA-016

- Severity: MEDIUM
- Date: 2026-09-04
- Source: QA-Lab Compose fixture journey
- Status: FIXED
- Symptom: Loading the 1,000-item QA fixture could leave the Compose test with no hierarchy and appear as an activity/startup failure.
- Root cause: The QA Lab sent synthetic relative `/qa/...` URLs through the production Media3 queue loader when no companion/Plex endpoint was configured; the invalid media items destabilized the test activity.
- Fix: Added `PlaybackManager.replaceQueueForQa`, which updates bounded in-memory queue state and clears the player without constructing or loading fixture media URIs. Renamed the local WAV test to make its infrastructure-only scope explicit.
- Verification: Direct `:app:connectedDebugAndroidTest` completed 9/9 on SM-S926U / Android 16 with zero failures/skips after the fix.

## SPOTAMP-QA-017

- Severity: HIGH
- Date: 2026-09-05
- Source: Download worker cancellation
- Status: FIXED
- Symptom: Cooperative WorkManager cancellation could be caught by the generic download failure path and misclassified as a retryable failure.
- Root cause: `CancellationException` was handled by the worker's broad `Exception` catch.
- Fix: Rethrow `CancellationException` before generic failure handling; repository cleanup removes the temporary `.part` file through its existing `finally` block.
- Verification: Focused `*Download*Test` suite PASS after the fix.


## SPOTAMP-QA-018

- Severity: HIGH
- Date: 2026-09-05
- Source: PlaybackManager singleton lifecycle
- Status: FIXED
- Symptom: After PlaybackManager.release(), a later activity or test could receive the released instance with a cancelled coroutine scope and no player.
- Reproduction: Run a companion-backed playback journey followed by a second Media3 playback journey in the same app process.
- Root cause: `release()` disposed the player and cancelled the scope but left the process singleton registered.
- Fix: Clear the singleton when the released instance is still the registered instance so the next caller receives a fresh manager.
- Verification: Companion playback plus the complete connected UI tier pass on SM-S926U / Android 16.

## SPOTAMP-QA-019

- Date: 2026-09-05
- Source: Download filesystem consistency
- Status: FIXED
- Risk: HIGH
- Symptom: Empty download responses or deletion paths could leave the QA acceptance boundary without an explicit regression for absent partial files and stale download indexes.
- Regression: `DownloadRepositoryIntegrationTest.emptyDownloadLeavesNoTargetPartialOrIndex`, `DownloadRepositoryIntegrationTest.deletingDownloadRemovesFileAndIndex`
- Fix/verification: The existing temporary-file/atomic-replacement implementation is now covered for empty responses, and deletion is verified to remove both the local file and persisted record. Focused `DownloadRepositoryIntegrationTest` PASS.
## SPOTAMP-QA-020

- Date: 2026-09-05
- Source: Real Media3 playback controls
- Status: FIXED
- Risk: HIGH
- Symptom: After a real local track entered the playing state, `pause()` could leave the public `isPlaying` StateFlow reporting `true` until an asynchronous player callback arrived or indefinitely in the connected fixture.
- Reproduction: Start a generated local WAV through `PlaybackManager.playTrack`, invoke `pause()`, and wait for `isPlaying == false`; the pre-fix connected regression timed out.
- Root cause: `play()` and `pause()` delegated to ExoPlayer but did not publish command intent synchronously; the manager relied only on `onIsPlayingChanged`.
- Regression: `Media3LocalFixtureInfrastructureTest.realMedia3ControlsPreserveQueueAndPlaybackState`
- Fix: Publish the requested playing state on the main-thread player mutation while retaining listener reconciliation for buffering, focus loss, and completion.
- Verification: Focused connected Media3 suite PASS: 3 tests, 0 failures on SM-S926U / Android 16.
## SPOTAMP-QA-021

- Date: 2026-09-05
- Source: QA result aggregation
- Status: FIXED
- Risk: MEDIUM
- Symptom: A filtered tier such as benchmark could report the entire accumulated JUnit inventory rather than the cases selected for that Gradle invocation.
- Root cause: The collector deduplicated XML globally but did not apply the tier's Gradle `--tests` pattern while counting cases.
- Fix: Apply the same wildcard filter to each JUnit test class/name before counting suites, tests, failures, errors, and skips.
- Verification: Benchmark tier PASS with 3 executed tests and 0 failures/errors/skips; quick tier remains PASS.
## SPOTAMP-QA-022

- Date: 2026-09-05
- Source: QA result aggregation
- Status: FIXED
- Risk: MEDIUM
- Symptom: A connected UI run reported 14 tests even though the device executed 12; two stale host cases were included by assemble/install collection and mixed result roots.
- Root cause: Assemble-only steps collected JUnit output, and connected test steps scanned both host and connected result directories.
- Fix: Collect results only for test tasks; connected tasks use only connected Android-test XML while host tasks use only host JUnit XML.
- Verification: Connected UI tier PASS with exactly 12 executed tests and 0 failures/errors/skips on SM-S926U / Android 16.

## SPOTAMP-QA-023

- Severity: MEDIUM
- Date: 2026-09-05
- Source: Lyrics parsing
- Status: FIXED
- Symptom: A malformed bracketed LRC record such as [not-a-timestamp] was emitted as lyric text.
- Root cause: The no-timestamp branch treated any non-metadata line as plain text, including bracketed records.
- Regression: LyricsParserTest.metadataAndUnparseableLinesDoNotBecomeFakeSyncedRows
- Fix: Ignore bracketed records that contain no valid timestamp while retaining ordinary unbracketed lyric text.
- Verification: Focused LyricsParserTest suite PASS; quick tier PASS with 153 tests and 0 failures/errors/skips.

## SPOTAMP-QA-024

- Severity: MEDIUM
- Date: 2026-09-05
- Source: Android lint / API compatibility
- Status: FIXED
- Symptom: `lintDebug` reported API-26-only file promotion calls for the API-24 minimum SDK, plus missing Media3 unstable-API opt-in and a camera hardware-feature declaration.
- Root cause: The atomic download promotion path used `java.nio.file` without an API guard; the existing Media3 integration used opt-in APIs without an explicit class-level annotation; the manifest declared camera permission without declaring camera as optional hardware.
- Fix: Retain atomic `Files.move` on API 26+, use a guarded legacy file-promotion fallback below API 26, annotate `PlaybackManager` with `@OptIn(UnstableApi::class)`, and declare the camera feature as optional.
- Verification: `:app:lintDebug` PASS with constrained Java/Gradle settings; `.\scripts\qa.ps1 quick` PASS with `QuickLint` and `Quick` both PASS, 153 tests and 0 failures/errors/skips.

## SPOTAMP-QA-025

- Severity: LOW
- Date: 2026-09-05
- Source: Android lint / locale stability
- Status: FIXED
- Symptom: Duration strings used the device-default locale implicitly.
- Root cause: `String.format` was called without an explicit locale in the now-playing and playlist detail duration formatters.
- Fix: Use `Locale.ROOT` for stable `m:ss` formatting across device locales.
- Verification: `:app:lintDebug` and `.\scripts\qa.ps1 quick` both PASS; 153 tests with no failures/errors/skips.

## SPOTAMP-QA-026

- Severity: HIGH
- Date: 2026-09-05
- Source: Release/network isolation audit
- Status: FIXED
- Symptom: The packaged network-security configuration contained a hardcoded public test-device endpoint, allowing release artifacts to retain an environment-specific cleartext destination.
- Root cause: A connected-test endpoint had been embedded in the shared network-security resource instead of being supplied by the companion test environment.
- Fix: Remove the public endpoint from the packaged resource; retain only loopback/emulator-local allowances for isolated QA infrastructure, and add an audit allowlist that rejects unapproved hardcoded domains.
- Verification: `:app:lintDebug`, `qa.ps1 quick`, and `qa-audit.ps1` PASS; release remains unsigned without explicit production credentials and no credentials are hardcoded.

## SPOTAMP-QA-027

- Severity: MEDIUM
- Date: 2026-09-05
- Source: QA runner / standalone audit
- Status: FIXED
- Symptom: `qa.ps1 audit` reported `QA report not found` even when a valid report existed immediately before the command.
- Root cause: The runner archived `qa-results/latest` during audit startup, then invoked `qa-audit.ps1` against the newly created empty directory.
- Fix: Preserve `qa-results/latest` for the audit tier; ordinary test/build tiers continue to archive the prior report before starting.
- Verification: Reproduced the failure, applied the scoped guard, regenerated a quick report, and verified `qa.ps1 audit`, direct `qa-audit.ps1`, PowerShell parsing, and targeted diff checks all PASS.

## SPOTAMP-QA-028

- Severity: CRITICAL
- Date: 2026-09-05
- Source: Room database safety
- Status: FIXED
- Symptom: Database construction enabled `fallbackToDestructiveMigration()`, allowing an unavailable migration path to erase valid cached library and playback data.
- Root cause: A startup-crash avoidance fallback was retained in both the primary and duplicate recovery builders without evidence that destructive recovery was safe.
- Fix: Remove destructive migration fallback and the duplicate recovery builder; use the canonical database name with the complete registered migration set and surface unsupported upgrades for explicit handling.
- Verification: Source audit rejects any destructive fallback; storage migration/recovery suite and quick lint/regression validation pass.

## SPOTAMP-QA-029

- Severity: HIGH
- Date: 2026-09-05
- Source: Disposable QA backend credential boundary
- Status: FIXED
- Symptom: The loopback QA backend accepted any non-empty Authorization header, so an accidentally supplied real-looking credential could be treated as valid test authentication.
- Root cause: The service checked only for header presence and the contract check used a non-sentinel token.
- Fix: Require the exact documented `SPOTCORE_TEST_TOKEN_DO_NOT_USE` sentinel, reject other credentials with `403`, and add a negative contract assertion.
- Verification: Standalone and `qa.ps1 qa-backend` checks pass with 8 assertions; generated QA artifacts contain no sentinel credentials; framework audit passes.

## SPOTAMP-QA-030

- Severity: MEDIUM
- Date: 2026-09-05
- Source: QA runner report lifecycle
- Status: FIXED
- Symptom: `qa.ps1 audit` preserved the existing report directory but overwrote `QA_REPORT.json` with an audit-only result, so the canonical latest test-run summary was lost.
- Root cause: The archive guard was corrected without separating audit output from normal tier output.
- Fix: Audit runs now write `QA_AUDIT_REPORT.json` and `.md` while leaving the existing `QA_REPORT.json` and `.md` intact.
- Verification: Backend failure-path report and successful backend report were exercised; audit validation passed while the canonical backend report remained available.

## SPOTAMP-QA-031

- Severity: MEDIUM
- Date: 2026-09-05
- Source: QA result classification
- Status: FIXED
- Symptom: A disposable QA backend that could not start was classified as a functional failure even though its contract assertions never executed.
- Root cause: The runner mapped every backend-check exception to `FAIL`.
- Fix: Startup/connectivity/timeout exceptions are now reported as `BLOCKED` with infrastructure classification; assertion failures remain functional `FAIL`.
- Verification: A loopback port-blocker probe produced a persisted `BLOCKED` backend tier and nonzero overall exit; the normal eight-assertion backend check remained PASS.

## SPOTAMP-QA-032

- Severity: LOW
- Date: 2026-09-05
- Source: QA framework audit
- Status: FIXED
- Symptom: Report outcome/classification mismatches could have passed framework validation, allowing an infrastructure block or functional failure to be mislabeled.
- Root cause: `qa-audit.ps1` validated outcome vocabulary but not the required `BLOCKED`/`FAIL` classification mapping.
- Fix: The audit now enforces `BLOCKED → infrastructure` and `FAIL → functional` for every reported tier.
- Verification: Framework audit passes against the current report, including the backend failure-path classification evidence.

## SPOTAMP-QA-033

- Severity: MEDIUM
- Date: 2026-09-05
- Source: Last.fm integration coverage
- Status: FIXED
- Symptom: Last.fm behavior had network-isolation coverage but no local contract proving request signing or graceful HTTP failure handling.
- Root cause: `LastFmScrobbler` used a fixed production endpoint with no test-only endpoint seam.
- Fix: Add a `@VisibleForTesting` endpoint override and disposable loopback fake tests for signed token requests and HTTP 503 handling; production defaults remain unchanged.
- Verification: Focused `LastFmIsolationContractTest` passed 2/2; quick gate passed 155 tests with zero failures/errors/skips; no real Last.fm endpoint was contacted.

## SPOTAMP-QA-034

- Severity: MEDIUM
- Date: 2026-09-05
- Source: Full-tier campaign isolation
- Status: FIXED
- Symptom: The full runner left the endurance stress action count in the process environment, so later UI and release Gradle invocations inherited `SPOTAMP_STRESS_ACTIONS=10000`.
- Root cause: Stress properties were set for endurance without being cleared before UI and release validation.
- Fix: Add an explicit stress-campaign cleanup before the full-tier UI and release phases.
- Verification: The full run reached connected UI with the leaked environment identified; the runner was corrected, the quick gate passed 155 tests, and the script now clears both stress properties before UI/release.

## SPOTAMP-QA-035

- Severity: HIGH
- Date: 2026-09-05
- Source: Connected-test reliability
- Status: FIXED
- Symptom: A stuck connected instrumentation task could hold the full runner indefinitely and prevent aggregate report generation.
- Root cause: Connected Gradle tasks had no process watchdog; the full run reproduced the condition at 10/12 UI tests.
- Fix: Add a 180-second connected-task watchdog with a process-local override, failure artifacts, and `BLOCKED` infrastructure classification on timeout.
- Verification: A one-second watchdog probe produced a persisted `UI=BLOCKED` report with infrastructure classification and failure-context artifacts; the normal quick gate remained green.

## SPOTAMP-QA-036

- Severity: LOW
- Date: 2026-09-05
- Source: QA framework self-audit
- Status: FIXED
- Symptom: The framework audit did not enforce retention of the connected-test watchdog, full-tier stress cleanup, or the separate audit-report path.
- Root cause: Those safeguards were implemented but not included in the audit’s required configuration terms.
- Fix: `qa-audit.ps1` now verifies all three safeguards are present in the runner.
- Verification: Framework audit passes against the current runner and canonical report.

## SPOTAMP-QA-037

- Severity: MEDIUM
- Date: 2026-09-05
- Source: QA Lab fixture journey
- Status: FIXED
- Symptom: The QA fixture playback-smoke button could stall connected instrumentation after the fixture was loaded.
- Root cause: Each of 100 shuffle toggles rebuilt and prepared the entire Media3 queue, multiplying player work for a state-only stress action.
- Fix: Add a QA-only shuffle-state stress helper that updates observable queue state without repeatedly reloading Media3; real player transitions remain covered by dedicated playback instrumentation.
- Verification: `QaLabFixtureJourneyTest` passed individually and the complete 12-test connected UI tier passed on SM-S926U / Android 16.

## SPOTAMP-QA-038

- Severity: MEDIUM
- Date: 2026-10-02
- Source: QA JUnit result aggregation
- Status: FIXED
- Symptom: The UI run's XML recorded one failure and two assumption skips, while `summary.json` and the report line said zero failures and zero skips.
- Root cause: Empty JUnit marker elements such as `<failure/>` and `<skipped/>` were tested by text value, which is false for empty nodes in PowerShell's XML adapter.
- Regression: `qa-audit.ps1` requires presence-based parsing for failure, error, and skipped markers; a synthetic empty-node fixture verifies all three outcome types.
- Fix: `qa.ps1` now uses `SelectSingleNode` to count each marker by node presence.
- Verification: PowerShell parsed the runner successfully; the captured XML parsed as 13 tests, 1 failure, 0 errors, 2 skips, and the empty-node fixture parsed as 1/1/1.

## SPOTAMP-QA-039

- Severity: HIGH
- Date: 2026-10-02
- Source: Concurrent connected QA runs
- Status: FIXED
- Symptom: The connected UI tier ended with an instrumentation process failure during Search. Its captured log also contains a real-companion instrumentation command at the same time, followed by QA app/test package installation and an ActivityManager instrumentation crash; no app-level fatal exception was recorded.
- Root cause: `qa.ps1` and `qa-companion-real.ps1` could run concurrently against the same QA package, device, Gradle outputs, and `qa-results/latest` tree. A concurrent package install could force-stop an active instrumentation run.
- Fix: Add a shared host-wide named mutex. Both harnesses acquire it before changing shared reports or starting device/package work; the generic runner passes its ownership through to the nested companion harness. A second harness now fails fast with a blocked message.
- Verification: PowerShell parser checks passed for both runners and the mutex helper; a separate PowerShell process and a competing `qa.ps1 quick` invocation were rejected while the mutex was held; `qa.ps1 audit` passed under the mutex, and a follow-up acquisition confirmed it was released. `qa-audit.ps1` statically guards the shared mutex and both call sites. The single authorized sequential UI rerun then completed 23 tests without an instrumentation-process crash; its separate first-run precondition failure is tracked in SPOTAMP-QA-041.

## SPOTAMP-QA-040

- Severity: MEDIUM
- Date: 2026-10-02
- Source: Companion Search with partial music analysis
- Status: FIXED
- Symptom: A style query still showed a track after its CLAP analysis moved to Running and the local Smart Search correctly removed that stale style.
- Root cause: An empty local result fell through to the broad SpotCore search endpoint, which could return the same track from its broader index.
- Regression: `SmartSearchServiceTest.staleAnalysisLabelsRemainLocalQueriesWhenNoCurrentTrackMatches` verifies that a persisted stale mood/style/genre label remains a local-only query even when current filtering returns no track.
- Fix: Treat query text matching a mood/style/genre label in the saved intelligence snapshot as local-query vocabulary, independent of whether the model component is currently complete; do not invoke the broad companion fallback for that query.
- Verification: Focused Smart Search test passed; current full QA unit suite passed (444 tests / 123 suites); `:app:compileQaAndroidTestKotlin` passed. QA and unsigned release assembly also pass, including `lintVitalRelease`. The latest real-companion report predates the fix; integration re-verification remains open.

## SPOTAMP-QA-041

- Severity: MEDIUM
- Date: 2026-10-02
- Source: First-run pairing connected UI journey
- Status: FIXED
- Symptom: The UI tier failed waiting for the Home first-run pairing action on an unpaired QA profile with existing cached content.
- Root cause: The test checked only that the profile was unpaired; it did not establish that Home was the active route or that the saved library was empty, both of which affect first-run card visibility.
- Fix: Require both an unpaired profile and an empty QA library, and explicitly select Home before checking the first-run action.
- Verification: Updated Android-test sources compile. The single authorized connected run recorded this as its only failure; the corrected precondition was not rerun on-device.

## SPOTAMP-QA-042

- Severity: LOW
- Date: 2026-10-02
- Source: Connected UI notification-permission lifecycle
- Status: FIXED
- Symptom: The UI report recorded notification cleanup PASS after the QA package disappeared at the end of instrumentation; a `pm uninstall` also removed any QA app data.
- Root cause: The runner delegated to Gradle's connected-test task, whose teardown uninstalls the QA app and test package. Treating an absent app as clean concealed both the missed explicit revoke and the app-data loss.
- Fix: The generic UI and real-companion runners now assemble/install APKs separately and execute the already-installed AndroidJUnitRunner directly. The UI tier requires the QA package to remain installed and reports cleanup PASS only after an explicit `pm revoke` succeeds. The audit exercises instrumentation-output parsing and guards against reintroducing Gradle connected-test teardown.
- Verification: Direct `scripts/qa-audit.ps1` passes, including successful/failing instrumentation parser fixtures; PowerShell parser checks pass for the runner, companion runner, audit, and helper; offline `:app:assembleQaAndroidTest` passes. Device revalidation is still open; no reinstall was made as part of the harness change. Production was untouched.

## SPOTAMP-QA-043

- Severity: LOW
- Date: 2026-10-02
- Source: Missing track metadata in Library and playback
- Status: FIXED
- Symptom: Active detail/Library paths hard-coded “Unknown Artist,” and playback mapping could use a track's album title as its artist when `grandparentTitle` was absent.
- Root cause: Fallback labels were embedded in UI/ViewModel code, and metadata mapping treated the track's `parentTitle` (album) as an artist fallback.
- Regression: `HomeFeedRetryTest.metadataPlaybackUsesLocalizedUnknownLabelsAndAlbumArtistFallback` verifies localized Unknown Artist/Album labels and that `albumArtist` is preferred to the album title.
- Fix: Add shared unknown-metadata resources for active surfaces, use them for blank/missing values, and map track artist as `grandparentTitle` → `albumArtist` → Unknown Artist.
- Verification: Full QA unit suite passed (444 tests / 123 suites); Android-test compilation, QA assembly, and unsigned release assembly with `lintVitalRelease` passed.

## SPOTAMP-QA-044

- Severity: LOW
- Date: 2026-10-02
- Source: Shared artist and album presentation fallbacks
- Status: FIXED
- Symptom: Missing track/album metadata and the internal “Various Artists” marker could surface inconsistently across Search, player, playlist, download, and recommendation surfaces; Search could show an album title as a track's artist, and artist popular-track actions could use “Popular Tracks” as a fake album name.
- Root cause: Individual surfaces applied their own fallback behavior, while a domain grouping marker was also used directly as display copy; Search also fell back from primary artist to parent album instead of album artist.
- Regression: `CommonUiCopyTest` covers missing artist/album labels, localized “Various Artists,” and preservation of real names. `SearchCopyTest.trackWithoutPrimaryArtistUsesAlbumArtistInsteadOfAlbumTitle`, `NowPlayingQueueSemanticsTest`, and `CachedAlbumMetadataPresentationTest` cover Search attribution, playback, and cached-album fallbacks.
- Fix: Centralize artist/album display formatting in resource-backed presentation helpers, preserve raw values for grouping/navigation, prefer album artist in Search when primary artist metadata is absent, and replace the fake album fallback with the shared Unknown Album label.
- Verification: Clean QA unit suite passed (461 tests / 129 suites, 0 failures/errors/skips). Clean QA and unsigned release assembly passed, including `lintVitalRelease`.

## SPOTAMP-QA-045

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Mini-player touch-target audit
- Status: FIXED
- Symptom: The mini-player play/pause semantic bounds measured 40.18dp wide on the Galaxy S24, below the 48dp target minimum.
- Root cause: Material's surrounding minimum-interactive expansion did not make the button's own accessible layout bounds 48dp.
- Regression: `TouchTargetLayoutDeviceTest.miniPlayerPlayPauseTargetMeetsMinimumAtNormalTextScale` and `...AtLargeTextScale` verify 48dp bounds, Button role, click behavior, and distinct play/open actions at a 320dp viewport.
- Fix: Extract the mini-player's state-free production content for safe fixture rendering and apply explicit 48dp minimum width/height to its play/pause action.
- Verification: Galaxy S24 / Android 16 connected suite passed 32 tests across 14 suites (0 failures/errors/skips); focused `NowPlayingQueueSemanticsTest` passed 20/20; full Quick QA passed lint and 522 host tests across 134 suites; unsigned release assembly and `lintVitalRelease` passed.

## SPOTAMP-QA-046

- Severity: LOW
- Date: 2026-10-07
- Source: Connected UI target/test APK pairing
- Status: FIXED
- Symptom: Instrumentation referenced a newly added production mini-player composable that was missing from the installed QA APK, causing `NoSuchMethodError`.
- Root cause: The UI runner assembled only `assembleQaAndroidTest`, then installed the pre-existing QA target APK, which could be stale.
- Regression: `qa-audit.ps1` asserts that the generic UI runner separately assembles `:app:assembleQa` and `:app:assembleQaAndroidTest` before installation.
- Fix: Build the target QA app first and the matching instrumentation APK second before `adb install -r`.
- Verification: `qa-audit.ps1` passed; the subsequent Galaxy S24 / Android 16 UI run passed 32 tests across 14 suites with 0 failures/errors/skips and verified both packages remained installed.

## SPOTAMP-QA-047

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Search query clear action
- Status: FIXED
- Symptom: Search's clear-query button measured 40.18dp wide on the Galaxy S24 at both 100% and 130% text scale.
- Root cause: The default Material `IconButton` semantic bounds were smaller than the 48dp touch-target minimum.
- Regression: `TouchTargetLayoutDeviceTest.searchClearTargetMeetsMinimumAtNormalTextScale` and `...AtLargeTextScale` verify bounds, Button role, and callback; `ArtistSimilarArtistNavigationTest.searchClearActionMeetsMinimumAndClearsTheQuery` covers the same action in host Compose.
- Fix: Set explicit 48dp minimum width and height on the Search clear action.
- Verification: Galaxy S24 / Android 16 UI suite passed 34 tests across 14 suites (0 failures/errors/skips); focused Search/artist host suite passed 5/5; full Quick QA passed lint and 523 tests across 134 suites (0 failures/errors/skips).

## SPOTAMP-QA-048

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Settings connection-error recovery
- Status: FIXED
- Symptom: The error-dismiss icon had no explicit 48dp target-size guarantee.
- Regression: `TouchTargetLayoutDeviceTest.settingsConnectionErrorDismissTargetMeetsMinimumAtNormalTextScale` and `...AtLargeTextScale` verify 48dp bounds, Button role, and callback. `SettingsRestoredConnectionCheckTest.settingsEntryChecksReachabilityWhenHomeIsNotCollected` also verifies the real Settings error banner clears when dismissed.
- Fix: Extract the production dismiss button and apply explicit 48dp minimum width and height.
- Verification: Galaxy S24 / Android 16 UI suite passed 36 tests across 14 suites (`20261007T093635098Z-ui`); focused Settings host suite passed 4/4; full Quick QA passed lint and 523 host tests across 134 suites (`20261007T093910081Z-quick`), all with 0 failures/errors/skips. QA package retention and notification cleanup passed.

## SPOTAMP-QA-049

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Settings companion-token visibility
- Status: FIXED
- Symptom: The show/hide token icon had no explicit 48dp target-size guarantee.
- Regression: `TouchTargetLayoutDeviceTest.settingsTokenVisibilityTargetMeetsMinimumAtNormalTextScale` and `...AtLargeTextScale` verify 48dp bounds, Button role, click behavior, and the dynamic Show/Hide label. The Settings recovery journey checks the same label transition in the actual Settings form after expanding manual connection options.
- Fix: Extract the token visibility control and apply explicit 48dp minimum width and height.
- Verification: Focused Settings host suite passed 4/4; Galaxy S24 / Android 16 connected UI suite passed 38 tests across 14 suites (`20261007T095903114Z-ui`) with 0 failures/errors/skips. The QA package stayed installed and notification cleanup passed.

## SPOTAMP-QA-050

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Settings connection-error recovery actions
- Status: FIXED
- Symptom: The technical-details disclosure measured 40dp high; the Retry and manual-connection TextButtons had no explicit 48dp minimum guarantee.
- Root cause: Material TextButton default sizing left the Details action below the 48dp target minimum.
- Regression: `SettingsRestoredConnectionCheckTest.settingsEntryChecksReachabilityWhenHomeIsNotCollected` verifies all three recovery actions have 48dp bounds, Button role, and click action. `TouchTargetLayoutDeviceTest.settingsConnectionDetailsTargetMeetsMinimumAtNormalTextScale` and `...AtLargeTextScale` verify the production Details toggle's bounds and expanded/collapsed state.
- Fix: Add explicit 48dp minimum height to Retry, manual-connection options, and Details; extract the Details control to keep its state semantics and target size together.
- Verification: Focused Settings host suite passed 4/4; Galaxy S24 / Android 16 connected UI suite passed 40 tests across 14 suites (`20261007T100828086Z-ui`) with 0 failures/errors/skips. The QA app/test packages remained installed and `POST_NOTIFICATION` returned to `ignore`.

## SPOTAMP-QA-051

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Library artist and playlist actions
- Status: FIXED
- Symptom: Artist Play, playlist Share, and Library Back each measured 40dp wide; related Shuffle, Download, and Delete icon actions relied on the same default IconButton sizing.
- Root cause: The default IconButton semantic bounds were below the 48dp touch-target minimum.
- Regression: `ArtistGridActionsTest.artistActionsAreTruthfullyNamedAndEveryVisibleActionWorks` and `LibraryCopyTest.playlistActionsNameTheirTargetAndInvokeTheRequestedOperation` / `libraryBackButtonCopyMatchesFilterAndNavigationBehavior` verify bounds, Button role, and callbacks. `TouchTargetLayoutDeviceTest.libraryArtistActionsMeetMinimumAtNormalTextScale`, `...AtLargeTextScale`, `libraryPlaylistActionsMeetMinimumAtNormalTextScale`, `...AtLargeTextScale`, and `libraryBackActionMeetsMinimumAtNormalTextScale` / `...AtLargeTextScale` cover connected bounds and callbacks.
- Fix: Apply explicit 48dp minimum width and height to the artist Play/Shuffle pair, playlist Share/Download/Delete actions, and Library Back control.
- Verification: Focused Library host suites passed 34 tests; Galaxy S24 / Android 16 connected UI suite passed 46 tests across 14 suites (`20261007T103125351Z-ui`) with 0 failures/errors/skips. QA package retention and notification cleanup passed.

## SPOTAMP-QA-052

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Lyrics close and Premium Stations navigation actions
- Status: FIXED
- Symptom: The Lyrics close icon and three Premium Stations back arrows relied on default IconButton sizing without an explicit 48dp minimum.
- Regression: `LyricsPanelInteractionTest.closeButtonClosesLyricsPanel` verifies the Lyrics close control has 48dp bounds, Button role, and closes the panel. `TouchTargetLayoutDeviceTest.lyricsCloseTargetMeetsMinimumAtNormalTextScale` / `...AtLargeTextScale` and `premiumStationsBackTargetMeetsMinimumAtNormalTextScale` / `...AtLargeTextScale` verify connected bounds and callbacks on the production controls shared by the three Premium Stations screens.
- Fix: Add a 48dp minimum to the Lyrics close action and consolidate Premium Stations back navigation into one tagged, accessible 48dp component.
- Verification: Focused Lyrics host suite passed 3/3; Galaxy S24 / Android 16 connected UI suite passed 50 tests across 14 suites (`20261007T104029100Z-ui`) with 0 failures/errors/skips. The preinstalled QA app was retained, the notification cleanup tier passed, and `POST_NOTIFICATION` returned to `ignore`.

## SPOTAMP-QA-053

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Standard and custom playlist detail actions
- Status: FIXED
- Symptom: Standard-playlist Shuffle/Download and custom-playlist Shuffle/Save/Download/Add-to-queue relied on default IconButton sizing without an explicit 48dp minimum.
- Regression: `PlaylistDetailCopyTest.playlistActionsNameTheirTargetAndInvokeEachAction` and `customPlaylistActionsNameTheirTargetAndSavedStatePreventsDuplicateSave` preserve action behavior. `TouchTargetLayoutDeviceTest.playlistDetailActionsMeetMinimumAtNormalTextScale` / `...AtLargeTextScale` and `customPlaylistActionsMeetMinimumAtNormalTextScale` / `...AtLargeTextScale` verify bounds, Button role, and callbacks on-device.
- Fix: Add explicit 48dp minimum width and height to all six compact playlist-detail actions. Existing Play-all and per-track Remove controls already met the target minimum.
- Verification: `PlaylistDetailCopyTest` passed 7/7; Galaxy S24 / Android 16 connected UI suite passed 54 tests across 14 suites (`20261007T104732078Z-ui`) with 0 failures/errors/skips. QA app retention and notification cleanup passed, and `POST_NOTIFICATION` returned to `ignore`.

## SPOTAMP-QA-054

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Production Now Playing transport controls
- Status: FIXED
- Symptom: The Now Playing header and core shuffle/previous/next/repeat controls did not have an explicit 48dp minimum target in compact layouts.
- Regression: `NowPlayingQueueSemanticsTest` asserts the production control bounds at normal and 130% text scale in a 320×568dp viewport. `TouchTargetLayoutDeviceTest.nowPlayingTransportTargetsMeetMinimumAtNormalTextScale` and `...AtLargeTextScale` verify the same controls on the Galaxy S24.
- Fix: Apply explicit 48dp minimum touch-target dimensions to the active Now Playing header and primary transport actions.
- Verification: `NowPlayingQueueSemanticsTest` passed 20/20; full QA lint and unit tests passed (524 tests across 134 suites, 0 failures/errors/skips); Galaxy S24 / Android 16 connected UI passed 56 tests across 14 suites (`20261008T002149635Z-ui`), including the new normal/130% transport-bound checks. QA retention and notification cleanup passed.

## SPOTAMP-QA-055

- Severity: HIGH
- Date: 2026-10-07
- Source: Music playback foreground-service lifecycle
- Status: FIXED
- Symptom: Playback could crash with `ForegroundServiceDidNotStartInTimeException` after a new start command arrived while the service was still alive but its foreground notification had been detached.
- Root cause: A service-running guard skipped re-promotion for an already-created service; the observer can detach the notification while the service object remains started.
- Regression: `MusicPlaybackServiceContractTest.everyStartCommandReentersForegroundAfterNotificationWasDetached` detaches the notification, issues a fresh start command, and verifies foreground state and notification identity are restored.
- Fix: Promote immediately before MediaSession initialization and re-promote on every `onStartCommand`, removing the stale service-running guard.
- Verification: `MusicPlaybackServiceContractTest` passed 3/3; full QA lint and unit tests passed (524 tests across 134 suites, 0 failures/errors/skips); the subsequent Galaxy S24 / Android 16 connected UI suite passed 56 tests across 14 suites (`20261008T002149635Z-ui`). QA retention passed and `POST_NOTIFICATION` returned to `ignore`.

## SPOTAMP-QA-056

- Severity: LOW
- Date: 2026-10-07
- Source: Home first-run pairing entry
- Status: FIXED
- Symptom: The unpaired Home headline, “Connect SpotCore to get started,” could read as an instruction about an existing connection rather than the next action.
- Regression: `HomePairingEntryTest` and `HomeFirstRunJourneyTest` verify the revised headline and preserve the first-run pairing and Settings actions.
- Fix: Change the headline to “Connect to SpotCore to get started.”
- Verification: Focused Home host tests passed; the Galaxy S24 / Android 16 connected UI suite passed 59 tests across 14 suites (`20261008T004201256Z-ui`), with 0 failures/errors/skips.

## SPOTAMP-QA-057

- Severity: MEDIUM
- Date: 2026-10-07
- Source: SpotCore HTTP 429 failure presentation
- Status: FIXED
- Symptom: Rate-limit recovery exposed technical request-count/HTTP jargon instead of a useful next step.
- Regression: `ProductFailurePresentationTest` checks the generic and pairing recovery messages and rejects visible status-code/request-count jargon.
- Fix: Use concise “SpotCore is busy right now” copy with a wait-and-retry instruction.
- Verification: Focused failure-presentation tests passed; the Galaxy S24 / Android 16 connected UI suite passed 59 tests across 14 suites (`20261008T004201256Z-ui`), with 0 failures/errors/skips.

## SPOTAMP-QA-058

- Severity: LOW
- Date: 2026-10-07
- Source: Settings manual connection terminology
- Status: FIXED
- Symptom: Settings called the SpotCore credential a device token, while the field and surrounding guidance describe it as an access key.
- Regression: `SettingsCopyTest` pins the access-key labels and recovery guidance; `SettingsRestoredConnectionCheckTest` verifies the expanded production form presents the matching field label.
- Fix: Use access-key terminology consistently for the field, show/hide action, URL guidance, and incomplete-credential error.
- Verification: Focused Settings host tests passed; the Galaxy S24 / Android 16 connected UI suite passed 59 tests across 14 suites (`20261008T004201256Z-ui`), with 0 failures/errors/skips.

## SPOTAMP-QA-059

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Home first-run and recovery actions
- Status: FIXED
- Symptom: The first-run Settings link and Home recovery TextButtons measured about 40.18dp high, below the 48dp target minimum.
- Regression: `HomePairingEntryTest` and `HomeFirstRunJourneyTest` check first-run target bounds; `HomeRecoveryBannerDeviceTest` checks Details, Retry, Settings, and error dismissal at normal and 130% text scale, including their actions.
- Fix: Apply explicit 48dp minimum heights to the first-run Settings link and Home recovery TextButtons.
- Verification: Focused Home host tests passed; the Galaxy S24 / Android 16 connected UI suite passed 59 tests across 14 suites (`20261008T004201256Z-ui`), with 0 failures/errors/skips. QA app and instrumentation package retention passed, and `POST_NOTIFICATION` returned to `ignore`.

## SPOTAMP-QA-060

- Severity: MEDIUM
- Date: 2026-10-07
- Source: Search downloaded-only filter
- Status: FIXED
- Symptom: Search scaled its Downloaded only Switch to 70%, and the control did not guarantee the 48dp minimum target; the unscaled Material Switch semantics bounds were 32dp high.
- Regression: `DownloadedOnlyToggleTest.compactLargeTextSearchHeaderKeepsSwitchTargetAndCopyVisible` checks the production header at 320dp and 130% text, including target bounds, visible labels, and the toggle callback. `TouchTargetLayoutDeviceTest.searchDownloadedOnlyTargetMeetsMinimumAtNormalTextScale` and `searchDownloadedOnlyTargetMeetsMinimumAtLargeTextScale` exercise the target on device.
- Fix: Remove the visual scale and enforce a 48dp minimum size in the shared Downloaded only Switch component.
- Verification: Focused host suite passed 2/2; Galaxy S24 / Android 16 connected UI passed 62 tests across 15 suites (2 unpaired-profile guards skipped; 0 failures/errors; `20261008T015727635Z-ui`). Quick passed 527 tests across 134 suites (`20261008T015857722Z-quick`). QA retention passed and `POST_NOTIFICATION` is `ignore`.

## SPOTAMP-QA-061

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Search query loading and cancellation
- Status: FIXED
- Symptom: When a user edited a query while its previous request was still finishing cancellation cleanup, the old request could clear the shared loading flag and make the current request appear idle.
- Regression: `HomeFeedRetryTest.cancelledSearchCannotClearLoadingStateForTheCurrentQuery` keeps the old request in non-cancellable cleanup until the newer query starts, then verifies the current loading state and results remain visible. The full `HomeFeedRetryTest` class also passes.
- Fix: Give each search a generation and allow only the current generation to update results, error, or loading state; propagate coroutine cancellation instead of treating it as a search error.
- Verification: Focused regression and `HomeFeedRetryTest` passed. Galaxy S24 / Android 16 connected UI passed 62 tests across 15 suites (2 protected-profile skips; 0 failures/errors; `20261008T023225512Z-ui`). Quick passed lint and 528 tests across 134 suites (0 failures/errors/skips; `20261008T023435550Z-quick`). QA app/instrumentation retention passed and `POST_NOTIFICATION` returned to `ignore`; QA data was preserved and production was not targeted.

## SPOTAMP-QA-062

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Search connection-failure recovery
- Status: FIXED
- Symptom: Search failure copy told listeners to check the SpotCore connection, but exposed only Retry; the visible Settings tab was not connected to that recovery message.
- Regression: `SearchCopyTest.unavailableStateOpensConnectionSettings` verifies the action. `SearchCopyTest.unavailableStateKeepsBothRecoveryActionsReachableAtLargeTextInCompactViewport` and `TouchTargetLayoutDeviceTest.searchUnavailableRecoveryActionsMeetMinimumAtNormalTextScale` / `...LargeTextScale` verify both actions remain reachable. `MainAppScreenFirstRunRouteTest.searchConnectionSettingsRoutePreservesTheQueryForBackNavigation` verifies Settings returns to the current query.
- Fix: Add a “Connection settings” action beside Retry and preserve the current Search query in the back-stack route.
- Verification: Focused Search/navigation suites passed 21 tests. Galaxy S24 / Android 16 connected UI passed 64 tests across 15 suites (2 protected-profile skips; 0 failures/errors; `20261008T025950332Z-ui`). Quick passed lint and 532 tests across 134 suites (0 failures/errors/skips; `20261008T030137419Z-quick`). QA retention passed; `POST_NOTIFICATION` returned to `ignore`; QA data was preserved and production was not targeted.

## SPOTAMP-QA-063

- Severity: LOW
- Date: 2026-10-08
- Source: Search whitespace-only query header
- Status: FIXED
- Symptom: The Search body treated whitespace-only input as an empty query, but the header labeled the same state “Results.”
- Regression: `DownloadedOnlyToggleTest.whitespaceOnlyQueryKeepsTheSearchHeaderInItsEmptyState` protects the empty-state title.
- Fix: Use the same blank-query condition for Search header and body state selection.
- Verification: Focused `DownloadedOnlyToggleTest` passed; the connected UI suite passed 64 tests across 15 suites (`20261008T025950332Z-ui`) and Quick passed 532 tests across 134 suites (`20261008T030137419Z-quick`), with no failures or errors.

## SPOTAMP-QA-064

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Library load-failure retry layout at large text
- Status: FIXED
- Symptom: In a 288×200dp host at 130% text scale, the Library load-failure Retry control was clipped to a 12.44dp visible height.
- Root cause: The centered, non-scrollable error column placed its primary recovery action below the compact viewport.
- Regression: `TouchTargetLayoutDeviceTest.libraryLoadFailureRetryTargetMeetsMinimumAtNormalTextScale` and `...AtLargeTextScale` assert the production Retry control’s 48dp bounds and callback.
- Fix: Keep the state top-aligned and vertically scrollable, and place Retry immediately after the heading so it remains reachable before longer error detail.
- Verification: `LibraryCopyTest` passes 36/36, including a local 288×200dp/130% retry-and-scroll regression. The first Galaxy S24 / Android 16 run (`20261008T033255959Z-ui`) exposed the clipped target; the immediate post-fix attempt was blocked by a device disconnect. After reconnect, `20261008T042042574Z-ui` passed 68 tests across 15 suites (0 failures/errors, 2 expected skips), and the target test passed at both normal and large text scales. QA app/instrumentation retention and notification cleanup passed; a follow-up app-op read returned `POST_NOTIFICATION: ignore`. Quick `20261008T040107784Z-quick` passed lint and 537 tests across 135 suites (0 failures/errors/skips) on the same source state. QA data was preserved and production was not targeted.

## SPOTAMP-QA-065

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Home Recently Added shelf failure state
- Status: FIXED
- Symptom: A Plex Recently Added request failure was treated as a successful empty response, silently removing the shelf and leaving no retry route.
- Regression: `MusicRepositoryLibraryFailureTest.plexArtistAlbumAndRecentlyAddedFailuresAreNotConvertedIntoSuccessfulEmptyLists` verifies HTTP 503 is surfaced. `HomeFeedRetryTest.failedRecentlyAddedLoadKeepsHomeShelfAndRetryRecoversIt` verifies last-good data retention, safe error copy, disabled retry-in-progress state, and successful recovery; `...changingLibrariesDoesNotShowThePreviousLibraryShelfAfterFailure` prevents cross-library stale content.
- Fix: Propagate Plex request failures, expose independent Recently Added loading/error state, retain the last good album list, and show Home’s Retry/Connection settings banner. Keep SpotCore Home behavior server-authoritative.
- Verification: All three focused QA host test cases passed; final-source lint, QA APK assembly, and Android-test Kotlin compilation passed. Full direct `:app:testQaUnitTest --offline --no-daemon` passed 539 tests across 135 suites (0 failures/errors/skips). Connected Galaxy S24 / Android 16 report `20261008T045555816Z-ui` passed 68 tests across 15 suites (2 protected-profile skips; 0 failures/errors), retained the QA app and instrumentation package, and restored `POST_NOTIFICATIONS` to `ignore`. QA data was preserved and production was not targeted. The Quick wrapper report reused the completed test task and lists 0 newly executed tests; a forced seeded rerun later reproduced a Compose idle loop in `NowPlayingQueueSemanticsTest.finalQueueItemDisablesNextUnlessRepeatAllWillWrap`, which passes when run alone.

## SPOTAMP-QA-066

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Search blank-state connection guidance
- Status: FIXED
- Symptom: On a paired/offline profile, Home correctly showed “Offline library available” while blank Search implied normal connected search. When SpotCore was unavailable and no library was saved, blank Search had no direct recovery action.
- Regression: `SearchCopyTest.companionOfflineSearchHintExplainsSavedDetailsAndPlayableDownloads` verifies the offline explanation; `SearchCopyTest.companionUnavailableEmptySearchOffersConnectionSettings` verifies the unavailable guidance and Settings callback.
- Fix: Blank Search now consumes the shared companion connection status, distinguishes checking/offline/unavailable from connected and first-run states, and offers Connection settings for an unavailable connection with no saved library.
- Verification: Both focused Search cases pass and `:app:lintDebug` passes. Connected Galaxy S24 / Android 16 report `20261008T052751642Z-ui` passed 68 tests across 15 suites (0 failures/errors; 2 protected-profile skips), preserved QA data and both QA packages, and restored `POST_NOTIFICATIONS` to `ignore`. On-device offline Search copy was verified at 100% and 130% text; font scale was restored to 1.0 and production was untouched. The fresh full Quick run was stopped after the known `NowPlayingQueueSemanticsTest.setUp` Compose root wait recurred; no full-Quick PASS is claimed.

## SPOTAMP-QA-067

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Search results while SpotCore is offline
- Status: FIXED
- Symptom: Search could display cached artist/album/track details during an outage without clarifying that only tracks with downloaded audio can play offline.
- Regression: `SearchCopyTest.offlineSearchResultsExplainThatCachedDetailsAreNotAudioAtLargeText` verifies the notice copy, polite announcement, and large-text visibility. `SearchCopyTest.searchShowsOfflineResultsNoticeOnlyWhenSpotCoreIsOfflineWithSavedLibrary` verifies the notice’s state gate.
- Fix: Add a scrollable, polite offline notice above populated Search results only when the paired SpotCore connection is offline and a saved library is available.
- Verification: `SearchCopyTest` passes 17/17; `:app:lintDebug` passes. Galaxy S24 / Android 16 connected report `20261008T054400345Z-ui` passed 68 tests across 15 suites (0 failures/errors; 2 protected-profile skips), retained QA app/instrumentation, and restored `POST_NOTIFICATIONS` to `ignore`. A live `QA` query displayed the notice above cached Artist and Album results; screenshot: `tmp/wave1-search-offline-results-100-20261008.png`. QA data and production were untouched. Full Quick remains pending because `NowPlayingQueueSemanticsTest.setUp` stalls in Compose `waitForIdle` in the full host run.

## SPOTAMP-QA-068

- Severity: LOW
- Date: 2026-10-08
- Source: Robolectric queue-scroll test synchronization
- Status: FIXED
- Symptom: Full Compose/Robolectric host runs could stall for an extended time in Espresso idle synchronization after screenshot and queue-semantics tests clicked the production queue button, whose scroll animation was still running in the paused host looper.
- Regression: The queue-content cases in `NowPlayingQueueSemanticsTest` and the player screenshots in `ProductStateScreenshotTest` now position the list deterministically before assertions/capture. Focused final-queue and single-track screenshot cases pass.
- Fix: Expose a non-accessibility test tag on the Now Playing lazy list and use `performScrollToIndex(1)` in host tests. The app's production queue-button animation is unchanged.
- Verification: Quick report `20261008T061633059Z-quick` passed lint and 543 host tests across 135 suites (0 failures/errors/skips). Galaxy S24 / Android 16 connected UI report `20261008T062106881Z-ui` passed 68 tests across 15 suites (2 expected protected-profile skips; 0 failures/errors); QA app/instrumentation retention passed and `POST_NOTIFICATION` returned to `ignore`. Both reports are against the same source state. QA data was preserved and production was not targeted.

## SPOTAMP-QA-069

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Downloads empty and failure states
- Status: FIXED
- Symptom: Downloads showed “Nothing saved for offline listening yet” whenever there were no playable files, even when an active download or failed/pending row was already visible with its own progress or Retry action.
- Regression: `DownloadsEmptyStateTest.emptyPromptOnlyAppearsWhenThereAreNoCompletedPendingOrActiveDownloads` covers empty, completed, pending, and active states. `DownloadsFailureRecoveryDeviceTest.failedDownloadOffersSafeGuidanceAndOptInRedactedDetails` verifies the real Downloads route keeps failure guidance and opt-in technical details while suppressing the unrelated empty prompt.
- Fix: Gate the empty-state CTA on there being no completed files, no pending download rows, and no active progress.
- Verification: Focused Downloads presentation tests passed (12/12). Quick report `20261008T063812707Z-quick` passed lint and 546 host tests across 135 suites (0 failures/errors/skips). Galaxy S24 / Android 16 connected UI report `20261008T064239508Z-ui` passed 68 tests across 15 suites (2 expected protected-profile skips; 0 failures/errors), including the updated failed-download route. QA app/instrumentation retention passed and `POST_NOTIFICATION` returned to `ignore`; QA data was preserved and production was not targeted.

## SPOTAMP-QA-070

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Downloads per-item accessibility actions
- Status: FIXED
- Symptom: Every pending/failed row exposed the same screen-reader name, “Retry” or “Cancel,” so the action did not identify which track would be retried or removed.
- Regression: `DownloadFailureDetailsTest.perTrackRetryActionNamesTheTrackAndInvokesRetry` and `...perTrackCancelActionNamesTheTrackAndInvokesCancel` verify formatted names include track and artist and invoke only the matching action. `DownloadsFailureRecoveryDeviceTest.failedDownloadOffersSafeGuidanceAndOptInRedactedDetails` asserts the real screen announces the specific failed track.
- Fix: Add resource-backed per-track accessibility labels for Retry and Cancel, using the localized Unknown Track fallback for a blank title; keep visible button text concise.
- Verification: `DownloadFailureDetailsTest` passes 10/10, `DownloadsEmptyStateTest` passes 2/2, and Quick `20261008T063812707Z-quick` passes 546 tests across 135 suites (0 failures/errors/skips). Galaxy S24 / Android 16 connected UI `20261008T064239508Z-ui` passes 68 tests across 15 suites (2 expected protected-profile skips; 0 failures/errors); QA packages were retained and `POST_NOTIFICATION` returned to `ignore`. QA data was preserved and production was not targeted.

## SPOTAMP-QA-071

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Library and Downloads connection status
- Status: FIXED
- Symptom: With SpotCore offline, Library showed saved categories without connection context and Downloads claimed “Online music is available,” although the phrase only reflected the stream-permission preference.
- Regression: `ConnectionStatusUiTest.noticesAreLimitedToConfiguredConnectionsThatNeedAttention`, `...offlineNoticeUsesSharedCopyAndItsCheckActionRetriesConnection`, and `...checkingNoticeShowsProgressCopyAndDoesNotOfferAnotherCheck` cover notice gating, shared copy, working recovery, and disabled progress state. `DownloadsCopyTest.playbackModeCopyDoesNotClaimStreamingIsCurrentlyAvailable` covers the preference wording; connected `ProductionNavigationJourneyTest.downloadsOfflineModeCanBeToggled` verifies both mode labels in the real app shell.
- Fix: Add the shared connection notice and Check connection action to paired Library and Downloads routes, ensure the connection probe starts if either route is restored directly, and state explicitly that streaming is allowed only when SpotCore is available.
- Verification: Focused connection/Downloads tests pass 15/15. Quick `20261008T070816213Z-quick` passes lint and 550 host tests across 135 suites (0 failures/errors/skips). Galaxy S24 / Android 16 UI `20261008T071440458Z-ui` passes 68 tests across 15 suites (0 failures/errors; 2 expected protected-profile skips); QA app and instrumentation retention plus notification cleanup pass. Physical captures at 100% text show the Library and Downloads offline notices and recovery action (`tmp/route-review/wave1-library-offline.png`, `wave1-downloads-offline.png`). QA data was preserved, camera and `POST_NOTIFICATION` are `ignore`, and production was not targeted.

## SPOTAMP-QA-072

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Bottom navigation at large text scale
- Status: FIXED
- Symptom: On the Galaxy S24 at 130% system text, the selected Downloads tab label was clipped to “Downloa…”, weakening route identification.
- Regression: `MainAppScreenFirstRunRouteTest.bottomNavigationUsesCompactLabelsForLargeTextWithoutRemovingNormalLabels` checks the Downloads label threshold; `ConnectionStatusUiTest.offlineNoticeAndRecoveryActionRemainVisibleAtLargeTextScale` checks the shared notice and 48dp recovery action at 320dp/130% text. Physical 130% captures verify the actual large-text tab label on the QA build.
- Fix: Use “Offline” as the selected Downloads tab’s visual label at large text scales; keep the page heading and the navigation icon’s full resource-backed “Downloads” description.
- Verification: Focused suites pass (5/5 connection-status and 5/5 navigation-route tests). Final-source Quick `20261008T073746572Z-quick` passes Debug lint and 551 tests across 135 suites (0 failures/errors/skips). Connected Galaxy S24 / Android 16 UI `20261008T074852256Z-ui` passes 68 tests across 15 suites (0 failures/errors; 2 expected protected-profile skips); the QA app and instrumentation remain installed and notification cleanup passed. Physical captures at 130%: `tmp/route-review/wave1-downloads-offline-130-final.png` and `wave1-library-offline-130-final.png`. Font scale is 1.0; QA data was preserved, camera and `POST_NOTIFICATION` are `ignore`, and production was not targeted. A floating Messenger overlay obscures part of the connection action, and TalkBack acceptance remains open.

## SPOTAMP-QA-073

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Home spotlight at large text scale
- Status: FIXED
- Symptom: The Home spotlight used a fixed 190dp height, clipping its Play action at 130% system text on the Galaxy S24.
- Regression: `HomeSectionCopyTest.spotlightPlayActionRemainsReachableAtLargeTextScale` renders at 320dp/130% and verifies the Play action is displayed, at least 48dp high, clickable, and invokes its callback.
- Fix: Scale the spotlight card height with the current font scale (never below the normal 190dp height).
- Verification: Final-source Quick `20261008T075759085Z-quick` passes Debug lint and 552 tests across 135 suites (0 failures/errors/skips). Connected Galaxy S24 / Android 16 UI `20261008T080244761Z-ui` passes 68 tests across 15 suites (2 expected protected-profile skips; 0 failures/errors); QA app/instrumentation retention and notification cleanup passed. Physical 130% capture `tmp/route-review/wave1-home-spotlight-130-postfix.png` shows the full Play action; font scale was restored to 1.0. QA data was preserved, camera and `POST_NOTIFICATION` are `ignore`, and production was not targeted. Broader visual and TalkBack acceptance remains open.

## SPOTAMP-QA-074

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Settings library-details summary at large text scale
- Status: FIXED
- Symptom: At 130% text, the “Tracks ready to browse” label collapsed into one- or two-character lines beside the sync action in “Library details on this phone.” Physical review and a new compact Compose regression measured the label at only 22dp wide.
- Regression: `SettingsLargeTextNavigationTest.libraryDetailsCountAndSyncActionStackAt130PercentFontScale` covers 320dp width at 130%, verifies usable label/value widths, count-before-action ordering, a 48dp-high action, and the sync callback. It failed before the fix and passes afterward; `SettingsLargeTextNavigationTest` passes 24/24.
- Fix: At font scale 1.15 or above, place the track label and count in a full-width column with the sync action below; retain the side-by-side layout at normal text scale.
- Verification: Quick `20261008T082037043Z-quick` passes Debug lint and 553 tests across 135 suites (0 failures/errors/skips). `:app:assembleQa` and `:app:compileQaAndroidTestKotlin` pass. Updated QA APK installed over the existing isolated package with `adb install -r`; physical 130% captures `tmp/route-review/wave1-settings-fix-130-scroll3.png` and `wave1-settings-fix-130-scroll4.png` show the complete label, 503-track count, and update action. The subsequent connected retest `20261008T084400686Z-ui` passed 68 tests across 15 suites (0 failures/errors; 2 expected skips), including Home Retry after fixture stabilization. Font scale restored to 1.0; QA data preserved; camera and `POST_NOTIFICATION` are `ignore`; production was not targeted.

## SPOTAMP-QA-075

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Home Retry connected acceptance
- Status: FIXED
- Symptom: In the initial connected run `20261008T083349088Z-ui`, the production Home retry test could not inject a tap because the `home_retry_button` semantics node had left the tree; the test had enabled the successful response before interacting. That run left product-state race versus test timing unresolved; the stabilized follow-up result is recorded under Verification.
- Regression: `HomeFeedRetryDeviceTest.productionHomeRetryRecoversAfterRealHttp503OnDevice` exercises the production `HomeScreen` and Retrofit client against a loopback HTTP 503/200 server. Its fixture now gates successful responses until the UI Retry action transitions reachability to Checking.
- Fix: Gate the loopback server’s successful response until the Retry action transitions reachability to Checking, so an unsolicited or in-flight fetch cannot remove the Retry control before the user action. No production UI change was required or claimed.
- Verification: First connected run `20261008T083349088Z-ui` failed at touch injection; Android-test compilation passed after fixture stabilization. Retest `20261008T084400686Z-ui` passed 68 tests across 15 suites (0 failures/errors; 2 expected skips), including `productionHomeRetryRecoversAfterRealHttp503OnDevice`. QA build/install, instrumentation install/retention, and notification cleanup passed. Follow-up checks confirmed font scale 1.0, camera and `POST_NOTIFICATION` `ignore`, and `.qa` foreground; no QA data clear or production-package operation was performed.

## SPOTAMP-QA-076

- Severity: LOW
- Date: 2026-10-08
- Source: QA framework audit result propagation
- Status: FIXED
- Symptom: The runner reported `Audit: FAIL` even though `qa-audit.ps1` printed `PASS QA framework audit` and found no violations.
- Root cause: `qa.ps1` inspected `$LASTEXITCODE` after invoking a PowerShell script that returned normally. That variable retained a nonzero status from an earlier failed ADB preflight, so an unrelated device bridge error contaminated the framework-audit result.
- Regression: `qa-audit.ps1` checks that the runner derives its Audit tier from the explicit success marker and does not use `$LASTEXITCODE` for this PowerShell-script result. A direct reproduction preloaded `$LASTEXITCODE=19`; `qa-audit.ps1` still passed and left the stale value unchanged.
- Fix: Determine the Audit tier result from the audit's explicit success marker in captured output.
- Verification: `scripts/qa.ps1 audit` exits 0 and reports PASS as `20261008T090456612Z-audit`, even while ADB startup is unavailable. This fixes QA report classification only; it does not restore connected-device QA.

## SPOTAMP-QA-077

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Release artifact QA-content exclusion
- Status: FIXED
- Symptom: The unsigned release APK contained QA Lab fixture and playback-smoke strings in compressed DEX, along with a QA Lab resource label. The previous release check scanned the outer ZIP bytes, so compression hid DEX strings from its marker search.
- Root cause: QA Lab implementation and its Settings action/resource lived in `src/main` and were retained in release because release shrinking is disabled. Runtime `BuildConfig.DEBUG` guards hid the route but did not remove its implementation from the package. The verifier searched the compressed APK as a flat file rather than reading ZIP entries.
- Regression: Release verification now scans decompressed APK entries as ASCII and UTF-16 for QA-only markers and forbidden test endpoints; the framework audit checks that this package-content scan remains in place.
- Fix: Moved the QA Lab implementation, Settings action, and label into a shared source/resource set used only by Debug and QA variants. Release and Benchmark compile against no-op UI placeholders without QA markers.
- Verification: With Android Studio JBR 21, offline Gradle passed `:app:testQaUnitTest` (553 tests/135 suites, 0 failures/errors/skips), `:app:lintDebug`, QA Android-test APK assembly, and Debug, QA, Benchmark, and Release APK assembly. `verify-release.ps1` passed the newly built unsigned local-validation APK and its negative control correctly rejected the QA APK with markers found in `resources.arsc` and `classes7.dex`. Four connected UI attempts were blocked before instrumentation when ADB reported no device; the final-source attempt `20261008T094320658Z-ui` confirms the same preflight failure after the new APKs were built. All recorded 0 tests and made no install, permission, or QA-data changes.

## SPOTAMP-QA-078

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Current-source connected UI preflight
- Status: INTERMITTENT; not blocking the latest run
- Symptom: The authorized Galaxy S24 UI runs intermittently report no device after six ADB probes, despite direct `adb devices -l` checks listing `R5CX70DVT1E` immediately before or after the run. On 2026-10-08, standard-sandbox runs `20261008T111024434Z-ui`, `20261008T111108971Z-ui`, and `20261008T111207049Z-ui` again failed before install; the elevated run `20261008T111242900Z-ui` passed.
- Root cause: Not established. Runner diagnostics show ADB server startup/handshake failure (`could not read ok from ADB Server`, `failed to start daemon`, `cannot connect to daemon`); the host ADB log records the daemon being killed by a remote request, but does not identify its source.
- Regression: No app regression was observed in these two attempts because installation and instrumentation were never reached. QA framework audit passes.
- Fix: Root cause remains unknown; no speculative product or test behavior change was made.
- Verification: Current-source connected run `20261008T111242900Z-ui` passed (68 cases across 15 suites, 0 failures/errors; 2 expected paired-profile skips). QA and instrumentation packages remained installed, `.qa` data was preserved, and notification cleanup passed. The issue remains intermittent and may recur.

## SPOTAMP-QA-079

- Severity: LOW
- Date: 2026-10-08
- Source: Search route state/recovery coverage
- Status: FIXED
- Symptom: Host Compose tests covered Search state components independently but did not verify the real Search screen's complete state transitions and recovery actions with the production view model.
- Regression: `SearchScreenJourneyTest.productionSearchMovesThroughLoadingResultsFilteredEmptyNoResultsAndClear` and `...productionSearchFailureCanOpenSettingsWithQueryAndRetryInPlace` exercise the production Search screen/view model boundary.
- Fix: Added deterministic host integration journeys for loading/results/filtering/true-empty/clear and failure/settings/retry without network or device dependencies.
- Verification: Focused QA unit tests passed 2/2. Quick run `20261008T100226351Z-quick` passed Debug lint and 555 QA unit tests across 136 suites (0 failures/errors/skips).

## SPOTAMP-QA-080

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Downloads stale local-file recovery
- Status: FIXED
- Symptom: A database row marked completed whose saved audio file was absent correctly showed “Saved file unavailable — download again,” but its detail fell back to “The download failed. Check the connection and try again.”
- Root cause: `DownloadsScreen` passed a null transfer error to `DownloadFailureDetails`, whose generic fallback assumed a network failure even when local-file integrity was the current problem.
- Regression: `DownloadsScreenJourneyTest.completedRecordWithMissingAudioIsNotPresentedAsOfflineAndGetsLocalRecoveryCopy` drives the production Downloads screen with a completed row whose file is absent and checks local guidance, Retry, and absence of the offline-ready/network/empty-state claims.
- Fix: Added a local-file recovery fallback for completed records without playable local audio; stale transfer details are not presented as the current cause.
- Verification: The focused screen journey passed. Quick run `20261008T105125600Z-quick` passed Debug lint and 560 QA unit tests across 137 suites (0 failures/errors/skips). Current-source unsigned Release verification `20261008T105401515Z-release` passed assembly, `lintVitalRelease`, and decompressed-content verification.

## SPOTAMP-QA-081

- Severity: LOW
- Date: 2026-10-08
- Source: Downloads empty state with downloaded-only mode enabled
- Status: FIXED
- Symptom: With no saved audio and downloaded-only mode enabled, the empty state offered Browse music but no direct way to restore online-eligible playback; the preference change was buried in the overflow menu.
- Regression: `DownloadsScreenJourneyTest.emptyDownloadedOnlyModeOffersOneTapToAllowOnlineMusic` exercises the production screen, verifies the action is visible only in this state, and checks that using it updates the displayed and persisted preference.
- Fix: Exposed the existing “Allow online music” preference action in the empty state while preserving Browse music as the primary path. The empty container can grow to fit wrapped text and controls.
- Verification: Both `DownloadsScreenJourneyTest` cases pass. Quick run `20261008T105125600Z-quick` passed Debug lint and 560 QA unit tests across 137 suites (0 failures/errors/skips); current-source connected run `20261008T105602069Z-ui` also exercised the production empty-state action and passed.

## SPOTAMP-QA-082

- Severity: LOW
- Date: 2026-10-08
- Source: Connected navigation journey preference isolation
- Status: FIXED
- Symptom: `ProductionNavigationJourneyTest` assumed downloaded-only mode started disabled and selected “Allow online music” by text, which matched both the overflow option and the production empty-state action. The aborted test persisted the changed preference, making later runs fail from the retained QA profile.
- Root cause: Connected UI journeys shared the app's persisted preference but did not snapshot/restore it; the action text was not unique in the merged Compose semantics tree.
- Regression: The test now uses the production action's unique test tag; both downloaded-only journeys capture and restore their pre-run setting.
- Fix: Make the journeys deterministic for any retained QA preference without clearing `.qa` data.
- Verification: Current-source connected run `20261008T105602069Z-ui` passed 68 instrumentation cases across 15 suites with 0 failures/errors and 2 expected paired-profile skips; QA data retention and notification cleanup passed.

## SPOTAMP-QA-083

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Artist and Album detail connection/offline recovery
- Status: FIXED
- Symptom: Artist and Album details could continue showing saved metadata while SpotCore was unavailable without the shared status language or a route-local Check connection action already present on Library and Downloads.
- Regression: `ConnectionStatusUiTest.detailRoutesShowSharedOfflineRecoveryAndCheckConnectionAction`, `detailNoticeIsHiddenForUnpairedProfile`, and `detailNoticeIsHiddenForHealthyConnection` verify common offline wording, retry, and suppression when a notice would be misleading.
- Fix: Added the shared connection notice and reachability-check startup to both detail routes. The notice remains hidden for unpaired and healthy connections.
- Verification: Quick run `20261008T110155587Z-quick` passed 561 tests across 137 suites; unsigned Release `20261008T110830288Z-release` passed. Connected UI `20261008T111242900Z-ui` passed 68 cases across 15 suites, though its generic journeys do not directly navigate into Artist/Album detail; on-device detail-placement review remains open.

## SPOTAMP-QA-084

- Severity: MEDIUM
- Date: 2026-10-08
- Source: Artist and Album detail loading/recovery
- Status: FIXED
- Symptom: A failed child request could produce an empty artist/album list with no indication that loading had failed or a way to retry.
- Root cause: The general-purpose `getArtistAlbums` and `getAlbumTracks` repository methods caught transport errors and returned empty lists. Detail screens therefore could not distinguish an empty result from an unavailable server.
- Regression: `MusicRepositoryLibraryFailureTest` now asserts artist and album detail HTTP failures remain observable; `ConnectionStatusUiTest.detailLoadFailureOffersAnAccessibleRetryAction` verifies the error notice and reachable retry action.
- Fix: Added detail-specific repository paths that preserve failures without changing existing resilient behavior for non-detail callers. Artist and Album routes now present product-language error copy and retry their detail load.
- Verification: Quick `20261008T110155587Z-quick` passed 561 tests across 137 suites; unsigned Release `20261008T110830288Z-release` passed. Connected UI `20261008T111242900Z-ui` passed 68 tests across 15 suites, but does not directly navigate to these detail failure states; physical route review remains open.

## SPOTAMP-QA-085

- Severity: LOW
- Date: 2026-10-08
- Source: Standard Playlist detail initial loading
- Status: FIXED
- Symptom: Before the first Room flow emission, the screen rendered its `emptyList()` collection placeholder as “There are no tracks in this playlist” and showed a zero-track count, briefly misrepresenting populated playlists as empty.
- Root cause: `collectAsStateWithLifecycle(initialValue = emptyList())` made “not loaded yet” indistinguishable from a loaded empty list.
- Regression: `PlaylistDetailCopyTest.playlistTrackQueryShowsLoadingInsteadOfAnEmptyPlaylistBeforeItsFirstEmission` and `...playlistTrackQueryShowsEmptyCopyAfterItCompletesWithoutTracks` verify distinct loading and confirmed-empty states.
- Fix: Collect the first Room value as nullable; show progress and a neutral header until it arrives, then display either tracks or the true empty state.
- Verification: Current Quick `20261008T115206089Z-quick` passed 565 tests across 138 suites (0 failures/errors/skips). Current-source connected UI `20261008T115640575Z-ui` passed 68 tests across 15 suites; the generic suite does not directly exercise this playlist-loading state. Unsigned Release `20261008T120002832Z-release` passed the decompressed-content verifier (`entries=541`).

## SPOTAMP-QA-086

- Severity: LOW
- Date: 2026-10-08
- Source: Downloads initial Room loading state
- Status: FIXED
- Symptom: Downloads treated the ViewModel's initial `emptyList()` as authoritative before Room emitted saved records, briefly showing a zero-size summary and an empty-library prompt on an installed user's populated library.
- Root cause: The downloaded-track `StateFlow` used an empty list for both “not loaded yet” and “loaded with no records.”
- Regression: `DownloadsCopyTest` verifies the production loading message is visible while the initial track snapshot is null and that the empty-state title is absent. `DownloadsScreenJourneyTest.emptyDownloadedOnlyModeOffersOneTapToAllowOnlineMusic` now waits for the initial Room result before asserting the confirmed-empty action.
- Fix: Expose a nullable initial snapshot to the screen, show a lightweight loading placeholder and neutral summary while pending, and retain the existing non-null track list for other callers.
- Verification: Focused Downloads journey suite passes. Current Quick `20261008T115206089Z-quick` passed Debug lint and 565 tests across 138 suites (0 failures/errors/skips). Current-source connected UI `20261008T115640575Z-ui` passed 68 tests across 15 suites (0 failures/errors; 2 paired-profile skips), but does not directly assert the first Room emission. Unsigned Release `20261008T120002832Z-release` passed the decompressed-content verifier (`entries=541`).

## SPOTAMP-QA-087

- Severity: LOW
- Date: 2026-10-08
- Source: Library category toolbar at phone width
- Status: FIXED
- Symptom: On the Galaxy S24, the “Downloaded only” filter chip wrapped to two lines and crowded the Back, view-mode, and sort controls in the Albums toolbar.
- Root cause: The full label was placed in a single horizontal row alongside the title and three actions, leaving too little width at normal phone size.
- Regression: `LibraryDownloadedOnlyFilterChipTest.fullFilterLabelAndActionRemainAvailableInCompactLargeTextRow` verifies the full label and working toggle at 320dp width/130% text. Current S24 capture `tmp/route-review/wave1-current-albums-v2-20261008.png` verifies the one-line result in the actual Albums toolbar.
- Fix: Keep the category title and icon actions in a responsive first row, place the filter chip on its own trailing row, and ellipsize unusually long category titles instead of pushing controls off-screen.
- Verification: Quick `20261008T115206089Z-quick` passed 565 tests across 138 suites (0 failures/errors/skips). Current-source connected UI `20261008T115640575Z-ui` passed 68 tests across 15 suites (0 failures/errors; 2 paired-profile skips), with QA retention and notification cleanup passing. Unsigned Release `20261008T120002832Z-release` passed assembly, `lintVitalRelease`, and decompressed-content verification (`entries=541`).

## SPOTAMP-QA-088

- Severity: LOW
- Date: 2026-10-08
- Source: Settings library actions at phone width
- Status: FIXED
- Symptom: On the Galaxy S24, the side-by-side “Refresh SpotCore library” and “Update library on this phone” buttons wrapped their labels awkwardly in the Library settings card.
- Root cause: Each action received only half of the available phone-width row, which was insufficient for the full button labels.
- Regression: `SettingsLargeTextNavigationTest.libraryActionsStackWithClearLabelsAt130PercentFontScale` checks stacking, full labels, and callbacks at a 320dp host width/130% font scale. `libraryActionsRemainSideBySideOnWideLayouts` checks the retained two-column arrangement at 600dp.
- Fix: Stack the actions below 520dp; keep them side-by-side on wider layouts.
- Verification: Quick `20261008T120745151Z-quick` passed 566 tests across 138 suites (0 failures/errors/skips). Connected S24 UI `20261008T124328185Z-ui` passed 69 tests across 15 suites (0 failures/errors/skips) after the explicitly approved `.qa`-only clean reset; it includes `TouchTargetLayoutDeviceTest.settingsLibraryActionsStackWithFullLabelsAtCompactLargeTextScale` on the handset. QA app/instrumentation retention and notification cleanup PASS. Camera and notification app-ops were verified `ignore` afterward; production was not targeted. Unsigned Release `20261008T121449543Z-release` passed assembly, `lintVitalRelease`, and decompressed-content verification (`entries=541`) against unchanged production sources. Full Settings-route visual confirmation remains open.

## SPOTAMP-QA-089

- Severity: LOW
- Date: 2026-10-08
- Source: Settings SpotCore connection pairing action
- Status: FIXED
- Symptom: The Connection card said “Scan Add Device QR,” while Home and the Library card said “Scan pairing QR code” for the same action. The former exposed SpotCore's internal desktop label and made the phone's pairing language inconsistent.
- Root cause: The Settings Connection card used a separate resource string from the two other pairing entry points.
- Regression: `SettingsLargeTextNavigationTest.unpairedConnectionCopyExplainsTheSpotCorePairingPath` verifies the tagged Connection action at 130% font scale and asserts both Settings pairing entry points use the shared plain-language label. The current S24 capture `tmp/route-review/wave1-live-settings-scroll-100-20261008.png` confirms the updated button is fully visible after scrolling.
- Fix: Align the Connection card label to “Scan pairing QR code.”
- Verification: Quick `20261008T125803413Z-quick` passed 566 tests across 138 suites (0 failures/errors/skips); connected S24 UI `20261008T130405522Z-ui` passed 69 tests across 15 suites (0 failures/errors/skips); unsigned Release `20261008T131130482Z-release` passed assembly, `lintVitalRelease`, and decompressed-content verification (`entries=541`). Camera and notification app-ops returned to `ignore`; QA app/instrumentation were retained and production was not targeted.

## SPOTAMP-QA-090

- Severity: LOW
- Date: 2026-10-08
- Source: Unpaired Library empty state
- Status: FIXED
- Symptom: An unpaired first-run listener opening Library saw “No music library selected. Choose one in Settings,” which hid the immediate pairing path behind another route.
- Root cause: The Library empty state did not distinguish an unpaired phone from a paired phone that had not selected a library.
- Regression: `LibraryCopyTest` verifies direct pairing guidance and callback for the unpaired state while retaining Settings guidance for the paired/no-library state. Connected `HomeFirstRunJourneyTest.unpairedLibraryOffersDirectPairingPreflight` verifies the real CTA opens camera-purpose preflight and can be canceled while camera remains denied.
- Fix: Show a SpotCore connection explanation and a direct 48dp “Scan pairing QR code” action when unpaired; preserve the existing Settings action after pairing.
- Verification: Quick `20261008T132256079Z-quick` passed 567 tests across 138 suites (0 failures/errors/skips); connected S24 UI `20261008T132751100Z-ui` passed 70 tests across 15 suites (0 failures/errors/skips); unsigned Release `20261008T133122274Z-release` passed assembly, `lintVitalRelease`, and decompressed-content verification (`entries=541`). Camera and notification app-ops are `ignore`; QA state was retained and production was not targeted. Visual capture: `tmp/route-review/wave1-live-library-pairing-100-20261008.png`.

## SPOTAMP-QA-091

- Severity: LOW
- Date: 2026-10-08
- Source: Unpaired Search first-run and request-failure states
- Status: FIXED
- Symptom: Search told an unpaired listener to connect to SpotCore but offered no direct way to pair, either from the blank state or after a failed search request.
- Root cause: Search's empty and unavailable states had no pairing callback, despite the app-level pairing dialog being available from Home, Library, and Settings.
- Regression: `SearchCopyTest.unpairedFirstRunSearchOffersLargeDirectPairingAction` verifies the direct action at 130% text and confirms it is omitted for paired/offline states. `SearchScreenJourneyTest.productionSearchFailureCanOpenSettingsWithQueryAndRetryInPlace` verifies direct pairing from the production Search failure state alongside query-preserving Settings and in-place retry. Connected `HomeFirstRunJourneyTest.unpairedSearchOffersDirectPairingPreflight` verifies the real action opens the existing preflight, can be canceled, and leaves camera denied.
- Fix: Add the shared 48dp “Scan pairing QR code” action to unpaired empty and no-library failure states, reusing the existing app-level pairing flow. Paired recovery behavior is unchanged.
- Verification: Quick `20261008T133940166Z-quick` passed 568 tests across 138 suites (0 failures/errors/skips); connected S24 UI `20261008T134540736Z-ui` passed 71 tests across 15 suites (0 failures/errors/skips), with QA retention and notification cleanup passing; unsigned Release `20261008T134921427Z-release` passed assembly, `lintVitalRelease`, and decompressed-content verification (`entries=541`). Camera and notification app-ops are `ignore`; QA data was retained and production was not targeted. Visual capture: `tmp/route-review/wave1-live-search-pairing-100-20261008.png`.

## SPOTAMP-QA-092

- Severity: LOW
- Date: 2026-10-08
- Source: Unpaired Downloads empty state
- Status: FIXED
- Symptom: The empty Downloads screen told an unpaired listener to “Browse music,” which opened Library and required another action before pairing could start.
- Root cause: Downloads presented the same empty-library action regardless of whether a SpotCore connection existed.
- Regression: `DownloadsEmptyStateTest.unpairedDownloadsEmptyStateOffersDirectPairingAtLargeText` checks the state-specific copy, callback, and 48dp target at 130% text; the paired test preserves Browse music. Connected `HomeFirstRunJourneyTest.unpairedDownloadsOffersDirectPairingPreflight` verifies the live route opens the existing preflight and cancellation leaves camera denied.
- Fix: Show SpotCore setup guidance and direct pairing when unpaired; preserve the existing Browse music path after pairing.
- Verification: Quick `20261008T135844370Z-quick` passed 569 tests across 138 suites (0 failures/errors/skips); connected S24 UI `20261008T140042045Z-ui` passed 72 tests across 15 suites (0 failures/errors/skips), with QA retention and notification cleanup passing; unsigned Release `20261008T140323234Z-release` passed assembly, `lintVitalRelease`, and decompressed-content verification (`entries=541`). Camera and notification app-ops are `ignore`; QA data was retained and production was not targeted. Visual capture: `tmp/route-review/wave1-live-downloads-pairing-100-20261008.png`.

## SPOTAMP-QA-093

- Severity: LOW
- Date: 2026-10-08
- Source: Home station tiles at increased font scale
- Status: FIXED
- Symptom: At 130% system text on the Galaxy S24, the Artist Mix subtitle “Choose an artist” was clipped to “Choose an…” inside the fixed-height station card.
- Root cause: The station tile had a fixed 68dp height and its title/subtitle were constrained to one line, so larger font metrics could not expand the card.
- Regression: `PremiumStationTileLayoutTest.stationTileSubtitleWrapsAtLargeTextOnPhoneWidth` checks both station tiles at 320dp/130% and verifies the complete subtitle wraps to a second line. Connected UI navigation matchers also accept the selected tab's visible label or an unselected tab icon's content description, matching the actual accessible state at large font scale.
- Fix: Make 68dp a minimum height, allow the tile to grow with two-line title/subtitle content, and add vertical padding.
- Verification: Quick `20261008T141050583Z-quick` passed Debug lint and 570 tests across 139 suites (0 failures/errors/skips). Connected S24 / Android 16 UI `20261008T142623245Z-ui` passed 72 tests across 15 suites at 130% (0 failures/errors/skips), including Home retry and the unpaired Home, Library, Search, and Downloads pairing-preflight journeys; QA app/instrumentation retention and notification cleanup passed. Unsigned Release `20261008T142846310Z-release` passed assembly, `lintVitalRelease`, and APK-content verification (`entries=541`). Physical capture: `tmp/route-review/wave1-live-home-large-text-final-130-20261008.png`. Font scale returned to 1.0, QA camera/notification app-ops are `ignore`, QA data was preserved, and production was not targeted.
