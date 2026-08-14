[CmdletBinding()]
param([switch]$AsJson)

& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Status -AsJson:$AsJson
exit $LASTEXITCODE
