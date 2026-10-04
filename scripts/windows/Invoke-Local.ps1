[CmdletBinding()]
param(
    [ValidatePattern('^(\d+(,\d+)*)?$')][string]$PreserveProcessIds = '',
    [Parameter(Mandatory)]
    [ValidateSet('Build', 'Start', 'Stop', 'Status', 'Test')]
    [string]$Action,
    [ValidateSet('All', 'Backend', 'Frontend')]
    [string]$Component = 'All',
    [string]$EnvironmentFile = (Join-Path 'D:\project\aienie\aienie-runtime\private\app-secrets' 'ainovel.env'),
    [ValidateRange(30, 900)]
    [int]$StartupTimeoutSeconds = 180,
    [ValidateSet('L1', 'L2')]
    [string]$TestLevel = 'L2',
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,47}$')][string]$InstanceName = 'ainovel',
    [ValidateRange(1024,65535)][int]$FrontendPort = 11040,
    [ValidateRange(1024,65535)][int]$BackendPort = 11041,
    [switch]$AsJson
    ,[switch]$EnableBackendDebug
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'LocalCommand.ps1')

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $PSScriptRoot 'LocalRuntime.psm1') -Force
$stateRoot = Join-Path 'D:\project\aienie\aienie-runtime\local-services\direct-runs\native-runs' $InstanceName
$statePath = Join-Path $stateRoot 'processes.json'
# Windows direct-run data root: logs/records live outside the checkout under
# the shared aienie-runtime local-services tree, never on a drive root.
$directRunRoot = 'D:\project\aienie\aienie-runtime\local-services\direct-runs\ainovel'
$frontendPackageManager = 'pnpm@11.22.0'
$components = @(
    [pscustomobject]@{
        Name = 'Backend'
        Directory = Join-Path $repoRoot 'backend'
        Command = 'mvn.cmd'
        BuildArguments = @('-q', '-DskipTests', 'package')
        TestArguments = @('-q', 'test')
        StartArguments = @('-f', (Join-Path $repoRoot 'backend\pom.xml'), '-q', 'spring-boot:run', ('-Dspring-boot.run.arguments=--server.port=' + $BackendPort))
        Port = $BackendPort
        HealthPath = '/api/actuator/health/readiness'
        HealthKind = 'JsonUp'
        NodeModulesPath = $null
    },
    [pscustomobject]@{
        Name = 'Frontend'
        Directory = Join-Path $repoRoot 'frontend'
        Command = 'corepack.cmd'
        BuildArguments = @($frontendPackageManager, 'install', '--frozen-lockfile')
        TestArguments = @($frontendPackageManager, 'run', 'test', '--maxWorkers=2')
        StartArguments = @($frontendPackageManager, '--dir', (Join-Path $repoRoot 'frontend'), 'run', 'dev', '--host', '127.0.0.1', '--port', [string]$FrontendPort, '--strictPort')
        Port = $FrontendPort
        HealthPath = '/'
        HealthKind = 'Http200'
        NodeModulesPath = (Join-Path $repoRoot 'frontend\node_modules')
    }
)

function Assert-NativePowerShell {
    if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7) {
        throw 'AINovel Windows-native operations require PowerShell 7 or newer.'
    }
    if ($env:OS -ne 'Windows_NT') {
        throw 'AINovel Windows-native operations must run on Windows.'
    }
}

function Get-SelectedComponents {
    if ($Component -eq 'All') {
        return @($components)
    }
    return @($components | Where-Object Name -eq $Component)
}

function Assert-FrontendToolchainVersions {
    param([string]$NodeVersion, [string]$PnpmVersion)
    if ($NodeVersion.Trim() -cne 'v22.23.2') {
        throw "AINovel requires Node.js exactly 22.23.2; found $NodeVersion. Select the pinned Node executable in PATH."
    }
    if ($PnpmVersion.Trim() -cne '11.22.0') {
        throw "AINovel requires pnpm exactly 11.22.0; found $PnpmVersion."
    }
}

function Assert-FrontendToolchain {
    $nodeCommand = Get-NativeCommand -Name 'node.exe'
    $nodeVersion = (& $nodeCommand --version | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to determine the Node.js version.' }
    # Reject Node drift before Corepack can resolve packages or mutate dependencies.
    if ($nodeVersion -cne 'v22.23.2') {
        Assert-FrontendToolchainVersions -NodeVersion $nodeVersion -PnpmVersion ''
    }
    $corepackCommand = Get-NativeCommand -Name 'corepack.cmd'
    $pnpmVersion = (& $corepackCommand $frontendPackageManager --version | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to determine the pinned pnpm version.' }
    Assert-FrontendToolchainVersions -NodeVersion $nodeVersion -PnpmVersion $pnpmVersion
}

function Get-NativeCommand {
    param([Parameter(Mandatory)][string]$Name)

    $command = Get-Command -Name $Name -CommandType Application -ErrorAction Stop | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($command.Path)) {
        return $command.Source
    }
    return $command.Path
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter(Mandatory)][string]$Label
    )

    Push-Location $WorkingDirectory
    try {
        & $Command @Arguments | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Get-NativeEnvironment {
    param([Parameter(Mandatory)][string]$Path)

    $item = Get-Item -LiteralPath (Resolve-AienieLocalPrivateFile -Path $Path -Label 'Environment file') -Force -ErrorAction Stop

    $blocked = @('PATH', 'PATHEXT', 'COMSPEC', 'SYSTEMROOT', 'WINDIR', 'PSMODULEPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_OPTS', 'NODE_OPTIONS')
    $values = @{}
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($item.FullName)) {
        $lineNumber++
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $match = [Regex]::Match($line, '^\s*(?:export\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*=(?<value>.*)$')
        if (-not $match.Success) {
            throw "Environment file has an unsupported line at $lineNumber. Use literal NAME=value entries only."
        }
        $name = $match.Groups['name'].Value
        if ($blocked -contains $name.ToUpperInvariant() -or
            (Test-AienieProtectedProcessVariable -Name $name)) {
            throw "Environment file cannot set protected process variable '$name'."
        }
        if ($values.ContainsKey($name)) {
            throw "Environment file defines '$name' more than once."
        }
        $value = $match.Groups['value'].Value
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

function Get-ChildEnvironment {
    param([Parameter(Mandatory)][hashtable]$ProjectEnvironment)

    $child = @{}
    foreach ($entry in [Environment]::GetEnvironmentVariables('Process').GetEnumerator()) {
        $child[[string]$entry.Key] = [string]$entry.Value
    }
    Assert-AienieLocalOnlyEnvironment -Values $child
    Assert-AienieLocalOnlyEnvironment -Values $ProjectEnvironment
    foreach ($entry in $ProjectEnvironment.GetEnumerator()) {
        $child[[string]$entry.Key] = [string]$entry.Value
    }
    Assert-AienieLocalOnlyEnvironment -Values $child
    Remove-AienieProcessInjectionEnvironment -Values $child
    . (Join-Path $repoRoot 'scripts\config-pair\ConfigurationPair.ps1')
    $yamlValues = Read-ConfigPairValues -ProjectRoot $repoRoot -EnvironmentFile $EnvironmentFile
    foreach ($key in $yamlValues.Keys) { $child[$key] = $yamlValues[$key] }
    if (Test-Path -LiteralPath ($EnvironmentFile + '.application.yml')) {
        $child['AIENIE_APPLICATION_FILE'] = ([Uri][IO.Path]::GetFullPath($EnvironmentFile + '.application.yml')).AbsoluteUri
    }
    $child['AINOVEL_BACKEND_PORT'] = [string]$BackendPort
    $child['SPRING_PROFILES_ACTIVE'] = 'local'
    $child['AIENIE_RUNTIME_PLANE'] = 'windows-local'
    Assert-AienieLocalOnlyEnvironment -Values $child
    return $child
}

function Get-ProcessState {
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    } catch {
        throw "Cannot parse native process state '$statePath': $($_.Exception.Message)"
    }
}

function Test-RecordedProcess {
    param([Parameter(Mandatory)]$Record)

    $spec = $components | Where-Object Name -ceq ([string]$Record.Name) | Select-Object -First 1
    if ($null -eq $spec) { return $false }
    return Test-AienieManagedProcessRecord -Record $Record -ExpectedName $spec.Name `
        -ExpectedWorkingRoot $spec.Directory -ExpectedPorts @($spec.Port)
}

function Save-ProcessState {
    param([Parameter(Mandatory)][object[]]$Records)

    New-Item -ItemType Directory -Path $stateRoot -Force | Out-Null
    [pscustomobject]@{
        product = 'AINovel'
        projectRoot = $repoRoot
        updatedUtc = [DateTime]::UtcNow.ToString('o')
        processes = @($Records)
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $statePath -Encoding utf8NoBOM
}

function Test-LocalTcpPort {
    param([Parameter(Mandatory)][int]$Port)

    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return $task.Wait(1000) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Assert-LocalPortAvailable {
    param([Parameter(Mandatory)][int]$Port)

    if (Test-LocalTcpPort -Port $Port) {
        throw "Port $Port is already listening. Stop the owning process before native startup."
    }
}

function Test-LocalHttpEndpoint {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$Path,
        [ValidateSet('Http200', 'JsonUp')][string]$HealthKind = 'Http200'
    )

    return Invoke-AienieBoundedHttp -Uri ("http://127.0.0.1:{0}{1}" -f $Port, $Path) `
        -TimeoutSeconds 3 -RequireJsonUp:($HealthKind -eq 'JsonUp')
}

function Wait-LocalReadiness {
    param(
        [Parameter(Mandatory)]$Spec,
        [Parameter(Mandatory)]$Process
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        if ($Process.HasExited) {
            throw "Native $($Spec.Name) process exited during startup with code $($Process.ExitCode)."
        }
        if ((Test-LocalTcpPort -Port $Spec.Port) -and (Test-LocalHttpEndpoint -Port $Spec.Port -Path $Spec.HealthPath -HealthKind $Spec.HealthKind)) {
            return
        }
        Start-Sleep -Seconds 1
        $Process.Refresh()
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "Native $($Spec.Name) did not become healthy on port $($Spec.Port) within $StartupTimeoutSeconds seconds."
}

function Start-NativeComponent {
    param(
        [Parameter(Mandatory)]$Spec,
        [Parameter(Mandatory)][hashtable]$ChildEnvironment
    )

    if ($null -ne $Spec.NodeModulesPath -and -not (Test-Path -LiteralPath $Spec.NodeModulesPath -PathType Container)) {
        throw "Native $($Spec.Name) dependencies are missing. Run Build-Local.ps1 before Start-Local.ps1."
    }
    Assert-LocalPortAvailable -Port $Spec.Port
    $logRoot = Join-Path $stateRoot 'logs'
    New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
    $command = Get-NativeCommand -Name $Spec.Command
    $process = Start-Process -FilePath $command -ArgumentList $Spec.StartArguments -WorkingDirectory $Spec.Directory -Environment $ChildEnvironment -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logRoot "$($Spec.Name.ToLowerInvariant()).stdout.log") -RedirectStandardError (Join-Path $logRoot "$($Spec.Name.ToLowerInvariant()).stderr.log") -PassThru
    $launchedStartUtc = try { $process.StartTime.ToUniversalTime() } catch { $null }
    try {
        $record = New-AienieRootProcessRecord -Name $Spec.Name -Process $process -WorkingRoot $Spec.Directory
        $record | Add-Member -NotePropertyName Port -NotePropertyValue $Spec.Port
        $record | Add-Member -NotePropertyName HealthPath -NotePropertyValue $Spec.HealthPath
        return $record
    } catch {
        $current = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
        if ($null -ne $current -and $null -ne $launchedStartUtc -and
            [Math]::Abs(($current.StartTime.ToUniversalTime() - $launchedStartUtc).TotalMilliseconds) -le 1000) {
            & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID ([string]$process.Id) /T /F 2>$null | Out-Null
        }
        $process.Dispose()
        throw
    }
}

function Resolve-NativeProcessRecord {
    param(
        [Parameter(Mandatory)]$Record,
        [Parameter(Mandatory)]$Spec
    )

    return Complete-AienieManagedProcessRecord -Record $Record -Ports @($Spec.Port)
}

function Stop-NativeRecord {
    param([Parameter(Mandatory)]$Record)

    $spec = $components | Where-Object Name -ceq ([string]$Record.Name) | Select-Object -First 1
    if ($null -eq $spec) { return $false }
    if (@($Record.Listeners).Count -eq 0 -and $null -ne $Record.PSObject.Properties['Process']) {
        Stop-AienieRootProcessRecord -Record $Record
        return $true
    }
    Stop-AienieManagedProcessRecord -Record $Record -ExpectedName $spec.Name `
        -ExpectedWorkingRoot $spec.Directory -ExpectedPorts @($spec.Port)
    return $true
}

Assert-NativePowerShell
if ($Action -in @('Build', 'Test', 'Start') -and $Component -in @('All', 'Frontend')) {
    Assert-FrontendToolchain
}

switch ($Action) {
    'Build' {
        foreach ($spec in Get-SelectedComponents) {
            $command = Get-NativeCommand -Name $spec.Command
            if ($spec.Name -eq 'Frontend') {
                Invoke-NativeCommand -Command $command -Arguments $spec.BuildArguments -WorkingDirectory $spec.Directory -Label 'Frontend dependency installation'
                Invoke-NativeCommand -Command $command -Arguments @($frontendPackageManager, 'run', 'build') -WorkingDirectory $spec.Directory -Label 'Frontend build'
            } else {
                Invoke-NativeCommand -Command $command -Arguments $spec.BuildArguments -WorkingDirectory $spec.Directory -Label 'Backend build'
            }
        }
    }
    'Test' {
        foreach ($spec in Get-SelectedComponents) {
            $command = Get-NativeCommand -Name $spec.Command
            if ($spec.Name -eq 'Frontend') {
                Invoke-NativeCommand -Command $command -Arguments $spec.BuildArguments -WorkingDirectory $spec.Directory -Label 'Frontend dependency installation'
                Invoke-NativeCommand -Command $command -Arguments @($frontendPackageManager, 'run', 'lint') -WorkingDirectory $spec.Directory -Label 'Frontend lint'
                Invoke-NativeCommand -Command $command -Arguments @($frontendPackageManager, 'run', 'typecheck') -WorkingDirectory $spec.Directory -Label 'Frontend typecheck'
                Invoke-NativeCommand -Command $command -Arguments @($frontendPackageManager, 'run', 'build') -WorkingDirectory $spec.Directory -Label 'Frontend production build'
                if ($TestLevel -eq 'L2') {
                    Invoke-NativeCommand -Command $command -Arguments $spec.TestArguments -WorkingDirectory $spec.Directory -Label 'Frontend L2 tests'
                }
            } else {
                Invoke-NativeCommand -Command $command -Arguments @('-q', '-DskipTests', 'package') -WorkingDirectory $spec.Directory -Label 'Backend compile'
                if ($TestLevel -eq 'L2') {
                    Invoke-NativeCommand -Command $command -Arguments $spec.TestArguments -WorkingDirectory $spec.Directory -Label 'Backend L2 tests'
                }
            }
        }
    }
    'Start' {
        $projectEnvironment = Get-NativeEnvironment -Path $EnvironmentFile
        $childEnvironment = Get-ChildEnvironment -ProjectEnvironment $projectEnvironment
        foreach ($directory in @($childEnvironment['APP_LOG_DIR'], $childEnvironment['APP_RECORD_DIR'])) {
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
        }
        $state = Get-ProcessState
        $records = @()
        if ($null -ne $state) {
            if (-not [string]::Equals([string]$state.projectRoot, $repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Native process state belongs to another checkout: $($state.projectRoot)"
            }
            $records = @($state.processes | Where-Object { Test-RecordedProcess -Record $_ })
        }
        $selected = Get-SelectedComponents
        $pending = @()
        foreach ($spec in $selected) {
            $live = @($records | Where-Object Name -eq $spec.Name)
            if ($live.Count -gt 0) {
                if (-not (Test-LocalHttpEndpoint -Port $spec.Port -Path $spec.HealthPath -HealthKind $spec.HealthKind)) { throw "Existing $($spec.Name) is unhealthy." }
                if ($EnableBackendDebug -and $spec.Name -eq 'Backend') { Assert-LocalDebugListener $spec.Port 51041 }
                $livePid = try { [string]$live[0].RootProcess.ProcessId } catch { 'unknown' }
                Write-Output "AINovel $($spec.Name) already running (PID $livePid); skipping start."
            } else {
                $pending += $spec
            }
        }
        $started = @()
        try {
        foreach ($spec in $pending) {
                if ($spec.Name -eq 'Backend') {
                    # The SSO HttpClient uses JSSE (separate from gRPC's explicit
                    # CA bundle). Honor Windows' trusted roots for local HTTPS.
                    $backendJvmArguments = @('-Djavax.net.ssl.trustStoreType=Windows-ROOT')
                    if ($EnableBackendDebug) {
                        if (Test-LocalTcpPort 51041) { throw 'Debug port 51041 is already listening. Stop its owner explicitly before starting.' }
                        $backendJvmArguments += '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:51041'
                    }
                    $spec.StartArguments = @($spec.StartArguments) + @('-Dspring-boot.run.jvmArguments=' + ($backendJvmArguments -join ' '))
                }
                $record = Start-NativeComponent -Spec $spec -ChildEnvironment $childEnvironment
                $started += $record
                Wait-LocalReadiness -Spec $spec -Process $record.process
                $resolved = Resolve-NativeProcessRecord -Record $record -Spec $spec
                $started[$started.Count - 1] = $resolved
                $record = $resolved
                $checkpoint = @($records) + @($started | ForEach-Object { ConvertTo-AieniePersistedProcessRecord -Record $_ })
                Save-ProcessState -Records $checkpoint
            }
            if ($started.Count -gt 0) {
                $persisted = @($records) + @($started | ForEach-Object { ConvertTo-AieniePersistedProcessRecord -Record $_ })
                Save-ProcessState -Records $persisted
            }
            if ($InstanceName -eq 'ainovel' -and $FrontendPort -eq 11040 -and $Component -in @('All','Frontend')) { Assert-LocalEndpoint 'https://localainovel.testhut.top/' }
            & $PSCommandPath -Action Status -InstanceName $InstanceName -FrontendPort $FrontendPort -BackendPort $BackendPort
        } catch {
            foreach ($record in $started) {
                try {
                    Stop-NativeRecord -Record $record
                } catch {
                    Write-Warning "Could not clean up native $($record.name): $($_.Exception.Message)"
                }
            }
            throw
        } finally {
            foreach ($record in $started) {
                $record.process.Dispose()
            }
        }
    }
    'Stop' {
        $state = Get-ProcessState
        if ($null -eq $state) {
            Write-Output 'No recorded AINovel Windows-native processes are active.'
            return
        }
        if (-not [string]::Equals([string]$state.projectRoot, $repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Native process state belongs to another checkout: $($state.projectRoot)"
        }
        $remaining = @()
        foreach ($record in @($state.processes)) {
            if ([string]$record.RootProcess.ProcessId -in ($PreserveProcessIds -split ',')) { $remaining += $record; continue }
            if ($Component -ne 'All' -and $record.Name -ne $Component) {
                $remaining += $record
                continue
            }
            if (Stop-NativeRecord -Record $record) {
                Write-Output "Stopped AINovel $($record.Name) process $($record.RootProcess.ProcessId)."
            }
        }
        if ($remaining.Count -eq 0) {
            Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
        } else {
            Save-ProcessState -Records $remaining
        }
    }
    'Status' {
        $state = Get-ProcessState
        $records = if ($null -eq $state) { @() } else { @($state.processes) }
        $result = foreach ($spec in $components) {
            $record = @($records | Where-Object Name -eq $spec.Name | Select-Object -First 1)
            $isLive = $record.Count -eq 1 -and (Test-RecordedProcess -Record $record[0])
            [pscustomobject]@{
                product = 'AINovel'
                component = $spec.Name
                port = $spec.Port
                processRecorded = $record.Count -eq 1
                processLive = $isLive
                tcpListening = Test-LocalTcpPort -Port $spec.Port
                healthy = Test-LocalHttpEndpoint -Port $spec.Port -Path $spec.HealthPath -HealthKind $spec.HealthKind
            }
        }
        if ($AsJson) {
            $result | ConvertTo-Json -Depth 4
        } else {
            $result | Format-Table -AutoSize
        }
    }
}
