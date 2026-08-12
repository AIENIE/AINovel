[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$EnvFile,
    [Parameter(Mandatory)][ValidateRange(30, 300)][int]$StartupTimeoutSeconds
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $PSScriptRoot 'AINovelNativeRun.psm1') -Force
Assert-AINovelWindowsHost

$configuration = Read-AINovelLiteralEnvironment -Path $EnvFile
$runtime = Protect-AINovelRunDirectory -ProjectRoot $projectRoot
$logDirectory = Protect-AINovelRunSubdirectory -ProjectRoot $projectRoot -Name 'logs'
$recordDirectory = Protect-AINovelRunSubdirectory -ProjectRoot $projectRoot -Name 'records'
$noProxy = '127.0.0.1,localhost,172.20.0.2'
$forcedBackendEnvironment = @{
    MYSQL_HOST = '172.20.0.2'
    MYSQL_PORT = '23306'
    MYSQL_DB = 'ainovel'
    REDIS_HOST = '172.20.0.2'
    REDIS_PORT = '26379'
    QDRANT_HOST = 'http://172.20.0.2'
    QDRANT_PORT = '26333'
    USER_HTTP_ADDR = 'http://127.0.0.1:10000'
    USER_GRPC_ADDR = 'static://127.0.0.1:10001'
    AI_GRPC_ADDR = 'static://127.0.0.1:10011'
    PAY_GRPC_ADDR = 'static://127.0.0.1:10021'
    EXTERNAL_GRPC_TLS_ENABLED = 'false'
    EXTERNAL_GRPC_PLAINTEXT_ENABLED = 'true'
    SSO_CALLBACK_ORIGIN = 'http://127.0.0.1:11040'
    APP_SECURITY_CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:11040'
    ADMIN_TRUSTED_ORIGINS = 'http://127.0.0.1:11040'
    ADMIN_SESSION_COOKIE_SECURE = 'false'
    NO_PROXY = $noProxy
    SERVER_ADDRESS = '127.0.0.1'
    PORT = '11041'
    BACKEND_PORT = '11041'
    APP_LOG_DIR = $logDirectory
    APP_RECORD_DIR = $recordDirectory
    LOGGING_FILE_NAME = (Join-Path $logDirectory 'ainovel.log')
}

foreach ($port in 11040, 11041) {
    Assert-AINovelPortAvailable -Port $port
}
foreach ($endpoint in @(
        @('172.20.0.2', 23306), @('172.20.0.2', 26379), @('172.20.0.2', 26333),
        @('127.0.0.1', 10000), @('127.0.0.1', 10001), @('127.0.0.1', 10010),
        @('127.0.0.1', 10011), @('127.0.0.1', 10020), @('127.0.0.1', 10021)
    )) {
    Assert-AINovelTcpEndpoint -HostName $endpoint[0] -Port $endpoint[1]
}

$frontendDist = Join-Path $projectRoot 'frontend\dist\index.html'
if (-not (Test-Path -LiteralPath $frontendDist -PathType Leaf)) {
    throw 'frontend/dist is missing. Run npm --prefix frontend run build first.'
}
$maven = (Get-Command mvn -ErrorAction Stop).Source
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$java = (Get-Command java.exe -ErrorAction Stop).Source
$baseEnvironment = @{}
foreach ($key in @(
        'SystemRoot', 'WINDIR', 'COMSPEC', 'PATHEXT', 'TEMP', 'TMP',
        'USERPROFILE', 'LOCALAPPDATA', 'APPDATA', 'PROGRAMDATA',
        'SystemDrive', 'HOMEDRIVE', 'HOMEPATH'
    )) {
    $value = [Environment]::GetEnvironmentVariable($key, [EnvironmentVariableTarget]::Process)
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        $baseEnvironment[$key] = $value
    }
}
$pathDirectories = @(
    (Split-Path -Parent $maven),
    (Split-Path -Parent $npm),
    (Split-Path -Parent $java),
    (Join-Path $env:SystemRoot 'System32'),
    (Join-Path $env:SystemRoot 'System32\Wbem'),
    $env:SystemRoot
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique
$baseEnvironment['PATH'] = $pathDirectories -join [IO.Path]::PathSeparator
$baseEnvironment['NO_PROXY'] = $noProxy
$backendEnvironment = $baseEnvironment.Clone()
foreach ($entry in $configuration.GetEnumerator()) {
    $backendEnvironment[$entry.Key] = $entry.Value
}
foreach ($entry in $forcedBackendEnvironment.GetEnumerator()) {
    $backendEnvironment[$entry.Key] = $entry.Value
}
$frontendEnvironment = $baseEnvironment.Clone()
$backend = $null
$frontend = $null
try {
    $backend = Start-AINovelRecordedProcess -Name 'backend' -ProjectRoot $projectRoot `
        -Executable $maven -Arguments @('-q', '-f', 'backend\pom.xml', 'spring-boot:run') `
        -Environment $backendEnvironment
    $backendEnvironment.Clear()
    $configuration.Clear()
    Wait-AINovelHttpHealth -Uri 'http://127.0.0.1:11041/api/actuator/health/readiness' `
        -Process $backend.Process -TimeoutSeconds $StartupTimeoutSeconds

    $frontend = Start-AINovelRecordedProcess -Name 'frontend' -ProjectRoot $projectRoot `
        -Executable $npm -Arguments @('--prefix', 'frontend', 'run', 'preview', '--', '--host', '127.0.0.1',
            '--port', '11040', '--strictPort') -Environment $frontendEnvironment
    Wait-AINovelHttpHealth -Uri 'http://127.0.0.1:11040' `
        -Process $frontend.Process -TimeoutSeconds $StartupTimeoutSeconds

    Write-Output 'AINovel is available at http://127.0.0.1:11040'
    Write-Output "Runtime logs: $runtime"
} catch {
    $startupFailure = $_
    $cleanupFailures = [Collections.Generic.List[object]]::new()
    if ($null -ne $frontend) {
        try {
            Stop-AINovelRecordedProcess -Name 'frontend' -ProjectRoot $projectRoot
        } catch {
            $cleanupFailures.Add($_)
        }
    }
    if ($null -ne $backend) {
        try {
            Stop-AINovelRecordedProcess -Name 'backend' -ProjectRoot $projectRoot
        } catch {
            $cleanupFailures.Add($_)
        }
    }
    if ($cleanupFailures.Count -gt 0) {
        throw "AINovel startup failed and cleanup reported $($cleanupFailures.Count) error(s): $($startupFailure.Exception.Message)"
    }
    throw $startupFailure
} finally {
    $configuration.Clear()
    $backendEnvironment.Clear()
}
