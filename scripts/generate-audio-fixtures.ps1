param(
  [string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) 'qa-fixtures/audio'),
  [int]$DurationSeconds = 3,
  [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ffmpeg = (Get-Command ffmpeg -ErrorAction Stop).Source
if ($DurationSeconds -lt 1 -or $DurationSeconds -gt 30) {
  throw 'DurationSeconds must be between 1 and 30.'
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$fixtures = @(
  @{ Name = 'tone-440.wav'; Input = 'sine=frequency=440:sample_rate=44100'; Codec = 'pcm_s16le'; Format = 'wav' },
  @{ Name = 'tone-880.mp3'; Input = 'sine=frequency=880:sample_rate=44100'; Codec = 'libmp3lame'; Format = 'mp3' },
  @{ Name = 'silence.wav'; Input = 'anullsrc=channel_layout=stereo:sample_rate=44100'; Codec = 'pcm_s16le'; Format = 'wav' }
)

foreach ($fixture in $fixtures) {
  $path = Join-Path $OutputDirectory $fixture.Name
  if ((Test-Path $path) -and -not $Force) { continue }
  & $ffmpeg -hide_banner -loglevel error -y -f lavfi -i $fixture.Input -t $DurationSeconds -c:a $fixture.Codec -f $fixture.Format $path
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $path)) { throw "Failed to generate $($fixture.Name)." }
}

Get-ChildItem -LiteralPath $OutputDirectory -File | Select-Object Name, Length
