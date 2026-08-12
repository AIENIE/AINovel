[CmdletBinding()]
param(
    [string]$EnvFile = (Join-Path $env:LOCALAPPDATA 'Aienie\secrets\ainovel-localbase.env'),
    [ValidateRange(30, 300)][int]$StartupTimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$worker = Join-Path $PSScriptRoot 'Start-LocalWorker.ps1'
if (-not (Test-Path -LiteralPath $worker -PathType Leaf)) {
    throw 'The Windows local-run worker script is missing.'
}
$pwsh = Join-Path $PSHOME 'pwsh.exe'
if (-not (Test-Path -LiteralPath $pwsh -PathType Leaf)) {
    throw 'PowerShell 7 is required.'
}

# Secrets are imported only in the isolated no-profile worker process.
& $pwsh -NoLogo -NoProfile -NonInteractive -File $worker `
    -EnvFile $EnvFile -StartupTimeoutSeconds $StartupTimeoutSeconds
if ($LASTEXITCODE -ne 0) {
    throw "AINovel local worker failed with exit code $LASTEXITCODE."
}
