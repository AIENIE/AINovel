Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-AienieLocalOnlyEnvironment {
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Values)

    foreach ($name in @('ENV', 'APP_ENV', 'SPRING_PROFILES_ACTIVE')) {
        if (-not $Values.Contains($name)) { continue }
        $value = [string]$Values[$name]
        if (-not [string]::IsNullOrWhiteSpace($value) -and $value -cne 'local') {
            throw "Windows direct runtime rejects non-local $name values."
        }
    }
    if ($Values.Contains('AIENIE_RUNTIME_PLANE')) {
        $runtimePlane = [string]$Values['AIENIE_RUNTIME_PLANE']
        if (-not [string]::IsNullOrWhiteSpace($runtimePlane) -and $runtimePlane -cne 'windows-local') {
            throw 'Windows direct runtime rejects non-windows-local AIENIE_RUNTIME_PLANE values.'
        }
    }
}

function Test-AienieProtectedProcessVariable {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name)

    $upperName = $Name.ToUpperInvariant()
    return $upperName -in @(
            'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
            'MAVEN_OPTS', 'SPRING_APPLICATION_JSON') -or
        $upperName.StartsWith('SPRING_CONFIG_', [StringComparison]::Ordinal)
}

function Assert-AieniePrivateEnvironmentKeyAllowed {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Name)

    if (Test-AienieProtectedProcessVariable -Name $Name) {
        throw "Environment file cannot set protected process variable '$Name'."
    }
}

function Remove-AienieProcessInjectionEnvironment {
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Values)

    foreach ($key in @($Values.Keys)) {
        if (Test-AienieProtectedProcessVariable -Name ([string]$key)) {
            $Values.Remove($key)
        }
    }
}

function Resolve-AienieLocalPrivateFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Path,
        [string]$Label = 'Private file'
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label was not found: $Path"
    }
    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label must be a regular non-reparse file: $Path"
    }
    return $item.FullName
}

function Test-AienieStrictJsonHealthPayload {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][int]$StatusCode,
        [Parameter(Mandatory)][byte[]]$BodyBytes,
        [int]$MaximumBytes = 65536
    )

    if ($StatusCode -ne 200 -or $BodyBytes.Length -gt $MaximumBytes) { return $false }
    try {
        $text = [Text.UTF8Encoding]::new($false, $true).GetString($BodyBytes)
        $document = [Text.Json.JsonDocument]::Parse($text)
        try {
            if ($document.RootElement.ValueKind -ne [Text.Json.JsonValueKind]::Object) { return $false }
            $stack = [Collections.Generic.Stack[Text.Json.JsonElement]]::new()
            $stack.Push($document.RootElement)
            while ($stack.Count -gt 0) {
                $element = $stack.Pop()
                if ($element.ValueKind -eq [Text.Json.JsonValueKind]::Object) {
                    $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
                    foreach ($property in $element.EnumerateObject()) {
                        if (-not $names.Add($property.Name)) { return $false }
                        $stack.Push($property.Value)
                    }
                } elseif ($element.ValueKind -eq [Text.Json.JsonValueKind]::Array) {
                    foreach ($child in $element.EnumerateArray()) { $stack.Push($child) }
                }
            }
            $statusProperties = @($document.RootElement.EnumerateObject() |
                Where-Object { $_.Name.Equals('status', [StringComparison]::OrdinalIgnoreCase) })
            return $statusProperties.Count -eq 1 -and
                $statusProperties[0].Name -ceq 'status' -and
                $statusProperties[0].Value.ValueKind -eq [Text.Json.JsonValueKind]::String -and
                $statusProperties[0].Value.GetString() -ceq 'UP'
        } finally { $document.Dispose() }
    } catch { return $false }
}

function Invoke-AienieBoundedHttp {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][uri]$Uri,
        [ValidateRange(1, 30)][int]$TimeoutSeconds = 3,
        [int]$MaximumBytes = 65536,
        [switch]$RequireJsonUp
    )

    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    try {
        $response = $client.GetAsync($Uri, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        try {
            if ([int]$response.StatusCode -ne 200 -or
                ($null -ne $response.Content.Headers.ContentLength -and $response.Content.Headers.ContentLength -gt $MaximumBytes)) {
                return $false
            }
            $stream = $response.Content.ReadAsStream()
            try {
                $memory = [IO.MemoryStream]::new()
                try {
                    $buffer = [byte[]]::new(8192)
                    while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                        if ($memory.Length + $read -gt $MaximumBytes) { return $false }
                        $memory.Write($buffer, 0, $read)
                    }
                    if (-not $RequireJsonUp) { return $true }
                    return Test-AienieStrictJsonHealthPayload -StatusCode ([int]$response.StatusCode) -BodyBytes $memory.ToArray() -MaximumBytes $MaximumBytes
                } finally { $memory.Dispose() }
            } finally { $stream.Dispose() }
        } finally { $response.Dispose() }
    } catch { return $false } finally { $client.Dispose(); $handler.Dispose() }
}

function Get-AienieProcessIdentitySnapshot {
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$ProcessId)

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    $cim = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
    if ($null -eq $cim -or [string]::IsNullOrWhiteSpace([string]$cim.ExecutablePath) -or
        [string]::IsNullOrWhiteSpace([string]$cim.CommandLine)) { return $null }
    return [pscustomobject]@{
        Process = $process
        ProcessId = $ProcessId
        StartedUtc = $process.StartTime.ToUniversalTime().ToString('o')
        ExecutablePath = [IO.Path]::GetFullPath([string]$cim.ExecutablePath)
        CommandLine = [string]$cim.CommandLine
    }
}

function ConvertTo-AienieUtcDateTime {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Value)

    if ($Value -is [DateTime]) { return ([DateTime]$Value).ToUniversalTime() }
    if ($Value -is [DateTimeOffset]) { return ([DateTimeOffset]$Value).UtcDateTime }
    return [DateTimeOffset]::Parse(
        [string]$Value,
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::RoundtripKind).UtcDateTime
}

function Test-AienieProcessIdentitySnapshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Expected,
        [Parameter(Mandatory)]$Actual
    )

    if ($null -eq $Actual -or [int]$Expected.ProcessId -ne [int]$Actual.ProcessId) { return $false }
    $expectedStart = ConvertTo-AienieUtcDateTime -Value $Expected.StartedUtc
    $actualStart = ConvertTo-AienieUtcDateTime -Value $Actual.StartedUtc
    if ([Math]::Abs(($actualStart - $expectedStart).TotalMilliseconds) -gt 1000) { return $false }
    if ([string]::IsNullOrWhiteSpace([string]$Expected.ExecutablePath) -or
        -not [IO.Path]::GetFullPath([string]$Actual.ExecutablePath).Equals(
            [IO.Path]::GetFullPath([string]$Expected.ExecutablePath),
            [StringComparison]::OrdinalIgnoreCase)) { return $false }
    return [string]$Actual.CommandLine -ceq [string]$Expected.CommandLine
}

function Test-AienieProcessTreeContains {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][int]$RootProcessId,
        [Parameter(Mandatory)][int]$CandidateProcessId
    )

    $seen = [Collections.Generic.HashSet[int]]::new()
    $current = $CandidateProcessId
    while ($current -gt 0 -and $seen.Add($current)) {
        if ($current -eq $RootProcessId) { return $true }
        $info = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $current" -ErrorAction SilentlyContinue
        if ($null -eq $info) { return $false }
        $current = [int]$info.ParentProcessId
    }
    return $false
}

function New-AienieRootProcessRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][System.Diagnostics.Process]$Process,
        [Parameter(Mandatory)][string]$WorkingRoot
    )

    $resolvedRoot = (Resolve-Path -LiteralPath $WorkingRoot).Path
    $identity = Get-AienieProcessIdentitySnapshot -ProcessId $Process.Id
    if ($null -eq $identity -or
        $identity.CommandLine.IndexOf($resolvedRoot, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
        throw "Native $Name launch command does not contain the absolute working-root marker."
    }
    return [pscustomobject]@{
        SchemaVersion = 3
        Name = $Name
        WorkingRoot = $resolvedRoot
        RootProcess = [ordered]@{
            ProcessId = $identity.ProcessId
            StartedUtc = $identity.StartedUtc
            ExecutablePath = $identity.ExecutablePath
            CommandLine = $identity.CommandLine
        }
        Listeners = @()
        Process = $Process
    }
}

function Complete-AienieManagedProcessRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Record,
        [Parameter(Mandatory)][int[]]$Ports
    )

    $rootId = [int]$Record.RootProcess.ProcessId
    $rootIdentity = Get-AienieProcessIdentitySnapshot -ProcessId $rootId
    if (-not (Test-AienieProcessIdentitySnapshot -Expected $Record.RootProcess -Actual $rootIdentity)) {
        throw "Native $($Record.Name) root identity changed before listener registration."
    }
    $registered = [Collections.Generic.List[object]]::new()
    $rootStartedUtc = [DateTimeOffset]::Parse([string]$Record.RootProcess.StartedUtc).UtcDateTime
    foreach ($port in $Ports) {
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction Stop)
        if ($listeners.Count -eq 0) { throw "Native $($Record.Name) has no listener on port $port." }
        if (@($listeners | Where-Object LocalAddress -notin @('127.0.0.1', '::1')).Count -gt 0) {
            throw "Native $($Record.Name) opened a wildcard or non-loopback listener on port $port."
        }
        foreach ($listener in $listeners) {
            $listenerPid = [int]$listener.OwningProcess
            if (-not (Test-AienieProcessTreeContains -RootProcessId $rootId -CandidateProcessId $listenerPid)) {
                throw "Native $($Record.Name) listener on port $port does not belong to the launched root process tree."
            }
            $identity = Get-AienieProcessIdentitySnapshot -ProcessId $listenerPid
            if ($null -eq $identity) { throw "Native $($Record.Name) listener identity disappeared on port $port." }
            if ([DateTimeOffset]::Parse([string]$identity.StartedUtc).UtcDateTime -lt $rootStartedUtc.AddSeconds(-1)) {
                throw "Native $($Record.Name) listener on port $port predates the launched root process."
            }
            $registered.Add([ordered]@{
                    Port = [int]$port
                    Address = [string]$listener.LocalAddress
                    Process = [ordered]@{
                        ProcessId = $identity.ProcessId
                        StartedUtc = $identity.StartedUtc
                        ExecutablePath = $identity.ExecutablePath
                        CommandLine = $identity.CommandLine
                    }
                })
        }
    }
    $Record.Listeners = @($registered)
    return $Record
}

function ConvertTo-AieniePersistedProcessRecord {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Record)

    return [pscustomobject]@{
        SchemaVersion = 3
        Name = [string]$Record.Name
        WorkingRoot = [string]$Record.WorkingRoot
        RootProcess = $Record.RootProcess
        Listeners = @($Record.Listeners)
        Port = [int]$Record.Port
        HealthPath = [string]$Record.HealthPath
    }
}

function Test-AienieManagedProcessRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Record,
        [Parameter(Mandatory)][string]$ExpectedName,
        [Parameter(Mandatory)][string]$ExpectedWorkingRoot,
        [Parameter(Mandatory)][int[]]$ExpectedPorts
    )

    try {
        if ([int]$Record.SchemaVersion -ne 3 -or [string]$Record.Name -cne $ExpectedName) { return $false }
        $resolvedRoot = (Resolve-Path -LiteralPath $ExpectedWorkingRoot).Path
        if (-not [string]::Equals([string]$Record.WorkingRoot, $resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) { return $false }
        $recordedPorts = @($Record.Listeners | ForEach-Object { [int]$_.Port } | Sort-Object -Unique)
        $expectedSorted = @($ExpectedPorts | Sort-Object -Unique)
        if (($recordedPorts -join ',') -cne ($expectedSorted -join ',')) { return $false }
        $rootId = [int]$Record.RootProcess.ProcessId
        $rootIdentity = Get-AienieProcessIdentitySnapshot -ProcessId $rootId
        if (-not (Test-AienieProcessIdentitySnapshot -Expected $Record.RootProcess -Actual $rootIdentity)) { return $false }
        $rootStartedUtc = [DateTimeOffset]::Parse([string]$Record.RootProcess.StartedUtc).UtcDateTime
        foreach ($port in $expectedSorted) {
            $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction Stop)
            $registered = @($Record.Listeners | Where-Object { [int]$_.Port -eq $port })
            if ($listeners.Count -eq 0 -or $listeners.Count -ne $registered.Count -or
                @($listeners | Where-Object LocalAddress -notin @('127.0.0.1', '::1')).Count -gt 0) { return $false }
            foreach ($entry in $registered) {
                $matching = @($listeners | Where-Object {
                        [int]$_.OwningProcess -eq [int]$entry.Process.ProcessId -and
                        [string]$_.LocalAddress -ceq [string]$entry.Address
                    })
                $listenerIdentity = Get-AienieProcessIdentitySnapshot -ProcessId ([int]$entry.Process.ProcessId)
                if ($matching.Count -ne 1 -or
                    -not (Test-AienieProcessIdentitySnapshot -Expected $entry.Process -Actual $listenerIdentity) -or
                    [DateTimeOffset]::Parse([string]$listenerIdentity.StartedUtc).UtcDateTime -lt $rootStartedUtc.AddSeconds(-1) -or
                    -not (Test-AienieProcessTreeContains -RootProcessId $rootId -CandidateProcessId ([int]$entry.Process.ProcessId))) {
                    return $false
                }
            }
        }
        return $true
    } catch { return $false }
}

function Stop-AienieManagedProcessRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Record,
        [Parameter(Mandatory)][string]$ExpectedName,
        [Parameter(Mandatory)][string]$ExpectedWorkingRoot,
        [Parameter(Mandatory)][int[]]$ExpectedPorts
    )

    if (-not (Test-AienieManagedProcessRecord -Record $Record -ExpectedName $ExpectedName -ExpectedWorkingRoot $ExpectedWorkingRoot -ExpectedPorts $ExpectedPorts)) {
        throw "Refusing to stop native $ExpectedName because its full process identity no longer matches."
    }
    $rootId = [int]$Record.RootProcess.ProcessId
    & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID ([string]$rootId) /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to stop native $ExpectedName root process $rootId." }
}

function Stop-AienieRootProcessRecord {
    [CmdletBinding()]
    param([Parameter(Mandatory)]$Record)

    $rootId = [int]$Record.RootProcess.ProcessId
    $actual = Get-AienieProcessIdentitySnapshot -ProcessId $rootId
    if ($null -eq $actual) { return }
    if (-not (Test-AienieProcessIdentitySnapshot -Expected $Record.RootProcess -Actual $actual)) {
        throw "Refusing to clean up native $($Record.Name) because its root identity changed."
    }
    & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID ([string]$rootId) /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to clean up native $($Record.Name) root process $rootId." }
}

function Test-AienieGrpcHealthResponsePayload {
    [CmdletBinding()]
    param([Parameter(Mandatory)][byte[]]$BodyBytes)

    return $BodyBytes.Length -eq 7 -and
        $BodyBytes[0] -eq 0 -and
        $BodyBytes[1] -eq 0 -and $BodyBytes[2] -eq 0 -and $BodyBytes[3] -eq 0 -and $BodyBytes[4] -eq 2 -and
        $BodyBytes[5] -eq 8 -and $BodyBytes[6] -eq 1
}

function Invoke-AienieGrpcHealthServing {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][ValidateRange(1, 65535)][int]$Port,
        [ValidateRange(1, 30)][int]$TimeoutSeconds = 3
    )

    $handler = [Net.Http.SocketsHttpHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.EnableMultipleHttp2Connections = $true
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    $request = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::Post,
        "http://127.0.0.1:$Port/grpc.health.v1.Health/Check")
    $request.Version = [Version]::new(2, 0)
    $request.VersionPolicy = [Net.Http.HttpVersionPolicy]::RequestVersionExact
    [void]$request.Headers.TryAddWithoutValidation('TE', 'trailers')
    $request.Content = [Net.Http.ByteArrayContent]::new([byte[]](0, 0, 0, 0, 0))
    $request.Content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/grpc')
    try {
        $response = $client.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        try {
            if ([int]$response.StatusCode -ne 200 -or $response.Version.Major -ne 2) { return $false }
            $body = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $grpcStatus = @($response.TrailingHeaders.GetValues('grpc-status'))
            return $grpcStatus.Count -eq 1 -and $grpcStatus[0] -ceq '0' -and
                (Test-AienieGrpcHealthResponsePayload -BodyBytes $body)
        } finally { $response.Dispose() }
    } catch { return $false } finally {
        $request.Dispose()
        $client.Dispose()
        $handler.Dispose()
    }
}

Export-ModuleMember -Function @(
    'Assert-AienieLocalOnlyEnvironment',
    'Test-AienieProtectedProcessVariable',
    'Assert-AieniePrivateEnvironmentKeyAllowed',
    'Remove-AienieProcessInjectionEnvironment',
    'Resolve-AienieLocalPrivateFile',
    'Test-AienieStrictJsonHealthPayload',
    'Invoke-AienieBoundedHttp',
    'Get-AienieProcessIdentitySnapshot',
    'Test-AienieProcessIdentitySnapshot',
    'Test-AienieProcessTreeContains',
    'New-AienieRootProcessRecord',
    'Complete-AienieManagedProcessRecord',
    'ConvertTo-AieniePersistedProcessRecord',
    'Test-AienieManagedProcessRecord',
    'Stop-AienieManagedProcessRecord',
    'Stop-AienieRootProcessRecord',
    'Test-AienieGrpcHealthResponsePayload',
    'Invoke-AienieGrpcHealthServing'
)
