[CmdletBinding()]
param([ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All')

$ErrorActionPreference = 'Stop'
try {
    & (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Build -Component $Component
    exit 0
} catch {
    Write-Error $_
    exit 1
}
