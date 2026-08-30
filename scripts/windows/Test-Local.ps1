[CmdletBinding()]
param(
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [ValidateSet('L1', 'L2')][string]$Level = 'L2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'tests\Test-LocalContract.ps1')
& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Test -Component $Component -TestLevel $Level
