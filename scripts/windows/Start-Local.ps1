[CmdletBinding()]
param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\..\env.txt'),
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [ValidateRange(30, 900)][int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
try {
    & (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Start -Component $Component -EnvironmentFile $EnvironmentFile -StartupTimeoutSeconds $StartupTimeoutSeconds
    exit 0
} catch {
    Write-Error $_
    exit 1
}
