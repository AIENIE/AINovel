[CmdletBinding()]
param(
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [ValidateSet('L1', 'L2', 'L3')][string]$Level = 'L2'
)
. (Join-Path $PSScriptRoot 'LocalCommand.ps1')
if ($Level -eq 'L3') { Invoke-LocalScript (Join-Path $PSScriptRoot '../../start.ps1') @{Action='Test';Level='L3';Component=$Component}; exit 0 }
if ($Level -ne 'L1') { Invoke-LocalScript (Join-Path $PSScriptRoot 'tests/Test-RootEntry.ps1') }


Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'tests\Test-LocalContract.ps1')
& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Test -Component $Component -TestLevel $Level
