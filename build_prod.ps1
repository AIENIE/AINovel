param(
  [switch]$Init
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$isWindowsPlatform = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)

function Invoke-Sudo {
  param([string[]]$Command)
  if (-not $env:SUDO_PASSWORD) {
    throw "SUDO_PASSWORD is required for sudo operations."
  }

  $cmdString = ($Command | ForEach-Object {
      if ($_ -match "[\s`"`$]") {
        '"' + ($_ -replace '"', '\"') + '"'
      } else {
        $_
      }
    }) -join " "
  & bash -lc "printf '%s\n' ""`$SUDO_PASSWORD"" | sudo -S -p '' $cmdString"
}

& (Join-Path $RootDir "build.ps1")

if ($Init) {
  if ($isWindowsPlatform) {
    Write-Warning "--init requires system nginx/certbot setup, this is only supported on Linux."
  } else {
    if (-not (Get-Command nginx -ErrorAction SilentlyContinue)) {
      Invoke-Sudo @("env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "update")
      Invoke-Sudo @("env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "install", "-y", "nginx")
    }
    if (-not (Get-Command certbot -ErrorAction SilentlyContinue)) {
      Invoke-Sudo @("env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "update")
      Invoke-Sudo @("env", "DEBIAN_FRONTEND=noninteractive", "apt-get", "install", "-y", "certbot", "python3-certbot-nginx")
    }

    $sourceConf = Join-Path $RootDir "deploy/nginx/ainovel_prod.conf"
    if (-not (Test-Path $sourceConf)) {
      throw "Missing nginx config: $sourceConf"
    }
    Invoke-Sudo @("install", "-m", "0644", $sourceConf, "/etc/nginx/conf.d/ainovel_prod.conf")
    Invoke-Sudo @("nginx", "-t")
    Invoke-Sudo @("systemctl", "reload", "nginx")

    $email = if ($env:LETSENCRYPT_EMAIL) { $env:LETSENCRYPT_EMAIL } else { "admin@ainovel.aienie.com" }
    Invoke-Sudo @("certbot", "--nginx", "-d", "ainovel.aienie.com", "--non-interactive", "--agree-tos", "--email", $email, "--redirect")
    Invoke-Sudo @("nginx", "-t")
    Invoke-Sudo @("systemctl", "reload", "nginx")
  }
}

Write-Host "Production deployment finished. URL: https://ainovel.aienie.com"
