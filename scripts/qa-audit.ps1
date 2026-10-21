param(
  [string]$ReportPath = 'qa-results/latest/QA_REPORT.json',
  [switch]$IncludeArchivedArtifacts
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$failures = [System.Collections.Generic.List[string]]::new()

function Require-File([string]$relativePath) {
  if (-not (Test-Path -LiteralPath (Join-Path $root $relativePath) -PathType Leaf)) {
    $failures.Add("Missing required file: $relativePath")
  }
}

foreach ($path in @(
  'app/build.gradle.kts',
  'scripts/qa.ps1',
  'scripts/qa-instrumentation.psm1',
  'scripts/qa-run-lock.ps1',
  'scripts/qa-backend.ps1',
  'scripts/qa-backend-check.ps1',
  'scripts/qa-signing.ps1',
  'scripts/qa-companion.ps1',
  'scripts/qa-companion-real.ps1',
  'scripts/qa-companion-real.mjs',
  'scripts/verify-release.ps1',
  'docs/QA.md',
  'docs/BUILD_SIGNING.md',
  'docs/QA_COMPANION_PLAYBACK.md',
  'docs/QA_COMPANION_OBSERVATION_TEMPLATE.json',
  'docs/QA_ARCHITECTURE_AUDIT.md',
  'docs/QA_EXISTING_TEST_INVENTORY.md',
  'docs/QA_COVERAGE_MATRIX.md',
  'docs/QA_PERFORMANCE_BASELINE.md',
  'docs/CODEX_QA_WORKFLOW.md',
  'docs/QA_BUG_LEDGER.md',
  'docs/QA_FINAL_AUDIT.md',
  '.github/workflows/host-qa.yml'
)) { Require-File $path }

$gitignorePath = Join-Path $root '.gitignore'
if (Test-Path -LiteralPath $gitignorePath -PathType Leaf) {
  $gitignore = [IO.File]::ReadAllText($gitignorePath)
  foreach ($pattern in @('/qa-results/','/app/qa-results/')) {
    if ($gitignore -notmatch [Text.RegularExpressions.Regex]::Escape($pattern)) { $failures.Add("Generated QA output is not ignored: $pattern") }
  }
}
$buildPath = Join-Path $root 'app/build.gradle.kts'
if (Test-Path -LiteralPath $buildPath -PathType Leaf) {
  $build = [IO.File]::ReadAllText($buildPath)
  if ($build -notmatch 'signingConfig\s*=\s*if \(releaseSigningReady\) signingConfigs\.getByName\("release"\) else null') {
    $failures.Add('Release signing is not explicitly credential-gated.')
  }
  $releaseBlock = [Text.RegularExpressions.Regex]::Match($build, '(?s)\brelease\s*\{(?<body>.*?)\n\s*\}')
  if ($releaseBlock.Success -and $releaseBlock.Groups['body'].Value -match 'signingConfigs\.getByName\("debug"\)') {
    $failures.Add('Production release block still references the debug signing config.')
  }
  if ($build -match '(?m)\b(storePassword|keyPassword)\s*=\s*"[^"$]+"') {
    $failures.Add('A literal signing password appears in the Gradle build script.')
  }
}

$databasePath = Join-Path $root 'app/src/main/java/com/example/data/MusicDatabase.kt'
if (Test-Path -LiteralPath $databasePath -PathType Leaf) {
  $databaseSource = [IO.File]::ReadAllText($databasePath)
  if ($databaseSource -match 'fallbackToDestructiveMigration\s*\(') {
    $failures.Add('MusicDatabase still enables destructive migration fallback.')
  }
}

$networkConfigPath = Join-Path $root 'app/src/main/res/xml/network_security_config.xml'
if (Test-Path -LiteralPath $networkConfigPath -PathType Leaf) {
  $networkConfig = [IO.File]::ReadAllText($networkConfigPath)
  foreach ($domain in [regex]::Matches($networkConfig, '<domain[^>]*>([^<]+)</domain>')) {
    $endpointHost = $domain.Groups[1].Value.Trim()
    if ($endpointHost -notin @('localhost', '127.0.0.1', '10.0.2.2', '::1')) {
      $failures.Add("Network security config contains an unapproved hardcoded endpoint: $endpointHost")
    }
  }
}

$backendPath = Join-Path $root 'scripts/qa-backend.ps1'
if (Test-Path -LiteralPath $backendPath -PathType Leaf) {
  $backend = [IO.File]::ReadAllText($backendPath)
  if ($backend -notmatch '127\.0\.0\.1:\$Port') {
    $failures.Add('Disposable QA backend is not explicitly loopback-bound.')
  }
  if ($backend -match '(?i)(plex|spotcore|last\.fm|gemini)\s*[:=].*https?://') {
    $failures.Add('Disposable QA backend contains a production integration endpoint.')
  }
}

$releaseVerifierPath = Join-Path $root 'scripts/verify-release.ps1'
if (Test-Path -LiteralPath $releaseVerifierPath -PathType Leaf) {
  $releaseVerifier = [IO.File]::ReadAllText($releaseVerifierPath)
  foreach ($term in @('forbiddenEndpointMarkers', '140.186.136.228', 'SPOTCORE_TEST_TOKEN_DO_NOT_USE')) {
    if ($releaseVerifier -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Release verifier is missing packaged endpoint exclusion check: $term")
    }
  }
  if ($releaseVerifier -notmatch '\$entry in \$archive\.Entries' -or
      $releaseVerifier -notmatch '\[Text\.Encoding\]::Unicode\.GetString\(\$entryBytes\)') {
    $failures.Add('Release verifier does not scan decompressed APK entry strings in UTF-8 and UTF-16.')
  }
}

$appBuildScriptPath = Join-Path $root 'app/build.gradle.kts'
if (Test-Path -LiteralPath $appBuildScriptPath -PathType Leaf) {
  $appBuildScript = [IO.File]::ReadAllText($appBuildScriptPath)
  foreach ($term in @(
      'getByName("debug").kotlin.directories.add("src/sharedQa/java")',
      'getByName("qa").kotlin.directories.add("src/sharedQa/java")',
      'getByName("release").kotlin.directories.add("src/nonQa/java")',
      'getByName("benchmark").kotlin.directories.add("src/nonQa/java")')) {
    if ($appBuildScript -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Build variants are missing QA-content isolation wiring: $term")
    }
  }
  foreach ($path in @(
      'app/src/sharedQa/java/com/example/ui/QaLabScreen.kt',
      'app/src/sharedQa/res/values/strings.xml',
      'app/src/nonQa/java/com/example/ui/QaLabBuildTypeUi.kt')) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $path) -PathType Leaf)) {
      $failures.Add("QA-content variant source is missing: $path")
    }
  }
}

$runnerPath = Join-Path $root 'scripts/qa.ps1'
if (Test-Path -LiteralPath $runnerPath -PathType Leaf) {
  $runner = [IO.File]::ReadAllText($runnerPath)
  foreach ($tier in @('quick','storage','sync','playback','downloads','ui','stress','benchmark','full','endurance','release','qa','qa-backend','companion','signing','audit')) {
    if ($runner -notmatch [Text.RegularExpressions.Regex]::Escape("'$tier'")) {
      $failures.Add("Runner does not declare tier: $tier")
    }
  }
  if ($runner -notmatch "'quick'\s*\{[^\r\n]*:app:lintDebug") {
    $failures.Add('Quick tier does not run the debug lint/static-analysis task.')
  }
  if (-not $runner.Contains('Run-Gradle ''Quick'' $unitTestTask -ForceFreshResults') -or
      $runner -notmatch 'function Run-Gradle\([^\r\n]*\[switch\]\$ForceFreshResults' -or
      $runner -notmatch '\$ForceFreshResults -or \$env:SPOTAMP_STRESS_SEED') {
    $failures.Add('Quick tier does not force fresh unit-test execution for a trustworthy retained report.')
  }
  if ($runner -notmatch "'full'\s*\{[\s\S]{0,200}:app:lintDebug") {
    $failures.Add('Full tier does not run the debug lint/static-analysis task.')
  }
  $auditCase = [regex]::Match($runner, "(?s)'audit'\s*\{(?<body>.*?)\n\s*\}")
  if (-not $auditCase.Success -or $auditCase.Groups['body'].Value -notmatch [regex]::Escape('PASS QA framework audit')) {
    $failures.Add('Audit tier does not derive its result from the QA audit success marker.')
  } elseif ($auditCase.Groups['body'].Value -match '\$LASTEXITCODE') {
    $failures.Add('Audit tier relies on a native exit code that may be stale after a PowerShell audit script.')
  }
  if ($runner -notmatch 'backendChecks') {
    $failures.Add('Runner report does not expose controlled QA backend assertion count.')
  }
  foreach ($outcomeNode in @('failure','error','skipped')) {
    $nodeLookup = "SelectSingleNode('$outcomeNode')"
    if ($runner -notmatch [Text.RegularExpressions.Regex]::Escape($nodeLookup)) {
      $failures.Add("Runner does not count empty JUnit <$outcomeNode/> outcome nodes by presence.")
    }
  }
  $outcomeFixture = [xml]'<testsuites><testsuite name="empty-markers"><testcase name="failed"><failure /></testcase><testcase name="errored"><error /></testcase><testcase name="skipped"><skipped /></testcase></testsuite></testsuites>'
  $fixtureCounts = [ordered]@{ failure=0; error=0; skipped=0 }
  $fixtureOutcomeNodes = @($fixtureCounts.Keys)
  foreach ($testCase in @($outcomeFixture.testsuites.testsuite.testcase)) {
    foreach ($outcomeNode in $fixtureOutcomeNodes) {
      if ($null -ne $testCase.SelectSingleNode($outcomeNode)) { $fixtureCounts[$outcomeNode]++ }
    }
  }
  if ($fixtureCounts.failure -ne 1 -or $fixtureCounts.error -ne 1 -or $fixtureCounts.skipped -ne 1) {
    $failures.Add('Runner JUnit outcome presence fixture did not count empty marker nodes correctly.')
  }
  if ($runner -notmatch '\[Globalization\.DateTimeStyles\]::AssumeUniversal') {
    $failures.Add('Runner does not treat unzoned JUnit timestamps consistently as UTC.')
  }
  $fixtureRunStartedUtc = [DateTime]::Parse('2026-10-02T19:40:28Z').ToUniversalTime()
  $fixtureTimestampStyle = [Globalization.DateTimeStyles]::AssumeUniversal
  $fixtureCulture = [Globalization.CultureInfo]::InvariantCulture
  $staleUnzonedTimestamp = [DateTime]::Parse('2026-10-02T19:37:37', $fixtureCulture, $fixtureTimestampStyle).ToUniversalTime()
  $freshUnzonedTimestamp = [DateTime]::Parse('2026-10-02T19:41:09', $fixtureCulture, $fixtureTimestampStyle).ToUniversalTime()
  $freshOffsetTimestamp = [DateTime]::Parse('2026-10-02T15:41:09-04:00', $fixtureCulture, $fixtureTimestampStyle).ToUniversalTime()
  if ($staleUnzonedTimestamp -ge $fixtureRunStartedUtc.AddSeconds(-5) -or
      $freshUnzonedTimestamp -lt $fixtureRunStartedUtc.AddSeconds(-5) -or
      $freshOffsetTimestamp -lt $fixtureRunStartedUtc.AddSeconds(-5)) {
    $failures.Add('Runner UTC JUnit timestamp fixture did not reject stale XML and accept fresh UTC/offset XML.')
  }
  foreach ($term in @('connectedTimeoutSeconds', 'WaitForExit\(', 'Clear-StressCampaign', 'QA_AUDIT_REPORT')) {
    if ($runner -notmatch $term) { $failures.Add("Runner is missing reliability safeguard: $term") }
  }
  if ($runner -notmatch '\[ValidateRange\(1,20\)\]\s*\[int\]\$AdbProbeAttempts\s*=\s*20' -or
      $runner -notmatch 'adbProbeAttempts\s*=\s*\$AdbProbeAttempts') {
    $failures.Add('Runner does not expose and report a bounded connected-device probe-attempt count.')
  }
  if ($runner -notmatch '\[string\]\$ExpectedDeviceModel\s*=\s*''' -or
      $runner -notmatch 'expectedDeviceModel\s*=\s*\$ExpectedDeviceModel') {
    $failures.Add('Runner does not record an optional expected connected-device model.')
  }
  if ([regex]::Matches($runner, 'Get-ConnectedAndroidDevices -attempts \$AdbProbeAttempts').Count -ne 2) {
    $failures.Add('UI/full connected-device discovery does not use the bounded attempt-count option.')
  }
  $deviceProbeFunction = [regex]::Match($runner, '(?ms)^function Get-ConnectedAndroidDevices\([^\r\n]*\)\s*\{(?<body>.*?)^\}')
  $deviceProbeBody = if ($deviceProbeFunction.Success) { $deviceProbeFunction.Groups['body'].Value } else { '' }
  if (-not $deviceProbeFunction.Success -or $deviceProbeBody -notmatch 'Start-Sleep -Milliseconds') {
    $failures.Add('Connected UI preflight does not retry transient ADB server startup failures.')
  }
  if (-not $deviceProbeFunction.Success -or
      $deviceProbeBody -notmatch 'WaitForExit\(\$probeTimeoutMilliseconds\)' -or
      $deviceProbeBody -notmatch '\$probeProcess\.Kill\(\$true\)' -or
      $deviceProbeBody -notmatch 'if \(\$probeTimedOut\) \{ break \}') {
    $failures.Add('Connected UI preflight does not bound a hung ADB client and stop retrying after timeout.')
  }
  if (-not $deviceProbeFunction.Success -or
      $deviceProbeBody -notmatch '\$probeHistory\.Add\(' -or
      $deviceProbeBody -notmatch '\$probeHistory -join \[Environment\]::NewLine') {
    $failures.Add('Connected UI preflight does not retain per-attempt ADB output for infrastructure diagnosis.')
  }
  if ($runner -notmatch '\$script:connectedDeviceProbeDiagnostic' -or
      $deviceProbeBody -notmatch '\$probeStartInfo\.Arguments = ''devices''' -or
      $deviceProbeBody -notmatch 'RedirectStandardOutput = \$true' -or
      $deviceProbeBody -notmatch 'RedirectStandardError = \$true') {
    $failures.Add('Connected UI preflight suppresses ADB device-detection diagnostics.')
  }
  $metadataGuardText = 'if ($Tier -in @(''qa'',''companion''))'
  $metadataGuardIndex = $runner.IndexOf($metadataGuardText, [StringComparison]::Ordinal)
  $metadataQueryIndex = $runner.IndexOf('$deviceModel = ((adb shell getprop', [StringComparison]::Ordinal)
  if ($metadataGuardIndex -lt 0 -or $metadataQueryIndex -lt $metadataGuardIndex -or
      $metadataQueryIndex -gt $runner.IndexOf('function Update-ConnectedDeviceEnvironment', [StringComparison]::Ordinal)) {
    $failures.Add('Early ADB device-property queries are not limited to device-install/companion tiers.')
  }
  $failureCaptureFunction = [regex]::Match($runner, '(?ms)^function Capture-FailureArtifacts\([^\r\n]*\)\s*\{(?<body>.*?)^\}')
  $failureCaptureBody = if ($failureCaptureFunction.Success) { $failureCaptureFunction.Groups['body'].Value } else { '' }
  $deviceCaptureGuardIndex = $failureCaptureBody.IndexOf('if (-not $IncludeDeviceContext) { return }', [StringComparison]::Ordinal)
  $deviceCaptureQueryIndex = $failureCaptureBody.IndexOf('$devices = @(adb devices', [StringComparison]::Ordinal)
  if (-not $failureCaptureFunction.Success -or $deviceCaptureGuardIndex -lt 0 -or
      $deviceCaptureQueryIndex -lt 0 -or $deviceCaptureQueryIndex -lt $deviceCaptureGuardIndex -or
      $runner -notmatch 'Capture-FailureArtifacts \$name \$failureReason -IncludeDeviceContext:\$includeConnected') {
    $failures.Add('Host-test failure capture can access a device without explicit connected-test context.')
  }
  if ([regex]::Matches($runner, "Capture-FailureArtifacts 'UI'.*-IncludeDeviceContext").Count -ne 4) {
    $failures.Add('Connected UI failure captures do not explicitly opt in to device diagnostics after preflight.')
  }
  $deviceListIndex = $runner.IndexOf('$devices = @(Get-ConnectedAndroidDevices -attempts $AdbProbeAttempts)', [StringComparison]::Ordinal)
  $deviceMetadataIndex = if ($deviceListIndex -ge 0) { $runner.IndexOf('Update-ConnectedDeviceEnvironment', $deviceListIndex, [StringComparison]::Ordinal) } else { -1 }
  if ($deviceListIndex -lt 0 -or $deviceMetadataIndex -lt $deviceListIndex) {
    $failures.Add('Connected UI tier does not gather device metadata after successful enumeration.')
  }
  $expectedModelGuardIndex = $runner.IndexOf('$environment.device -ne $ExpectedDeviceModel', [StringComparison]::Ordinal)
  $deviceWakeIndex = $runner.IndexOf('& (Join-Path $PSScriptRoot ''wake-device.ps1'')', [StringComparison]::Ordinal)
  if ($runner -notmatch '\$ExpectedDeviceModel\).*\$devices\.Count\s*-ne\s*1' -or
      $runner -notmatch '\$env:ANDROID_SERIAL\s*=\s*\$connectedDeviceSerial' -or
      $expectedModelGuardIndex -lt 0 -or $deviceWakeIndex -lt $expectedModelGuardIndex) {
    $failures.Add('Runner does not require an unambiguous expected device before wake/install/test actions.')
  }
  if ($runner -notmatch '\$originalAndroidSerial\s*=\s*\[Environment\]::GetEnvironmentVariable\(''ANDROID_SERIAL''' -or
      $runner -notmatch 'Remove-Item Env:ANDROID_SERIAL' -or
      $runner -notmatch '\$env:ANDROID_SERIAL\s*=\s*\$originalAndroidSerial') {
    $failures.Add('Runner does not restore the caller''s ANDROID_SERIAL after device-targeted execution.')
  }
  if ($runner -notmatch '\$Tier\s*-ne\s*''audit''\s*-and') {
    $failures.Add('Runner may archive latest before standalone audit validation.')
  }
  $lockAcquireIndex = $runner.IndexOf('$qaRunMutex = Enter-SpotAmpQaRunLock', [StringComparison]::Ordinal)
  $sharedArtifactsIndex = $runner.IndexOf('$runsRoot = Join-Path', [StringComparison]::Ordinal)
  if ($lockAcquireIndex -lt 0 -or $sharedArtifactsIndex -lt 0 -or $lockAcquireIndex -gt $sharedArtifactsIndex) {
    $failures.Add('Runner must acquire the shared QA mutex before creating or rotating QA artifacts.')
  }
  if ($runner -notmatch 'qa-run-lock\.ps1' -or $runner -notmatch 'Exit-SpotAmpQaRunLock' -or $runner -notmatch 'QA run blocked:') {
    $failures.Add('Runner does not use and release the shared QA mutex with a fail-fast blocked result.')
  }
  if ($runner -notmatch 'qa-companion-real\.ps1''\)\s*-RunLockAlreadyHeld') {
    $failures.Add('Runner does not pass its held QA mutex into the nested companion harness.')
  }
  if ($runner -match ':app:connectedQaAndroidTest') {
    $failures.Add('Generic UI runner uses Gradle connected-test teardown, which uninstalls the QA app.')
  }
  if ($runner -notmatch "Run-Gradle 'UIInstall' ':app:assembleQa'") {
    $failures.Add('Generic UI runner does not assemble the QA target APK before installing it.')
  }
  if ($runner -notmatch "Run-Gradle 'UITestAssembly' ':app:assembleQaAndroidTest'") {
    $failures.Add('Generic UI runner does not separately assemble the matching QA instrumentation APK.')
  }
  foreach ($term in @('assembleQaAndroidTest', 'Invoke-SpotAmpAndroidInstrumentation', 'ConvertFrom-SpotAmpInstrumentationOutput', 'UIQaAppRetention', 'UIInstrumentationAppRetention')) {
    if ($runner -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Generic UI runner is missing install-retention safeguard: $term")
    }
  }
  $notificationGrantIndex = $runner.IndexOf('pm grant $qaAppId android.permission.POST_NOTIFICATIONS', [StringComparison]::Ordinal)
  $notificationRevokeIndex = $runner.IndexOf('pm revoke $qaAppId android.permission.POST_NOTIFICATIONS', [StringComparison]::Ordinal)
  if ($notificationGrantIndex -lt 0 -or $notificationRevokeIndex -le $notificationGrantIndex -or $runner -notmatch 'UINotificationPermissionCleanup') {
    $failures.Add('Connected UI permission grant is not followed by recorded QA-only cleanup.')
  }
  if ($runner -match "An absent QA package cannot retain an app-scoped runtime grant|UINotificationPermissionCleanup.*=\s*'PASS'.*absent") {
    $failures.Add('Connected UI runner can report notification cleanup PASS without revoking on a retained QA install.')
  }
}

$instrumentationModulePath = Join-Path $root 'scripts/qa-instrumentation.psm1'
if (Test-Path -LiteralPath $instrumentationModulePath -PathType Leaf) {
  Import-Module $instrumentationModulePath -Force
  $successFixture = @'
INSTRUMENTATION_STATUS: class=com.example.HomeTest
INSTRUMENTATION_STATUS: test=loadsHome
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.HomeTest
INSTRUMENTATION_STATUS: test=loadsHome
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=com.example.SettingsTest
INSTRUMENTATION_STATUS: test=opensSettings
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.SettingsTest
INSTRUMENTATION_STATUS: test=opensSettings
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: stream=

OK (2 tests)
INSTRUMENTATION_CODE: -1
'@
  $failureFixture = @'
INSTRUMENTATION_STATUS: class=com.example.HomeTest
INSTRUMENTATION_STATUS: test=loadsHome
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=com.example.HomeTest
INSTRUMENTATION_STATUS: test=loadsHome
INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: expected Home
INSTRUMENTATION_STATUS_CODE: -2
INSTRUMENTATION_RESULT: stream=

FAILURES!!!
Tests run: 1,  Failures: 1
INSTRUMENTATION_CODE: -1
'@
  $successXmlPath = Join-Path $env:TEMP ('spotamp-instrumentation-success-' + [Guid]::NewGuid().ToString('N') + '.xml')
  $failureXmlPath = Join-Path $env:TEMP ('spotamp-instrumentation-failure-' + [Guid]::NewGuid().ToString('N') + '.xml')
  try {
    $successResult = ConvertFrom-SpotAmpInstrumentationOutput -Output $successFixture -JUnitPath $successXmlPath
    if (-not $successResult.Passed -or $successResult.TestCount -ne 2 -or $successResult.SuiteCount -ne 2) {
      $failures.Add('Instrumentation parser success fixture did not preserve two test outcomes across two suites.')
    }
    if (Test-Path -LiteralPath $successXmlPath) {
      [xml]$successXml = [IO.File]::ReadAllText($successXmlPath)
      if ($successXml.SelectNodes('//testcase').Count -ne 2 -or $successXml.SelectNodes('//testsuite').Count -ne 2) {
        $failures.Add('Instrumentation parser success fixture emitted an incomplete JUnit report.')
      }
    } else { $failures.Add('Instrumentation parser success fixture did not emit JUnit XML.') }

    $failureResult = ConvertFrom-SpotAmpInstrumentationOutput -Output $failureFixture -JUnitPath $failureXmlPath
    if ($failureResult.Passed -or $failureResult.TestCount -ne 1 -or $failureResult.Failures -ne 1) {
      $failures.Add('Instrumentation parser failure fixture did not preserve its failed test outcome.')
    }
  } catch {
    $failures.Add('Instrumentation output parser fixtures threw: ' + $_.Exception.Message)
  } finally {
    Remove-Item -LiteralPath $successXmlPath, $failureXmlPath -Force -ErrorAction SilentlyContinue
  }
} else {
  $failures.Add('Android instrumentation helper is missing for parser validation.')
}

$lockHelperPath = Join-Path $root 'scripts/qa-run-lock.ps1'
if (Test-Path -LiteralPath $lockHelperPath -PathType Leaf) {
  $lockHelper = [IO.File]::ReadAllText($lockHelperPath)
  foreach ($term in @('Global\SpotAmpAndroidQaRun', 'WaitOne($WaitMilliseconds)', 'AbandonedMutexException', 'Enter-SpotAmpQaRunLock', 'Exit-SpotAmpQaRunLock')) {
    if ($lockHelper -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Shared QA mutex helper is missing required behavior: $term")
    }
  }
} else {
  $failures.Add('Shared QA mutex helper is missing.')
}

$companionRunnerPath = Join-Path $root 'scripts/qa-companion-real.ps1'
if (Test-Path -LiteralPath $companionRunnerPath -PathType Leaf) {
  $companionRunner = [IO.File]::ReadAllText($companionRunnerPath)
  foreach ($term in @('RunLockAlreadyHeld', 'qa-run-lock.ps1', 'Enter-SpotAmpQaRunLock', 'Exit-SpotAmpQaRunLock', 'BLOCKED: another SpotAmp QA harness')) {
    if ($companionRunner -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Real companion runner is missing shared QA-mutex behavior: $term")
    }
  }
  if ($companionRunner -match ':app:connectedQaAndroidTest') {
    $failures.Add('Real companion runner uses Gradle connected-test teardown, which uninstalls the QA app.')
  }
  foreach ($term in @('assembleQaAndroidTest', 'Invoke-SpotAmpAndroidInstrumentation', 'Test-SpotAmpAndroidPackageInstalled', 'qaAppPresentAfterRun')) {
    if ($companionRunner -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Real companion runner is missing QA-install retention verification: $term")
    }
  }
} else {
  $failures.Add('Real companion runner is missing for mutex validation.')
}

$devicePerformancePath = Join-Path $root 'scripts/qa-device-performance.ps1'
if (Test-Path -LiteralPath $devicePerformancePath -PathType Leaf) {
  $devicePerformance = [IO.File]::ReadAllText($devicePerformancePath)
  foreach ($term in @('Samples', 'coldStartP95Ms', 'warmStartP95Ms', 'thermalSnapshot', 'COMPARABLE_ONLY_WITH_MATCHING_METADATA')) {
    if ($devicePerformance -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Device performance script is missing required capability: $term")
    }
  }
} else {
  $failures.Add('Device performance script is missing.')
}

$companionScriptPath = Join-Path $root 'scripts/qa-companion.ps1'
if (Test-Path -LiteralPath $companionScriptPath -PathType Leaf) {
  $companionScript = [IO.File]::ReadAllText($companionScriptPath)
  foreach ($term in @('redactionConfirmed', 'placeholder run or SpotCore build values', 'observationRunId')) {
    if ($companionScript -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("Companion gate is missing required evidence safeguard: $term")
    }
  }
} else {
  $failures.Add('Companion gate script is missing.')
}

$qaDoc = Join-Path $root 'docs/QA.md'
if (Test-Path -LiteralPath $qaDoc -PathType Leaf) {
  $doc = [IO.File]::ReadAllText($qaDoc)
  foreach ($term in @('companion-only','paired desktop SpotCore','BLOCKED','qa-signing.ps1','qa-companion.ps1')) {
    if ($doc -notmatch [Text.RegularExpressions.Regex]::Escape($term)) {
      $failures.Add("QA documentation is missing required boundary/policy term: $term")
    }
  }
}

$resolvedReport = if ([IO.Path]::IsPathRooted($ReportPath)) { $ReportPath } else { Join-Path $root $ReportPath }
if (Test-Path -LiteralPath $resolvedReport -PathType Leaf) {
  try {
    $report = [IO.File]::ReadAllText($resolvedReport) | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace([string]$report.result)) { $failures.Add('QA report is missing result.') }
    if ([string]::IsNullOrWhiteSpace([string]$report.tier)) { $failures.Add('QA report is missing the selected tier field.') }
    if ($null -eq $report.tiers) { $failures.Add('QA report is missing tiers.') }
    $allowedOutcomes = @('PASS', 'FAIL', 'BLOCKED', 'SKIPPED', 'NOT_IMPLEMENTED', 'INCOMPLETE')
    if ([string]$report.result -notin $allowedOutcomes) { $failures.Add("QA report has an invalid aggregate result: $($report.result)") }
    if ($null -ne $report.tiers) {
      foreach ($tierProperty in $report.tiers.PSObject.Properties) {
        if ([string]$tierProperty.Value -notin $allowedOutcomes) { $failures.Add("QA report has an invalid tier outcome: $($tierProperty.Name)=$($tierProperty.Value)") }
        if ($null -ne $report.failureClass -and $report.failureClass.PSObject.Properties.Name -contains $tierProperty.Name) {
          $classification = [string]$report.failureClass.($tierProperty.Name)
          if ($tierProperty.Value -eq 'BLOCKED' -and $classification -ne 'infrastructure') { $failures.Add("Blocked tier is not classified as infrastructure: $($tierProperty.Name)=$classification") }
          if ($tierProperty.Value -eq 'FAIL' -and $classification -ne 'functional') { $failures.Add("Failed tier is not classified as functional: $($tierProperty.Name)=$classification") }
        }
      }
    }
    if ($null -eq $report.tests) { $failures.Add('QA report is missing test summary.') }
    if ($null -eq $report.failureClass) { $failures.Add('QA report is missing failure classifications.') }
    if ($null -eq $report.artifacts) { $failures.Add('QA report is missing artifact intent metadata.') }
    if ($report.tiers.QABackend -eq 'PASS' -and ($null -eq $report.backendChecks -or [int]$report.backendChecks -lt 1)) { $failures.Add('Passing QA backend report is missing assertion count.') }
  } catch {
    $failures.Add("QA report is not valid JSON: $ReportPath")
  }
} else {
  $failures.Add("QA report not found: $ReportPath")
}

$companionReportPath = Join-Path $root 'qa-results/latest/COMPANION_PLAYBACK_REPORT.json'
if (Test-Path -LiteralPath $companionReportPath -PathType Leaf) {
  try {
    $companionReport = [IO.File]::ReadAllText($companionReportPath) | ConvertFrom-Json
    if ($companionReport.gate -ne 'companion-playback') { $failures.Add('Companion report has an unexpected gate name.') }
    if ([string]$companionReport.result -notin @('PASS', 'BLOCKED', 'FAIL')) { $failures.Add('Companion report has an invalid result vocabulary.') }
    if ([string]::IsNullOrWhiteSpace([string]$companionReport.runId)) { $failures.Add('Companion report is missing a gate run ID.') }
  } catch {
    $failures.Add('Companion report is not valid JSON.')
  }
}

$qaOutputRoots = [System.Collections.Generic.List[string]]::new()
$qaOutputRoots.Add((Join-Path $root 'qa-results/latest'))
$companionRunsRoot = Join-Path $root 'qa-results/companion-runs'
if ($IncludeArchivedArtifacts) {
  $qaOutputRoots.Add((Join-Path $root 'qa-results/runs'))
  $qaOutputRoots.Add($companionRunsRoot)
} elseif (Test-Path -LiteralPath $companionRunsRoot -PathType Container) {
  $latestCompanionRun = Get-ChildItem -LiteralPath $companionRunsRoot -Directory -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
  if ($latestCompanionRun) { $qaOutputRoots.Add($latestCompanionRun.FullName) }
}
$textArtifactExtensions = @('.csv', '.json', '.log', '.md', '.properties', '.txt', '.xml', '.yaml', '.yml')
$credentialMarkerPattern = '(?i)-----BEGIN [A-Z ]*PRIVATE KEY-----|(?:store|key|signing)[_-]?password\s*[=:]\s*[^\s]+|PLEX_TEST_TOKEN_DO_NOT_USE|SPOTCORE_TEST_TOKEN_DO_NOT_USE|LASTFM_TEST_TOKEN_DO_NOT_USE|GEMINI_TEST_KEY_DO_NOT_USE'
$credentialMarkerRegex = [regex]::new($credentialMarkerPattern)
$credentialScanBuffer = New-Object char[] (1024 * 1024)
$credentialScanOverlap = 256
foreach ($qaOutputRoot in $qaOutputRoots) {
  if (-not (Test-Path -LiteralPath $qaOutputRoot -PathType Container)) { continue }
  foreach ($file in Get-ChildItem -LiteralPath $qaOutputRoot -File -Recurse -ErrorAction SilentlyContinue) {
    if ($file.Extension.ToLowerInvariant() -notin $textArtifactExtensions) { continue }
    $reader = [IO.StreamReader]::new($file.FullName)
    try {
      $carry = ''
      while (($charsRead = $reader.Read($credentialScanBuffer, 0, $credentialScanBuffer.Length)) -gt 0) {
        $chunk = [string]::new($credentialScanBuffer, 0, $charsRead)
        $content = $carry + $chunk
        if ($credentialMarkerRegex.IsMatch($content)) {
          $failures.Add("Possible credential material found in QA artifact: $($file.FullName)")
          break
        }
        $carry = if ($content.Length -gt $credentialScanOverlap) {
          $content.Substring($content.Length - $credentialScanOverlap)
        } else { $content }
      }
    } finally {
      $reader.Dispose()
    }
  }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}

Write-Output 'PASS QA framework audit'
Write-Output "root=$root"
Write-Output "report=$resolvedReport"
Write-Output "archivedArtifactsScanned=$([bool]$IncludeArchivedArtifacts)"
