[CmdletBinding()]
param(
    [string]$EnvironmentFile = (Join-Path $(if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }) 'Aienie\secrets\ainovel.env'),
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [ValidateRange(30, 900)][int]$StartupTimeoutSeconds = 180,
    [switch]$EnableBackendDebug,
    # -NoBrowser skips opening the project homepage after a successful start.
    [switch]$NoBrowser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Invoke-Local requires PowerShell 7 Core; transparently re-enter under pwsh so
# this script also works when launched from Windows PowerShell 5.1.
if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7) {
    # Windows PowerShell 5.1 predates the Platform property and runs on Windows only.
    $platform = if ($PSVersionTable.ContainsKey('Platform')) { [string]$PSVersionTable.Platform } else { 'Win32NT' }
    if ($platform -cne 'Win32NT') {
        throw 'This entrypoint supports native Windows only.'
    }
    $pwsh = Get-Command -Name 'pwsh.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $pwsh) {
        throw 'PowerShell 7 (pwsh) is required. Install it from https://aka.ms/powershell and re-run this script.'
    }
    $forward = @()
    foreach ($entry in $PSBoundParameters.GetEnumerator()) {
        if ($entry.Value -is [System.Management.Automation.SwitchParameter]) {
            if ($entry.Value.IsPresent) { $forward += "-$($entry.Key)" }
        } else {
            $forward += "-$($entry.Key)"
            $forward += [string]$entry.Value
        }
    }
    & $pwsh.Source -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $PSCommandPath @forward
    exit $LASTEXITCODE
}

& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action Start -Component $Component -EnvironmentFile $EnvironmentFile -StartupTimeoutSeconds $StartupTimeoutSeconds -EnableBackendDebug:$EnableBackendDebug

if (-not $NoBrowser -and $Component -eq 'All') {
    $homepage = 'https://localainovel.testhut.top/'
    Write-Output "Opening the project homepage in the default browser: $homepage"
    Start-Process -FilePath $homepage
}
