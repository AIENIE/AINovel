[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Build', 'Start', 'Stop', 'Status', 'Test')]
    [string]$Action,
    [ValidateSet('All', 'Backend', 'Frontend')]
    [string]$Component = 'All',
    [string]$EnvironmentFile = (Join-Path $(if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }) 'Aienie\secrets\ainovel.env'),
    [ValidateRange(30, 900)]
    [int]$StartupTimeoutSeconds = 180,
    [ValidateSet('L1', 'L2')]
    [string]$TestLevel = 'L2',
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $PSScriptRoot 'LocalRuntime.psm1') -Force
$stateRoot = if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    Join-Path $env:TEMP 'Aienie\native-runs\ainovel'
} else {
    Join-Path $env:LOCALAPPDATA 'Aienie\native-runs\ainovel'
}
$statePath = Join-Path $stateRoot 'processes.json'
$frontendPackageManager = 'pnpm@11.22.0'
$components = @(
    [pscustomobject]@{
        Name = 'Backend'
        Directory = Join-Path $repoRoot 'backend'
        Command = 'mvn.cmd'
        BuildArguments = @('-q', '-DskipTests', 'package')
        TestArguments = @('-q', 'test')
        StartArguments = @('-f', (Join-Path $repoRoot 'backend\pom.xml'), '-q', 'spring-boot:run')
        Port = 11041
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
        StartArguments = @($frontendPackageManager, '--dir', (Join-Path $repoRoot 'frontend'), 'run', 'dev', '--', '--host', '127.0.0.1', '--port', '11040', '--strictPort')
        Port = 11040
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
    $child['ENV'] = 'local'
    $child['APP_ENV'] = 'local'
    $child['SPRING_PROFILES_ACTIVE'] = 'local'
    $child['AIENIE_RUNTIME_PLANE'] = 'windows-local'
    $child['AUTH_MODE'] = 'password'
    $child['SERVER_ADDRESS'] = '127.0.0.1'
    $child['PORT'] = '11041'
    $child['DB_URL'] = 'jdbc:mysql://localbase.testhut.top:23306/ainovel?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
    $child['MYSQL_HOST'] = 'localbase.testhut.top'
    $child['MYSQL_PORT'] = '23306'
    $child['REDIS_HOST'] = 'localbase.testhut.top'
    $child['REDIS_PORT'] = '26379'
    $child['REDIS_SSL_ENABLED'] = 'false'
    $child['QDRANT_HOST'] = 'http://localbase.testhut.top'
    $child['QDRANT_PORT'] = '26333'
    $child['AI_GRPC_ADDR'] = 'static://localaiservice.testhut.top:12011'
    $child['USER_GRPC_ADDR'] = 'static://localuserservice.testhut.top:12001'
    $child['PAY_GRPC_ADDR'] = 'static://localpayservice.testhut.top:12021'
    $child['USER_HTTP_ADDR'] = 'https://localuserservice.testhut.top'
    $child['SSO_CALLBACK_ORIGIN'] = 'https://localainovel.testhut.top'
    $child['VITE_SSO_ENTRY_BASE_URL'] = 'https://localuserservice.testhut.top'
    $child['EXTERNAL_GRPC_TRUST_CERT_COLLECTION'] = ''
    $child['EXTERNAL_GRPC_TLS_ENABLED'] = 'true'
    $child['EXTERNAL_GRPC_PLAINTEXT_ENABLED'] = 'false'
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
                $resolved = Resolve-NativeProcessRecord -Record $record -Spec $spec
                $started[$started.Count - 1] = $resolved
                $record = $resolved
                $checkpoint = @($records) + @($started | ForEach-Object { ConvertTo-AieniePersistedProcessRecord -Record $_ })
                Save-ProcessState -Records $checkpoint
            }
            $persisted = @($records) + @($started | ForEach-Object { ConvertTo-AieniePersistedProcessRecord -Record $_ })
            Save-ProcessState -Records $persisted
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
