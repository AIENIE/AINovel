Set-StrictMode -Version Latest

$script:LocalbaseHost = 'localbase.testhut.top'
$script:LocalbaseAddress = '172.20.0.2'
$script:HostsBeginMarker = '# BEGIN AIENIE LOCALBASE WSL - managed'
$script:HostsEndMarker = '# END AIENIE LOCALBASE WSL - managed'

function Get-NativeProjectRoot {
    [CmdletBinding()]
    param()

    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
}

function Test-NativeAdministrator {
    [CmdletBinding()]
    param()

    if ($env:OS -ne 'Windows_NT') {
        return $false
    }

    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-NativeHostsPath {
    [CmdletBinding()]
    param()

    if ($env:OS -ne 'Windows_NT') {
        throw 'The localbase hosts mapping is supported only on Windows.'
    }

    $systemDirectory = [Environment]::GetFolderPath([Environment+SpecialFolder]::System)
    return Join-Path $systemDirectory 'drivers\etc\hosts'
}

function Remove-LocalbaseAliasFromHostsLine {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Line
    )

    if ([string]::IsNullOrWhiteSpace($Line) -or $Line.TrimStart().StartsWith('#')) {
        return $Line
    }

    $commentIndex = $Line.IndexOf('#')
    $addressPart = $Line
    $comment = ''
    if ($commentIndex -ge 0) {
        $addressPart = $Line.Substring(0, $commentIndex)
        $comment = $Line.Substring($commentIndex).TrimStart()
    }

    $tokens = @($addressPart -split '\s+' | Where-Object { $_ -ne '' })
    if ($tokens.Count -lt 2) {
        return $Line
    }

    $aliases = New-Object System.Collections.Generic.List[string]
    $found = $false
    for ($index = 1; $index -lt $tokens.Count; $index++) {
        if ([string]::Equals($tokens[$index], $script:LocalbaseHost, [StringComparison]::OrdinalIgnoreCase)) {
            $found = $true
            continue
        }
        [void]$aliases.Add($tokens[$index])
    }

    if (-not $found) {
        return $Line
    }

    if ($aliases.Count -eq 0) {
        return $null
    }

    $rewritten = $tokens[0] + "`t" + ($aliases -join ' ')
    if (-not [string]::IsNullOrWhiteSpace($comment)) {
        $rewritten += ' ' + $comment
    }
    return $rewritten
}

function Ensure-LocalbaseHostsMapping {
    [CmdletBinding(SupportsShouldProcess = $true)]
    param()

    if (-not (Test-NativeAdministrator)) {
        throw 'Run this script from an elevated Administrator PowerShell session to update the Windows hosts file.'
    }

    $hostsPath = Get-NativeHostsPath
    if (-not (Test-Path -LiteralPath $hostsPath -PathType Leaf)) {
        throw "Windows hosts file was not found at '$hostsPath'."
    }

    $sourceText = [IO.File]::ReadAllText($hostsPath)
    $sourceLines = @([IO.File]::ReadAllLines($hostsPath))
    $beginIndexes = New-Object System.Collections.Generic.List[int]
    $endIndexes = New-Object System.Collections.Generic.List[int]
    for ($index = 0; $index -lt $sourceLines.Count; $index++) {
        if ($sourceLines[$index].Trim() -eq $script:HostsBeginMarker) {
            [void]$beginIndexes.Add($index)
        }
        if ($sourceLines[$index].Trim() -eq $script:HostsEndMarker) {
            [void]$endIndexes.Add($index)
        }
    }

    if ($beginIndexes.Count -ne $endIndexes.Count -or $beginIndexes.Count -gt 1) {
        throw "The hosts file has malformed '$script:HostsBeginMarker' markers. Resolve the markers manually before retrying."
    }
    if ($beginIndexes.Count -eq 1 -and $beginIndexes[0] -gt $endIndexes[0]) {
        throw "The hosts file has an invalid localbase managed marker order. Resolve it manually before retrying."
    }

    $withoutManagedBlock = New-Object System.Collections.Generic.List[string]
    for ($index = 0; $index -lt $sourceLines.Count; $index++) {
        if ($beginIndexes.Count -eq 1 -and $index -ge $beginIndexes[0] -and $index -le $endIndexes[0]) {
            continue
        }
        [void]$withoutManagedBlock.Add($sourceLines[$index])
    }

    $resultLines = New-Object System.Collections.Generic.List[string]
    foreach ($line in $withoutManagedBlock) {
        $rewritten = Remove-LocalbaseAliasFromHostsLine -Line $line
        if ($null -ne $rewritten) {
            [void]$resultLines.Add($rewritten)
        }
    }
    if ($resultLines.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace($resultLines[$resultLines.Count - 1])) {
        [void]$resultLines.Add('')
    }
    [void]$resultLines.Add($script:HostsBeginMarker)
    [void]$resultLines.Add("$script:LocalbaseAddress $script:LocalbaseHost")
    [void]$resultLines.Add($script:HostsEndMarker)

    $resultText = [string]::Join([Environment]::NewLine, [string[]]$resultLines) + [Environment]::NewLine
    if ($sourceText -eq $resultText) {
        Write-Host "Windows hosts already maps $script:LocalbaseHost to $script:LocalbaseAddress."
        return
    }

    if ($PSCmdlet.ShouldProcess($hostsPath, "set $script:LocalbaseHost to $script:LocalbaseAddress")) {
        $directory = [IO.Path]::GetDirectoryName($hostsPath)
        $temporaryPath = Join-Path $directory ('.hosts.aienie-localbase.' + [Guid]::NewGuid().ToString('N') + '.tmp')
        try {
            $encoding = New-Object System.Text.UTF8Encoding($false)
            $currentText = [IO.File]::ReadAllText($hostsPath)
            if ($currentText -ne $sourceText) {
                throw "The Windows hosts file changed while this update was being prepared. Retry so no concurrent edit is overwritten."
            }
            [IO.File]::WriteAllText($temporaryPath, $resultText, $encoding)
            [IO.File]::Replace($temporaryPath, $hostsPath, $null, $true)
        }
        finally {
            if (Test-Path -LiteralPath $temporaryPath) {
                Remove-Item -LiteralPath $temporaryPath -Force
            }
        }

        try {
            $clearDns = Get-Command -Name Clear-DnsClientCache -ErrorAction SilentlyContinue
            if ($null -ne $clearDns) {
                Clear-DnsClientCache | Out-Null
            }
            else {
                & ipconfig.exe /flushdns | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    throw "ipconfig.exe exited with code $LASTEXITCODE."
                }
            }
        }
        catch {
            Write-Warning "Hosts file was updated, but the DNS cache could not be flushed: $($_.Exception.Message)"
        }
        Write-Host "Windows hosts now maps $script:LocalbaseHost to $script:LocalbaseAddress."
    }
}

function Assert-LocalbaseResolution {
    [CmdletBinding()]
    param()

    $addresses = @(
        [Net.Dns]::GetHostAddresses($script:LocalbaseHost) |
            Where-Object { $_.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork } |
            ForEach-Object { $_.IPAddressToString } |
            Sort-Object -Unique
    )
    if ($addresses.Count -ne 1 -or $addresses[0] -ne $script:LocalbaseAddress) {
        $actual = if ($addresses.Count -gt 0) { $addresses -join ', ' } else { 'no IPv4 address' }
        throw "Expected $script:LocalbaseHost to resolve only to $script:LocalbaseAddress; got $actual. Run .\scripts\windows\Ensure-LocalbaseHosts.ps1 from an elevated PowerShell session."
    }
}

function Test-NativeTcpEndpoint {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$HostName,
        [Parameter(Mandatory)]
        [int]$Port,
        [int]$TimeoutMilliseconds = 3000
    )

    $client = New-Object Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        if (-not $task.Wait($TimeoutMilliseconds)) {
            return $false
        }
        $task.GetAwaiter().GetResult()
        return $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Assert-NativePortAvailable {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    $listeners = @(
        [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() |
            Where-Object { $_.Port -eq $Port }
    )
    if ($listeners.Count -gt 0) {
        $endpoints = $listeners | ForEach-Object { "$($_.Address):$($_.Port)" }
        throw "TCP port $Port is already listening on $($endpoints -join ', '). Stop the conflicting process before native startup."
    }
}

function Get-NativeListeningProcessIds {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [ValidateRange(1, 65535)]
        [int]$Port
    )

    if ($null -eq (Get-Command -Name Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
        return @()
    }
    try {
        return @(
            Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop |
                Select-Object -ExpandProperty OwningProcess -Unique
        )
    }
    catch {
        return @()
    }
}

function Test-NativeProcessTreeContains {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [int]$RootProcessId,
        [Parameter(Mandatory)]
        [int]$CandidateProcessId
    )

    if ($RootProcessId -eq $CandidateProcessId) {
        return $true
    }
    if ($null -eq (Get-Command -Name Get-CimInstance -ErrorAction SilentlyContinue)) {
        return $null
    }

    $seen = New-Object System.Collections.Generic.HashSet[int]
    $currentProcessId = $CandidateProcessId
    try {
        while ($currentProcessId -gt 0 -and $seen.Add($currentProcessId)) {
            $processInfo = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $currentProcessId" -ErrorAction Stop
            if ($null -eq $processInfo) {
                return $false
            }
            $currentProcessId = [int]$processInfo.ParentProcessId
            if ($currentProcessId -eq $RootProcessId) {
                return $true
            }
        }
        return $false
    }
    catch {
        return $null
    }
}

function Assert-LocalbasePorts {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [int[]]$Ports
    )

    Assert-LocalbaseResolution
    $unreachable = New-Object System.Collections.Generic.List[int]
    foreach ($port in $Ports) {
        if (-not (Test-NativeTcpEndpoint -HostName $script:LocalbaseHost -Port $port)) {
            [void]$unreachable.Add($port)
        }
    }
    if ($unreachable.Count -gt 0) {
        throw "Cannot reach $script:LocalbaseHost on TCP port(s): $($unreachable -join ', '). Start or repair the provider on aienie-wsl, then retry."
    }
}

function Clear-NativeProcessEnvironment {
    [CmdletBinding()]
    param(
        [string[]]$Names = @()
    )

    foreach ($name in $Names) {
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            # Setting a process variable to $null can leave an empty value in
            # Windows PowerShell. Remove the Env: provider entry so Spring and
            # other child processes observe the variable as genuinely absent.
            $environmentPath = "Env:$name"
            if (Test-Path -LiteralPath $environmentPath) {
                Remove-Item -LiteralPath $environmentPath -Force -ErrorAction Stop
            }
        }
    }
}

function Import-LiteralEnvironmentFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Path,
        [string[]]$ClearNames = @(),
        [string[]]$AllowedNames = @()
    )

    Clear-NativeProcessEnvironment -Names $ClearNames
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Environment file '$Path' was not found. Copy the project's documented template and fill in local values."
    }

    $item = Get-Item -LiteralPath $Path -Force
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Refusing to import environment file '$Path' because it is a reparse point."
    }

    $blockedNames = @('PATH', 'PATHEXT', 'COMSPEC', 'SYSTEMROOT', 'WINDIR', 'PSMODULEPATH', 'POWERSHELL_DISTRIBUTION_CHANNEL')
    $seen = @{}
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadAllLines($item.FullName)) {
        $lineNumber++
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }

        $match = [Regex]::Match($line, '^\s*(?:export\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*=(?<value>.*)$')
        if (-not $match.Success) {
            throw "Environment file '$Path' has an unsupported line at $lineNumber. Use literal NAME=value entries only."
        }

        $name = $match.Groups['name'].Value
        if ($AllowedNames.Count -gt 0 -and -not ($AllowedNames -contains $name)) {
            throw "Environment file '$Path' is not allowed to set '$name'."
        }
        if ($blockedNames -contains $name.ToUpperInvariant()) {
            throw "Environment file '$Path' attempts to set blocked process variable '$name'."
        }
        if ($seen.ContainsKey($name)) {
            throw "Environment file '$Path' defines '$name' more than once."
        }
        $seen[$name] = $true

        $value = $match.Groups['value'].Value
        # Parse only the literal suffix after the first equals sign. Matching outer
        # quotes are environment-file delimiters; remove those delimiters without
        # evaluating, expanding, trimming, or unescaping the enclosed value.
        if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

function Set-LocalbaseJdbcMySqlEndpoint {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$EnvironmentName,
        [int]$Port = 23306
    )

    $current = [Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
    if ([string]::IsNullOrWhiteSpace($current)) {
        return
    }

    $prefix = 'jdbc:mysql://'
    if (-not $current.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$EnvironmentName must use a jdbc:mysql:// URL for native localbase startup."
    }
    $remainder = $current.Substring($prefix.Length)
    $databaseIndex = $remainder.IndexOf('/')
    if ($databaseIndex -lt 1) {
        throw "$EnvironmentName must include a database path for native localbase startup."
    }
    $databaseAndOptions = $remainder.Substring($databaseIndex)
    [Environment]::SetEnvironmentVariable($EnvironmentName, "jdbc:mysql://$script:LocalbaseHost`:$Port$databaseAndOptions", 'Process')
}

function Set-LocalbaseUriEndpoint {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$EnvironmentName,
        [Parameter(Mandatory)]
        [string]$Scheme,
        [Parameter(Mandatory)]
        [int]$Port,
        [string]$DefaultPath = ''
    )

    $current = [Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
    if ([string]::IsNullOrWhiteSpace($current)) {
        [Environment]::SetEnvironmentVariable($EnvironmentName, "${Scheme}://$script:LocalbaseHost`:$Port$DefaultPath", 'Process')
        return
    }

    $uri = $null
    if (-not [Uri]::TryCreate($current, [UriKind]::Absolute, [ref]$uri) -or -not [string]::Equals($uri.Scheme, $Scheme, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$EnvironmentName must use a $Scheme URI for native localbase startup."
    }
    if (-not [string]::IsNullOrWhiteSpace($uri.UserInfo)) {
        throw "$EnvironmentName must not contain URI credentials; keep them in a dedicated environment variable."
    }
    [Environment]::SetEnvironmentVariable($EnvironmentName, "${Scheme}://$script:LocalbaseHost`:$Port$($uri.PathAndQuery)", 'Process')
}

function Get-NativeStateDirectory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    $stateDirectory = Join-Path $ProjectRoot '.native-run'
    if (-not (Test-Path -LiteralPath $stateDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
    }
    return (Resolve-Path -LiteralPath $stateDirectory).Path
}

function Get-NativeStatePath {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$StateDirectory,
        [Parameter(Mandatory)]
        [string]$Name
    )

    return Join-Path $StateDirectory ($Name + '.json')
}

function Get-NativeProcessRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$StatePath
    )

    if (-not (Test-Path -LiteralPath $StatePath -PathType Leaf)) {
        return $null
    }
    try {
        return Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
    }
    catch {
        throw "Cannot read native process state '$StatePath': $($_.Exception.Message)"
    }
}

function Test-NativeProcessRecordLive {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        $Record
    )

    try {
        $process = Get-Process -Id ([int]$Record.Pid) -ErrorAction Stop
        $recordedStart = $Record.ProcessStartTimeUtc
        $expectedStart = if ($recordedStart -is [DateTime]) {
            $recordedStart.ToUniversalTime()
        }
        else {
            [DateTime]::Parse([string]$recordedStart, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
        }
        $actualStart = $process.StartTime.ToUniversalTime()
        return [Math]::Abs(($actualStart - $expectedStart).TotalSeconds) -le 2
    }
    catch {
        return $false
    }
}

function Get-RequiredNativeCommandPath {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )

    $command = Get-Command -Name $Name -CommandType Application -ErrorAction Stop | Select-Object -First 1
    if (-not [string]::IsNullOrWhiteSpace($command.Path)) {
        return $command.Path
    }
    return $command.Source
}

function Start-NativeProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [Parameter(Mandatory)]
        [string]$FilePath,
        [string[]]$ArgumentList = @(),
        [Parameter(Mandatory)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory)]
        [string]$StateDirectory,
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    $statePath = Get-NativeStatePath -StateDirectory $StateDirectory -Name $Name
    $existingRecord = Get-NativeProcessRecord -StatePath $statePath
    if ($null -ne $existingRecord) {
        if (Test-NativeProcessRecordLive -Record $existingRecord) {
            throw "Native $Name process is already running (PID $($existingRecord.Pid)). Stop it before starting another instance."
        }
        Remove-Item -LiteralPath $statePath -Force
    }

    $logDirectory = Join-Path $StateDirectory 'logs'
    if (-not (Test-Path -LiteralPath $logDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    }
    $stdoutPath = Join-Path $logDirectory ($Name + '.stdout.log')
    $stderrPath = Join-Path $logDirectory ($Name + '.stderr.log')
    $launchFilePath = $FilePath
    $launchArgumentList = $ArgumentList
    if ([IO.Path]::GetExtension($FilePath) -in @('.cmd', '.bat')) {
        $commandProcessor = [Environment]::GetEnvironmentVariable('ComSpec', 'Process')
        if ([string]::IsNullOrWhiteSpace($commandProcessor) -or -not (Test-Path -LiteralPath $commandProcessor -PathType Leaf)) {
            throw 'Windows command processor COMSPEC is unavailable for the native launcher.'
        }
        if (@($ArgumentList | Where-Object { $_ -match '[&|<>()^\"]' }).Count -gt 0) {
            throw "Native batch command '$FilePath' includes unsupported command-shell metacharacters."
        }

        # cmd.exe normally transfers control to a nested batch file, which can
        # make the recorded outer PID exit while Maven and its JVM continue.
        # `call` preserves the cmd.exe process as the tree root until its child
        # command exits, so Stop-NativeProcess can safely stop only the PID it
        # recorded and its descendants.
        $quotedBatchPath = '"' + $FilePath.Replace('"', '""') + '"'
        $quotedArguments = @(
            foreach ($argument in $ArgumentList) {
                if ($argument -match '\s') { '"' + $argument + '"' } else { $argument }
            }
        )
        $launchFilePath = $commandProcessor
        $launchArgumentList = @('/d', '/s', '/c', ('call ' + $quotedBatchPath + ' ' + ($quotedArguments -join ' ')))
    }
    $process = Start-Process -FilePath $launchFilePath -ArgumentList $launchArgumentList -WorkingDirectory $WorkingDirectory -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -PassThru

    try {
        $processStart = (Get-Process -Id $process.Id -ErrorAction Stop).StartTime.ToUniversalTime().ToString('o')
    }
    catch {
        throw "Started native $Name process but could not verify its process metadata: $($_.Exception.Message)"
    }

    [pscustomobject]@{
        Name = $Name
        Pid = $process.Id
        ProcessStartTimeUtc = $processStart
        ProjectRoot = $ProjectRoot
        StartedAtUtc = [DateTime]::UtcNow.ToString('o')
        StdoutLog = $stdoutPath
        StderrLog = $stderrPath
    } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
    return $process
}

function Stop-NativeProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [Parameter(Mandatory)]
        [string]$StateDirectory,
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    $statePath = Get-NativeStatePath -StateDirectory $StateDirectory -Name $Name
    $record = Get-NativeProcessRecord -StatePath $statePath
    if ($null -eq $record) {
        Write-Host "No recorded native $Name process."
        return
    }
    if (-not [string]::Equals([string]$record.ProjectRoot, $ProjectRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to stop $Name because its state file belongs to a different project root."
    }
    if (-not (Test-NativeProcessRecordLive -Record $record)) {
        Remove-Item -LiteralPath $statePath -Force
        Write-Host "Removed stale native $Name process state."
        return
    }

    & taskkill.exe /PID ([string]$record.Pid) /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "taskkill could not stop native $Name process $($record.Pid) (exit code $LASTEXITCODE)."
    }
    Remove-Item -LiteralPath $statePath -Force
    Write-Host "Stopped native $Name process $($record.Pid)."
}

function Get-NativeManagedListenerProcess {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [int]$ListenerProcessId,
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    $listenerProcess = Get-Process -Id $ListenerProcessId -ErrorAction Stop
    $candidate = [pscustomobject]@{
        Pid = $listenerProcess.Id
        ProcessStartTimeUtc = $listenerProcess.StartTime.ToUniversalTime().ToString('o')
    }
    $normalizedProjectRoot = $ProjectRoot.TrimEnd('\\')
    $currentProcessId = $ListenerProcessId
    $seen = New-Object System.Collections.Generic.HashSet[int]
    while ($currentProcessId -gt 0 -and $seen.Add($currentProcessId)) {
        $processInfo = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $currentProcessId" -ErrorAction SilentlyContinue
        if ($null -eq $processInfo) {
            break
        }
        $isShell = $processInfo.Name -in @('powershell.exe', 'pwsh.exe')
        if (-not $isShell -and -not [string]::IsNullOrWhiteSpace($processInfo.CommandLine) -and
            $processInfo.CommandLine.IndexOf($normalizedProjectRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            # The listener is the durable lifecycle anchor. Maven/npm wrappers
            # can be replaced while their child server remains alive, so recording
            # an ancestor here makes Stop-NativeProcess incorrectly treat it as stale.
            return $candidate
        }
        $currentProcessId = [int]$processInfo.ParentProcessId
    }

    throw "Refusing to adopt native listener $ListenerProcessId because it is not owned by project '$ProjectRoot'."
}

function Update-NativeProcessRecordForListener {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [Parameter(Mandatory)]
        [string]$StateDirectory,
        [Parameter(Mandatory)]
        [string]$ProjectRoot,
        [Parameter(Mandatory)]
        [int]$ListenerProcessId,
        [Parameter(Mandatory)]
        [int]$Port
    )

    $statePath = Get-NativeStatePath -StateDirectory $StateDirectory -Name $Name
    $record = Get-NativeProcessRecord -StatePath $statePath
    if ($null -eq $record) {
        throw "Cannot adopt the native listener on port $Port because state '$statePath' is missing."
    }
    if (-not [string]::Equals([string]$record.ProjectRoot, $ProjectRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Cannot adopt the native listener on port $Port because its state belongs to a different project root."
    }

    $listenerProcess = Get-Process -Id $ListenerProcessId -ErrorAction Stop
    $startedAt = [DateTime]::Parse([string]$record.StartedAtUtc).ToUniversalTime()
    if ($listenerProcess.StartTime.ToUniversalTime() -lt $startedAt.AddSeconds(-2)) {
        throw "TCP port $Port is owned by a process that predates native $Name startup."
    }

    $managedProcess = Get-NativeManagedListenerProcess -ListenerProcessId $ListenerProcessId -ProjectRoot $ProjectRoot
    $record.Pid = $managedProcess.Pid
    $record.ProcessStartTimeUtc = $managedProcess.ProcessStartTimeUtc
    $record | Add-Member -NotePropertyName ListenerPort -NotePropertyValue $Port -Force
    $record | Add-Member -NotePropertyName ListenerProcessId -NotePropertyValue $ListenerProcessId -Force
    $record | Add-Member -NotePropertyName AdoptedAtUtc -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
    $record | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
}

function Wait-NativeLoopbackPort {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [int]$Port,
        [int]$TimeoutSeconds = 45,
        [Parameter(Mandatory)]
        [int]$ExpectedRootProcessId,
        [Parameter(Mandatory)]
        [string]$Name,
        [Parameter(Mandatory)]
        [string]$StateDirectory,
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (Test-NativeTcpEndpoint -HostName '127.0.0.1' -Port $Port -TimeoutMilliseconds 1000) {
            $listenerProcessIds = @(Get-NativeListeningProcessIds -Port $Port)
            if ($listenerProcessIds.Count -eq 0) {
                return
            }
            foreach ($listenerProcessId in $listenerProcessIds) {
                try {
                    Update-NativeProcessRecordForListener -Name $Name -StateDirectory $StateDirectory -ProjectRoot $ProjectRoot -ListenerProcessId ([int]$listenerProcessId) -Port $Port
                    return
                }
                catch {
                    $rootProcess = Get-Process -Id $ExpectedRootProcessId -ErrorAction SilentlyContinue
                    if ($null -ne $rootProcess) {
                        $isOwned = Test-NativeProcessTreeContains -RootProcessId $ExpectedRootProcessId -CandidateProcessId ([int]$listenerProcessId)
                        if ($isOwned) {
                            throw
                        }
                    }
                }
            }
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "Native process did not listen on 127.0.0.1:$Port within $TimeoutSeconds seconds. Inspect .native-run\\logs."
}

Export-ModuleMember -Function @(
    'Assert-NativePortAvailable',
    'Assert-LocalbasePorts',
    'Assert-LocalbaseResolution',
    'Clear-NativeProcessEnvironment',
    'Ensure-LocalbaseHostsMapping',
    'Get-NativeProjectRoot',
    'Get-NativeStateDirectory',
    'Get-RequiredNativeCommandPath',
    'Import-LiteralEnvironmentFile',
    'Set-LocalbaseJdbcMySqlEndpoint',
    'Set-LocalbaseUriEndpoint',
    'Start-NativeProcess',
    'Stop-NativeProcess',
    'Wait-NativeLoopbackPort'
)
