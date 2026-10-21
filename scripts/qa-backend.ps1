param(
  [int]$Port = 8765,
  [switch]$Once
)

$ErrorActionPreference = 'Stop'
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Port must be between 1024 and 65535.' }

$listener = [Net.HttpListener]::new()
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
$baseUrl = "http://127.0.0.1:$Port"
$sentinelAuthorization = 'Bearer SPOTCORE_TEST_TOKEN_DO_NOT_USE'
Write-Output "SpotAmp QA backend listening at $baseUrl"

function Write-Json([Net.HttpListenerResponse]$Response, [int]$StatusCode, [object]$Body) {
  $bytes = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8 -Compress))
  $Response.StatusCode = $StatusCode
  $Response.ContentType = 'application/json; charset=utf-8'
  $Response.ContentLength64 = $bytes.Length
  $Response.OutputStream.Write($bytes, 0, $bytes.Length)
  $Response.Close()
}

function Write-Text([Net.HttpListenerResponse]$Response, [int]$StatusCode, [string]$Body) {
  $bytes = [Text.Encoding]::UTF8.GetBytes($Body)
  $Response.StatusCode = $StatusCode
  $Response.ContentType = 'text/plain; charset=utf-8'
  $Response.ContentLength64 = $bytes.Length
  $Response.OutputStream.Write($bytes, 0, $bytes.Length)
  $Response.Close()
}

function Write-File([Net.HttpListenerResponse]$Response, [string]$Path) {
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Write-Text $Response 404 'fixture_not_found'
    return
  }
  $bytes = [IO.File]::ReadAllBytes($Path)
  $Response.StatusCode = 200
  $Response.ContentType = switch ([IO.Path]::GetExtension($Path).ToLowerInvariant()) {
    '.mp3' { 'audio/mpeg' }
    default { 'audio/wav' }
  }
  $Response.ContentLength64 = $bytes.Length
  $Response.OutputStream.Write($bytes, 0, $bytes.Length)
  $Response.Close()
}
try {
  do {
    $context = $listener.GetContext()
    $request = $context.Request
    $response = $context.Response
    $path = $request.Url.AbsolutePath

    if ($request.HttpMethod -eq 'GET' -and $path -eq '/healthz') {
      Write-Json $response 200 @{ service = 'spotamp-qa-backend'; contractVersion = 'qa-v1'; state = 'ready' }
      continue
    }

    if ($request.HttpMethod -eq 'GET' -and $path -like '/fixtures/*') {
      $fixtureName = $path.Substring('/fixtures/'.Length)
      if ($fixtureName -notmatch '^[A-Za-z0-9._-]+$') {
        Write-Text $response 400 'invalid_fixture_name'
      } else {
        Write-File $response (Join-Path $PSScriptRoot "../qa-fixtures/audio/$fixtureName")
      }
      continue
    }
    if ($request.HttpMethod -eq 'POST' -and $path -eq '/api/v1/qa/reset') {
      Write-Json $response 200 @{ reset = $true; scope = 'in-memory-only' }
      continue
    }

    if ($request.HttpMethod -eq 'GET' -and $path -eq '/api/v1/library/tracks') {
      $authorization = $request.Headers['Authorization']
      if ([string]::IsNullOrWhiteSpace($authorization)) {
        Write-Json $response 401 @{ error = 'authorization_required'; contractVersion = 'qa-v1' }
        continue
      }
      if ($authorization -cne $sentinelAuthorization) {
        Write-Json $response 403 @{ error = 'qa_sentinel_required'; contractVersion = 'qa-v1' }
        continue
      }
      $query = [Web.HttpUtility]::ParseQueryString($request.Url.Query)
      $offset = 0
      $limit = 100
      [int]::TryParse($query['offset'], [ref]$offset) | Out-Null
      [int]::TryParse($query['limit'], [ref]$limit) | Out-Null
      $track = @{ id = "qa-track-$offset"; title = 'QA Fixture'; artist = 'SpotAmp QA'; album = 'Local Contract'; streamUrl = "$baseUrl/fixtures/tone-440.wav"; duration = 3 }
      Write-Json $response 200 @{ contractVersion = 'qa-v1'; offset = $offset; limit = $limit; tracks = @($track) }
      continue
    }

    if ($request.HttpMethod -eq 'GET' -and $path -like '/api/v1/lyrics/*') {
      $trackId = $path.Substring('/api/v1/lyrics/'.Length)
      Write-Json $response 200 @{ text = "[00:00.00] QA lyrics for $trackId"; synced = $true }
      continue
    }

    Write-Text $response 404 'not_found'
  } while (-not $Once)
}
finally {
  if ($listener.IsListening) { $listener.Stop() }
  $listener.Close()
}
