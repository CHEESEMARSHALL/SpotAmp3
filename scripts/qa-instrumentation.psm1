Set-StrictMode -Version Latest

function Test-SpotAmpAndroidPackageInstalled {
  [CmdletBinding()]
  param([Parameter(Mandatory)] [string]$PackageName)

  $packageLines = @(adb shell pm list packages --user 0 $PackageName 2>$null)
  $queryExitCode = $LASTEXITCODE
  $exactMatch = @($packageLines | Where-Object { ([string]$_).Trim() -eq "package:$PackageName" })
  return ($queryExitCode -eq 0 -and $exactMatch.Count -eq 1)
}

function Invoke-SpotAmpAndroidInstrumentation {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)] [string[]]$RunnerArguments,
    [Parameter(Mandatory)] [string]$OutputPath,
    [ValidateRange(1, 3600)] [int]$TimeoutSeconds = 180
  )

  $adbPath = (Get-Command adb -ErrorAction Stop).Source
  $arguments = @('shell', 'am', 'instrument', '-w', '-r') + $RunnerArguments
  $quotedArguments = @($arguments | ForEach-Object { '"' + ([string]$_).Replace('"', '\"') + '"' })
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $adbPath
  $startInfo.Arguments = $quotedArguments -join ' '
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true

  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  [void]$process.Start()
  $stdout = $process.StandardOutput.ReadToEndAsync()
  $stderr = $process.StandardError.ReadToEndAsync()
  $timedOut = -not $process.WaitForExit($TimeoutSeconds * 1000)
  if ($timedOut) {
    try { $process.Kill($true) } catch { try { $process.Kill() } catch { } }
    $process.WaitForExit()
  }
  $output = $stdout.Result + [Environment]::NewLine + $stderr.Result
  $exitCode = if ($timedOut) { 124 } else { $process.ExitCode }
  $process.Dispose()

  $outputDirectory = Split-Path -Parent $OutputPath
  if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
  }
  [IO.File]::WriteAllText($OutputPath, $output)
  [pscustomobject]@{ Output=$output; ExitCode=$exitCode; TimedOut=$timedOut; LogPath=$OutputPath }
}

function ConvertFrom-SpotAmpInstrumentationOutput {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)] [string]$Output,
    [Parameter(Mandatory)] [string]$JUnitPath
  )

  $testCases = [System.Collections.Generic.Dictionary[string, object]]::new([StringComparer]::Ordinal)
  $status = @{}
  foreach ($line in ($Output -split '\r?\n')) {
    if ($line -match '^INSTRUMENTATION_STATUS: (?<key>[^=]+)=(?<value>.*)$') {
      $status[$matches.key] = $matches.value
      continue
    }
    if ($line -notmatch '^INSTRUMENTATION_STATUS_CODE:\s*(?<code>-?\d+)\s*$') { continue }

    $code = [int]$matches.code
    $className = [string]$status['class']
    $testName = [string]$status['test']
    if (-not [string]::IsNullOrWhiteSpace($className) -and -not [string]::IsNullOrWhiteSpace($testName)) {
      $outcome = switch ($code) {
        0 { 'passed' }
        -2 { 'failure' }
        { $_ -in @(-3,-4) } { 'skipped' }
        -1 { 'error' }
        default { $null }
      }
      if ($outcome) {
        $key = $className + '|' + $testName
        $candidate = [pscustomobject]@{
          ClassName = $className
          Name = $testName
          Outcome = $outcome
          Detail = [string]$status['stack']
        }
        if (-not $testCases.ContainsKey($key) -or $outcome -ne 'passed' -or $testCases[$key].Outcome -eq 'passed') {
          $testCases[$key] = $candidate
        }
      }
    }
    $status = @{}
  }

  $reportedTestCount = -1
  $successSummary = [regex]::Matches($Output, '(?m)^OK \((?<count>\d+) tests?\)\s*$')
  $failureSummary = [regex]::Matches($Output, '(?m)^Tests run:\s*(?<count>\d+)')
  if ($successSummary.Count -gt 0) {
    $reportedTestCount = [int]$successSummary[$successSummary.Count - 1].Groups['count'].Value
  } elseif ($failureSummary.Count -gt 0) {
    $reportedTestCount = [int]$failureSummary[$failureSummary.Count - 1].Groups['count'].Value
  }
  $instrumentationCodes = [regex]::Matches($Output, '(?m)^INSTRUMENTATION_CODE:\s*(?<code>-?\d+)\s*$')
  $instrumentationCode = if ($instrumentationCodes.Count -gt 0) {
    [int]$instrumentationCodes[$instrumentationCodes.Count - 1].Groups['code'].Value
  } else { $null }

  $caseValues = @($testCases.Values | Sort-Object ClassName, Name)
  $failureCount = @($caseValues | Where-Object Outcome -eq 'failure').Count
  $errorCount = @($caseValues | Where-Object Outcome -eq 'error').Count
  $skippedCount = @($caseValues | Where-Object Outcome -eq 'skipped').Count
  $hasFailureMarker = $Output -match '(?m)^FAILURES!!!\s*$|^INSTRUMENTATION_FAILED:'
  $summaryPresent = $reportedTestCount -ge 0
  $caseCountsMatch = $summaryPresent -and $reportedTestCount -eq $caseValues.Count
  $passed = $instrumentationCode -eq -1 -and $summaryPresent -and $caseValues.Count -gt 0 -and
    $caseCountsMatch -and $failureCount -eq 0 -and $errorCount -eq 0 -and -not $hasFailureMarker

  if ($caseValues.Count -gt 0) {
    $xml = [System.Xml.XmlDocument]::new()
    $declaration = $xml.CreateXmlDeclaration('1.0', 'utf-8', $null)
    [void]$xml.AppendChild($declaration)
    $root = $xml.CreateElement('testsuites')
    $root.SetAttribute('tests', [string]$caseValues.Count)
    $root.SetAttribute('failures', [string]$failureCount)
    $root.SetAttribute('errors', [string]$errorCount)
    $root.SetAttribute('skipped', [string]$skippedCount)
    $root.SetAttribute('timestamp', [DateTimeOffset]::UtcNow.ToString('o'))
    [void]$xml.AppendChild($root)

    foreach ($group in @($caseValues | Group-Object ClassName | Sort-Object Name)) {
      $suiteCases = @($group.Group)
      $suite = $xml.CreateElement('testsuite')
      $suite.SetAttribute('name', [string]$group.Name)
      $suite.SetAttribute('tests', [string]$suiteCases.Count)
      $suite.SetAttribute('failures', [string]@($suiteCases | Where-Object Outcome -eq 'failure').Count)
      $suite.SetAttribute('errors', [string]@($suiteCases | Where-Object Outcome -eq 'error').Count)
      $suite.SetAttribute('skipped', [string]@($suiteCases | Where-Object Outcome -eq 'skipped').Count)
      $suite.SetAttribute('time', '0')
      [void]$root.AppendChild($suite)

      foreach ($testCase in $suiteCases) {
        $caseNode = $xml.CreateElement('testcase')
        $caseNode.SetAttribute('classname', $testCase.ClassName)
        $caseNode.SetAttribute('name', $testCase.Name)
        $caseNode.SetAttribute('time', '0')
        [void]$suite.AppendChild($caseNode)
        if ($testCase.Outcome -in @('failure', 'error')) {
          $detail = if ([string]::IsNullOrWhiteSpace($testCase.Detail)) { "Android instrumentation reported $($testCase.Outcome)." } else { $testCase.Detail }
          $marker = $xml.CreateElement($testCase.Outcome)
          $marker.SetAttribute('message', (($detail -split '\r?\n' | Select-Object -First 1).Trim()))
          $marker.InnerText = $detail
          [void]$caseNode.AppendChild($marker)
        } elseif ($testCase.Outcome -eq 'skipped') {
          $marker = $xml.CreateElement('skipped')
          if (-not [string]::IsNullOrWhiteSpace($testCase.Detail)) { $marker.SetAttribute('message', $testCase.Detail) }
          [void]$caseNode.AppendChild($marker)
        }
      }
    }

    $outputDirectory = Split-Path -Parent $JUnitPath
    if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
      New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    }
    $xml.Save($JUnitPath)
  }

  $reason = if ($passed) { 'All instrumentation tests passed.' }
    elseif (-not $summaryPresent) { 'Instrumentation output did not include a final test summary.' }
    elseif ($instrumentationCode -ne -1) { "Instrumentation did not complete successfully (code $instrumentationCode)." }
    elseif (-not $caseCountsMatch) { "Instrumentation summary reported $reportedTestCount tests but $($caseValues.Count) test outcomes were captured." }
    elseif ($hasFailureMarker -or $failureCount -gt 0 -or $errorCount -gt 0) { "Instrumentation reported $failureCount failures and $errorCount errors." }
    else { 'Instrumentation completed without a verifiable test outcome.' }

  [pscustomobject]@{
    Passed = $passed
    Reason = $reason
    TestCount = $caseValues.Count
    ReportedTestCount = $reportedTestCount
    SuiteCount = @($caseValues | Select-Object -ExpandProperty ClassName -Unique).Count
    Failures = $failureCount
    Errors = $errorCount
    Skipped = $skippedCount
    InstrumentationCode = $instrumentationCode
    JUnitPath = if ($caseValues.Count -gt 0) { $JUnitPath } else { $null }
  }
}

Export-ModuleMember -Function Test-SpotAmpAndroidPackageInstalled, Invoke-SpotAmpAndroidInstrumentation, ConvertFrom-SpotAmpInstrumentationOutput
