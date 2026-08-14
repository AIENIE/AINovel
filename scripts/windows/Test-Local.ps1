[CmdletBinding()]
param(
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [ValidateSet('L1', 'L2')][string]$Level = 'L2'
)

& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Test -Component $Component -TestLevel $Level
exit $LASTEXITCODE
