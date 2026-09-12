[CmdletBinding()]
param([ValidateSet('All','Backend','Frontend')][string]$Component='All', [ValidatePattern('^(\d+(,\d+)*)?$')][string]$PreserveProcessIds='')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'LocalCommand.ps1')
Invoke-LocalScript (Join-Path $PSScriptRoot 'Invoke-Local.ps1') @{Action='Stop';Component=$Component;PreserveProcessIds=$PreserveProcessIds}
