param(
  [string]$ObservationPath = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$latest = Join-Path $root 'qa-results/latest'
New-Item -ItemType Directory -Force -Path $latest | Out-Null
$companionRuns = Join-Path $root 'qa-results/companion-runs'
New-Item -ItemType Directory -Force -Path $companionRuns | Out-Null
$runId = ((Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ') + '-companion')
$previousJson = Join-Path $latest 'COMPANION_PLAYBACK_REPORT.json'
$previousMarkdown = Join-Path $latest 'COMPANION_PLAYBACK_REPORT.md'
if ((Test-Path -LiteralPath $previousJson -PathType Leaf) -or (Test-Path -LiteralPath $previousMarkdown -PathType Leaf)) {
  $archive = Join-Path $companionRuns $runId
  New-Item -ItemType Directory -Force -Path $archive | Out-Null
  if (Test-Path -LiteralPath $previousJson -PathType Leaf) { Copy-Item -LiteralPath $previousJson -Destination (Join-Path $archive 'COMPANION_PLAYBACK_REPORT.json') }
  if (Test-Path -LiteralPath $previousMarkdown -PathType Leaf) { Copy-Item -LiteralPath $previousMarkdown -Destination (Join-Path $archive 'COMPANION_PLAYBACK_REPORT.md') }
}

$deviceModel = ((adb shell getprop ro.product.model 2>$null) -join '').Trim()
$androidVersion = ((adb shell getprop ro.build.version.release 2>$null) -join '').Trim()
$report = [ordered]@{
  gate = 'companion-playback'
  runId = $runId
  generatedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
  project = $root
  device = if ($deviceModel) { $deviceModel } else { 'none' }
  android = if ($androidVersion) { $androidVersion } else { 'none' }
  result = 'BLOCKED'
  reason = 'No paired desktop SpotCore observation was supplied.'
}

function Save-CompanionReport([hashtable]$value) {
  $value | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $latest 'COMPANION_PLAYBACK_REPORT.json') -Encoding utf8
  $md = @(
    '# Companion Playback QA Report',
    '',
    "Result: **$($value.result)**",
    '',
    "- Generated: $($value.generatedAtUtc)",
    "- Gate run ID: $($value.runId)",
    "- Device: $($value.device) / Android $($value.android)",
    "- Reason: $($value.reason)",
    '',
    'This gate requires an actual paired desktop SpotCore session. Local fixtures and loopback streams are not substitutes.'
  )
  $md | Set-Content -LiteralPath (Join-Path $latest 'COMPANION_PLAYBACK_REPORT.md') -Encoding utf8
}

if ([string]::IsNullOrWhiteSpace($ObservationPath)) {
  Save-CompanionReport $report
  Write-Output 'BLOCKED companion playback: supply -ObservationPath from a real paired SpotCore session'
  exit 2
}

$resolvedObservation = Resolve-Path -LiteralPath $ObservationPath -ErrorAction Stop
$observation = Get-Content -LiteralPath $resolvedObservation -Raw | ConvertFrom-Json
$required = @('runId', 'spotCoreBuild', 'paired', 'audibleObserved', 'redactionConfirmed')
$missing = @($required | Where-Object { $null -eq $observation.$_ -or ([string]$observation.$_).Trim() -eq '' })
if ($null -eq $observation.steps) { $missing += 'steps' }
if ($missing.Count -gt 0) {
  $report.result = 'FAIL'
  $report.reason = "Observation is missing required fields: $($missing -join ', ')"
} elseif ([string]$observation.runId -match '(?i)replace_with|todo|example' -or [string]$observation.spotCoreBuild -match '(?i)replace_with|todo|example') {
  $report.result = 'FAIL'
  $report.reason = 'Observation still contains placeholder run or SpotCore build values.'
} elseif (-not [bool]$observation.redactionConfirmed) {
  $report.result = 'FAIL'
  $report.reason = 'Observation redaction was not confirmed.'
} elseif (-not [bool]$observation.paired -or -not [bool]$observation.audibleObserved) {
  $report.result = 'BLOCKED'
  $report.reason = 'The supplied observation does not establish both pairing and audible product playback.'
} elseif (@($observation.steps).Count -lt 8) {
  $report.result = 'FAIL'
  $report.reason = 'At least eight documented companion journey steps are required.'
} else {
  $report.result = 'PASS'
  $report.reason = 'Real paired SpotCore session and audible product playback were documented.'
  $report.observationRunId = [string]$observation.runId
  $report.spotCoreBuild = [string]$observation.spotCoreBuild
  $report.stepCount = @($observation.steps).Count
}

Save-CompanionReport $report
Write-Output "$($report.result) companion playback: $($report.reason)"
if ($report.result -eq 'PASS') { exit 0 }
if ($report.result -eq 'BLOCKED') { exit 2 }
exit 1

