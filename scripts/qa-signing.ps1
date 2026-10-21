param(
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $root 'gradlew.bat'
$releaseDir = Join-Path $root 'app/build/outputs/apk/release'
$qaApk = Join-Path $root 'app/build/outputs/apk/qa/app-qa.apk'
$benchmarkApk = Join-Path $root 'app/build/outputs/apk/benchmark/app-benchmark.apk'
$unsignedRelease = Join-Path $releaseDir 'app-release-unsigned.apk'
$signedRelease = Join-Path $releaseDir 'app-release.apk'

function Invoke-Gradle([string[]]$arguments) {
  Push-Location $root
  try {
    & $gradle @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code ${LASTEXITCODE}: $($arguments -join ' ')" }
  } finally {
    Pop-Location
  }
}

function Get-ApkSigner {
  $candidate = (Get-Command apksigner.bat -ErrorAction SilentlyContinue).Source
  if (-not [string]::IsNullOrWhiteSpace($candidate)) { return $candidate }
  $sdkRoot = $env:ANDROID_SDK_ROOT
  if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_HOME }
  if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
  $found = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Filter 'apksigner.bat' -Recurse -File -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
  if ([string]::IsNullOrWhiteSpace($found)) { throw 'apksigner is required for signing QA.' }
  return $found
}

function Assert-Signed([string]$apk, [string]$label) {
  if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "$label APK not found: $apk" }
  $output = @(& $script:apksigner verify --verbose $apk 2>&1)
  if ($LASTEXITCODE -ne 0 -or @($output | Where-Object { $_ -match 'Verified using v[23](\.1|\.2)? scheme.*: true' }).Count -eq 0) {
    throw "$label APK is not verified with a modern APK signature: $apk`n$($output -join [Environment]::NewLine)"
  }
  Write-Output "PASS $label signing"
}

$script:apksigner = Get-ApkSigner
$hasProductionEnv = -not [string]::IsNullOrWhiteSpace($env:KEYSTORE_PATH) -and
  -not [string]::IsNullOrWhiteSpace($env:STORE_PASSWORD) -and
  -not [string]::IsNullOrWhiteSpace($env:KEY_PASSWORD)

if (-not $SkipBuild) {
  Invoke-Gradle @(':app:assembleQa', ':app:assembleBenchmark', '--no-daemon', '--console=plain')
  Assert-Signed $qaApk 'qa'
  Assert-Signed $benchmarkApk 'benchmark'

  $savedKeyStore = $env:KEYSTORE_PATH
  $savedStorePassword = $env:STORE_PASSWORD
  $savedKeyPassword = $env:KEY_PASSWORD
  $savedKeyAlias = $env:KEY_ALIAS
  try {
    Remove-Item Env:KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:KEY_ALIAS -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $unsignedRelease) { Remove-Item -LiteralPath $unsignedRelease -Force }
    if (Test-Path -LiteralPath $signedRelease) { Remove-Item -LiteralPath $signedRelease -Force }
    Invoke-Gradle @(':app:assembleRelease', '--no-daemon', '--console=plain')
    if (-not (Test-Path -LiteralPath $unsignedRelease -PathType Leaf)) { throw 'Release without credentials did not produce app-release-unsigned.apk.' }
    if (Test-Path -LiteralPath $signedRelease -PathType Leaf) { throw 'Release without credentials produced a signed-named app-release.apk.' }
    & (Join-Path $PSScriptRoot 'verify-release.ps1') -ApkPath $unsignedRelease
    if ($LASTEXITCODE -ne 0) { throw 'Unsigned release verification failed.' }
    Write-Output 'PASS release unsigned-local-validation without credentials'
  } finally {
    if ($null -eq $savedKeyStore) { Remove-Item Env:KEYSTORE_PATH -ErrorAction SilentlyContinue } else { $env:KEYSTORE_PATH = $savedKeyStore }
    if ($null -eq $savedStorePassword) { Remove-Item Env:STORE_PASSWORD -ErrorAction SilentlyContinue } else { $env:STORE_PASSWORD = $savedStorePassword }
    if ($null -eq $savedKeyPassword) { Remove-Item Env:KEY_PASSWORD -ErrorAction SilentlyContinue } else { $env:KEY_PASSWORD = $savedKeyPassword }
    if ($null -eq $savedKeyAlias) { Remove-Item Env:KEY_ALIAS -ErrorAction SilentlyContinue } else { $env:KEY_ALIAS = $savedKeyAlias }
  }
}

if ($hasProductionEnv) {
  Invoke-Gradle @(':app:assembleRelease', '--no-daemon', '--console=plain')
  Assert-Signed $signedRelease 'release production-configured'
  Write-Output 'PASS explicit production signing selected from supplied environment'
} else {
  Write-Output 'INFO explicit production signing not exercised; KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD were not supplied'
}