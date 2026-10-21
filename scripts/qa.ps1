param(
  [Parameter(Position=0)] [ValidateSet('quick','storage','sync','playback','downloads','ui','stress','benchmark','full','endurance','release','qa','qa-backend','companion','signing','audit','now-playing')] [string]$Tier = 'quick',
  [int]$Seed = 847229173,
  [int]$Actions = 500,
  [string]$CompanionBaseUrl = '',
  [string]$GradleExecutable = '',
  [string]$TestFilter = '',
  [ValidateRange(1,20)] [int]$AdbProbeAttempts = 20,
  [string]$ExpectedDeviceModel = ''
)

$root = Split-Path -Parent $PSScriptRoot
$originalAndroidSerial = [Environment]::GetEnvironmentVariable('ANDROID_SERIAL', 'Process')
Import-Module (Join-Path $PSScriptRoot 'qa-instrumentation.psm1') -Force
. (Join-Path $PSScriptRoot 'qa-run-lock.ps1')
$qaRunMutex = Enter-SpotAmpQaRunLock
if ($null -eq $qaRunMutex) {
  Write-Output 'QA run blocked: another SpotAmp QA harness is using the shared Android device or QA artifacts. No tests were started; retry after it finishes.'
  exit 2
}
try {
$ErrorActionPreference = 'Continue'
$appId = 'com.aistudio.plexmusicplayer.vctplx'
$qaAppId = 'com.aistudio.plexmusicplayer.vctplx.qa'
$qaTestAppId = "$qaAppId.test"
$unitTestTask = ':app:testQaUnitTest'
$debugApk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
$qaApk = Join-Path $root 'app/build/outputs/apk/qa/app-qa.apk'
$qaAndroidTestApk = Join-Path $root 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'
$jbr = 'C:\Program Files\Android\Android Studio\jbr'
if ([string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
  $sdkCandidate = $env:ANDROID_HOME
  if ([string]::IsNullOrWhiteSpace($sdkCandidate)) { $sdkCandidate = Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk' }
  if (Test-Path -LiteralPath $sdkCandidate -PathType Container) {
    $env:ANDROID_SDK_ROOT = (Resolve-Path -LiteralPath $sdkCandidate).Path
  }
}
$useDefaultGradle = [string]::IsNullOrWhiteSpace($GradleExecutable)
$gradleExecutable = if ($useDefaultGradle) {
  Join-Path $root 'gradlew.bat'
} else {
  [System.IO.Path]::GetFullPath($GradleExecutable)
}
if ($useDefaultGradle) {
  $wrapperPropertiesPath = Join-Path $root 'gradle/wrapper/gradle-wrapper.properties'
  if (Test-Path -LiteralPath $wrapperPropertiesPath -PathType Leaf) {
    $distributionMatch = [Text.RegularExpressions.Regex]::Match([IO.File]::ReadAllText($wrapperPropertiesPath), '(?m)^distributionUrl=(.+)$')
    if ($distributionMatch.Success) {
      $distributionUrl = $distributionMatch.Groups[1].Value.Replace('\:', ':').Replace('\\', '\')
      $distributionFile = [IO.Path]::GetFileName(([Uri]$distributionUrl).AbsolutePath)
      $distributionName = [IO.Path]::GetFileNameWithoutExtension($distributionFile)
      $unpackedName = $distributionName -replace '-(bin|all)$', ''
      $gradleUserHome = if (-not [string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        $env:GRADLE_USER_HOME
      } else {
        Join-Path $env:USERPROFILE '.gradle'
      }
      $distributionCache = Join-Path $gradleUserHome "wrapper/dists/$distributionName"
      if (Test-Path -LiteralPath $distributionCache -PathType Container) {
        foreach ($cacheBucket in (Get-ChildItem -LiteralPath $distributionCache -Directory -ErrorAction SilentlyContinue)) {
          $cachedGradle = Join-Path $cacheBucket.FullName "$unpackedName/bin/gradle.bat"
          if (Test-Path -LiteralPath $cachedGradle -PathType Leaf) {
            $gradleExecutable = $cachedGradle
            break
          }
        }
      }
    }
  }
}
if (-not (Test-Path -LiteralPath $gradleExecutable -PathType Leaf)) {
  throw "Gradle executable was not found: '$gradleExecutable'."
}
Write-Output "Gradle executable: $gradleExecutable"
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { Write-Output "Android SDK: $env:ANDROID_SDK_ROOT" }
if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
$connectedTimeoutSeconds = 180
$parsedConnectedTimeout = 0
if ([int]::TryParse($env:SPOTAMP_CONNECTED_TIMEOUT_SECONDS, [ref]$parsedConnectedTimeout) -and $parsedConnectedTimeout -gt 0) {
  $connectedTimeoutSeconds = $parsedConnectedTimeout
}

if ([string]::IsNullOrWhiteSpace($env:JAVA_TOOL_OPTIONS)) { $env:JAVA_TOOL_OPTIONS = '-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m' }
$gradleNativeDir = Join-Path $root 'build/gradle-native'
if ($env:JAVA_TOOL_OPTIONS -notmatch '(?:^|\s)-Dorg\.gradle\.native\.dir=') {
  New-Item -ItemType Directory -Force -Path $gradleNativeDir | Out-Null
  $nativeDirOption = '-Dorg.gradle.native.dir="' + $gradleNativeDir + '"'
  $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' ' + $nativeDirOption).Trim()
}
$gradleDaemonDir = Join-Path $root 'build/gradle-daemon'
if ($env:JAVA_TOOL_OPTIONS -notmatch '(?:^|\s)-Dorg\.gradle\.daemon\.registry\.base=') {
  New-Item -ItemType Directory -Force -Path $gradleDaemonDir | Out-Null
  $daemonDirOption = '-Dorg.gradle.daemon.registry.base="' + $gradleDaemonDir + '"'
  $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' ' + $daemonDirOption).Trim()
}
$runsRoot = Join-Path $root 'qa-results/runs'
$latest = Join-Path $root 'qa-results/latest'
$runId = ((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ') + '-' + $Tier)
New-Item -ItemType Directory -Force -Path $runsRoot | Out-Null
if ($Tier -ne 'audit' -and (Test-Path -LiteralPath $latest -PathType Container)) {
  $archiveRunId = $null
  $previousReportPath = Join-Path $latest 'QA_REPORT.json'
  if (Test-Path -LiteralPath $previousReportPath -PathType Leaf) {
    try { $archiveRunId = ([IO.File]::ReadAllText($previousReportPath) | ConvertFrom-Json).runId } catch { $archiveRunId = $null }
  }
  if ([string]::IsNullOrWhiteSpace([string]$archiveRunId) -or [string]$archiveRunId -notmatch '^[A-Za-z0-9_.-]+$') { $archiveRunId = 'archived-' + (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ') }
  $archivePath = Join-Path $runsRoot ([string]$archiveRunId)
  if (Test-Path -LiteralPath $archivePath) { $archivePath = Join-Path $runsRoot ([string]$archiveRunId + '-' + [Guid]::NewGuid().ToString('N')) }
  Move-Item -LiteralPath $latest -Destination $archivePath
}
New-Item -ItemType Directory -Force -Path $latest | Out-Null
foreach ($d in @('junit','logs','screenshots','traces','benchmarks','failure-context')) { New-Item -ItemType Directory -Force -Path (Join-Path $latest $d) | Out-Null }
$results = [ordered]@{}
$backendChecks = $null
$deviceModel = ''
$androidVersion = ''
# Keep host-only tiers such as Quick independent of any attached Android device.
# The QA install and real-companion tiers need device metadata before their device work.
if ($Tier -in @('qa','companion')) {
  $deviceModel = ((adb shell getprop ro.product.model 2>$null) -join '').Trim()
  $androidVersion = ((adb shell getprop ro.build.version.release 2>$null) -join '').Trim()
}
$environment = [ordered]@{
  project = $root
  device = if ($deviceModel) { $deviceModel } else { 'none' }
  android = if ($androidVersion) { $androidVersion } else { 'none' }
  javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'unset' }
  generatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
  runId = $runId
  connectedTimeoutSeconds = $connectedTimeoutSeconds
  adbProbeAttempts = $AdbProbeAttempts
  expectedDeviceModel = $ExpectedDeviceModel
  qaAppPreinstalledBeforeUiRun = $null
  qaAppPresentAfterUiRun = $null
}

$testSummary = [ordered]@{ suites=0; tests=0; failures=0; errors=0; skipped=0 }
$seenSuites = New-Object 'System.Collections.Generic.HashSet[string]'
$seenTests = New-Object 'System.Collections.Generic.HashSet[string]'
$script:connectedDeviceProbeDiagnostic = ''
$junitRoot = Join-Path $latest 'junit'
function Collect-TestResults([string]$runName, [bool]$includeConnected = $false, [string]$testFilter = '') {
  $xmlFiles = @()
  $resultRoots = if ($includeConnected) {
    @((Join-Path $root 'app/build/outputs/androidTest-results/connected'))
  } else {
    @((Join-Path $root 'app/build/test-results'))
  }
  foreach ($resultsRoot in $resultRoots) {
    if (Test-Path -LiteralPath $resultsRoot) {
      $xmlFiles += @(Get-ChildItem -LiteralPath $resultsRoot -Filter '*.xml' -File -Recurse -ErrorAction SilentlyContinue)
    }
  }
  $runStartedUtc = [DateTime]::Parse($environment.generatedAtUtc).ToUniversalTime()
  $matchedXmlFiles = 0
  foreach ($xmlFile in $xmlFiles) {
    try {
      [xml]$x = [IO.File]::ReadAllText($xmlFile.FullName)
      $timestampText = if ($x.testsuites -and $x.testsuites.timestamp) { [string]$x.testsuites.timestamp } elseif ($x.testsuite -and $x.testsuite.timestamp) { [string]$x.testsuite.timestamp } else { '' }
      if ([string]::IsNullOrWhiteSpace($timestampText)) {
        continue
      }
      try {
        # Gradle/JUnit XML timestamps are ISO values commonly emitted without a
        # timezone suffix. Treat an unzoned value as UTC, like generatedAtUtc,
        # rather than converting it from the host's local timezone.
        $xmlTimestamp = [DateTime]::Parse(
          $timestampText,
          [Globalization.CultureInfo]::InvariantCulture,
          [Globalization.DateTimeStyles]::AssumeUniversal
        ).ToUniversalTime()
      } catch {
        continue
      }
      if ($xmlTimestamp -lt $runStartedUtc.AddSeconds(-5)) {
        continue
      }
      $nodes = @($x.testsuite) + @($x.testsuites.testsuite)
      $safeName = $runName -replace '[^A-Za-z0-9_.-]','_'
      $runRoot = Join-Path $junitRoot $safeName
      New-Item -ItemType Directory -Force -Path $runRoot | Out-Null
      Copy-Item -LiteralPath $xmlFile.FullName -Destination $runRoot -Force
      $matchedXmlFiles++
      foreach ($suite in $nodes) {
        if($null -eq $suite) { continue }
        $suiteName = if($suite.Attributes['name']) { $suite.Attributes['name'].Value } else { $xmlFile.BaseName }
        $suiteMatched = $false
        foreach ($testCase in @($suite.testcase)) {
          if($null -eq $testCase) { continue }
          $testClass = if($testCase.Attributes['classname']) { $testCase.Attributes['classname'].Value } else { '' }
          $testName = if($testCase.Attributes['name']) { $testCase.Attributes['name'].Value } else { '' }
          if($testFilter) {
            $patterns = @($testFilter -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
            $includePatterns = @($patterns | Where-Object { -not $_.StartsWith('!') })
            $excludePatterns = @($patterns | Where-Object { $_.StartsWith('!') } | ForEach-Object { $_.Substring(1) })
            $matchesInclude = $includePatterns.Count -eq 0
            foreach($pattern in $includePatterns) {
              $methodSeparator = $pattern.IndexOf('#')
              $matchesPattern = if ($methodSeparator -ge 0) {
                $classPattern = $pattern.Substring(0, $methodSeparator)
                $methodPattern = $pattern.Substring($methodSeparator + 1)
                ($testClass -like $classPattern) -and ($testName -like $methodPattern)
              } else {
                ($testClass -like $pattern) -or ($testName -like $pattern)
              }
              if($matchesPattern) { $matchesInclude = $true; break }
            }
            $matchesExclude = $false
            foreach($pattern in $excludePatterns) {
              $methodSeparator = $pattern.IndexOf('#')
              $matchesPattern = if ($methodSeparator -ge 0) {
                $classPattern = $pattern.Substring(0, $methodSeparator)
                $methodPattern = $pattern.Substring($methodSeparator + 1)
                ($testClass -like $classPattern) -and ($testName -like $methodPattern)
              } else {
                ($testClass -like $pattern) -or ($testName -like $pattern)
              }
              if($matchesPattern) { $matchesExclude = $true; break }
            }
            if(-not $matchesInclude -or $matchesExclude) { continue }
          }
          $suiteMatched = $true
          $testCaseKey = $testClass + '|' + $testName
          if($seenTests.Add($testCaseKey)) {
            $testSummary.tests++
            # JUnit's <failure/>, <error/>, and <skipped/> nodes are often empty;
            # testing their text value loses those outcomes. Test node presence.
            if($null -ne $testCase.SelectSingleNode('failure')) { $testSummary.failures++ }
            if($null -ne $testCase.SelectSingleNode('error')) { $testSummary.errors++ }
            if($null -ne $testCase.SelectSingleNode('skipped')) { $testSummary.skipped++ }
          }
        }
        if($suiteMatched -and $seenSuites.Add($suiteName)) { $testSummary.suites++ }
      }
    } catch { $testSummary.errors++ }
  }
  if ($includeConnected -and $matchedXmlFiles -eq 0) {
    $results[$runName] = 'FAIL'
    Capture-FailureArtifacts $runName 'Connected Gradle task completed without producing fresh instrumentation XML.' -IncludeDeviceContext
  }
}
function Test-DeviceReadyForCompose {
  $policy = adb shell dumpsys window policy 2>$null | Out-String
  return ($policy -match 'mIsShowing=false' -and $policy -match 'interactiveState=INTERACTIVE_STATE_AWAKE')
}
# Starting a cold Windows ADB server can briefly fail its first client handshake.
# Allow retryable failures to settle, but keep any one ADB client from hanging preflight indefinitely.
function Get-ConnectedAndroidDevices([int]$attempts = 20, [int]$delayMilliseconds = 1500, [int]$probeTimeoutMilliseconds = 15000) {
  $probeHistory = [System.Collections.Generic.List[string]]::new()
  $adbExecutable = (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -First 1).Source
  if ([string]::IsNullOrWhiteSpace($adbExecutable) -or -not (Test-Path -LiteralPath $adbExecutable -PathType Leaf)) {
    $script:connectedDeviceProbeDiagnostic = 'adb executable could not be resolved from PATH.'
    return @()
  }
  $script:connectedDeviceProbeDiagnostic = "adbExecutable=$adbExecutable probeTimeoutMilliseconds=$probeTimeoutMilliseconds"
  for ($attempt = 1; $attempt -le $attempts; $attempt++) {
    $probeTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $probeStartInfo = [Diagnostics.ProcessStartInfo]::new()
    $probeStartInfo.FileName = $adbExecutable
    $probeStartInfo.Arguments = 'devices'
    $probeStartInfo.WorkingDirectory = $root
    $probeStartInfo.UseShellExecute = $false
    $probeStartInfo.CreateNoWindow = $true
    $probeStartInfo.RedirectStandardOutput = $true
    $probeStartInfo.RedirectStandardError = $true
    $probeProcess = [Diagnostics.Process]::new()
    $probeProcess.StartInfo = $probeStartInfo
    $probeProcessId = $null
    $probeStdoutText = ''
    $probeStderrText = ''
    $probeTimedOut = $false
    $probeExitCode = 'START_ERROR'
    try {
      [void]$probeProcess.Start()
      $probeProcessId = $probeProcess.Id
      $stdoutTask = $probeProcess.StandardOutput.ReadToEndAsync()
      $stderrTask = $probeProcess.StandardError.ReadToEndAsync()
      if (-not $probeProcess.WaitForExit($probeTimeoutMilliseconds)) {
        $probeTimedOut = $true
        try { $probeProcess.Kill($true) } catch { try { $probeProcess.Kill() } catch { } }
        $probeProcess.WaitForExit()
      } else {
        $probeProcess.WaitForExit()
      }
      $probeStdoutText = $stdoutTask.Result
      $probeStderrText = $stderrTask.Result
      if (-not $probeTimedOut) { $probeExitCode = $probeProcess.ExitCode } else { $probeExitCode = 'TIMEOUT' }
    } catch {
      $probeStderrText = $_.Exception.Message
    } finally {
      $probeTimer.Stop()
      $probeProcess.Dispose()
    }
    $probeOutput = @()
    if (-not [string]::IsNullOrWhiteSpace($probeStdoutText)) { $probeOutput += $probeStdoutText -split '\r?\n' }
    if (-not [string]::IsNullOrWhiteSpace($probeStderrText)) { $probeOutput += $probeStderrText -split '\r?\n' }
    $probeText = ($probeOutput | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ }) -join ' | '
    $probeHistory.Add("attempt=$attempt exitCode=$probeExitCode elapsedMs=$($probeTimer.ElapsedMilliseconds) processId=$probeProcessId output=$probeText")
    $script:connectedDeviceProbeDiagnostic = $probeHistory -join [Environment]::NewLine
    $devices = @($probeOutput | Where-Object { ([string]$_) -match '\tdevice$' })
    if ($devices.Count -gt 0) { return $devices }
    if ($probeTimedOut) { break }
    if ($attempt -lt $attempts) { Start-Sleep -Milliseconds $delayMilliseconds }
  }
  $script:connectedDeviceProbeDiagnostic = "adbExecutable=$adbExecutable probeTimeoutMilliseconds=$probeTimeoutMilliseconds`n" + ($probeHistory -join [Environment]::NewLine)
  return @()
}
function Update-ConnectedDeviceEnvironment {
  $deviceModel = ((adb shell getprop ro.product.model 2>$null) -join '').Trim()
  $androidVersion = ((adb shell getprop ro.build.version.release 2>$null) -join '').Trim()
  $environment.device = if ($deviceModel) { $deviceModel } else { 'unknown' }
  $environment.android = if ($androidVersion) { $androidVersion } else { 'unknown' }
}
function Test-QaPackageInstalled([string]$packageName) {
  return Test-SpotAmpAndroidPackageInstalled -PackageName $packageName
}
function Capture-FailureArtifacts([string]$name, [string]$reason, [switch]$IncludeDeviceContext) {
  $safeName = $name.ToLower() -replace '[^a-z0-9_.-]', '_'
  $contextRoot = Join-Path $latest 'failure-context'
  Set-Content -LiteralPath (Join-Path $contextRoot ("{0}.txt" -f $safeName)) -Value ("tier={0}`nreason={1}`nutc={2}" -f $name, $reason, (Get-Date).ToUniversalTime().ToString('o'))
  if (-not $IncludeDeviceContext) { return }
  $devices = @(adb devices 2>$null | Select-String '\tdevice$')
  if ($devices.Count -gt 0) {
    adb logcat -d -v threadtime | Set-Content -LiteralPath (Join-Path $contextRoot ("{0}-logcat.txt" -f $safeName))
    $screenPath = Join-Path $contextRoot ("{0}-screen.png" -f $safeName)
    cmd.exe /c ("adb exec-out screencap -p > ""{0}""" -f $screenPath) | Out-Null
    adb shell dumpsys window windows 2>$null | Set-Content -LiteralPath (Join-Path $contextRoot ("{0}-window.txt" -f $safeName))
    adb shell uiautomator dump /sdcard/spotamp-qa-failure.xml 2>$null | Out-Null
    adb exec-out cat /sdcard/spotamp-qa-failure.xml 2>$null | Set-Content -LiteralPath (Join-Path $contextRoot ("{0}-ui.xml" -f $safeName))
    adb shell rm /sdcard/spotamp-qa-failure.xml 2>$null | Out-Null
  }
}
function Run-Gradle([string]$name, [string]$task, [string]$filter = '', [switch]$ForceFreshResults) {
  $log = Join-Path $latest ("logs/{0}.log" -f $name.ToLower())
  $gradleArgs = @($task, '--no-daemon', '--console=plain')
  if ($env:SPOTAMP_STRESS_SEED) { $gradleArgs += "-Pspotamp.stress.seed=$env:SPOTAMP_STRESS_SEED" }
  if ($env:SPOTAMP_STRESS_ACTIONS) { $gradleArgs += "-Pspotamp.stress.actions=$env:SPOTAMP_STRESS_ACTIONS" }
  if ($ForceFreshResults -or $env:SPOTAMP_STRESS_SEED -or $env:SPOTAMP_STRESS_ACTIONS) { $gradleArgs += '--rerun-tasks' }
  $includeConnected = $task -match ':connected.*AndroidTest'
  if ($includeConnected -and $filter) {
    foreach ($pattern in @($filter -split ';' | Where-Object { $_.StartsWith('!') })) {
      $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.notClass=$($pattern.Substring(1))"
    }
  } elseif ($filter) {
    foreach ($pattern in @($filter -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
      $gradleArgs += @('--tests', $pattern)
    }
  }
  if ($CompanionBaseUrl -and $includeConnected) { $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.companionBaseUrl=$CompanionBaseUrl" }
  $timedOut = $false
  Push-Location $root
  if ($includeConnected) {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    # Windows PowerShell cannot launch .bat files directly through Process.Start,
    # and its .NET Framework ProcessStartInfo has no ArgumentList collection.
    # Route the wrapper through cmd.exe and use the legacy Arguments property.
    $startInfo.FileName = $env:ComSpec
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $quotedGradleArgs = @($gradleArgs | ForEach-Object { '"' + ([string]$_).Replace('"', '\"') + '"' })
    $startInfo.Arguments = '/d /s /c ""' + $gradleExecutable + '" ' + ($quotedGradleArgs -join ' ') + '"'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($connectedTimeoutSeconds * 1000)) {
      $timedOut = $true
      try { $process.Kill($true) } catch { }
      $process.WaitForExit()
    }
    $combinedOutput = $stdout.Result + [Environment]::NewLine + $stderr.Result
    $combinedOutput | Set-Content -LiteralPath $log
    $combinedOutput | Write-Output
    $code = if ($timedOut) { 124 } else { $process.ExitCode }
    $process.Dispose()
  } else {
    & $gradleExecutable @gradleArgs *>&1 | Tee-Object -FilePath $log
    $code = $LASTEXITCODE
  }
  Pop-Location
  $deviceUnavailable = $includeConnected -and ($combinedOutput -match 'No connected devices|No devices connected|no connected devices')
  $companionUnavailable = $includeConnected -and ($combinedOutput -match 'Missing instrumentation argument: companionBaseUrl')
  $results[$name] = if ($code -eq 0) { 'PASS' } elseif ($timedOut -or $deviceUnavailable -or $companionUnavailable) { 'BLOCKED' } else { 'FAIL' }
  if ($code -ne 0) {
    $failureReason = 'Gradle task failed'
    if ($timedOut) { $failureReason = "Connected Gradle test exceeded the $connectedTimeoutSeconds-second watchdog." }
    elseif ($deviceUnavailable) { $failureReason = 'Connected device disappeared before the connected test task started.' }
    elseif ($companionUnavailable) { $failureReason = 'Real SpotCore companion playback environment is unavailable.' }
    Capture-FailureArtifacts $name $failureReason -IncludeDeviceContext:$includeConnected
  }
  $isTestTask = $task -match ':test[^:]*Test' -or $includeConnected
  if ($isTestTask) { Collect-TestResults $name $includeConnected $filter }
}
function Clear-StressCampaign {
  Remove-Item Env:SPOTAMP_STRESS_SEED -ErrorAction SilentlyContinue
  Remove-Item Env:SPOTAMP_STRESS_ACTIONS -ErrorAction SilentlyContinue
}

function Run-QA {
  Run-Gradle "QAAssembly" ":app:assembleQa"
  if ($results["QAAssembly"] -eq "PASS" -and (Test-Path $qaApk)) {
    adb install -r $qaApk 2>$null
    $results["QAInstall"] = if ($LASTEXITCODE -eq 0) { "PASS" } else { "FAIL" }
    $qaPackages = @(adb shell pm list packages $qaAppId 2>$null | Where-Object { ([string]$_).Trim() -eq "package:$qaAppId" })
    $results["QAIdentity"] = if ($qaPackages.Count -eq 1) { "PASS" } else { "FAIL" }
  } elseif ($results["QAAssembly"] -eq "PASS") { $results["QAInstall"] = "FAIL" }
}

# Keep checks that require a manually prepared route, lock state, or global animation scale in their dedicated physical runs.
$defaultConnectedUiTestFilter = @(
  '!com.example.CompanionRealPlaybackTest'
  '!com.example.BluetoothPlaybackRouteDeviceTest'
  '!com.example.LockScreenTransportDeviceTest'
  '!com.example.ui.PlaybackMotionDeviceTest'
) -join ';'

function Run-ConnectedUi([string]$testFilter = '') {
  if ([string]::IsNullOrWhiteSpace($testFilter)) {
    $testFilter = $defaultConnectedUiTestFilter
  }
  Run-Gradle 'UIInstall' ':app:assembleQa'
  if ($results['UIInstall'] -eq 'PASS') {
    Run-Gradle 'UITestAssembly' ':app:assembleQaAndroidTest'
  }
  if ($results['UIInstall'] -ne 'PASS' -or $results['UITestAssembly'] -ne 'PASS' -or -not (Test-Path -LiteralPath $qaApk) -or -not (Test-Path -LiteralPath $qaAndroidTestApk)) {
    $results['UIAppInstall'] = 'SKIPPED'
    $results['UIInstrumentationAppInstall'] = 'SKIPPED'
    $results['UIQaAppRetention'] = 'SKIPPED'
    $results['UINotificationPermissionCleanup'] = 'SKIPPED'
    return
  }

  $qaWasInstalled = Test-QaPackageInstalled $qaAppId
  $environment['qaAppPreinstalledBeforeUiRun'] = $qaWasInstalled
  $notificationGrantAttempted = $false
  $notificationGrantSucceeded = $false
  $qaInstallSucceeded = $false
  try {
    $qaInstallOutput = @(adb install -r $qaApk 2>&1)
    $qaInstallExitCode = $LASTEXITCODE
    $qaInstallOutput | Tee-Object -FilePath (Join-Path $latest 'logs/uiinstall.log')
    if ($qaInstallExitCode -ne 0) {
      $results['UIAppInstall'] = 'FAIL'
      $results['UIInstrumentationAppInstall'] = 'SKIPPED'
      $results['UI'] = 'FAIL'
      return
    }
    $qaInstallSucceeded = Test-QaPackageInstalled $qaAppId
    $environment['qaAppPresentAfterUiRun'] = $qaInstallSucceeded
    $results['UIAppInstall'] = if ($qaInstallSucceeded) { 'PASS' } else { 'FAIL' }
    if (-not $qaInstallSucceeded) {
      $results['UIInstrumentationAppInstall'] = 'SKIPPED'
      $results['UI'] = 'FAIL'
      return
    }

    $testInstallOutput = @(adb install -r $qaAndroidTestApk 2>&1)
    $testInstallExitCode = $LASTEXITCODE
    $testInstallOutput | Add-Content -LiteralPath (Join-Path $latest 'logs/uiinstall.log')
    $testInstallSucceeded = $testInstallExitCode -eq 0 -and (Test-QaPackageInstalled $qaTestAppId)
    $results['UIInstrumentationAppInstall'] = if ($testInstallSucceeded) { 'PASS' } else { 'FAIL' }
    if (-not $testInstallSucceeded) {
      $results['UI'] = 'FAIL'
      return
    }

    $instrumentationEntries = @(adb shell pm list instrumentation 2>$null | Where-Object { $_ -match "target=$([regex]::Escape($qaAppId))\)" })
    $instrumentationComponents = @($instrumentationEntries | ForEach-Object {
      $componentMatch = [regex]::Match([string]$_, '^instrumentation:(?<component>[^ ]+)')
      if ($componentMatch.Success) { $componentMatch.Groups['component'].Value }
    } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($instrumentationComponents.Count -ne 1) {
      $results['UI'] = 'FAIL'
      Add-Content -LiteralPath (Join-Path $latest 'logs/ui.log') -Value "Expected one installed QA instrumentation component; found $($instrumentationComponents.Count)."
      Capture-FailureArtifacts 'UI' 'Expected one installed QA instrumentation component.' -IncludeDeviceContext
      return
    }

    $notificationGrantAttempted = $true
    $grantOutput = @(adb shell pm grant $qaAppId android.permission.POST_NOTIFICATIONS 2>&1)
    $grantExitCode = $LASTEXITCODE
    $grantOutput | Add-Content -LiteralPath (Join-Path $latest 'logs/uiinstall.log')
    $notificationGrantSucceeded = $grantExitCode -eq 0
    $results['UINotificationPermissionGrant'] = if ($notificationGrantSucceeded) { 'PASS' } else { 'FAIL' }
    if (-not $notificationGrantSucceeded) {
      $results['UI'] = 'FAIL'
      Capture-FailureArtifacts 'UI' 'Could not grant POST_NOTIFICATIONS to the isolated QA package.' -IncludeDeviceContext
      return
    }

    $runnerArguments = @()
    $testPatterns = @($testFilter -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $includedClasses = @($testPatterns | Where-Object { -not $_.StartsWith('!') })
    if ($includedClasses.Count -gt 0) {
      $runnerArguments += @('-e', 'class', ($includedClasses -join ','))
    }
    $excludedClasses = @($testPatterns | Where-Object { $_.StartsWith('!') } | ForEach-Object { $_.Substring(1) })
    if ($excludedClasses.Count -gt 0) {
      $runnerArguments += @('-e', 'notClass', ($excludedClasses -join ','))
    }
    $runnerArguments += $instrumentationComponents[0]
    $rawOutputPath = Join-Path $latest 'logs/ui.log'
    $instrumentationRun = Invoke-SpotAmpAndroidInstrumentation -RunnerArguments $runnerArguments -OutputPath $rawOutputPath -TimeoutSeconds $connectedTimeoutSeconds
    $junitPath = Join-Path (Join-Path $root 'app/build/outputs/androidTest-results/connected/qa') ("TEST-manual-{0}.xml" -f $runId)
    $parsedResults = ConvertFrom-SpotAmpInstrumentationOutput -Output $instrumentationRun.Output -JUnitPath $junitPath
    if ($parsedResults.JUnitPath) {
      Collect-TestResults 'UI' $true $testFilter
    }
    if ($instrumentationRun.TimedOut) {
      adb shell am force-stop --user 0 $qaTestAppId 2>$null | Out-Null
      $results['UI'] = 'BLOCKED'
      Capture-FailureArtifacts 'UI' "Direct AndroidJUnitRunner exceeded the $connectedTimeoutSeconds-second watchdog." -IncludeDeviceContext
    } elseif ($instrumentationRun.ExitCode -ne 0 -or -not $parsedResults.Passed) {
      $results['UI'] = 'FAIL'
      Add-Content -LiteralPath $rawOutputPath -Value ("`nHarness result: " + $parsedResults.Reason)
      Capture-FailureArtifacts 'UI' ("Direct AndroidJUnitRunner failed: " + $parsedResults.Reason) -IncludeDeviceContext
    } else {
      $results['UI'] = 'PASS'
      Write-Output ("AndroidJUnitRunner passed {0} tests across {1} suites." -f $parsedResults.TestCount, $parsedResults.SuiteCount)
    }
  } finally {
    $connectedState = ((adb get-state 2>$null) -join '').Trim()
    if ($connectedState -ne 'device') {
      $results['UINotificationPermissionCleanup'] = 'BLOCKED'
      $results['UIQaAppRetention'] = 'BLOCKED'
      Write-Output 'QA notification permission cleanup blocked: the device is unavailable.'
    } else {
      $qaAppPresentAfterRun = Test-QaPackageInstalled $qaAppId
      $qaTestAppPresentAfterRun = Test-QaPackageInstalled $qaTestAppId
      $environment['qaAppPresentAfterUiRun'] = $qaAppPresentAfterRun
      $results['UIQaAppRetention'] = if ($qaAppPresentAfterRun) { 'PASS' } else { 'FAIL' }
      $results['UIInstrumentationAppRetention'] = if ($qaTestAppPresentAfterRun) { 'PASS' } else { 'FAIL' }
      if (-not $qaAppPresentAfterRun) {
        $results['UINotificationPermissionCleanup'] = 'FAIL'
        Write-Output 'QA app was unexpectedly removed during instrumentation; app-data retention and permission cleanup could not be verified.'
      } elseif ($notificationGrantAttempted) {
        $revokeOutput = @(adb shell pm revoke $qaAppId android.permission.POST_NOTIFICATIONS 2>&1)
        $revokeExitCode = $LASTEXITCODE
        if ($revokeOutput.Count -gt 0) { $revokeOutput | Add-Content -LiteralPath (Join-Path $latest 'logs/uiinstall.log') }
        $results['UINotificationPermissionCleanup'] = if ($revokeExitCode -eq 0) { 'PASS' } else { 'FAIL' }
      } else {
        $results['UINotificationPermissionCleanup'] = 'SKIPPED'
      }
    }
  }
}

function Run-Backend {
  try {
    $output = & (Join-Path $PSScriptRoot 'qa-backend-check.ps1') 2>&1
    $output | Tee-Object -FilePath (Join-Path $latest 'logs/qa-backend.log')
    if ($LASTEXITCODE -ne 0) { throw "QA backend contract check failed with exit code $LASTEXITCODE" }
    $checkMarker = @($output | Select-String -Pattern '^QA_BACKEND_CHECKS=(\d+)$' | Select-Object -First 1)
    if ($checkMarker.Count -eq 0) { throw 'QA backend check output did not report its assertion count.' }
    $script:backendChecks = [int]$checkMarker[0].Matches[0].Groups[1].Value
    $results['QABackend'] = 'PASS'
  } catch {
    $exceptionText = ($_ | Out-String)
    $results['QABackend'] = if ($exceptionText -match '(?i)did not become healthy|timed out|connection|address.*use|failed to start') { 'BLOCKED' } else { 'FAIL' }
    Add-Content -LiteralPath (Join-Path $latest 'logs/qa-backend.log') -Value ("QA backend check exception: " + $exceptionText)
  }
}

function Run-Release {
  $keystorePath = $env:KEYSTORE_PATH
  if (-not [string]::IsNullOrWhiteSpace($keystorePath) -and -not [IO.Path]::IsPathRooted($keystorePath)) {
    $keystorePath = Join-Path $root $keystorePath
  }
  $keystoreAvailable = $false
  if (-not [string]::IsNullOrWhiteSpace($keystorePath)) {
    $keystoreAvailable = Test-Path -LiteralPath $keystorePath -PathType Leaf
  }
  $explicitSigning = $keystoreAvailable -and
    -not [string]::IsNullOrWhiteSpace($env:STORE_PASSWORD) -and
    -not [string]::IsNullOrWhiteSpace($env:KEY_PASSWORD)
  $releaseArtifact = if ($explicitSigning) {
    Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
  } else {
    Join-Path $root 'app/build/outputs/apk/release/app-release-unsigned.apk'
  }

  Run-Gradle 'Release' ':app:assembleRelease'
  if ($results['Release'] -eq 'PASS') {
    $verifyOutput = & (Join-Path $PSScriptRoot 'verify-release.ps1') -ApkPath $releaseArtifact 2>&1
    $verifyOutput | Tee-Object -FilePath (Join-Path $latest 'logs/release-verify.log')
    if ($LASTEXITCODE -ne 0) {
      $results['Release'] = 'FAIL'
    } else {
      $verifyText = $verifyOutput -join [Environment]::NewLine
      $verifyMatch = [Text.RegularExpressions.Regex]::Match($verifyText, 'PASS release artifact=(?<artifact>\S+) signing=(?<signing>\S+)')
      if ($verifyMatch.Success) {
        $environment.releaseArtifact = $verifyMatch.Groups['artifact'].Value
        $environment.releaseSigning = $verifyMatch.Groups['signing'].Value
      }
    }
  }
}
switch ($Tier) {
  'quick' { Run-Gradle 'QuickLint' ':app:lintDebug'; Run-Gradle 'Quick' $unitTestTask -ForceFreshResults }
  'now-playing' {
    $filter = if ([string]::IsNullOrWhiteSpace($TestFilter)) { '*NowPlayingQueueSemanticsTest*' } else { $TestFilter }
    Run-Gradle 'NowPlayingSemantics' $unitTestTask $filter
  }
  'storage' { Run-Gradle 'Storage' $unitTestTask '*MusicDatabaseMigration*' }
  'sync' { Run-Gradle 'SyncPolicy' $unitTestTask '*TrackCacheSyncPolicyTest*'; Run-Gradle 'SyncSafety' $unitTestTask '*TrackCacheSyncSafetyTest*'; Run-Gradle 'SyncGeneration' $unitTestTask '*SyncGenerationSafetyTest*'; Run-Gradle 'SyncPagination' $unitTestTask '*PlexPaginationTest*'; Run-Gradle 'SyncBackend' $unitTestTask '*BackendApi*'; Run-Gradle 'SyncSharedPlaylists' $unitTestTask '*SharedPlaylistRepositoryIntegrationTest*'; Run-Gradle 'SyncCompanionIsolation' $unitTestTask '*Companion*IsolationTest*' }
  'playback' { Run-Gradle 'PlaybackRestoration' $unitTestTask '*PlaybackStateRestorationTest*'; Run-Gradle 'PlaybackService' $unitTestTask '*MusicPlaybackServiceContractTest*'; Run-Gradle 'PlaybackUri' $unitTestTask '*PlaybackUriPolicyTest*'; Run-Gradle 'PlaybackOrigin' $unitTestTask '*OriginAwareHttpDataSourceTest*' }
  'downloads' { Run-Gradle 'Downloads' $unitTestTask '*Download*Test*' }
  'ui' {
    $devices = @(Get-ConnectedAndroidDevices -attempts $AdbProbeAttempts)
    if ($devices.Count -eq 0) {
      $results['UI'] = 'BLOCKED'
      Set-Content (Join-Path $latest 'logs/ui.log') ("No connected device or emulator after bounded ADB retry.`n" + $script:connectedDeviceProbeDiagnostic)
    }
    elseif (-not [string]::IsNullOrWhiteSpace($ExpectedDeviceModel) -and $devices.Count -ne 1) {
      $results['UI'] = 'BLOCKED'
      Set-Content (Join-Path $latest 'logs/ui.log') "Expected one connected device for model '$ExpectedDeviceModel', found $($devices.Count); no app changes were made."
    }
    else {
      # Keep the smoke test independent of the first-run notification dialog on Android 13+.
      # Connected Compose tests require an awake, unlocked display to attach to the real activity.
      if (-not [string]::IsNullOrWhiteSpace($ExpectedDeviceModel)) {
        $connectedDeviceSerial = ([string]$devices[0] -split '\s+')[0]
        if ([string]::IsNullOrWhiteSpace($connectedDeviceSerial)) {
          $results['UI'] = 'BLOCKED'
          Set-Content (Join-Path $latest 'logs/ui.log') 'Connected device serial could not be identified; no app changes were made.'
        }
        else { $env:ANDROID_SERIAL = $connectedDeviceSerial }
      }
      if ($results['UI'] -ne 'BLOCKED') {
      Update-ConnectedDeviceEnvironment
        if (-not [string]::IsNullOrWhiteSpace($ExpectedDeviceModel) -and $environment.device -ne $ExpectedDeviceModel) {
          $results['UI'] = 'BLOCKED'
          Set-Content (Join-Path $latest 'logs/ui.log') "Expected device model '$ExpectedDeviceModel', found '$($environment.device)'; no app changes were made."
        }
        if ($results['UI'] -ne 'BLOCKED') {
          & (Join-Path $PSScriptRoot 'wake-device.ps1')
          if (-not (Test-DeviceReadyForCompose)) { $results['UI'] = 'BLOCKED'; Set-Content (Join-Path $latest 'logs/ui.log') 'Connected device is not awake and unlocked; Compose UI tests were not started.' }
          else {
            $uiTestFilter = if ([string]::IsNullOrWhiteSpace($TestFilter)) { $defaultConnectedUiTestFilter } else { $TestFilter }
            Run-ConnectedUi $uiTestFilter
          }
        }
      }
    }
  }
  'stress' { $env:SPOTAMP_STRESS_SEED = $Seed; $env:SPOTAMP_STRESS_ACTIONS = $Actions; Run-Gradle 'Stress' $unitTestTask '*PlaybackStateStressTest*'; Run-Gradle 'StressTraceArtifact' $unitTestTask '*PlaybackStateTraceArtifactTest*' }
  'benchmark' {
    Run-Gradle 'BenchmarkAssembly' ':app:assembleBenchmark'
    if ($results['BenchmarkAssembly'] -eq 'PASS') { Run-Gradle 'Benchmark' $unitTestTask '*BenchmarkTest*' }
  }
  'endurance' { $env:SPOTAMP_STRESS_SEED = $Seed; $env:SPOTAMP_STRESS_ACTIONS = [Math]::Max($Actions, 10000); Run-Gradle 'Endurance' $unitTestTask '*PlaybackStateStressTest*' }
  'release' { Run-Release }
  'qa-backend' { Run-Backend }
  'companion' {
    $companionOutput = & (Join-Path $PSScriptRoot 'qa-companion-real.ps1') -RunLockAlreadyHeld 2>&1
    $companionOutput | Tee-Object -FilePath (Join-Path $latest 'logs/companion.log')
    $results['Companion'] = if ($LASTEXITCODE -eq 0) { 'PASS' } elseif ($LASTEXITCODE -eq 2) { 'BLOCKED' } else { 'FAIL' }
  }
  'audit' {
    $auditOutput = & (Join-Path $PSScriptRoot 'qa-audit.ps1') 2>&1
    $auditOutput | Tee-Object -FilePath (Join-Path $latest 'logs/audit.log')
    $auditPassed = @($auditOutput | Where-Object { ([string]$_).Trim() -eq 'PASS QA framework audit' }).Count -gt 0
    $results['Audit'] = if ($auditPassed) { 'PASS' } else { 'FAIL' }
  }
  'signing' {
    Run-Gradle 'SigningTokenSafety' $unitTestTask '*TokenSafetyTest*'
    $signingOutput = & (Join-Path $PSScriptRoot 'qa-signing.ps1') 2>&1
    $signingOutput | Tee-Object -FilePath (Join-Path $latest 'logs/signing.log')
    if ($results['SigningTokenSafety'] -eq 'PASS' -and $LASTEXITCODE -eq 0) { $results['Signing'] = 'PASS' } else { $results['Signing'] = 'FAIL' }
  }
  'qa' { Run-QA }
  'full' {
    Run-Gradle 'QuickLint' ':app:lintDebug'
    Run-Gradle 'Quick' $unitTestTask
    Run-Gradle 'Storage' $unitTestTask '*MusicDatabaseMigration*'
    Run-Gradle 'SyncPolicy' $unitTestTask '*TrackCacheSyncPolicyTest*'
    Run-Gradle 'SyncSafety' $unitTestTask '*TrackCacheSyncSafetyTest*'
    Run-Gradle 'SyncGeneration' $unitTestTask '*SyncGenerationSafetyTest*'
    Run-Gradle 'SyncPagination' $unitTestTask '*PlexPaginationTest*'
    Run-Gradle 'SyncBackend' $unitTestTask '*BackendApi*'
    Run-Gradle 'SyncSharedPlaylists' $unitTestTask '*SharedPlaylistRepositoryIntegrationTest*'
    Run-Gradle 'SyncCompanionIsolation' $unitTestTask '*Companion*IsolationTest*'
    Run-Gradle 'PlaybackRestoration' $unitTestTask '*PlaybackStateRestorationTest*'
    Run-Gradle 'PlaybackService' $unitTestTask '*MusicPlaybackServiceContractTest*'
    Run-Gradle 'PlaybackUri' $unitTestTask '*PlaybackUriPolicyTest*'
    Run-Gradle 'PlaybackOrigin' $unitTestTask '*OriginAwareHttpDataSourceTest*'
    Run-Gradle 'Downloads' $unitTestTask '*Download*Test*'
    Run-Backend
    Run-QA
    Run-Gradle 'BenchmarkAssembly' ':app:assembleBenchmark'
    Run-Gradle 'Benchmark' $unitTestTask '*BenchmarkTest*'
    $env:SPOTAMP_STRESS_SEED = $Seed
    $env:SPOTAMP_STRESS_ACTIONS = $Actions
    Run-Gradle 'Stress' $unitTestTask '*PlaybackStateStressTest*'
    Run-Gradle 'StressTraceArtifact' $unitTestTask '*PlaybackStateTraceArtifactTest*'
    $env:SPOTAMP_STRESS_ACTIONS = [Math]::Max($Actions, 10000)
    Run-Gradle 'Endurance' $unitTestTask '*PlaybackStateStressTest*'
    Clear-StressCampaign
    $devices = @(Get-ConnectedAndroidDevices -attempts $AdbProbeAttempts)
    if ($devices.Count -eq 0) {
      $results['UI'] = 'BLOCKED'
      Set-Content (Join-Path $latest 'logs/ui.log') ("No connected device or emulator after bounded ADB retry.`n" + $script:connectedDeviceProbeDiagnostic)
    }
    else {
      Update-ConnectedDeviceEnvironment
      & (Join-Path $PSScriptRoot 'wake-device.ps1')
      if (-not (Test-DeviceReadyForCompose)) {
        $results['UI'] = 'BLOCKED'
        Set-Content (Join-Path $latest 'logs/ui.log') 'Connected device is not awake and unlocked; Compose UI tests were not started.'
      } else { Run-ConnectedUi }
    }
    Run-Release
  }
}
$bad = @($results.Values | Where-Object { $_ -in @('FAIL','BLOCKED') })
$incomplete = @($results.Values | Where-Object { $_ -in @('NOT_IMPLEMENTED','SKIPPED') })
$overall = if ($bad.Count -gt 0) { 'FAIL' } elseif ($incomplete.Count -gt 0) { 'INCOMPLETE' } else { 'PASS' }
$summary=$testSummary
$summary.generatedAtUtc=(Get-Date).ToUniversalTime().ToString('o')
$summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $latest 'summary.json')
$artifactReleasePath = if ($environment.releaseArtifact) { $environment.releaseArtifact } elseif (Test-Path -LiteralPath (Join-Path $root 'app/build/outputs/apk/release/app-release-unsigned.apk')) { Join-Path $root 'app/build/outputs/apk/release/app-release-unsigned.apk' } else { Join-Path $root 'app/build/outputs/apk/release/app-release.apk' }
$artifacts = [ordered]@{
  debug = [ordered]@{ path=$debugApk; present=(Test-Path -LiteralPath $debugApk); intent='debug-signed-development' }
  qa = [ordered]@{ path=$qaApk; present=(Test-Path -LiteralPath $qaApk); intent='locally-signed-QA' }
  benchmark = [ordered]@{ path=(Join-Path $root 'app/build/outputs/apk/benchmark/app-benchmark.apk'); present=(Test-Path -LiteralPath (Join-Path $root 'app/build/outputs/apk/benchmark/app-benchmark.apk')); intent='locally-signed-benchmark' }
  release = [ordered]@{ path=$artifactReleasePath; present=(Test-Path -LiteralPath $artifactReleasePath); intent=if ($environment.releaseSigning -eq 'signed') { 'production-signed' } else { 'unsigned-local-validation' } }
}
$commit = (& git -C $root rev-parse --short HEAD 2>$null)
$failureClass = [ordered]@{}
foreach ($k in $results.Keys) {
  $failureClass[$k] = if ($results[$k] -eq 'BLOCKED') { 'infrastructure' } elseif ($results[$k] -eq 'FAIL') { 'functional' } else { 'none' }
}
$reportBaseName = if ($Tier -eq 'audit') { 'QA_AUDIT_REPORT' } else { 'QA_REPORT' }
[ordered]@{ build=$Tier; tier=$Tier; runId=$runId; commit=$commit; result=$overall; tiers=$results; failureClass=$failureClass; artifacts=$artifacts; environment=$environment; tests=$summary; backendChecks=$backendChecks; seed=$Seed; actions=$Actions } | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $latest ("{0}.json" -f $reportBaseName))
$lines = @('# SpotAmp QA Report','',"Result: **$overall**",'',"Commit: $commit",'',"Environment: $($environment.device) / Android $($environment.android)",'',"| Tier | Outcome | Classification |",'|---|---|---|')
foreach ($k in $results.Keys) { $lines += "| $k | $($results[$k]) | $($failureClass[$k]) |" }
$lines += @('','BLOCKED and NOT_IMPLEMENTED are never reported as PASS.')
$lines += "","Artifacts:"; foreach ($k in $artifacts.Keys) { $lines += "- ${k}: $($artifacts[$k].intent); present=$($artifacts[$k].present); path=$($artifacts[$k].path)" }; $lines += "","Executed test summary: $($summary.tests) tests; $($summary.failures) failures; $($summary.errors) errors; $($summary.skipped) skipped."
$lines | Set-Content (Join-Path $latest ("{0}.md" -f $reportBaseName))
if ($overall -eq 'FAIL') { exit 1 }; if ($overall -eq 'INCOMPLETE') { exit 2 }; exit 0
} finally {
  if ($null -eq $originalAndroidSerial) { Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue }
  else { $env:ANDROID_SERIAL = $originalAndroidSerial }
  Exit-SpotAmpQaRunLock -Mutex $qaRunMutex
}

