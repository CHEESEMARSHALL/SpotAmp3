# QA Performance Baseline

The benchmark tier now executes `PlaybackStateRestorationBenchmarkTest`, `RoomLibraryPerformanceBenchmarkTest`, and `BackendPaginationPerformanceBenchmarkTest` through `scripts/qa.ps1 benchmark`. It performs 1,000 queue-restoration operations against a 100-track queue, exercises a real Room in-memory database with 2,000 cached tracks, and fetches 20 loopback pages of 100 tracks through the backend client.

The benchmark is a repeatable smoke baseline, not a device-performance claim and not a hard pass/fail latency threshold. Gradle task output is retained in `qa-results/latest/logs/benchmark.log`; the machine-readable benchmark timings are retained in `qa-results/latest/junit/Benchmark/` under each test XML `system-out`. Compare repeated runs on the same environment and test shape for regressions.


## QA diagnostics

Debug and QA builds install non-punitive StrictMode logging for main-thread disk/network work and leaked closable objects. Release builds do not install these policies.

## Device measurements

Before the 2026-09-05 smoke collection, no device measurements were claimed. Device measurements are collected separately from the normal `full` gate with `scripts/qa-device-performance.ps1`. The script runs a bounded set of cold and warm launches (three samples by default), records median/P95 startup and memory values, captures frame/jank summaries, and records thermal-service output when available. It is diagnostic only and does not impose an automatic performance threshold. Thermal measurements and real companion audio performance remain unmeasured.
## Measured host baseline (2026-09-04)

PlaybackStateRestorationBenchmarkTest completed 1,000 restoration iterations against a 100-track queue in 119.2229 ms (0.1192 ms/iteration) on host PETERS_CHIN. RoomLibraryPerformanceBenchmarkTest inserted and emitted 2,000 cached tracks through the real Room DAO/Flow path in 516.4419 ms. BackendPaginationPerformanceBenchmarkTest fetched 2,000 tracks across 20 loopback pages through the backend client in 207.7921 ms. These are repeatable host baselines, not Android device latency targets; compare only on the same environment and test shape.


## Measured QA device smoke (2026-09-05)

Device: SM-S926U / Android 16. The isolated com.aistudio.plexmusicplayer.vctplx.qa package was used with no production credentials or companion endpoint. Reproduce this measurement with `scripts/qa-device-performance.ps1 -Samples 3`. The earlier scripted smoke sample reported one cold process start at TotalTime=916 ms and a same-process relaunch at TotalTime=0 ms. After the cold launch and a two-second settling interval, dumpsys gfxinfo reported 5 rendered frames, 4 janky frames (80%), and 50th/90th/95th/99th percentiles of 53/400/400/400 ms. The small frame sample is diagnostic only, not a representative UI benchmark. Compare new reports only when device, Android version, package, library shape, sample count, settle time, and thermal conditions match; otherwise record `NOT_COMPARABLE`.

The latest scripted post-launch memory sample was TOTAL PSS=204,747 KB and TOTAL RSS=318,964 KB. These values are single-sample smoke measurements; no trend, leak, or regression threshold is claimed. Thermal state and real companion audio performance remain unmeasured.

## Latest bounded device campaign (2026-09-05)

The current `qa-results/latest/benchmarks/device-performance.json` contains three cold and three warm QA-package samples on SM-S926U / Android 16 with a two-second settling interval. Cold startup was 1,125 ms median / 1,153 ms P95; warm startup was 0 ms median / 0 ms P95. Cold total PSS was 163,242 KB median and warm total PSS was 160,204 KB median. The report contains six per-sample records and the thermal snapshot. These values are diagnostic only and are comparable only to runs with matching device, Android version, package, library shape, sample count, settle time, and thermal conditions.
