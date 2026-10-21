param(
  [string]$Package = 'com.aistudio.plexmusicplayer.vctplx.qa',
  [string]$Activity = 'com.example.MainActivity',
  [ValidateRange(1, 10)] [int]$Samples = 3,
  [ValidateRange(0, 30)] [int]$SettleSeconds = 2
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$outputDir = Join-Path $repoRoot 'qa-results/latest/benchmarks'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$outputPath = Join-Path $outputDir 'device-performance.json'

function Save-Result([hashtable]$Result) {
  $Result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputPath -Encoding utf8
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  Save-Result @{ result = 'BLOCKED'; reason = 'adb is not available'; package = $Package; generatedAtUtc = [DateTime]::UtcNow.ToString('o') }
  throw 'adb is required for device performance smoke measurements.'
}

$deviceState = ((@(adb get-state 2>$null) | Select-Object -First 1) -join "").Trim()
if ($deviceState -ne 'device') {
  Save-Result @{ result = 'BLOCKED'; reason = 'no ready adb device'; package = $Package; deviceState = $deviceState; generatedAtUtc = [DateTime]::UtcNow.ToString('o') }
  throw 'No ready adb device is available.'
}

$packageLine = adb shell pm path $Package 2>$null | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($packageLine)) {
  Save-Result @{ result = 'BLOCKED'; reason = 'isolated QA package is not installed'; package = $Package; generatedAtUtc = [DateTime]::UtcNow.ToString('o') }
  throw "QA package is not installed: $Package"
}

function Get-StartMetric([string[]]$Lines, [string]$Name) {
  $match = $Lines | Select-String -Pattern ("^" + $Name + ":\s*(\d+)") | Select-Object -First 1
  if ($null -eq $match) { return $null }
  return [int64]$match.Matches[0].Groups[1].Value
}

function Get-MemoryMetric([string[]]$Lines, [string]$Name) {
  $total = $Lines | Select-String -Pattern 'TOTAL PSS:\s*(\d+).*TOTAL RSS:\s*(\d+)' | Select-Object -First 1
  if ($null -ne $total) {
    $group = if ($Name -eq 'TOTAL RSS') { 2 } else { 1 }
    return [int64]$total.Matches[0].Groups[$group].Value
  }
  $match = $Lines | Select-String -Pattern ("^\s*" + $Name + ":\s*(\d+)") | Select-Object -First 1
  if ($null -eq $match) { return $null }
  return [int64]$match.Matches[0].Groups[1].Value
}

function Get-GfxMetric([string[]]$Lines, [string]$Pattern) {
  $match = $Lines | Select-String -Pattern $Pattern | Select-Object -First 1
  if ($null -eq $match) { return $null }
  return $match.Line.Trim()
}

$component = "$Package/$Activity"

function Get-NumericValues([object[]]$Values) {
  @($Values | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ })
}

function Get-Percentile([double[]]$Values, [double]$Percentile) {
  if ($Values.Count -eq 0) { return $null }
  $ordered = @($Values | Sort-Object)
  $rank = [Math]::Ceiling(($Percentile / 100.0) * $ordered.Count) - 1
  return $ordered[[Math]::Max(0, [Math]::Min($ordered.Count - 1, [int]$rank))]
}

function Measure-Launch([int]$Sample, [string]$Kind) {
  if ($Kind -eq 'cold') { adb shell am force-stop $Package | Out-Null }
  $startLines = @(adb shell am start -W -n $component)
  Start-Sleep -Seconds $SettleSeconds
  $memoryLines = @(adb shell dumpsys meminfo $Package)
  $gfxLines = @(adb shell dumpsys gfxinfo $Package)
  [ordered]@{
    sample = $Sample
    kind = $Kind
    startMs = Get-StartMetric $startLines 'TotalTime'
    waitMs = Get-StartMetric $startLines 'WaitTime'
    totalPssKb = Get-MemoryMetric $memoryLines 'TOTAL PSS'
    totalRssKb = Get-MemoryMetric $memoryLines 'TOTAL RSS'
    frames = Get-GfxMetric $gfxLines 'Total frames rendered:'
    jankyFrames = Get-GfxMetric $gfxLines '^Janky frames:'
    framePercentiles = @(
      (Get-GfxMetric $gfxLines '^50th percentile:'),
      (Get-GfxMetric $gfxLines '^90th percentile:'),
      (Get-GfxMetric $gfxLines '^95th percentile:'),
      (Get-GfxMetric $gfxLines '^99th percentile:')
    )
  }
}

$samplesMeasured = @()
for ($sample = 1; $sample -le $Samples; $sample++) {
  $samplesMeasured += Measure-Launch $sample 'cold'
  $samplesMeasured += Measure-Launch $sample 'warm'
}
$cold = @($samplesMeasured | Where-Object { $_.kind -eq 'cold' })
$warm = @($samplesMeasured | Where-Object { $_.kind -eq 'warm' })
$coldStartValues = Get-NumericValues @($cold | ForEach-Object { $_.startMs })
$warmStartValues = Get-NumericValues @($warm | ForEach-Object { $_.startMs })
$coldPssValues = Get-NumericValues @($cold | ForEach-Object { $_.totalPssKb })
$warmPssValues = Get-NumericValues @($warm | ForEach-Object { $_.totalPssKb })
$coldRssValues = Get-NumericValues @($cold | ForEach-Object { $_.totalRssKb })
$warmRssValues = Get-NumericValues @($warm | ForEach-Object { $_.totalRssKb })

$result = [ordered]@{
  result = 'PASS'
  package = $Package
  activity = $Activity
  device = ((adb shell getprop ro.product.model).ToString().Trim())
  android = ((adb shell getprop ro.build.version.release).ToString().Trim())
  generatedAtUtc = [DateTime]::UtcNow.ToString('o')
  samples = $Samples
  settleSeconds = $SettleSeconds
  coldStartMedianMs = Get-Percentile $coldStartValues 50
  coldStartP95Ms = Get-Percentile $coldStartValues 95
  warmStartMedianMs = Get-Percentile $warmStartValues 50
  warmStartP95Ms = Get-Percentile $warmStartValues 95
  coldTotalPssMedianKb = Get-Percentile $coldPssValues 50
  coldTotalPssP95Kb = Get-Percentile $coldPssValues 95
  warmTotalPssMedianKb = Get-Percentile $warmPssValues 50
  warmTotalPssP95Kb = Get-Percentile $warmPssValues 95
  coldTotalRssMedianKb = Get-Percentile $coldRssValues 50
  warmTotalRssMedianKb = Get-Percentile $warmRssValues 50
  measurements = $samplesMeasured
  thermalSnapshot = @((adb shell dumpsys thermalservice 2>$null) -join "`n")
  comparability = [ordered]@{
    status = 'COMPARABLE_ONLY_WITH_MATCHING_METADATA'
    hardware = 'recorded'
    android = 'recorded'
    package = $Package
    libraryShape = 'QA shell / no seeded production library'
    thermalState = 'recorded when available'
  }
  notes = @('QA package only; no production credentials or companion endpoint configured.', 'Bounded diagnostic samples; values are not automatic regression thresholds.', 'Compare only with matching device, Android, package, library shape, sample count, settle time, and thermal conditions.')
}
Save-Result $result
Write-Output ("PASS device performance package=$Package samples=$Samples coldMedianMs=$($result.coldStartMedianMs) warmMedianMs=$($result.warmStartMedianMs) pssMedianKb=$($result.coldTotalPssMedianKb)")
