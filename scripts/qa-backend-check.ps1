param(
  [int]$Port = 18765
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot 'qa-backend.ps1'
$process = $null
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(1)

function Get-Response([string]$Method, [string]$Url, [hashtable]$Headers = @{}) {
  $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $Url)
  foreach ($header in $Headers.GetEnumerator()) {
    [void]$request.Headers.TryAddWithoutValidation($header.Key, $header.Value)
  }
  $response = $client.SendAsync($request).GetAwaiter().GetResult()
  [pscustomobject]@{
    Status = [int]$response.StatusCode
    Body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    Length = $response.Content.Headers.ContentLength
  }
}

try {
  $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
  $process = Start-Process -FilePath $powershell -ArgumentList @(
    '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $backend, '-Port', $Port
  ) -WindowStyle Hidden -PassThru

  $baseUrl = "http://127.0.0.1:$Port"
  $healthy = $false
  for ($attempt = 0; $attempt -lt 15; $attempt++) {
    try {
      $health = Get-Response 'Get' "$baseUrl/healthz"
      if ($health.Status -eq 200) { $healthy = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 200
  }
  if (-not $healthy) { throw 'QA backend did not become healthy.' }

  if ($health.Body -notmatch 'spotamp-qa-backend' -or $health.Body -notmatch 'qa-v1') {
    throw 'QA backend health contract is invalid.'
  }

  $unauthorized = Get-Response 'Get' "$baseUrl/api/v1/library/tracks"
  if ($unauthorized.Status -ne 401) { throw "Expected unauthorized library response 401, got $($unauthorized.Status)." }

  $wrongCredential = Get-Response 'Get' "$baseUrl/api/v1/library/tracks?offset=20&limit=100" @{ Authorization = 'Bearer not-a-qa-sentinel' }
  if ($wrongCredential.Status -ne 403 -or $wrongCredential.Body -notmatch 'qa_sentinel_required') {
    throw 'Non-sentinel authorization was not rejected by the QA backend.'
  }

  $authorized = Get-Response 'Get' "$baseUrl/api/v1/library/tracks?offset=20&limit=100" @{ Authorization = 'Bearer SPOTCORE_TEST_TOKEN_DO_NOT_USE' }
  if ($authorized.Status -ne 200 -or $authorized.Body -notmatch 'qa-track-20') {
    throw 'Authorized library contract is invalid.'
  }

  $invalidFixture = Get-Response 'Get' "$baseUrl/fixtures/bad%2Fname"
  if ($invalidFixture.Status -ne 400) { throw "Expected invalid fixture response 400, got $($invalidFixture.Status)." }

  $fixture = Get-Response 'Get' "$baseUrl/fixtures/tone-440.wav"
  if ($fixture.Status -ne 200 -or $fixture.Length -le 0) { throw 'Generated audio fixture contract is invalid.' }

  $lyrics = Get-Response 'Get' "$baseUrl/api/v1/lyrics/qa-track-20"
  if ($lyrics.Status -ne 200 -or $lyrics.Body -notmatch 'QA lyrics for qa-track-20') { throw 'Lyrics contract is invalid.' }

  $reset = Get-Response 'Post' "$baseUrl/api/v1/qa/reset"
  if ($reset.Status -ne 200 -or $reset.Body -notmatch 'in-memory-only') { throw 'QA reset contract is invalid.' }

  Write-Output 'QA_BACKEND_CHECKS=8'
  Write-Output "PASS QA backend contract loopback=$baseUrl"
}
finally {
  $client.Dispose()
  if ($null -ne $process -and -not $process.HasExited) {
    Stop-Process -Id $process.Id -Force
    $process.WaitForExit()
  }
}
