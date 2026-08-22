[CmdletBinding()]
param(
    [string]$EnvironmentFile = (Join-Path $(if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }) 'Aienie\secrets\ainovel.env'),
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [ValidateRange(30, 900)][int]$StartupTimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Start -Component $Component -EnvironmentFile $EnvironmentFile -StartupTimeoutSeconds $StartupTimeoutSeconds
