param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RunDir = Join-Path $RootDir "tmp/local-run"
$BackendOutLog = Join-Path $RunDir "backend.out.log"
$BackendErrLog = Join-Path $RunDir "backend.err.log"
$FrontendOutLog = Join-Path $RunDir "frontend.out.log"
$FrontendErrLog = Join-Path $RunDir "frontend.err.log"
$BackendPidFile = Join-Path $RunDir "backend.pid"
$FrontendPidFile = Join-Path $RunDir "frontend.pid"
$isWindowsPlatform = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)

function Invoke-Native {
  param(
    [string]$FilePath,
    [string[]]$Arguments
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed: $FilePath $($Arguments -join ' ')"
  }
}

function Require-Command {
  param([string]$Name)
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Missing required command: $Name"
  }
}

function Test-NpmScriptExists {
  param(
    [string]$ProjectDir,
    [string]$ScriptName
  )

  Push-Location $ProjectDir
  try {
    $output = (& npm run) 2>&1 | Out-String
    return $output -match "(?m)^\s+$([Regex]::Escape($ScriptName))(\s|$)"
  } finally {
    Pop-Location
  }
}

function Load-DotEnv {
  param([string]$Path)

  if (-not (Test-Path $Path)) {
    return $false
  }

  foreach ($rawLine in Get-Content $Path) {
    $line = $rawLine.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
      continue
    }
    $index = $line.IndexOf("=")
    if ($index -le 0) {
      continue
    }

    $name = $line.Substring(0, $index).Trim()
    if ($name -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
      continue
    }

    $value = $line.Substring($index + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, [EnvironmentVariableTarget]::Process)
  }
  return $true
}

function Set-DefaultEnv {
  param(
    [string]$Name,
    [string]$Value
  )

  $existing = [Environment]::GetEnvironmentVariable($Name, [EnvironmentVariableTarget]::Process)
  if ([string]::IsNullOrEmpty($existing)) {
    [Environment]::SetEnvironmentVariable($Name, $Value, [EnvironmentVariableTarget]::Process)
  }
}

function Stop-ManagedProcess {
  param(
    [string]$PidFile,
    [string]$Name
  )

  if (-not (Test-Path $PidFile)) {
    return
  }

  $raw = (Get-Content -Path $PidFile -Raw).Trim()
  if ($raw -match "^\d+$") {
    $pidValue = [int]$raw
    $proc = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
    if ($proc) {
      Write-Host "Stopping existing $Name process ($pidValue)"
      Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
      Start-Sleep -Seconds 1
    }
  }

  Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
}

function Stop-StaleProjectProcessByPort {
  param([int]$Port)

  if (-not $isWindowsPlatform) {
    return
  }
  if (-not (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
    return
  }

  $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if (-not $connections) {
    return
  }

  $pidValues = $connections | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($pidValue in $pidValues) {
    $procInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$pidValue" -ErrorAction SilentlyContinue
    if (-not $procInfo) {
      continue
    }
    if ($procInfo.CommandLine -and $procInfo.CommandLine.Contains($RootDir)) {
      Write-Host "Stopping stale project process on port $Port (PID $pidValue)"
      Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
    }
  }
}

function Wait-Port {
  param(
    [string]$Address,
    [int]$Port,
    [string]$Label,
    [int]$TimeoutSec = 180,
    [int]$WatchProcessId = 0
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    if ($WatchProcessId -gt 0 -and -not (Get-Process -Id $WatchProcessId -ErrorAction SilentlyContinue)) {
      throw "$Label process exited before port $Port became ready."
    }
    try {
      $client = New-Object System.Net.Sockets.TcpClient
      $task = $client.ConnectAsync($Address, $Port)
      if ($task.Wait(500) -and $client.Connected) {
        $client.Close()
        return
      }
      $client.Close()
    } catch {
    }
    Start-Sleep -Milliseconds 500
  }

  throw "Timeout waiting for $Label at ${Address}:$Port"
}

function Wait-Http {
  param(
    [string]$Url,
    [string]$Label,
    [int]$TimeoutSec = 180,
    [int]$WatchProcessId = 0
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    if ($WatchProcessId -gt 0 -and -not (Get-Process -Id $WatchProcessId -ErrorAction SilentlyContinue)) {
      throw "$Label process exited before $Url became reachable."
    }
    try {
      $response = Invoke-WebRequest -Uri $Url -Method Get -UseBasicParsing -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return
      }
    } catch {
    }
    Start-Sleep -Milliseconds 500
  }

  throw "Timeout waiting for $Label at $Url"
}

function Build-Frontend {
  $frontDir = Join-Path $RootDir "frontend"
  Push-Location $frontDir
  try {
    $npmCommand = if ($isWindowsPlatform) { "npm.cmd" } else { "npm" }
    $ciSucceeded = $false
    $attempt = 0
    $maxAttempts = 2
    while (-not $ciSucceeded -and $attempt -lt $maxAttempts) {
      try {
        Invoke-Native -FilePath $npmCommand -Arguments @("ci", "--legacy-peer-deps")
        $ciSucceeded = $true
      } catch {
        $attempt += 1
        if ($attempt -lt $maxAttempts) {
          Start-Sleep -Seconds 2
        }
      }
    }
    if (-not $ciSucceeded) {
      Write-Warning "npm ci failed, fallback to npm install --legacy-peer-deps."
      Invoke-Native -FilePath $npmCommand -Arguments @("install", "--legacy-peer-deps")
    }
    if (Test-NpmScriptExists -ProjectDir $frontDir -ScriptName "test") {
      Invoke-Native -FilePath $npmCommand -Arguments @("run", "test")
    }
    Invoke-Native -FilePath $npmCommand -Arguments @("run", "build")
  } finally {
    Pop-Location
  }
}

function Build-Backend {
  $backendDir = Join-Path $RootDir "backend"
  $targetDir = Join-Path $backendDir "target"
  if (Test-Path $targetDir) {
    Remove-Item -Recurse -Force $targetDir
  }

  Push-Location $backendDir
  try {
    Invoke-Native -FilePath "mvn" -Arguments @("-q", "test")
    Invoke-Native -FilePath "mvn" -Arguments @("-q", "-DskipTests", "clean", "package")
  } finally {
    Pop-Location
  }

  $jar = Get-ChildItem -Path $targetDir -Filter *.jar |
    Where-Object { -not $_.Name.EndsWith(".original") } |
    Select-Object -First 1
  if (-not $jar) {
    throw "Backend jar not found in $targetDir"
  }

  Copy-Item -Path $jar.FullName -Destination (Join-Path $targetDir "app.jar") -Force
}

function Start-Backend {
  $backendDir = Join-Path $RootDir "backend"
  $jarPath = Join-Path $backendDir "target/app.jar"
  $args = @()
  if ($env:JAVA_OPTS) {
    $args += $env:JAVA_OPTS -split "\s+"
  }
  $args += @("-jar", $jarPath)

  return Start-Process -FilePath "java" `
    -ArgumentList $args `
    -WorkingDirectory $backendDir `
    -RedirectStandardOutput $BackendOutLog `
    -RedirectStandardError $BackendErrLog `
    -PassThru
}

function Start-Frontend {
  $frontDir = Join-Path $RootDir "frontend"
  $npmCommand = if ($isWindowsPlatform) { "npm.cmd" } else { "npm" }

  return Start-Process -FilePath $npmCommand `
    -ArgumentList @("run", "dev", "--", "--host", "0.0.0.0", "--port", "11040", "--strictPort") `
    -WorkingDirectory $frontDir `
    -RedirectStandardOutput $FrontendOutLog `
    -RedirectStandardError $FrontendErrLog `
    -PassThru
}

function Show-StartupLogs {
  Write-Host "Backend stderr tail:"
  if (Test-Path $BackendErrLog) {
    Get-Content -Path $BackendErrLog -Tail 80
  }
  Write-Host "Frontend stderr tail:"
  if (Test-Path $FrontendErrLog) {
    Get-Content -Path $FrontendErrLog -Tail 80
  }
}

Require-Command -Name npm
Require-Command -Name mvn
Require-Command -Name java

New-Item -ItemType Directory -Path $RunDir -Force | Out-Null
$loadedDotEnv = $false
if (Load-DotEnv -Path (Join-Path $RootDir "env.txt")) {
  $loadedDotEnv = $true
}
if (-not $loadedDotEnv) {
  Write-Host "No env.txt found, using built-in defaults."
}
Set-DefaultEnv -Name "MYSQL_HOST" -Value "127.0.0.1"
Set-DefaultEnv -Name "MYSQL_PORT" -Value "3308"
Set-DefaultEnv -Name "MYSQL_DB" -Value "ainovel"
Set-DefaultEnv -Name "MYSQL_USER" -Value "root"
Set-DefaultEnv -Name "MYSQL_PASSWORD" -Value "123456"
Set-DefaultEnv -Name "REDIS_HOST" -Value "127.0.0.1"
Set-DefaultEnv -Name "REDIS_PORT" -Value "6381"
Set-DefaultEnv -Name "REDIS_PASSWORD" -Value ""
Set-DefaultEnv -Name "REDIS_KEY_PREFIX" -Value "aienie:ainovel:"
Set-DefaultEnv -Name "QDRANT_HOST" -Value "http://127.0.0.1"
Set-DefaultEnv -Name "QDRANT_PORT" -Value "6335"
Set-DefaultEnv -Name "QDRANT_ENABLED" -Value "true"
Set-DefaultEnv -Name "CONSUL_ENABLED" -Value "true"
Set-DefaultEnv -Name "CONSUL_SCHEME" -Value "http"
Set-DefaultEnv -Name "CONSUL_HOST" -Value "127.0.0.1"
Set-DefaultEnv -Name "CONSUL_PORT" -Value "8502"
Set-DefaultEnv -Name "CONSUL_DATACENTER" -Value ""
Set-DefaultEnv -Name "CONSUL_CACHE_SECONDS" -Value "30"
Set-DefaultEnv -Name "USER_HTTP_SERVICE_NAME" -Value "aienie-userservice-http"
Set-DefaultEnv -Name "USER_HTTP_ADDR" -Value "http://127.0.0.1:10000"
Set-DefaultEnv -Name "AI_GRPC_SERVICE_NAME" -Value "aienie-aiservice-grpc"
Set-DefaultEnv -Name "AI_GRPC_ADDR" -Value "static://127.0.0.1:10011"
Set-DefaultEnv -Name "PAY_GRPC_SERVICE_NAME" -Value "aienie-payservice-grpc"
Set-DefaultEnv -Name "PAY_GRPC_ADDR" -Value "static://127.0.0.1:20021"
Set-DefaultEnv -Name "USER_GRPC_SERVICE_NAME" -Value "aienie-userservice-grpc"
Set-DefaultEnv -Name "USER_GRPC_SERVICE_TAG" -Value ""
Set-DefaultEnv -Name "USER_SESSION_GRPC_TIMEOUT_MS" -Value "2000"
Set-DefaultEnv -Name "USER_GRPC_ADDR" -Value "static://127.0.0.1:10001"
Set-DefaultEnv -Name "SSO_SESSION_VALIDATION_ENABLED" -Value "true"
Set-DefaultEnv -Name "SSO_CALLBACK_ORIGIN" -Value ""
Set-DefaultEnv -Name "VITE_SSO_ENTRY_BASE_URL" -Value ""
Set-DefaultEnv -Name "DB_URL" -Value "jdbc:mysql://$($env:MYSQL_HOST):$($env:MYSQL_PORT)/$($env:MYSQL_DB)?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false"
Set-DefaultEnv -Name "DB_USERNAME" -Value "$($env:MYSQL_USER)"
Set-DefaultEnv -Name "DB_PASSWORD" -Value "$($env:MYSQL_PASSWORD)"
Set-DefaultEnv -Name "PORT" -Value "11041"

Stop-ManagedProcess -PidFile $BackendPidFile -Name "backend"
Stop-ManagedProcess -PidFile $FrontendPidFile -Name "frontend"
Stop-StaleProjectProcessByPort -Port 11040
Stop-StaleProjectProcessByPort -Port 11041

Build-Frontend
Build-Backend

$backendProc = $null
$frontendProc = $null
try {
  $backendProc = Start-Backend
  Set-Content -Path $BackendPidFile -Value $backendProc.Id -NoNewline

  $frontendProc = Start-Frontend
  Set-Content -Path $FrontendPidFile -Value $frontendProc.Id -NoNewline

  Wait-Port -Address "127.0.0.1" -Port 11041 -Label "backend" -WatchProcessId $backendProc.Id
  Wait-Http -Url "http://127.0.0.1:11040" -Label "frontend homepage" -WatchProcessId $frontendProc.Id
} catch {
  Show-StartupLogs
  throw
}

Write-Host "Local deployment finished."
Write-Host "Frontend: http://127.0.0.1:11040"
Write-Host "Backend API: http://127.0.0.1:11041/api"
Write-Host "Backend PID file: $BackendPidFile"
Write-Host "Frontend PID file: $FrontendPidFile"
