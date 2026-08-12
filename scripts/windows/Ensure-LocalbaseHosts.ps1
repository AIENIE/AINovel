[CmdletBinding(SupportsShouldProcess = $true)]
param()

Set-StrictMode -Version Latest

$modulePath = Join-Path $PSScriptRoot 'Native-Localbase.psm1'
Import-Module -Name $modulePath -Force

if ($WhatIfPreference) {
    Ensure-LocalbaseHostsMapping -WhatIf
}
else {
    Ensure-LocalbaseHostsMapping
}
