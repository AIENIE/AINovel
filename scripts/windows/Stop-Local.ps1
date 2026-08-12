[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $PSScriptRoot 'AINovelNativeRun.psm1') -Force
Assert-AINovelWindowsHost

$failures = [Collections.Generic.List[object]]::new()
foreach ($name in 'frontend', 'backend') {
    try {
        Stop-AINovelRecordedProcess -Name $name -ProjectRoot $projectRoot
    } catch {
        $failures.Add($_)
    }
}
if ($failures.Count -gt 0) {
    $details = @($failures | ForEach-Object { $_.Exception.Message }) -join '; '
    throw "Failed to stop $($failures.Count) AINovel process(es): $details"
}
Write-Output 'AINovel Windows local processes are stopped.'
