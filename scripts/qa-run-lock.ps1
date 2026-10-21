# Serialize the QA harnesses that share one ADB device and qa-results/latest.
# A host-wide mutex also covers separate PowerShell processes and worktrees.
function Enter-SpotAmpQaRunLock {
  [CmdletBinding()]
  param([int]$WaitMilliseconds = 0)

  if ($WaitMilliseconds -lt 0) { throw 'WaitMilliseconds must be zero or greater.' }
  $mutex = [System.Threading.Mutex]::new($false, 'Global\SpotAmpAndroidQaRun')
  try {
    $acquired = $false
    try { $acquired = $mutex.WaitOne($WaitMilliseconds) }
    catch [System.Threading.AbandonedMutexException] { $acquired = $true }
    if (-not $acquired) {
      $mutex.Dispose()
      return $null
    }
    return $mutex
  } catch {
    $mutex.Dispose()
    throw
  }
}

function Exit-SpotAmpQaRunLock {
  [CmdletBinding()]
  param([Parameter(Mandatory)] [System.Threading.Mutex]$Mutex)

  try { $Mutex.ReleaseMutex() }
  finally { $Mutex.Dispose() }
}
