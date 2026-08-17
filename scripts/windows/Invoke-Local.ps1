[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Build', 'Start', 'Stop', 'Status', 'Test')]
    [string]$Action,
    [ValidateSet('All', 'Backend', 'Frontend')]
    [string]$Component = 'All',
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\..\env.txt'),
    [ValidateRange(30, 900)]
    [int]$StartupTimeoutSeconds = 180,
    [ValidateSet('L1', 'L2')]
    [string]$TestLevel = 'L2',
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$stateRoot = if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    Join-Path $env:TEMP 'Aienie\native-runs\ainovel'
} else {
    Join-Path $env:LOCALAPPDATA 'Aienie\native-runs\ainovel'
}
$statePath = Join-Path $stateRoot 'processes.json'
$operationalStateWriter = if ([string]::IsNullOrWhiteSpace($env:AIENIE_PRODUCT_STATE_WRITER)) {
    Join-Path $repoRoot '..\..\aienie-runtime\infrastructure\monitoring\windows-agent\Set-AienieProductOperationalState.ps1'
} else {
    $env:AIENIE_PRODUCT_STATE_WRITER
}

$components = @(
    [pscustomobject]@{
        Name = 'Backend'
        Directory = Join-Path $repoRoot 'backend'
        Command = 'mvn.cmd'
        BuildArguments = @('-q', '-DskipTests', 'package')
        TestArguments = @('-q', 'test')
        StartArguments = @('-q', 'spring-boot:run')
        Port = 11041
        HealthPath = '/actuator/health'
        NodeModulesPath = $null
    },
    [pscustomobject]@{
        Name = 'Frontend'
        Directory = Join-Path $repoRoot 'frontend'
        Command = 'npm.cmd'
        BuildArguments = @('ci', '--no-audit', '--no-fund')
        TestArguments = @('run', 'test')
        StartArguments = @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '11040', '--strictPort')
        Port = 11040
        HealthPath = '/'
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

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Environment file was not found: $Path"
    }
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Environment file must not be a reparse point: $Path"
    }

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
        if ($blocked -contains $name.ToUpperInvariant() -or $name -like 'SPRING_CONFIG_*') {
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
    foreach ($entry in $ProjectEnvironment.GetEnumerator()) {
        $child[[string]$entry.Key] = [string]$entry.Value
    }
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

    try {
        $process = Get-Process -Id ([int]$Record.Pid) -ErrorAction Stop
        $expected = [DateTime]::Parse([string]$Record.ProcessStartTimeUtc).ToUniversalTime()
        return [Math]::Abs(($process.StartTime.ToUniversalTime() - $expected).TotalSeconds) -le 2
    } catch {
        return $false
    }
}

function Save-ProcessState {
    param([Parameter(Mandatory)][object[]]$Records)

    New-Item -ItemType Directory -Path $stateRoot -Force | Out-Null
    [pscustomobject]@{
        product = 'AINovel'
        projectRoot = $repoRoot
        updatedUtc = [DateTime]::UtcNow.ToString('o')
        processes = @($Records)
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding utf8NoBOM
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
        [Parameter(Mandatory)][string]$Path
    )

    try {
        $response = Invoke-WebRequest -Uri ("http://127.0.0.1:{0}{1}" -f $Port, $Path) -TimeoutSec 3 -SkipHttpErrorCheck
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
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
        if ((Test-LocalTcpPort -Port $Spec.Port) -and (Test-LocalHttpEndpoint -Port $Spec.Port -Path $Spec.HealthPath)) {
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
    $process = Start-Process -FilePath $command -ArgumentList $Spec.StartArguments -WorkingDirectory $Spec.Directory -Environment $ChildEnvironment -RedirectStandardOutput (Join-Path $logRoot "$($Spec.Name.ToLowerInvariant()).stdout.log") -RedirectStandardError (Join-Path $logRoot "$($Spec.Name.ToLowerInvariant()).stderr.log") -PassThru
    $processStart = (Get-Process -Id $process.Id -ErrorAction Stop).StartTime.ToUniversalTime().ToString('o')
    return [pscustomobject]@{
        name = $Spec.Name
        pid = $process.Id
        processStartTimeUtc = $processStart
        port = $Spec.Port
        healthPath = $Spec.HealthPath
        process = $process
    }
}

function Stop-NativeRecord {
    param([Parameter(Mandatory)]$Record)

    if (-not (Test-RecordedProcess -Record $Record)) {
        return $false
    }
    & taskkill.exe /PID ([string]$Record.Pid) /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to stop native $($Record.Name) process $($Record.Pid) (taskkill exit $LASTEXITCODE)."
    }
    return $true
}

function Publish-OperationalState {
    if (-not (Test-Path -LiteralPath $operationalStateWriter -PathType Leaf)) {
        Write-Verbose "Operational-state writer is unavailable: $operationalStateWriter"
        return
    }
    $state = Get-ProcessState
    $records = if ($null -eq $state) { @() } else { @($state.processes) }
    $live = @($records | Where-Object { Test-RecordedProcess -Record $_ })
    $healthyComponents = @($components | Where-Object {
            (Test-LocalTcpPort -Port $_.Port) -and (Test-LocalHttpEndpoint -Port $_.Port -Path $_.HealthPath)
        })
    $desiredState = if ($live.Count -gt 0) { 'running' } else { 'stopped' }
    $health = if ($healthyComponents.Count -eq $components.Count) {
        'healthy'
    } elseif ($live.Count -gt 0) {
        'unhealthy'
    } else {
        'unknown'
    }
    & $operationalStateWriter -Component 'ai-novel' -DesiredState $desiredState -Health $health
    if ($LASTEXITCODE -ne 0) {
        throw "Operational-state writer failed with exit code $LASTEXITCODE."
    }
}

Assert-NativePowerShell

switch ($Action) {
    'Build' {
        foreach ($spec in Get-SelectedComponents) {
            $command = Get-NativeCommand -Name $spec.Command
            if ($spec.Name -eq 'Frontend') {
                Invoke-NativeCommand -Command $command -Arguments $spec.BuildArguments -WorkingDirectory $spec.Directory -Label 'Frontend dependency installation'
                Invoke-NativeCommand -Command $command -Arguments @('run', 'build') -WorkingDirectory $spec.Directory -Label 'Frontend build'
            } else {
                Invoke-NativeCommand -Command $command -Arguments $spec.BuildArguments -WorkingDirectory $spec.Directory -Label 'Backend build'
            }
        }
    }
    'Test' {
        foreach ($spec in Get-SelectedComponents) {
            $command = Get-NativeCommand -Name $spec.Command
            if ($TestLevel -eq 'L1') {
                $arguments = if ($spec.Name -eq 'Frontend') { @('run', 'build') } else { @('-q', '-DskipTests', 'package') }
                Invoke-NativeCommand -Command $command -Arguments $arguments -WorkingDirectory $spec.Directory -Label "$($spec.Name) L1 verification"
            } else {
                Invoke-NativeCommand -Command $command -Arguments $spec.TestArguments -WorkingDirectory $spec.Directory -Label "$($spec.Name) L2 tests"
            }
        }
        Publish-OperationalState
    }
    'Start' {
        $projectEnvironment = Get-NativeEnvironment -Path $EnvironmentFile
        $childEnvironment = Get-ChildEnvironment -ProjectEnvironment $projectEnvironment
        $state = Get-ProcessState
        $records = @()
        if ($null -ne $state) {
            if (-not [string]::Equals([string]$state.projectRoot, $repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Native process state belongs to another checkout: $($state.projectRoot)"
            }
            $records = @($state.processes | Where-Object { Test-RecordedProcess -Record $_ })
        }
        $selected = Get-SelectedComponents
        foreach ($spec in $selected) {
            if (@($records | Where-Object Name -eq $spec.Name).Count -gt 0) {
                throw "Native $($spec.Name) is already running. Use Get-LocalStatus.ps1 or Stop-Local.ps1."
            }
        }
        $started = @()
        try {
            foreach ($spec in $selected) {
                $record = Start-NativeComponent -Spec $spec -ChildEnvironment $childEnvironment
                $started += $record
                Wait-LocalReadiness -Spec $spec -Process $record.process
            }
            $persisted = @($records) + @($started | ForEach-Object {
                    [pscustomobject]@{
                        Name = $_.name
                        Pid = $_.pid
                        ProcessStartTimeUtc = $_.processStartTimeUtc
                        Port = $_.port
                        HealthPath = $_.healthPath
                    }
                })
            Save-ProcessState -Records $persisted
            Publish-OperationalState
            & $PSCommandPath -Action Status
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
            if ($Component -ne 'All' -and $record.Name -ne $Component) {
                $remaining += $record
                continue
            }
            if (Stop-NativeRecord -Record $record) {
                Write-Output "Stopped AINovel $($record.Name) process $($record.Pid)."
            }
        }
        if ($remaining.Count -eq 0) {
            Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
        } else {
            Save-ProcessState -Records $remaining
        }
        Publish-OperationalState
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
                healthy = Test-LocalHttpEndpoint -Port $spec.Port -Path $spec.HealthPath
            }
        }
        Publish-OperationalState
        if ($AsJson) {
            $result | ConvertTo-Json -Depth 4
        } else {
            $result | Format-Table -AutoSize
        }
    }
}
