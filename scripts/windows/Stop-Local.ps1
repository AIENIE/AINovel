[CmdletBinding()]
param([ValidatePattern('^[a-z0-9][a-z0-9-]{0,47}$')][string]$InstanceName='ainovel', [ValidateRange(1024,65535)][int]$FrontendPort=11040, [ValidateRange(1024,65535)][int]$BackendPort=11041, [ValidateSet('All','Backend','Frontend')][string]$Component='All', [ValidatePattern('^(\d+(,\d+)*)?$')][string]$PreserveProcessIds='')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'LocalCommand.ps1')
Invoke-LocalScript (Join-Path $PSScriptRoot 'Invoke-Local.ps1') @{Action='Stop';Component=$Component;PreserveProcessIds=$PreserveProcessIds;InstanceName=$InstanceName;FrontendPort=$FrontendPort;BackendPort=$BackendPort}
