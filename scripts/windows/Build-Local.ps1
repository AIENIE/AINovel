[CmdletBinding()]
param([ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All')

& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Build -Component $Component
exit $LASTEXITCODE
