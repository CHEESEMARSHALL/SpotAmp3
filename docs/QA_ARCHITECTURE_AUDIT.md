# SpotAmp QA Architecture Audit

Audit date: 2026-09-04. Authoritative root: `D:\Documents\SpotAmp\SpotAmp`; one Gradle root and one `:app` module were found.

| Item | Verified value |
|---|---|
| Application ID | `com.aistudio.plexmusicplayer.vctplx` |
| Namespace | `com.example` |
| Gradle / AGP / Kotlin | 9.6.1 / 9.1.1 / 2.2.10 |
| SDKs | compile 36, target 36, min 24 |
| Baseline revision | `d6d84cb6af5e12f418805661ef08c7dedaa4f0de` |

The app uses Compose, `MainActivity`, `MusicViewModel`/StateFlow, Room database version 16, Retrofit/OkHttp, WorkManager, Media3 ExoPlayer, and a custom `MusicPlaybackService` with platform MediaSession. Integration/data code is primarily in `com.example.data`; playback code is in `com.example.playback`.

Source risks found: Room has destructive fallback recovery and no verified shipped schema history. Playback restoration had an initial source risk around non-zero current-index handling; it is now covered by regression tests and corrected in the current checkout. Service play/pause intent forwarding is covered, while Robolectric cannot reliably dispatch platform MediaSession TransportControls callbacks; that platform callback path remains device-level evidence. Gemini client code is not present in this checkout, so Gemini contract coverage remains explicitly unimplemented.

## Baseline

`:app:testDebugUnitTest --no-daemon --console=plain` passed. The current worktree was already dirty before this task and was not reset.


## Historical schema provenance

The repository history currently contains five commits and no Git release tags or archived APK/schema snapshots. The checkout contains generated Room schema exports for versions 15 and 16 only; the migration tests exercise controlled historical database shapes, not verified shipped artifacts. Therefore historical shipped versions and their exact schema files remain HISTORICAL_SCHEMA_UNVERIFIED. This is an evidence limitation, not a claim that the synthetic fixtures correspond to releases.
