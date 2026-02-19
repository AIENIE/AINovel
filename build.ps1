param(
  [switch]$Init
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$isWindowsPlatform = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)
$composeFile = if ($isWindowsPlatform) { "docker-compose.windows.yml" } else { "docker-compose.yml" }

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

function Build-Frontend {
  $frontDir = Join-Path $RootDir "frontend"
  $npmCommand = if ($isWindowsPlatform) { "npm.cmd" } else { "npm" }
  Push-Location $frontDir
  try {
    Invoke-Native -FilePath $npmCommand -Arguments @("ci", "--legacy-peer-deps")
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

Require-Command -Name docker
Require-Command -Name npm
Require-Command -Name mvn

Build-Frontend
Build-Backend

$composePath = Join-Path $RootDir $composeFile
Write-Host "Using compose file: $composePath"
Invoke-Native -FilePath "docker" -Arguments @("compose", "-f", $composePath, "down", "--remove-orphans")
Invoke-Native -FilePath "docker" -Arguments @("compose", "-f", $composePath, "up", "-d")

if ($Init) {
  if ($isWindowsPlatform) {
    Write-Warning "--init requires hosts and system nginx setup, this is only supported on Linux."
  } else {
    Write-Warning "--init in build.ps1 is not implemented for non-Windows PowerShell. Use build.sh --init on Linux."
  }
}

Write-Host "Deployment finished. Frontend: http://ainovel.seekerhut.com. Backend API: http://ainovel.seekerhut.com:10011/api"
