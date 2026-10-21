param(
  [string]$ApkPath = "app/build/outputs/apk/release/app-release-unsigned.apk"
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
  throw "Release artifact not found: $ApkPath"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$requiredBackupExclusions = @("plex_settings_secure.xml", "plex_settings.xml")
foreach ($ruleFile in @("app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml")) {
  $rulePath = Join-Path $repoRoot $ruleFile
  if (-not (Test-Path -LiteralPath $rulePath -PathType Leaf)) { throw "Backup rule file missing: $ruleFile" }
  $ruleText = [IO.File]::ReadAllText($rulePath)
  foreach ($preferenceFile in $requiredBackupExclusions) {
    $excludePattern = '<exclude\s+domain="sharedpref"\s+path="' + [Text.RegularExpressions.Regex]::Escape($preferenceFile) + '"\s*/?>'
    if ($ruleText -notmatch $excludePattern) { throw "Credential preference is not excluded from backup rules: $preferenceFile" }
  }
}

$apksigner = (Get-Command apksigner.bat -ErrorAction SilentlyContinue).Source
if ([string]::IsNullOrWhiteSpace($apksigner)) {
  $sdkRoot = $env:ANDROID_SDK_ROOT
  if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
  if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
  $apksigner = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Filter 'apksigner.bat' -Recurse -File -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($apksigner)) { throw 'apksigner is required to classify modern APK signatures.' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApkPath))
try {
  $entries = @($archive.Entries | ForEach-Object { $_.FullName })
  $forbidden = @('androidTest', 'src/test', 'fixtures', 'test_endpoint', 'qa/')
  $matches = @($entries | Where-Object {
      $entry = $_
      $forbidden | Where-Object { $entry -like "*$_*" }
    })
  if ($matches.Count -gt 0) {
    throw "Release artifact contains QA/test-only paths: $($matches -join ', ')"
  }

  $qaMarkers = @('QA Lab', 'qa-fixture-', 'open-qa-lab', 'qa-playback-smoke', 'SPOTCORE_TEST_TOKEN_DO_NOT_USE')
  $forbiddenEndpointMarkers = @('140.186.136.228')
  $markerMatches = [System.Collections.Generic.List[string]]::new()
  $endpointMatches = [System.Collections.Generic.List[string]]::new()
  foreach ($entry in $archive.Entries) {
    if ($entry.Length -eq 0) { continue }
    $entryStream = $entry.Open()
    $memory = [IO.MemoryStream]::new()
    try {
      $entryStream.CopyTo($memory)
      $entryBytes = $memory.ToArray()
    } finally {
      $entryStream.Dispose()
      $memory.Dispose()
    }
    # APK compression hides DEX/resource strings from a raw scan of the outer
    # ZIP. Decode both common Android string encodings from each packaged entry.
    $asciiText = [Text.Encoding]::ASCII.GetString($entryBytes)
    $utf16Text = [Text.Encoding]::Unicode.GetString($entryBytes)
    foreach ($marker in $qaMarkers) {
      if ($asciiText.IndexOf($marker, [StringComparison]::Ordinal) -ge 0 -or
          $utf16Text.IndexOf($marker, [StringComparison]::Ordinal) -ge 0) {
        $markerMatches.Add("$($entry.FullName):$marker")
      }
    }
    foreach ($endpoint in $forbiddenEndpointMarkers) {
      if ($asciiText.IndexOf($endpoint, [StringComparison]::Ordinal) -ge 0 -or
          $utf16Text.IndexOf($endpoint, [StringComparison]::Ordinal) -ge 0) {
        $endpointMatches.Add("$($entry.FullName):$endpoint")
      }
    }
  }
  if ($markerMatches.Count -gt 0) {
    throw "Release artifact contains QA Lab marker strings: $($markerMatches -join ', ')"
  }
  if ($endpointMatches.Count -gt 0) {
    throw "Release artifact contains a forbidden hardcoded test endpoint: $($endpointMatches -join ', ')"
  }
  $global:LASTEXITCODE = 0

  $signatureEntries = @($entries | Where-Object { $_ -match '^META-INF/[^/]+\.(SF|RSA|DSA)$' })
  $signatureErrorAction = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  $signatureOutput = @(& cmd.exe /d /c $apksigner verify --verbose $ApkPath 2>&1)
  $ErrorActionPreference = $signatureErrorAction
  $signatureExitCode = $LASTEXITCODE
  $modernSignature = $signatureExitCode -eq 0 -and @($signatureOutput | Where-Object { $_ -match 'Verified using v[23](\.1|\.2)? scheme.*: true' }).Count -gt 0
  $signingState = if ($modernSignature -or $signatureEntries.Count -gt 0) { 'signed' } else { 'unsigned-local-validation' }
  Write-Output "PASS release artifact=$ApkPath signing=$signingState entries=$($entries.Count)"
  $global:LASTEXITCODE = 0
}
finally {
  $archive.Dispose()
}
