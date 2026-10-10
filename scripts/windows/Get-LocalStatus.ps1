[CmdletBinding()]
param([switch]$AsJson, [ValidatePattern('^[a-z0-9][a-z0-9-]{0,47}$')][string]$InstanceName='ainovel', [ValidateRange(1024,65535)][int]$FrontendPort=11040, [ValidateRange(1024,65535)][int]$BackendPort=11041)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Status -AsJson:$AsJson -InstanceName $InstanceName -FrontendPort $FrontendPort -BackendPort $BackendPort
