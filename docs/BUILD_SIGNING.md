# Build and Signing Policy

SpotAmp keeps local validation signing separate from production signing:

| Variant | Local behavior | Intended use |
|---|---|---|
| `debug` | Standard Android debug keystore | Development and unit/device testing |
| `qa` | Debug-compatible local signing with `.qa` application ID suffix | Installable QA builds and device journeys |
| `benchmark` / profileable test variants | Gradle standard debug signing | Performance and profiling runs; never production distribution |
| `release` without explicit credentials | Unsigned `app-release-unsigned.apk` | Local compilation, packaging, lint, and artifact inspection |
| `release` with explicit credentials | Upload/production signing config | Production release preparation |
| `release` signed output | `app-release.apk`, verified with Android `apksigner` | Explicitly signed local/production-preparation artifact |

Production signing is enabled only when the file at the explicitly supplied `KEYSTORE_PATH` exists and `STORE_PASSWORD` and `KEY_PASSWORD` are supplied. Relative `KEYSTORE_PATH` values resolve from the repository root; absolute paths are also supported. There is no default production keystore path. `KEY_ALIAS` is optional and defaults to `upload`. No signing passwords or production keys are hardcoded.

The release QA runner applies the same selection: `scripts\qa.ps1 release` verifies `app-release-unsigned.apk` when explicit credentials are absent, and verifies `app-release.apk` when all required release signing variables are present.

The release build never falls back to a user debug keystore. The QA build remains locally installable through its debug-compatible signing configuration; run .\scripts\qa.ps1 qa to assemble and install the isolated QA package.


Run `scripts/qa-signing.ps1` for the repeatable signing-policy gate, then run `scripts/verify-release.ps1` after release assembly to check the archive for QA/test-only paths and report whether the artifact is unsigned local-validation or signed.
For an explicitly signed output, pass the APK path directly: `scripts\verify-release.ps1 -ApkPath app/build/outputs/apk/release/app-release.apk`. A local debug keystore may be supplied explicitly for signing-policy smoke tests, but that output is not a production artifact. The same gate verifies that credential-bearing preference files remain excluded from Android backup and device transfer rules.

The verifier also scans the APK payload for QA Lab marker strings, in addition to checking archive paths. It uses `apksigner verify` for modern APK v2/v3 signatures, so a signed APK is classified correctly even when it has no legacy `META-INF/*.RSA` entries.

## Verification evidence (2026-09-05)

The repeatable `scripts/qa-signing.ps1` gate passed QA and benchmark local signing, unsigned release validation with cleared credentials, and an explicit production-signing smoke using a disposable workspace-only keystore. The unsigned local release-validation path also passed the comprehensive QA gate. A separate smoke build using a disposable, workspace-local PKCS#12 keystore and non-production test credentials selected the explicit release signing configuration; scripts\verify-release.ps1 -ApkPath app\build\outputs\apk\release\app-release.apk classified the result as signed. The disposable keystore was removed after verification. No production credentials were used or persisted.
