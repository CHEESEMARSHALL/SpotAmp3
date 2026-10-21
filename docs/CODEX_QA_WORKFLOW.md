# Codex QA Workflow

Operate as SpotAmp's QA and reliability engineer. Do not add unrelated product features.

1. Establish build identity and Git state.
2. Run the appropriate `scripts/qa.ps1` tier.
3. Read `qa-results/latest/QA_REPORT.md` and `.json`; inspect each tier outcome and its machine-readable failure classification. Preserve the reported `runId`; prior results are archived under `qa-results/runs/`.
4. Reproduce failures individually, classify severity, and add regression coverage.
5. Fix confirmed root cause only; rerun focused, neighboring, and tier tests.
6. Update documentation and the bug ledger.

Never make a failing test green by weakening it unless it is demonstrably wrong. Never use production credentials, endpoints, storage, or destructive external actions in QA.

## Evidence rules

- Treat `PASS`, `FAIL`, `BLOCKED`, and `NOT_IMPLEMENTED` as distinct outcomes. A blocked device or unavailable companion session is evidence to report, never a pass.
- Keep source suspicions separate from confirmed defects. Reproduce the behavior first; record unreproduced concerns as `SOURCE RISK ONLY` or `BLOCKED`.
- For intermittent failures, investigate coroutine scheduling, player callbacks, WorkManager timing, network timing, and device state before considering quarantine. Do not add arbitrary sleeps or weaken assertions. If quarantine is unavoidable, record the test, reason, observed failure rate, and tracking note in the bug ledger.
- Re-run a requested stress seed and action count exactly; confirm both values in the generated JUnit/report artifacts.
- Product music is companion-only. Local WAV/MP3 fixtures and loopback streams prove media plumbing and origin policy, not audible desktop playback. Use `scripts/qa-companion.ps1` only with a redacted paired-session observation.
- For automated Level-B desktop compatibility, use `scripts/qa.ps1 companion`; it imports the real adjacent SpotCore companion implementation with isolated data and a locally signed QA APK. This explicit tier may be `BLOCKED` when the SpotCore checkout or connected device is unavailable and is intentionally excluded from `quick`.
- Run `scripts/qa-signing.ps1` for signing intent and `scripts/qa-audit.ps1` for framework consistency. Never provide production credentials to ordinary host QA or CI.
- The framework audit scans current QA artifacts and the newest companion run by default. Before a release/evidence-retention audit, run `scripts/qa-audit.ps1 -IncludeArchivedArtifacts` to scan all preserved run folders for credential markers; the scan streams known-text artifact types and skips binary outputs.
- When a companion report is present, the framework audit validates its `companion-playback` gate name, `PASS`/`BLOCKED`/`FAIL` result, and gate run ID.
- `qa.ps1 audit` preserves the last test-run `QA_REPORT.json`; inspect `QA_AUDIT_REPORT.json` for the audit invocation itself.

## Handoff checklist

Before handing off a phase, record the authoritative report path, commit, tier outcomes, executed test counts, device state, and any blocked or unimplemented coverage. Update the relevant audit, coverage matrix, performance baseline, and bug ledger entries; do not replace an older passing run with a later blocked run without preserving both contexts.
