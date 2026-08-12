Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-AINovelWindowsHost {
    if (-not $IsWindows -or $PSVersionTable.PSEdition -ne 'Core') {
        throw 'AINovel local run requires PowerShell 7+ on Windows.'
    }
}

function Resolve-AINovelRegularFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Label)

    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($item.PSIsContainer -or (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
        throw "$Label must be a regular file and must not be a reparse point."
    }
    $parent = Get-Item -LiteralPath $item.DirectoryName -Force -ErrorAction Stop
    if (($parent.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "$Label parent directory must not be a reparse point."
    }
    return (Resolve-Path -LiteralPath $item.FullName -ErrorAction Stop).Path
}

function Get-AINovelApprovedAclSids {
    return @(
        [Security.Principal.WindowsIdentity]::GetCurrent().User.Value,
        'S-1-5-18',
        'S-1-5-32-544'
    )
}

function Get-AINovelAclOwnerSid {
    param([Parameter(Mandatory)][Security.AccessControl.ObjectSecurity]$Acl)

    return $Acl.GetOwner([Security.Principal.SecurityIdentifier]).Value
}

function Assert-AINovelPrivateAcl {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Label,
        [switch]$IncludeInherited,
        [switch]$RequireProtected
    )

    $allowed = @(Get-AINovelApprovedAclSids)
    $acl = Get-Acl -LiteralPath $Path -ErrorAction Stop
    $ownerSid = Get-AINovelAclOwnerSid -Acl $acl
    if ($ownerSid -notin @($allowed[0], 'S-1-5-32-544')) {
        throw "$Label owner must be the current user or local Administrators."
    }
    if ($RequireProtected -and -not $acl.AreAccessRulesProtected) {
        throw "$Label permissions must not be inherited."
    }

    $rules = if ($IncludeInherited) {
        @($acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    } else {
        @($acl.GetAccessRules($true, $false, [Security.Principal.SecurityIdentifier]))
    }
    if ($rules.Count -ne $allowed.Count) {
        throw "$Label contains unexpected permission entries."
    }
    $fullControl = [int64][Security.AccessControl.FileSystemRights]::FullControl
    foreach ($rule in $rules) {
        $sid = ([Security.Principal.SecurityIdentifier]$rule.IdentityReference).Value
        if ((-not $IncludeInherited -and $rule.IsInherited) -or
            $sid -notin $allowed -or
            $rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow -or
            (([int64]$rule.FileSystemRights -band $fullControl) -ne $fullControl)) {
            throw "$Label permissions are not restricted to approved principals."
        }
    }
    foreach ($sid in $allowed) {
        if (@($rules | Where-Object {
                    ([Security.Principal.SecurityIdentifier]$_.IdentityReference).Value -eq $sid
                }).Count -ne 1) {
            throw "$Label is missing an approved permission entry."
        }
    }
}

function Assert-AINovelPrivateDirectory {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Label)

    $item = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if (-not $item.PSIsContainer -or
        (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
        throw "$Label must be a regular directory and must not be a reparse point."
    }
    Assert-AINovelPrivateAcl -Path $item.FullName -Label $Label -RequireProtected
    return $item.FullName
}

function Assert-AINovelPrivateFileAcl {
    param([Parameter(Mandatory)][string]$Path)

    $resolved = Resolve-AINovelRegularFile -Path $Path -Label 'Environment file'
    Assert-AINovelPrivateDirectory -Path (Split-Path -Parent $resolved) `
        -Label 'Environment file parent directory' | Out-Null
    Assert-AINovelPrivateAcl -Path $resolved -Label 'Environment file' -RequireProtected
    return $resolved
}

function Protect-AINovelPrivateDirectory {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Label)

    $directory = $Path
    if (Test-Path -LiteralPath $directory) {
        $item = Get-Item -LiteralPath $directory -Force -ErrorAction Stop
        if (-not $item.PSIsContainer -or
            (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
            throw "$Label must be a regular directory and must not be a reparse point."
        }
        $ownerSid = Get-AINovelAclOwnerSid -Acl (Get-Acl -LiteralPath $item.FullName)
        $allowedOwners = @(
            [Security.Principal.WindowsIdentity]::GetCurrent().User.Value,
            'S-1-5-32-544'
        )
        if ($ownerSid -notin $allowedOwners) {
            throw "$Label owner must be the current user or local Administrators."
        }
    } else {
        New-Item -ItemType Directory -Path $directory | Out-Null
    }

    $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $icacls = Join-Path $env:SystemRoot 'System32\icacls.exe'
    & $icacls $directory '/inheritance:r' 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to disable inherited ACLs on $Label."
    }
    & $icacls $directory '/grant:r' `
        ("*{0}:(OI)(CI)(F)" -f $currentSid) `
        '*S-1-5-18:(OI)(CI)(F)' `
        '*S-1-5-32-544:(OI)(CI)(F)' 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to grant private ACLs on $Label."
    }
    $acl = Get-Acl -LiteralPath $directory
    $approvedSids = @(Get-AINovelApprovedAclSids)
    $existingSids = @($acl.GetAccessRules($true, $false,
                [Security.Principal.SecurityIdentifier]) |
            ForEach-Object { ([Security.Principal.SecurityIdentifier]$_.IdentityReference).Value } |
            Where-Object { $_ -notin $approvedSids } |
            Sort-Object -Unique)
    foreach ($sid in $existingSids) {
        & $icacls $directory '/remove' ("*{0}" -f $sid) 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to remove an existing ACL entry from $Label."
        }
    }
    return Assert-AINovelPrivateDirectory -Path $directory -Label $Label
}

function Protect-AINovelRunDirectory {
    param([Parameter(Mandatory)][string]$ProjectRoot)

    return Protect-AINovelPrivateDirectory -Path (Join-Path $ProjectRoot '.native-run') `
        -Label '.native-run'
}

function Protect-AINovelRunSubdirectory {
    param(
        [Parameter(Mandatory)][string]$ProjectRoot,
        [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._-]+$')][string]$Name
    )

    $runDirectory = Protect-AINovelRunDirectory -ProjectRoot $ProjectRoot
    return Protect-AINovelPrivateDirectory -Path (Join-Path $runDirectory $Name) `
        -Label ".native-run/$Name"
}

function Read-AINovelLiteralEnvironment {
    param([Parameter(Mandatory)][string]$Path)

    $resolved = Assert-AINovelPrivateFileAcl -Path $Path
    $values = @{}
    $stream = [IO.FileStream]::new(
        $resolved,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true)
    try {
        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine().Trim()
            if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
                continue
            }
            $separator = $line.IndexOf('=')
            if ($separator -lt 1) {
                throw 'Environment file contains a non KEY=VALUE line.'
            }
            $key = $line.Substring(0, $separator).Trim()
            if ($key -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
                throw 'Environment file contains an invalid key.'
            }
            $value = $line.Substring($separator + 1)
            if ($value.Length -ge 2 -and
                (($value.StartsWith("'") -and $value.EndsWith("'")) -or
                 ($value.StartsWith('"') -and $value.EndsWith('"')))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $values[$key] = $value
        }
    } finally {
        $reader.Dispose()
        $stream.Dispose()
    }

    $required = @(
        'ENV', 'AUTH_MODE', 'ADMIN_USERNAME', 'ADMIN_PASSWORD_HASH',
        'JWT_SECRET', 'JWT_ISSUER', 'JWT_AUDIENCE', 'MYSQL_PASSWORD',
        'SPRING_JPA_HIBERNATE_DDL_AUTO', 'EXTERNAL_AI_HMAC_CALLER',
        'EXTERNAL_AI_HMAC_SECRET', 'EXTERNAL_USER_INTERNAL_GRPC_TOKEN',
        'EXTERNAL_PAY_SERVICE_JWT'
    )
    $missing = @($required | Where-Object {
            -not $values.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($values[$_])
        })
    if ($missing.Count -gt 0) {
        throw "Environment file is missing required keys: $($missing -join ', ')."
    }
    if ($values['ENV'] -ne 'local' -or $values['AUTH_MODE'] -notin @('password', 'totp')) {
        throw 'Windows local run requires ENV=local and AUTH_MODE=password or totp.'
    }
    if ($values['SPRING_JPA_HIBERNATE_DDL_AUTO'] -ne 'none') {
        throw 'Windows local run requires SPRING_JPA_HIBERNATE_DDL_AUTO=none.'
    }
    if ($values.ContainsKey('MYSQL_DB') -and $values['MYSQL_DB'] -ne 'ainovel') {
        throw 'Windows local run only allows MYSQL_DB=ainovel.'
    }
    if ($values['AUTH_MODE'] -eq 'totp') {
        foreach ($key in 'ADMIN_TOTP_ENCRYPTION_KEYS', 'ADMIN_TOTP_ACTIVE_KEY_VERSION') {
            if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($values[$key])) {
                throw "Environment file is missing required key: $key."
            }
        }
    }
    $placeholderKeys = @($values.Keys | Where-Object {
            $values[$_] -match '^(?i:replace[-_]|change[-_]?me|replace_with_your_own)'
        })
    if ($placeholderKeys.Count -gt 0) {
        throw "Environment file contains template values for: $($placeholderKeys -join ', ')."
    }
    $forbiddenProcessKeys = @(
        'PATH', 'PATHEXT', 'COMSPEC', 'SYSTEMROOT', 'WINDIR', 'SYSTEMDRIVE',
        'HOMEDRIVE', 'HOMEPATH', 'HOME', 'USERPROFILE', 'APPDATA', 'LOCALAPPDATA',
        'PROGRAMDATA', 'TEMP', 'TMP', 'PSMODULEPATH',
        'JAVA_HOME', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
        'CLASSPATH', 'MAVEN_OPTS', 'MAVEN_ARGS', 'NODE_OPTIONS', 'NODE_PATH',
        'NPM_CONFIG_USERCONFIG', 'NPM_CONFIG_PREFIX', 'NPM_CONFIG_CACHE',
        'SPRING_APPLICATION_JSON', 'SPRING_CONFIG_NAME', 'SPRING_CONFIG_LOCATION',
        'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT'
    )
    $forbiddenPresent = @($values.Keys | Where-Object { $_ -in $forbiddenProcessKeys })
    if ($forbiddenPresent.Count -gt 0) {
        throw "Environment file contains forbidden process-control keys: $($forbiddenPresent -join ', ')."
    }
    return $values
}

function Assert-AINovelPortAvailable {
    param([Parameter(Mandatory)][int]$Port)

    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $listener) {
        throw "Local port $Port is already in use by PID $($listener.OwningProcess)."
    }
}

function Assert-AINovelTcpEndpoint {
    param(
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][int]$Port,
        [ValidateRange(1, 30)][int]$TimeoutSeconds = 5
    )

    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        if (-not $task.Wait([TimeSpan]::FromSeconds($TimeoutSeconds)) -or -not $client.Connected) {
            throw "Required endpoint is unreachable at ${HostName}:$Port."
        }
    } catch {
        throw "Required endpoint is unreachable at ${HostName}:$Port."
    } finally {
        $client.Dispose()
    }
}

function Write-AINovelAtomicRecord {
    param(
        [Parameter(Mandatory)][string]$RecordPath,
        [Parameter(Mandatory)][string]$Json
    )

    $temporaryPath = Join-Path (Split-Path -Parent $RecordPath) `
        ('.{0}.{1}.tmp' -f (Split-Path -Leaf $RecordPath), [Guid]::NewGuid().ToString('N'))
    try {
        [IO.File]::WriteAllText($temporaryPath, $Json, [Text.UTF8Encoding]::new($false))
        [IO.File]::Move($temporaryPath, $RecordPath)
    } finally {
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Resolve-AINovelPrivateRuntimeFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$RunDirectory,
        [Parameter(Mandatory)][string]$Label
    )

    $resolved = Resolve-AINovelRegularFile -Path $Path -Label $Label
    if (-not [string]::Equals(
            (Split-Path -Parent $resolved),
            $RunDirectory,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must remain directly inside .native-run."
    }
    Assert-AINovelPrivateAcl -Path $resolved -Label $Label -IncludeInherited
    return $resolved
}

function Start-AINovelRecordedProcess {
    param(
        [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._-]+$')][string]$Name,
        [Parameter(Mandatory)][string]$ProjectRoot,
        [Parameter(Mandatory)][string]$Executable,
        [Parameter(Mandatory)][string[]]$Arguments,
        [Parameter(Mandatory)][hashtable]$Environment
    )

    $runDirectory = Protect-AINovelRunDirectory -ProjectRoot $ProjectRoot
    $recordPath = Join-Path $runDirectory "$Name.json"
    if (Test-Path -LiteralPath $recordPath) {
        throw "A recorded $Name process already exists. Run Stop-Local.ps1 first."
    }
    $stdout = Join-Path $runDirectory "$Name.stdout.log"
    $stderr = Join-Path $runDirectory "$Name.stderr.log"
    foreach ($logPath in $stdout, $stderr) {
        if (Test-Path -LiteralPath $logPath) {
            Resolve-AINovelPrivateRuntimeFile -Path $logPath -RunDirectory $runDirectory `
                -Label "$Name process log" | Out-Null
        }
    }
    $process = $null
    $parentEnvironment = @{}
    foreach ($entry in [Environment]::GetEnvironmentVariables(
            [EnvironmentVariableTarget]::Process).GetEnumerator()) {
        $parentEnvironment[[string]$entry.Key] = [string]$entry.Value
    }
    try {
        try {
            foreach ($key in @([Environment]::GetEnvironmentVariables(
                        [EnvironmentVariableTarget]::Process).Keys)) {
                Remove-Item -LiteralPath ("Env:{0}" -f $key) -ErrorAction SilentlyContinue
            }
            foreach ($entry in $Environment.GetEnumerator()) {
                Set-Item -LiteralPath ("Env:{0}" -f $entry.Key) -Value ([string]$entry.Value)
            }
            $process = Start-Process -FilePath $Executable -ArgumentList $Arguments `
                -WorkingDirectory $ProjectRoot -RedirectStandardOutput $stdout `
                -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
        } finally {
            foreach ($key in @([Environment]::GetEnvironmentVariables(
                        [EnvironmentVariableTarget]::Process).Keys)) {
                Remove-Item -LiteralPath ("Env:{0}" -f $key) -ErrorAction SilentlyContinue
            }
            foreach ($entry in $parentEnvironment.GetEnumerator()) {
                Set-Item -LiteralPath ("Env:{0}" -f $entry.Key) -Value ([string]$entry.Value)
            }
        }
        $identity = $null
        foreach ($attempt in 1..10) {
            $process.Refresh()
            $identity = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)" `
                -ErrorAction SilentlyContinue
            if ($null -ne $identity -and
                -not [string]::IsNullOrWhiteSpace($identity.ExecutablePath)) {
                break
            }
            Start-Sleep -Milliseconds 100
        }
        if ($null -eq $identity -or [string]::IsNullOrWhiteSpace($identity.ExecutablePath)) {
            throw "Unable to establish the executable identity for $Name."
        }
        $recordJson = [ordered]@{
            processId = $process.Id
            startedUtc = $process.StartTime.ToUniversalTime().ToString('o')
            processPath = $identity.ExecutablePath
            commandLine = $identity.CommandLine
            launcher = $Executable
            projectRoot = $ProjectRoot
            name = $Name
        } | ConvertTo-Json
        Write-AINovelAtomicRecord -RecordPath $recordPath -Json $recordJson
        Resolve-AINovelPrivateRuntimeFile -Path $recordPath -RunDirectory $runDirectory `
            -Label "$Name process record" | Out-Null
    } catch {
        $startFailure = $_
        $cleanupFailure = $null
        try {
            if ($null -ne $process) {
                $process.Refresh()
                if (-not $process.HasExited) {
                    & (Join-Path $env:SystemRoot 'System32\taskkill.exe') `
                        /PID $process.Id /T /F | Out-Null
                    if ($LASTEXITCODE -ne 0 -and
                        (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
                        throw "Unable to stop $Name after its startup failed."
                    }
                }
            }
        } catch {
            $cleanupFailure = $_
        }
        if (Test-Path -LiteralPath $recordPath -PathType Leaf) {
            try {
                Remove-Item -LiteralPath $recordPath -Force
            } catch {
                if ($null -eq $cleanupFailure) {
                    $cleanupFailure = $_
                }
            }
        }
        if ($null -ne $cleanupFailure) {
            throw "Failed to start $Name, and rollback also failed: $($startFailure.Exception.Message)"
        }
        throw $startFailure
    }
    return [pscustomobject]@{
        Process = $process
        RecordPath = $recordPath
        RunDirectory = $runDirectory
    }
}

function Wait-AINovelHttpHealth {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][Diagnostics.Process]$Process,
        [ValidateRange(10, 300)][int]$TimeoutSeconds = 120
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $Process.Refresh()
        if ($Process.HasExited) {
            throw "Process exited before $Uri became healthy."
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -NoProxy -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                if ($Uri -like '*/health*') {
                    $responseText = if ($response.Content -is [byte[]]) {
                        [Text.Encoding]::UTF8.GetString([byte[]]$response.Content)
                    } else {
                        [string]$response.Content
                    }
                    $payload = $responseText | ConvertFrom-Json
                    if ($payload.status -eq 'UP') {
                        return
                    }
                } else {
                    return
                }
            }
        } catch {
            # The final error names only the local URL; logs remain on disk.
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Uri."
}

function Stop-AINovelRecordedProcess {
    param(
        [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._-]+$')][string]$Name,
        [Parameter(Mandatory)][string]$ProjectRoot
    )

    $resolvedRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
    $runDirectory = Protect-AINovelRunDirectory -ProjectRoot $resolvedRoot
    $candidateRecordPath = Join-Path $runDirectory "$Name.json"
    if (-not (Test-Path -LiteralPath $candidateRecordPath)) {
        return
    }
    if (-not (Test-Path -LiteralPath $candidateRecordPath -PathType Leaf)) {
        throw "Refusing to stop ${Name}: its process record is not a regular file."
    }
    $recordPath = Resolve-AINovelPrivateRuntimeFile -Path $candidateRecordPath `
        -RunDirectory $runDirectory -Label "$Name process record"
    $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    if ($record.projectRoot -ne $resolvedRoot -or $record.name -ne $Name) {
        throw "Refusing to stop an untrusted $Name process record."
    }
    $process = Get-Process -Id ([int]$record.processId) -ErrorAction SilentlyContinue
    if ($null -ne $process) {
        $process.Refresh()
        $recordedStart = if ($record.startedUtc -is [DateTime]) {
            ([DateTime]$record.startedUtc).ToUniversalTime()
        } else {
            [DateTimeOffset]::Parse(
                [string]$record.startedUtc,
                [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::RoundtripKind
            ).UtcDateTime
        }
        $actualStart = $process.StartTime.ToUniversalTime()
        $sameStart = [Math]::Abs(($actualStart - $recordedStart).TotalSeconds) -lt 2
        $samePath = [string]::Equals($process.Path, [string]$record.processPath,
            [StringComparison]::OrdinalIgnoreCase)
        $identity = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)" `
            -ErrorAction SilentlyContinue
        $sameCommand = $null -ne $identity -and
            [string]::Equals([string]$identity.CommandLine, [string]$record.commandLine,
                [StringComparison]::Ordinal)
        if (-not $sameStart -or -not $samePath -or -not $sameCommand) {
            throw "Refusing to stop PID $($record.processId): process identity changed."
        }
        & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID $process.Id /T /F | Out-Null
        if ($LASTEXITCODE -ne 0 -and (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
            throw "Unable to stop recorded $Name process."
        }
    }
    Remove-Item -LiteralPath $recordPath -Force
}

Export-ModuleMember -Function @(
    'Assert-AINovelWindowsHost',
    'Read-AINovelLiteralEnvironment',
    'Protect-AINovelRunDirectory',
    'Protect-AINovelRunSubdirectory',
    'Assert-AINovelPortAvailable',
    'Assert-AINovelTcpEndpoint',
    'Start-AINovelRecordedProcess',
    'Wait-AINovelHttpHealth',
    'Stop-AINovelRecordedProcess'
)
