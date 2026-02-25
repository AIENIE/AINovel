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

    $existing = [Environment]::GetEnvironmentVariable($name, [EnvironmentVariableTarget]::Process)
    if ([string]::IsNullOrEmpty($existing)) {
      [Environment]::SetEnvironmentVariable($name, $value, [EnvironmentVariableTarget]::Process)
    }
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

function Test-PlaceholderValue {
  param([string]$Value)

  if ([string]::IsNullOrWhiteSpace($Value)) {
    return $true
  }
  $normalized = $Value.Trim().ToUpperInvariant()
  return $normalized.StartsWith("REPLACE_ME") -or
    $normalized.Contains("REPLACE_WITH_YOUR_OWN") -or
    $normalized.Contains("REPLACE-WITH-YOUR-OWN") -or
    $normalized.Contains("CHANGE-ME") -or
    $normalized.Contains("CHANGE_ME")
}

function ConvertTo-Base64Url {
  param([byte[]]$Bytes)

  return [Convert]::ToBase64String($Bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function New-Hs256Jwt {
  param(
    [string]$Secret,
    [string]$Issuer,
    [string]$Audience,
    [string]$Role,
    [string]$ServiceName,
    [string[]]$Scopes,
    [int]$TtlSeconds
  )

  $headerJson = '{"alg":"HS256","typ":"JWT"}'
  $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $ttl = [Math]::Max(300, $TtlSeconds)
  $payloadObj = [ordered]@{
    sub = $ServiceName
    service = $ServiceName
    role = $Role
    scopes = $Scopes
    iat = $now
    exp = $now + $ttl
    iss = $Issuer
    aud = $Audience
  }
  $payloadJson = $payloadObj | ConvertTo-Json -Compress -Depth 6

  $headerB64 = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($headerJson))
  $payloadB64 = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($payloadJson))
  $signingInput = "$headerB64.$payloadB64"

  $hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
  try {
    $signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($signingInput))
  } finally {
    $hmac.Dispose()
  }
  $signatureB64 = ConvertTo-Base64Url $signatureBytes
  return "$signingInput.$signatureB64"
}

function Ensure-PayServiceJwt {
  function Test-PayServiceJwtClaims {
    param(
      [string]$Token,
      [string]$ExpectedIssuer,
      [string]$ExpectedAudience,
      [string]$ExpectedRole,
      [string[]]$RequiredScopes
    )

    if ([string]::IsNullOrWhiteSpace($Token)) {
      return $false
    }
    $raw = $Token.Trim()
    if ($raw.StartsWith("Bearer ", [System.StringComparison]::OrdinalIgnoreCase)) {
      $raw = $raw.Substring(7).Trim()
    }
    $parts = $raw.Split(".")
    if ($parts.Length -ne 3) {
      return $false
    }
    try {
      $payloadBytes = [Convert]::FromBase64String($parts[1].Replace("-", "+").Replace("_", "/").PadRight($parts[1].Length + ((4 - $parts[1].Length % 4) % 4), "="))
      $payload = ([System.Text.Encoding]::UTF8.GetString($payloadBytes) | ConvertFrom-Json)
    } catch {
      return $false
    }
    if ($null -eq $payload) {
      return $false
    }

    if ([string]::IsNullOrWhiteSpace([string]$payload.role) -or [string]$payload.role -ine $ExpectedRole) {
      return $false
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedIssuer) -and [string]$payload.iss -ne $ExpectedIssuer) {
      return $false
    }

    $audiences = @()
    if ($payload.aud -is [System.Array]) {
      $audiences = @($payload.aud | ForEach-Object { [string]$_ })
    } elseif ($null -ne $payload.aud) {
      $audiences = @([string]$payload.aud -split "[,\s]+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedAudience) -and -not ($audiences -contains $ExpectedAudience)) {
      return $false
    }

    $actualScopes = @()
    if ($payload.scopes -is [System.Array]) {
      $actualScopes = @($payload.scopes | ForEach-Object { [string]$_ })
    } elseif ($null -ne $payload.scopes) {
      $actualScopes = @([string]$payload.scopes -split "[,\s]+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    }
    foreach ($required in $RequiredScopes) {
      if (-not ($actualScopes | Where-Object { $_ -ieq $required })) {
        return $false
      }
    }

    $exp = 0L
    [void][long]::TryParse([string]$payload.exp, [ref]$exp)
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    return $exp -gt ($now + 30)
  }

  $current = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_SERVICE_JWT", [EnvironmentVariableTarget]::Process)
  $secret = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_JWT_SECRET", [EnvironmentVariableTarget]::Process)
  $issuer = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_JWT_ISSUER", [EnvironmentVariableTarget]::Process)
  $audience = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_JWT_AUDIENCE", [EnvironmentVariableTarget]::Process)
  $role = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_JWT_ROLE", [EnvironmentVariableTarget]::Process)
  $scopesRaw = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_JWT_SCOPES", [EnvironmentVariableTarget]::Process)
  $requiredScopes = @()
  if (-not [string]::IsNullOrWhiteSpace($scopesRaw)) {
    $requiredScopes = $scopesRaw -split "[,\s]+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  }

  if (-not (Test-PlaceholderValue -Value $current)) {
    if (Test-PayServiceJwtClaims -Token $current -ExpectedIssuer $issuer -ExpectedAudience $audience -ExpectedRole $role -RequiredScopes $requiredScopes) {
      return
    }
  }

  if (Test-PlaceholderValue -Value $secret) {
    throw "EXTERNAL_PAY_SERVICE_JWT missing/invalid and EXTERNAL_PAY_JWT_SECRET is invalid."
  }
  $service = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_JWT_SERVICE", [EnvironmentVariableTarget]::Process)
  $ttlRaw = [Environment]::GetEnvironmentVariable("EXTERNAL_PAY_JWT_TTL_SECONDS", [EnvironmentVariableTarget]::Process)

  $ttl = 3600
  if (-not [string]::IsNullOrWhiteSpace($ttlRaw)) {
    [void][int]::TryParse($ttlRaw, [ref]$ttl)
  }
  $scopes = @()
  if (-not [string]::IsNullOrWhiteSpace($scopesRaw)) {
    $scopes = $scopesRaw -split "[,\s]+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  }

  $token = New-Hs256Jwt `
    -Secret $secret `
    -Issuer $issuer `
    -Audience $audience `
    -Role $role `
    -ServiceName $service `
    -Scopes $scopes `
    -TtlSeconds $ttl

  [Environment]::SetEnvironmentVariable("EXTERNAL_PAY_SERVICE_JWT", $token, [EnvironmentVariableTarget]::Process)
  if (Test-PlaceholderValue -Value $current) {
    Write-Host "Generated EXTERNAL_PAY_SERVICE_JWT from EXTERNAL_PAY_JWT_SECRET."
  } else {
    Write-Host "Regenerated EXTERNAL_PAY_SERVICE_JWT because existing token claims are invalid or expired."
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

  $appJarPath = Join-Path $targetDir "app.jar"
  if (Test-Path $appJarPath) {
    Remove-Item -Recurse -Force $appJarPath
  }
  Copy-Item -Path $jar.FullName -Destination $appJarPath -Force
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
Set-DefaultEnv -Name "MYSQL_HOST" -Value "192.168.1.4"
Set-DefaultEnv -Name "MYSQL_PORT" -Value "3306"
Set-DefaultEnv -Name "MYSQL_DB" -Value "ainovel"
Set-DefaultEnv -Name "MYSQL_USER" -Value "ainovel"
Set-DefaultEnv -Name "MYSQL_PASSWORD" -Value "ainovelpwd"
Set-DefaultEnv -Name "REDIS_HOST" -Value "192.168.1.4"
Set-DefaultEnv -Name "REDIS_PORT" -Value "6379"
Set-DefaultEnv -Name "REDIS_PASSWORD" -Value ""
Set-DefaultEnv -Name "REDIS_KEY_PREFIX" -Value "aienie:ainovel:"
Set-DefaultEnv -Name "QDRANT_HOST" -Value "http://192.168.1.4"
Set-DefaultEnv -Name "QDRANT_PORT" -Value "6333"
Set-DefaultEnv -Name "QDRANT_ENABLED" -Value "true"
Set-DefaultEnv -Name "CONSUL_ENABLED" -Value "true"
Set-DefaultEnv -Name "CONSUL_SCHEME" -Value "http"
Set-DefaultEnv -Name "CONSUL_HOST" -Value "192.168.1.4"
Set-DefaultEnv -Name "CONSUL_PORT" -Value "60000"
Set-DefaultEnv -Name "CONSUL_DATACENTER" -Value ""
Set-DefaultEnv -Name "CONSUL_CACHE_SECONDS" -Value "30"
Set-DefaultEnv -Name "USER_HTTP_SERVICE_NAME" -Value "aienie-userservice-http"
Set-DefaultEnv -Name "USER_HTTP_ADDR" -Value "https://userservice.seekerhut.com"
Set-DefaultEnv -Name "AI_GRPC_SERVICE_NAME" -Value "aienie-aiservice-grpc"
Set-DefaultEnv -Name "AI_GRPC_ADDR" -Value "static://aiservice.seekerhut.com:10011"
Set-DefaultEnv -Name "PAY_GRPC_SERVICE_NAME" -Value "aienie-payservice-grpc"
Set-DefaultEnv -Name "PAY_GRPC_ADDR" -Value "static://payservice.seekerhut.com:20021"
Set-DefaultEnv -Name "USER_GRPC_SERVICE_NAME" -Value "aienie-userservice-grpc"
Set-DefaultEnv -Name "USER_GRPC_SERVICE_TAG" -Value ""
Set-DefaultEnv -Name "USER_SESSION_GRPC_TIMEOUT_MS" -Value "5000"
Set-DefaultEnv -Name "USER_GRPC_ADDR" -Value "static://userservice.seekerhut.com:10001"
Set-DefaultEnv -Name "SSO_SESSION_VALIDATION_ENABLED" -Value "true"
Set-DefaultEnv -Name "SSO_CALLBACK_ORIGIN" -Value "https://ainovel.seekerhut.com"
Set-DefaultEnv -Name "VITE_SSO_ENTRY_BASE_URL" -Value "https://ainovel.seekerhut.com"
Set-DefaultEnv -Name "EXTERNAL_PROJECT_KEY" -Value "ainovel"
Set-DefaultEnv -Name "EXTERNAL_SECURITY_FAIL_FAST" -Value "true"
Set-DefaultEnv -Name "EXTERNAL_GRPC_TLS_ENABLED" -Value "false"
Set-DefaultEnv -Name "EXTERNAL_GRPC_PLAINTEXT_ENABLED" -Value "true"
Set-DefaultEnv -Name "EXTERNAL_AI_HMAC_CALLER" -Value "integration-test"
Set-DefaultEnv -Name "EXTERNAL_AI_HMAC_SECRET" -Value "0f18f9c1548e4f5d8520c55f5c8d0b3d9e95bd5f81a40fbb8e2ffeb8b7f0d530"
Set-DefaultEnv -Name "EXTERNAL_USER_INTERNAL_GRPC_TOKEN" -Value "local-userservice-internal-token"
Set-DefaultEnv -Name "EXTERNAL_PAY_SERVICE_JWT" -Value "REPLACE_ME_PAY_SERVICE_JWT"
Set-DefaultEnv -Name "JWT_SECRET" -Value "replace-with-your-own-long-random-secret"
Set-DefaultEnv -Name "EXTERNAL_PAY_JWT_SECRET" -Value "$($env:JWT_SECRET)"
Set-DefaultEnv -Name "EXTERNAL_PAY_JWT_ISSUER" -Value "aienie-services"
Set-DefaultEnv -Name "EXTERNAL_PAY_JWT_AUDIENCE" -Value "aienie-payservice-grpc"
Set-DefaultEnv -Name "EXTERNAL_PAY_JWT_ROLE" -Value "SERVICE"
Set-DefaultEnv -Name "EXTERNAL_PAY_JWT_SERVICE" -Value "$($env:EXTERNAL_PROJECT_KEY)"
Set-DefaultEnv -Name "EXTERNAL_PAY_JWT_SCOPES" -Value "billing.read,billing.write"
Set-DefaultEnv -Name "EXTERNAL_PAY_JWT_TTL_SECONDS" -Value "3600"
Set-DefaultEnv -Name "APP_TIME_ZONE" -Value "Asia/Shanghai"
Set-DefaultEnv -Name "JAVA_OPTS" -Value "-Duser.timezone=Asia/Shanghai"
Set-DefaultEnv -Name "SPRINGDOC_API_DOCS_ENABLED" -Value "false"
Set-DefaultEnv -Name "SPRINGDOC_SWAGGER_UI_ENABLED" -Value "false"
Set-DefaultEnv -Name "APP_SECURITY_CORS_ALLOWED_ORIGINS" -Value "https://ainovel.seekerhut.com,https://ainovel.aienie.com,http://127.0.0.1:11040,http://localhost:11040"
Set-DefaultEnv -Name "APP_SECURITY_CORS_ALLOWED_METHODS" -Value "GET,POST,PUT,DELETE,OPTIONS,PATCH"
Set-DefaultEnv -Name "APP_SECURITY_CORS_ALLOWED_HEADERS" -Value "Authorization,Content-Type,Idempotency-Key,X-Requested-With"
Set-DefaultEnv -Name "DB_URL" -Value "jdbc:mysql://$($env:MYSQL_HOST):$($env:MYSQL_PORT)/$($env:MYSQL_DB)?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
Set-DefaultEnv -Name "DB_USERNAME" -Value "$($env:MYSQL_USER)"
Set-DefaultEnv -Name "DB_PASSWORD" -Value "$($env:MYSQL_PASSWORD)"
Set-DefaultEnv -Name "PORT" -Value "11041"

Ensure-PayServiceJwt

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
