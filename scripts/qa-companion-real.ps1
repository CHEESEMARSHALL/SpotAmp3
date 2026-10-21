[CmdletBinding()]
param(
  [string]$SpotCoreRoot = '',
  [string]$GradleExecutable = '',
  [int]$Port = 44182,
  [int]$ControlPort = 44183,
  [int]$TimeoutSeconds = 420,
  [switch]$ResolveGradleDependencies,
  [switch]$UseInstalledInstrumentation,
  [switch]$DisableKspIncremental,
  [switch]$RunLockAlreadyHeld
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'qa-instrumentation.psm1') -Force
$qaRunMutex = $null
if (-not $RunLockAlreadyHeld) {
  . (Join-Path $PSScriptRoot 'qa-run-lock.ps1')
  $qaRunMutex = Enter-SpotAmpQaRunLock
  if ($null -eq $qaRunMutex) {
    Write-Output 'BLOCKED: another SpotAmp QA harness is using the shared Android device or QA artifacts. No companion tests were started; retry after it finishes.'
    exit 2
  }
}
$androidStudioJbr = 'C:\Program Files\Android\Android Studio\jbr'
if (Test-Path -LiteralPath (Join-Path $androidStudioJbr 'bin\java.exe')) {
  # Gradle 9 requires Java 17+; make the harness independent of a stale
  # machine-wide JAVA_HOME (commonly still pointing at Java 8).
  $env:JAVA_HOME = $androidStudioJbr
}
if (-not $SpotCoreRoot) { $SpotCoreRoot = Join-Path (Split-Path -Parent (Split-Path -Parent $repoRoot)) 'SpotCore' }
$SpotCoreRoot = [System.IO.Path]::GetFullPath($SpotCoreRoot)
$hostExecutable = Join-Path $SpotCoreRoot 'native-qt\build-mingw\CompanionQaServer.exe'
if (-not (Test-Path -LiteralPath $hostExecutable -PathType Leaf)) {
  $buildDirectory = Join-Path $SpotCoreRoot 'native-qt\build-mingw'
  if (-not (Test-Path -LiteralPath (Join-Path $buildDirectory 'CMakeCache.txt') -PathType Leaf)) {
    throw "The SpotCore Qt build directory is unavailable: '$buildDirectory'. Configure it before running current-Qt companion QA."
  }
  $cmake = Get-Command cmake -ErrorAction SilentlyContinue
  if (-not $cmake) { throw 'CMake is required to build SpotCore CompanionQaServer.' }
  & $cmake.Source --build $buildDirectory --target CompanionQaServer --config Release
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $hostExecutable -PathType Leaf)) {
    throw 'Could not build SpotCore native-Qt CompanionQaServer.'
  }
}

$runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-companion'
$outputDirectory = Join-Path $repoRoot "qa-results\companion-runs\$runId"
$fixtureDirectory = Join-Path $outputDirectory 'audio-fixtures'
$gradleExecutable = if ([string]::IsNullOrWhiteSpace($GradleExecutable)) {
  Join-Path $repoRoot 'gradlew.bat'
} else {
  [System.IO.Path]::GetFullPath($GradleExecutable)
}
$qaAppId = 'com.aistudio.plexmusicplayer.vctplx.qa'
$qaTestAppId = "$qaAppId.test"
$qaApk = Join-Path $repoRoot 'app/build/outputs/apk/qa/app-qa.apk'
$qaAndroidTestApk = Join-Path $repoRoot 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'
if (-not (Test-Path -LiteralPath $gradleExecutable -PathType Leaf)) {
  throw "Gradle executable was not found: '$gradleExecutable'."
}
$readyPath = Join-Path $env:TEMP "spotamp-$runId-ready.json"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
& (Join-Path $PSScriptRoot 'generate-audio-fixtures.ps1') -OutputDirectory $fixtureDirectory -DurationSeconds 30 -Force | Out-Null

$serverOut = Join-Path $outputDirectory 'spotcore.stdout.log'
$serverErr = Join-Path $outputDirectory 'spotcore.stderr.log'
$databasePath = Join-Path $outputDirectory 'spotcore-qa.sqlite'
$settingsPath = Join-Path $outputDirectory 'spotcore-qa-settings'
$serverEvidence = Join-Path $outputDirectory 'server-evidence.json'
$qaAppPreinstalledBeforeRun = $false
$qaAppPresentAfterRun = $false
$qaTestAppPresentAfterRun = $false
$qaInstallMode = 'use-installed-instrumentation'
$serverArgs = @(
  '--port', "$Port",
  '--control-port', "$ControlPort",
  '--database', "`"$databasePath`"",
  '--settings', "`"$settingsPath`"",
  '--fixtures', "`"$fixtureDirectory`"",
  '--ready', "`"$readyPath`"",
  '--evidence', "`"$serverEvidence`""
)
$server = Start-Process -FilePath $hostExecutable -ArgumentList $serverArgs -WorkingDirectory (Split-Path -Parent $hostExecutable) -RedirectStandardOutput $serverOut -RedirectStandardError $serverErr -WindowStyle Hidden -PassThru
$result = 'BLOCKED'
$reason = ''

try {
  $deadline = [DateTime]::UtcNow.AddSeconds(30)
  while (-not (Test-Path -LiteralPath $readyPath -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) {
    if ($server.HasExited) { throw "SpotCore Qt QA host exited with code $($server.ExitCode)." }
    Start-Sleep -Milliseconds 250
  }
  if (-not (Test-Path -LiteralPath $readyPath -PathType Leaf)) { throw 'SpotCore Qt QA host did not become ready within 30 seconds.' }
  $ready = Get-Content -LiteralPath $readyPath -Raw | ConvertFrom-Json
  adb reverse "tcp:$Port" "tcp:$Port" | Out-Null
  adb reverse "tcp:$ControlPort" "tcp:$ControlPort" | Out-Null

  $gradleOut = Join-Path $outputDirectory 'android.stdout.log'
  if (-not $UseInstalledInstrumentation) {
    $qaAppPreinstalledBeforeRun = Test-SpotAmpAndroidPackageInstalled -PackageName $qaAppId
    $assemblyOut = Join-Path $outputDirectory 'android-build.stdout.log'
    $assemblyErr = Join-Path $outputDirectory 'android-build.stderr.log'
    $assemblyArgs = @(':app:assembleQaAndroidTest', '--no-daemon', '--no-configuration-cache', '--no-build-cache', '--max-workers=1', '--console=plain')
    if ($DisableKspIncremental) { $assemblyArgs += '-Pksp.incremental=false' }
    if (-not $ResolveGradleDependencies) { $assemblyArgs += '--offline' }
    $assembly = Start-Process -FilePath $gradleExecutable -ArgumentList $assemblyArgs -WorkingDirectory $repoRoot -RedirectStandardOutput $assemblyOut -RedirectStandardError $assemblyErr -WindowStyle Hidden -PassThru
    if (-not $assembly.WaitForExit($TimeoutSeconds * 1000)) {
      try { $assembly.Kill($true) } catch { try { $assembly.Kill() } catch { } }
      throw "QA Android-test APK assembly exceeded the $TimeoutSeconds-second watchdog."
    }
    if ($assembly.ExitCode -ne 0) { throw "QA Android-test APK assembly failed with exit code $($assembly.ExitCode)." }
    if (-not (Test-Path -LiteralPath $qaApk -PathType Leaf) -or -not (Test-Path -LiteralPath $qaAndroidTestApk -PathType Leaf)) {
      throw 'QA app or Android-test APK is missing after assembleQaAndroidTest.'
    }

    $installLog = Join-Path $outputDirectory 'android-install.log'
    $qaInstallOutput = @(adb install -r $qaApk 2>&1)
    $qaInstallExitCode = $LASTEXITCODE
    if ($qaInstallOutput.Count -gt 0) { $qaInstallOutput | Add-Content -LiteralPath $installLog }
    if ($qaInstallExitCode -ne 0) { throw 'Could not install the isolated QA app with data-preserving replacement.' }
    $testInstallOutput = @(adb install -r $qaAndroidTestApk 2>&1)
    $testInstallExitCode = $LASTEXITCODE
    if ($testInstallOutput.Count -gt 0) { $testInstallOutput | Add-Content -LiteralPath $installLog }
    if ($testInstallExitCode -ne 0) { throw 'Could not install the QA Android-test APK.' }
    if (-not (Test-SpotAmpAndroidPackageInstalled -PackageName $qaAppId) -or
        -not (Test-SpotAmpAndroidPackageInstalled -PackageName $qaTestAppId)) {
      throw 'QA app/test package identity check failed after installation.'
    }
    $qaInstallMode = if ($qaAppPreinstalledBeforeRun) { 'replace-existing-with-app-data-retained' } else { 'fresh-install-no-existing-app-data' }
  } else {
    $qaAppPreinstalledBeforeRun = Test-SpotAmpAndroidPackageInstalled -PackageName $qaAppId
    if (-not $qaAppPreinstalledBeforeRun -or -not (Test-SpotAmpAndroidPackageInstalled -PackageName $qaTestAppId)) {
      throw 'UseInstalledInstrumentation requires the QA app and test APK to already be installed.'
    }
  }

  $targetPackage = $qaAppId
  $instrumentation = @(adb shell pm list instrumentation | Where-Object { $_ -match "target=$([regex]::Escape($targetPackage))\)" })
  if ($instrumentation.Count -ne 1) { throw "Expected one installed QA instrumentation targeting '$targetPackage'; found $($instrumentation.Count)." }
  $instrumentationComponent = $instrumentation[0] -replace '^instrumentation:([^ ]+).*$', '$1'
  $runnerArgs = @(
    '-e', 'class', 'com.example.CompanionRealPlaybackTest',
    '-e', 'companionBaseUrl', $ready.baseUrl,
    '-e', 'companionControlUrl', $ready.controlBaseUrl,
    '-e', 'companionPairingPayloadEncoded', $ready.pairingPayloadEncoded,
    $instrumentationComponent
  )
  $testStartUnix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
  $instrumentationRun = Invoke-SpotAmpAndroidInstrumentation -RunnerArguments $runnerArgs -OutputPath $gradleOut -TimeoutSeconds $TimeoutSeconds
  if ($instrumentationRun.TimedOut) {
    adb shell am force-stop --user 0 $qaTestAppId 2>$null | Out-Null
    $reason = "Connected real-companion test exceeded the $TimeoutSeconds-second watchdog."
  } else {
    $runnerOutput = $instrumentationRun.Output
    $junitPath = Join-Path $outputDirectory 'android.junit.xml'
    $parsedResults = ConvertFrom-SpotAmpInstrumentationOutput -Output $runnerOutput -JUnitPath $junitPath
    $qaAppPresentAfterRun = Test-SpotAmpAndroidPackageInstalled -PackageName $qaAppId
    $qaTestAppPresentAfterRun = Test-SpotAmpAndroidPackageInstalled -PackageName $qaTestAppId
    $runnerSucceeded = $instrumentationRun.ExitCode -eq 0 -and $parsedResults.Passed -and $qaAppPresentAfterRun -and $qaTestAppPresentAfterRun
    if ($runnerSucceeded) {
      $result = 'PASS'
      $reason = 'Current SpotCore Qt pairing and library indexing including metadata refresh, individual track removal and restoration, authoritative empty-library sync, bidirectional favorite/rating sync with offline retry, authenticated track/album/artist artwork and hashed-cache artwork, cross-origin external artwork byte fetch/decode, sidecar and embedded timed lyrics with seek alignment, including lyric revision refresh after a paired file edit, CLAP/Essentia sync, analysis-aware local Search and Mood/Style radio, unanalyzed facet behavior, bidirectional shared-playlist edits and conflict resolution, idempotent playlist-create retry, playlist reorder in both directions, deleted-track playlist refresh, audio streaming and recovery, offline playback, play-event outbox retry through a Qt outage with exactly-once canonical history, pairing revocation and re-pair, and background playback passed.'
    } else {
      $result = 'FAIL'
      $failureDetail = if (-not $qaAppPresentAfterRun) { 'QA app package was removed during instrumentation; app-data retention failed' }
        elseif (-not $qaTestAppPresentAfterRun) { 'QA instrumentation package was removed during the manual test run' }
        elseif ($instrumentationRun.ExitCode -ne 0) { "adb instrumentation exit code $($instrumentationRun.ExitCode)" }
        else { $parsedResults.Reason }
      $reason = "Real companion instrumentation failed: $failureDetail."
    }
  }} catch {
  $result = 'BLOCKED'
  $reason = $_.Exception.Message
} finally {
  adb reverse --remove "tcp:$Port" 2>$null | Out-Null
  adb reverse --remove "tcp:$ControlPort" 2>$null | Out-Null
  if ($ready -and $ready.controlBaseUrl) {
    try { Invoke-RestMethod -Uri "$($ready.controlBaseUrl.TrimEnd('/'))/shutdown" -Method Post -TimeoutSec 3 | Out-Null } catch {}
  }
  if ($server -and -not $server.HasExited) {
    try { $server.Kill($true) } catch {
      # Windows PowerShell's .NET Framework Process type does not expose
      # Kill(entireProcessTree); the QA server has no child process tree.
      try { $server.Kill() } catch { }
    }
    try { $server.WaitForExit() } catch { }
  }
  Remove-Item -LiteralPath $readyPath -Force -ErrorAction SilentlyContinue
}

# Read only checkpoints emitted by this device run so partial failures remain diagnosable.
if (((adb get-state 2>$null) -join '').Trim() -eq 'device') {
  $qaAppPresentAfterRun = Test-SpotAmpAndroidPackageInstalled -PackageName $qaAppId
  $qaTestAppPresentAfterRun = Test-SpotAmpAndroidPackageInstalled -PackageName $qaTestAppId
}
$verifiedCheckpoints = @{}
if ($testStartUnix -gt 0) {
  $deviceLog = @(adb logcat -d -v epoch -s CompanionRealQA:I '*:S' 2>$null)
  foreach ($line in $deviceLog) {
    if ($line -match '^\s*(?<epoch>\d+(?:\.\d+)?)\s+.*CompanionRealQA:\s+(?<name>[A-Za-z0-9-]+)') {
      $checkpointEpoch = [double]::Parse($matches.epoch, [System.Globalization.CultureInfo]::InvariantCulture)
      if ($checkpointEpoch -ge $testStartUnix) { $verifiedCheckpoints[$matches.name] = $true }
    }
  }
}
$checkpointLines = @($verifiedCheckpoints.Keys | Sort-Object)
if ($verifiedCheckpoints.ContainsKey('embedded-lyrics-blocked')) {
  $result = 'BLOCKED'
  $reason = 'Android embedded timed-lyrics validation is blocked because the current SpotCore Qt build has no TagLib support.'
}
[System.IO.File]::WriteAllLines((Join-Path $outputDirectory 'android-checkpoints.log'), [string[]]$checkpointLines)
$getStep = {
  param([string]$checkpoint)
  if ($verifiedCheckpoints.ContainsKey($checkpoint)) { 'PASS' }
  elseif ($result -eq 'BLOCKED' -and $verifiedCheckpoints.Count -eq 0) { 'BLOCKED' }
  else { 'NOT_RUN' }
}
# Server evidence is written by the Qt fixture host during the run.
$evidence = if (Test-Path -LiteralPath $serverEvidence) { Get-Content -LiteralPath $serverEvidence -Raw | ConvertFrom-Json } else { $null }
$report = [ordered]@{
  gate = 'companion-playback'
  runId = $runId
  result = $result
  failureClass = if ($result -eq 'BLOCKED') { 'infrastructure' } elseif ($result -eq 'FAIL' -and $reason -match 'Compilation error|Could not flush') { 'build' } elseif ($result -eq 'FAIL') { 'functional' } else { 'none' }
  reason = $reason
  desktopCompanionAvailable = $true
  desktopCompanionRealImplementation = $true
  spotCoreRoot = $SpotCoreRoot
  spotCoreImplementation = 'SpotCore Qt CompanionHttpServer with disposable SQLite fixtures'
  pairing = (& $getStep 'paired')
  qrPayloadGenerated = (& $getStep 'paired')
  qrPayloadParsed = (& $getStep 'paired')
  libraryIndexStarted = (& $getStep 'sync-started')
  libraryIndexCompleted = (& $getStep 'indexed')
  libraryMultiPageSync = (& $getStep 'multi-page-library-sync-verified')
  libraryMetadataAndIndividualRemoval = (& $getStep 'library-metadata-update-and-individual-removal-verified')
  libraryMetadataAndIndividualRemovalRestored = (& $getStep 'library-metadata-update-and-individual-removal-restored')
  libraryAuthoritativeEmptySnapshot = (& $getStep 'empty-library-snapshot-cleared-and-restored')
  libraryIncompletePageRetryPreservedCache = (& $getStep 'failed-page-retry-verified')
  libraryFailedPageRetry = (& $getStep 'failed-page-retry-verified')
  desktopFavoriteRatingToPhone = (& $getStep 'companion-favorite-rating-desktop-to-phone-verified')
  phoneFavoriteRatingToDesktop = (& $getStep 'companion-favorite-rating-phone-to-desktop-verified')
  favoriteRatingConflictRule = (& $getStep 'companion-favorite-rating-desktop-last-write-wins')
  favoriteRatingOfflineRetry = (& $getStep 'companion-favorite-rating-offline-retry-verified')
  favoriteRatingStateRestored = (& $getStep 'companion-favorite-rating-state-restored')
  libraryTrackRows = if ($evidence) { $evidence.libraryTrackRows } else { $null }
  libraryFetch = (& $getStep 'indexed')
  streamRequest = (& $getStep 'track-artwork-and-stream-verified')
  streamResponse = (& $getStep 'track-artwork-and-stream-verified')
  playerPrepared = (& $getStep 'track-a-playing')
  playerReady = (& $getStep 'track-a-playing')
  isPlayingObserved = (& $getStep 'track-a-playing')
  positionAdvanced = (& $getStep 'track-a-playing')
  pauseResume = (& $getStep 'resumed')
  seekForward = (& $getStep 'seeked')
  seekBackward = (& $getStep 'seeked')
  offlineFixtureDownload = (& $getStep 'offline-fixture-downloaded')
  offlinePlaybackDuringOutage = (& $getStep 'offline-cache-playback-during-outage-verified')
  offlinePlaybackResumedAfterReconnect = (& $getStep 'offline-cache-resumed-after-reconnect')
  trackTransition = (& $getStep 'track-c-playing')
  backgroundPlayback = (& $getStep 'background-verified')
  mediaSessionCoherence = (& $getStep 'background-verified')
  playbackSource = 'tracks read from the app indexed SpotCore library cache'
  failureRecovery = (& $getStep 'recovery-verified')
  failedDownloadRetryRecovery = (& $getStep 'failed-download-retry-recovery-verified')
  downloadsRetryButtonRecovery = (& $getStep 'failed-download-retry-recovery-verified')
  sharedPlaylistCreation = (& $getStep 'shared-playlist-created')
  sharedPlaylistDeleteCancellation = (& $getStep 'shared-playlist-delete-cancelled')
  sharedPlaylistRemoteAndLocalDeletion = (& $getStep 'shared-playlist-delete-confirmed-remote-and-local')
  bidirectionalPlaylistTrackSync = (& $getStep 'shared-playlist-bidirectional-sync-verified')
  playlistPhoneReorder = (& $getStep 'shared-playlist-phone-reorder-verified')
  playlistDesktopReorderSync = (& $getStep 'shared-playlist-desktop-reorder-sync-verified')
  playlistDeletedTrackRefresh = (& $getStep 'shared-playlist-deleted-track-refresh-verified')
  playlistCreateRetryIdempotent = (& $getStep 'shared-playlist-create-retry-idempotent')
  playlistConflictKeepPhoneEdits = (& $getStep 'playlist-conflict-keep-local-verified')
  playlistConflictUseSpotCoreVersion = (& $getStep 'playlist-conflict-use-remote-verified')
  trackArtwork = (& $getStep 'track-artwork-and-stream-verified')
  externalArtworkReferenceSync = (& $getStep 'external-artwork-shared-verified')
  externalArtworkBytesFetch = (& $getStep 'external-artwork-bytes-fetched-and-rendered')
  externalArtworkFetchRequests = if ($evidence) { $evidence.externalArtworkFetchRequests } else { $null }
  missingArtworkCacheCleared = (& $getStep 'missing-artwork-cleared-stale-cache-verified')
  missingArtworkRestored = (& $getStep 'missing-artwork-restored-verified')
  artworkCacheInvalidation = (& $getStep 'artwork-cache-invalidation-verified')
  hashedProductionArtworkCache = (& $getStep 'hashed-production-artwork-cache-verified')
  albumArtwork = (& $getStep 'home-album-and-artist-artwork-verified')
  artistArtwork = (& $getStep 'home-album-and-artist-artwork-verified')
  timedAndPlainLyrics = (& $getStep 'timed-and-plain-lyrics-verified')
  lyricsRevisionRefresh = (& $getStep 'lyrics-revision-refresh-verified')
  embeddedLyricsPhoneSync = (& $getStep 'embedded-lyrics-phone-sync-verified')
  embeddedLyricsHostCapability = if ($verifiedCheckpoints.ContainsKey('embedded-lyrics-blocked')) { 'BLOCKED' } elseif ($verifiedCheckpoints.ContainsKey('embedded-lyrics-phone-sync-verified')) { 'PASS' } else { 'NOT_RUN' }
  embeddedLyricsSeekAlignment = (& $getStep 'embedded-lyrics-seek-alignment-verified')
  clapAndEssentiaSync = (& $getStep 'clap-and-essentia-sync-verified')
  staleFailedAnalysisFacetFiltering = (& $getStep 'stale-analysis-filtered-from-facets-verified')
  partialAnalysisFacetBehavior = (& $getStep 'partial-analysis-facet-behavior-verified')
  analysisSearchRadioBehavior = (& $getStep 'analysis-search-radio-behavior-verified')
  unanalyzedAnalysisUi = (& $getStep 'unanalyzed-analysis-ui-verified')
  analysisStateRestored = (& $getStep 'analysis-state-restored-verified')
  canonicalListeningHistory = (& $getStep 'canonical-history-sync-verified')
  playbackEventOutageRetry = (& $getStep 'event-outage-retry-verified')
  pairingRevocationAndRepair = (& $getStep 'pairing-revocation-and-repair-verified')
  audibleOutputObservation = 'BLOCKED'
  levelB = if ($result -eq 'PASS') { 'PASS' } else { $result }
  levelC = 'BLOCKED'
  qaAppPreinstalledBeforeRun = $qaAppPreinstalledBeforeRun
  qaAppPresentAfterRun = $qaAppPresentAfterRun
  qaTestAppPresentAfterRun = $qaTestAppPresentAfterRun
  qaInstallMode = $qaInstallMode
  instrumentationTests = if ($parsedResults) { $parsedResults.TestCount } else { $null }
  instrumentationSuites = if ($parsedResults) { $parsedResults.SuiteCount } else { $null }
  instrumentationFailures = if ($parsedResults) { $parsedResults.Failures } else { $null }
  instrumentationErrors = if ($parsedResults) { $parsedResults.Errors } else { $null }
  instrumentationSkipped = if ($parsedResults) { $parsedResults.Skipped } else { $null }
  device = (adb get-state 2>$null)
  androidTest = 'com.example.CompanionRealPlaybackTest'
  fixtureTracks = @('QA Track A', 'QA Track B', 'QA Track C')
  serverEvidence = if ($evidence) { 'server-evidence.json' } else { $null }
  verifiedCheckpoints = $checkpointLines
  playHistoryRows = if ($evidence) { $evidence.playHistoryRows } else { $null }
  playbackEventRows = if ($evidence) { $evidence.playbackEventRows } else { $null }
  latestHistoryTrackId = if ($evidence) { $evidence.latestTrackId } else { $null }
  fixtureTrackFavoriteAfterRun = if ($evidence) { $evidence.qaTrackFavorite } else { $null }
  fixtureTrackRatingAfterRun = if ($evidence) { $evidence.qaTrackRating } else { $null }
  generatedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $outputDirectory 'COMPANION_PLAYBACK_REPORT.json')
@("# Real Companion Playback QA", "", "Result: **$result**", "", $reason, "", "- Real implementation: SpotCore Qt CompanionHttpServer", "- Level B automated playback: $($report.levelB)", "- Level C audible observation: BLOCKED", "- Run ID: $runId", "- Evidence directory: $outputDirectory") | Set-Content -LiteralPath (Join-Path $outputDirectory 'COMPANION_PLAYBACK_REPORT.md')
Copy-Item -LiteralPath (Join-Path $outputDirectory 'COMPANION_PLAYBACK_REPORT.json') -Destination (Join-Path $repoRoot 'qa-results\latest\COMPANION_PLAYBACK_REPORT.json') -Force
Copy-Item -LiteralPath (Join-Path $outputDirectory 'COMPANION_PLAYBACK_REPORT.md') -Destination (Join-Path $repoRoot 'qa-results\latest\COMPANION_PLAYBACK_REPORT.md') -Force
if ($qaRunMutex) { Exit-SpotAmpQaRunLock -Mutex $qaRunMutex }
if ($result -eq 'PASS') { exit 0 }
if ($result -eq 'FAIL') { exit 1 }
exit 2
