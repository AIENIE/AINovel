[CmdletBinding()]
param([switch]$AsJson)

$LASTEXITCODE = 0
& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Status -AsJson:$AsJson
exit $LASTEXITCODE
