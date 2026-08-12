[CmdletBinding()]
param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\..\env.txt'),
    [switch]$NativeWorker
)

Set-StrictMode -Version Latest

if (-not $NativeWorker) {
    $shellName = if ($PSVersionTable.PSEdition -eq 'Core') { 'pwsh.exe' } else { 'powershell.exe' }
    $shellPath = Join-Path $PSHOME $shellName
    if (-not (Test-Path -LiteralPath $shellPath -PathType Leaf)) {
        $shellPath = (Get-Command -Name $shellName -CommandType Application -ErrorAction Stop | Select-Object -First 1).Path
    }
    $resolvedEnvironmentFile = if (Test-Path -LiteralPath $EnvironmentFile -PathType Leaf) { (Resolve-Path -LiteralPath $EnvironmentFile).Path } else { $EnvironmentFile }
    & $shellPath -NoLogo -NoProfile -File $PSCommandPath -EnvironmentFile $resolvedEnvironmentFile -NativeWorker
    $workerExitCode = $LASTEXITCODE
    if ($null -ne $workerExitCode -and $workerExitCode -ne 0) {
        exit $workerExitCode
    }
    return
}

$modulePath = Join-Path $PSScriptRoot 'Native-Localbase.psm1'
Import-Module -Name $modulePath -Force

$projectRoot = Get-NativeProjectRoot
$stateDirectory = Get-NativeStateDirectory -ProjectRoot $projectRoot
$environmentNames = @(
    'DB_URL', 'MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_DB', 'MYSQL_USER', 'MYSQL_PASSWORD',
    'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD', 'REDIS_KEY_PREFIX',
    'QDRANT_HOST', 'QDRANT_PORT', 'QDRANT_ENABLED',
    'PORT', 'BACKEND_PORT', 'SERVER_PORT', 'SERVER_ADDRESS',
    'USER_HTTP_ADDR', 'USER_GRPC_ADDR', 'AI_GRPC_ADDR', 'PAY_GRPC_ADDR',
    'EXTERNAL_AI_HMAC_CALLER', 'EXTERNAL_AI_HMAC_SECRET',
    'EXTERNAL_USER_INTERNAL_GRPC_TOKEN', 'EXTERNAL_PAY_SERVICE_JWT'
)

Import-LiteralEnvironmentFile -Path $EnvironmentFile -ClearNames $environmentNames
Set-LocalbaseJdbcMySqlEndpoint -EnvironmentName 'DB_URL'
$env:MYSQL_HOST = 'localbase.testhut.top'
$env:MYSQL_PORT = '23306'
$env:REDIS_HOST = 'localbase.testhut.top'
$env:REDIS_PORT = '26379'
$env:QDRANT_HOST = 'http://localbase.testhut.top'
$env:QDRANT_PORT = '26333'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:PORT = '11041'
$env:BACKEND_PORT = '11041'

Assert-NativePortAvailable -Port 11041
Assert-NativePortAvailable -Port 11040
Assert-LocalbasePorts -Ports @(23306, 26379, 26333)

$backendCommand = Get-RequiredNativeCommandPath -Name 'mvn.cmd'
$frontendCommand = Get-RequiredNativeCommandPath -Name 'npm.cmd'
$backendDirectory = Join-Path $projectRoot 'backend'
$frontendDirectory = Join-Path $projectRoot 'frontend'

try {
    $backendProcess = Start-NativeProcess -Name 'backend' -FilePath $backendCommand -ArgumentList @('-q', 'spring-boot:run') -WorkingDirectory $backendDirectory -StateDirectory $stateDirectory -ProjectRoot $projectRoot
    Wait-NativeLoopbackPort -Port 11041 -ExpectedRootProcessId $backendProcess.Id -Name 'backend' -StateDirectory $stateDirectory -ProjectRoot $projectRoot
    $frontendProcess = Start-NativeProcess -Name 'frontend' -FilePath $frontendCommand -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '11040', '--strictPort') -WorkingDirectory $frontendDirectory -StateDirectory $stateDirectory -ProjectRoot $projectRoot
    Wait-NativeLoopbackPort -Port 11040 -ExpectedRootProcessId $frontendProcess.Id -Name 'frontend' -StateDirectory $stateDirectory -ProjectRoot $projectRoot
}
catch {
    $failure = $_
    foreach ($name in @('frontend', 'backend')) {
        try {
            Stop-NativeProcess -Name $name -StateDirectory $stateDirectory -ProjectRoot $projectRoot
        }
        catch {
            Write-Warning "Could not clean up native $name after startup failure: $($_.Exception.Message)"
        }
    }
    throw $failure
}

Write-Host 'AINovel is running at http://127.0.0.1:11040. Logs and PID state are in .native-run.'
