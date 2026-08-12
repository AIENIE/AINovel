[CmdletBinding()]
param()

Set-StrictMode -Version Latest

$modulePath = Join-Path $PSScriptRoot 'Native-Localbase.psm1'
Import-Module -Name $modulePath -Force

$projectRoot = Get-NativeProjectRoot
$stateDirectory = Get-NativeStateDirectory -ProjectRoot $projectRoot

Stop-NativeProcess -Name 'frontend' -StateDirectory $stateDirectory -ProjectRoot $projectRoot
Stop-NativeProcess -Name 'backend' -StateDirectory $stateDirectory -ProjectRoot $projectRoot

Write-Host 'AINovel native processes stopped. The localbase Windows hosts mapping was left in place.'
