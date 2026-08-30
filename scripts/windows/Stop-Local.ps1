[CmdletBinding()]
param([ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Stop -Component $Component
