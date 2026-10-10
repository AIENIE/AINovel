[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$windowsRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
foreach ($name in @('Build-Local.ps1', 'Test-Local.ps1', 'Start-Local.ps1', 'Get-LocalStatus.ps1', 'Stop-Local.ps1', 'Invoke-Local.ps1', 'LocalRuntime.psm1')) {
    $tokens = $null
    $errors = $null
    [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $windowsRoot $name), [ref]$tokens, [ref]$errors)
    if ($errors.Count -ne 0) { throw "PowerShell parse failed: $name" }
}

$startText = [IO.File]::ReadAllText((Join-Path $windowsRoot 'Start-Local.ps1')) +
    [IO.File]::ReadAllText((Join-Path $windowsRoot 'Invoke-Local.ps1'))
$statusText = [IO.File]::ReadAllText((Join-Path $windowsRoot 'Get-LocalStatus.ps1')) +
    [IO.File]::ReadAllText((Join-Path $windowsRoot 'Invoke-Local.ps1'))
$moduleText = [IO.File]::ReadAllText((Join-Path $windowsRoot 'LocalRuntime.psm1'))
$runtimeText = $startText + $statusText + $moduleText
# Exercise the exact launcher policy without executing its main action or loading runtime secrets.
$launcherAst = [Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $windowsRoot 'Invoke-Local.ps1'), [ref]$null, [ref]$null)
$versionPolicy = $launcherAst.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -ceq 'Assert-FrontendToolchainVersions'
}, $true)
if ($null -eq $versionPolicy) { throw 'The launcher has no enforced frontend version policy.' }
. ([scriptblock]::Create($versionPolicy.Extent.Text))
Assert-FrontendToolchainVersions -NodeVersion 'v22.23.2' -PnpmVersion '11.22.0'
foreach ($versions in @(@('v24.13.0', '11.22.0'), @('v22.23.2', '11.21.0'), @('', ''))) {
    $rejected = $false
    try { Assert-FrontendToolchainVersions -NodeVersion $versions[0] -PnpmVersion $versions[1] }
    catch { $rejected = $_.Exception.Message -match 'requires' }
    if (-not $rejected) { throw 'The launcher accepted a mismatched frontend toolchain.' }
}
if ($startText -notmatch "(?s)if \(\`$Action -in @\('Build', 'Test', 'Start'\).*?Assert-FrontendToolchain") {
    throw 'The frontend version policy is not applied before build/test/start.'
}
$localTrust = Join-Path $windowsRoot 'local-trust\localcert-root-ca.crt'
$localTrustItem = Get-Item -LiteralPath $localTrust -ErrorAction Stop
if (($localTrustItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
        ([Security.Cryptography.X509Certificates.X509Certificate2]::new($localTrustItem.FullName)).Thumbprint -cne
        'ABB779409203615F6864BC73A32A983B91E9D081') {
    throw 'AINovel local gRPC trust root is missing or differs from LocalCert.'
}
if (($startText + $statusText) -match '(?i)Set-AienieProductOperationalState|AIENIE_PRODUCT_STATE_WRITER|config-center|release\.center|icacls|Get-Acl|Start-Direct\.ps1|Start-Native\.ps1') {
    throw 'AINovel local Start/Status must not depend on release, monitoring, ACL, or privileged launch automation.'
}
# Endpoint authority moved to the local config pair; launchers should not duplicate it.
$localConfiguration = [IO.File]::ReadAllText((Join-Path $windowsRoot '../../backend/src/main/resources/application-local.yml'))
foreach ($domain in @('localmysql.testhut.top', 'localredis.testhut.top', 'localqdrant.testhut.top',
        'localuserservice.testhut.top', 'localpayservice.testhut.top', 'localaiservice.testhut.top')) {
    if ($localConfiguration.IndexOf($domain, [StringComparison]::Ordinal) -lt 0) {
        throw "AINovel local dependency configuration is missing: $domain"
    }
}
foreach ($required in @(
        '[''SPRING_PROFILES_ACTIVE''] = ''local''',
        '[''AIENIE_RUNTIME_PLANE''] = ''windows-local''',
        'Remove-AienieProcessInjectionEnvironment',
        'Complete-AienieManagedProcessRecord',
        '''backend\pom.xml''',
        'Directory = Join-Path $repoRoot ''frontend''',
        '--maxWorkers=2'
)) {
    if ($runtimeText.IndexOf($required, [StringComparison]::Ordinal) -lt 0) {
        throw "AINovel local contract is missing: $required"
    }
}
if ($moduleText.IndexOf("LocalAddress -notin @('127.0.0.1', '::1')", [StringComparison]::Ordinal) -lt 0 -or
    $moduleText.IndexOf('Test-AienieProcessTreeContains', [StringComparison]::Ordinal) -lt 0) {
    throw 'AINovel listener ownership must reject wildcard addresses and prove root-tree ownership.'
}
if ($runtimeText -match '(?i)(?<![a-z0-9-])(?:base|userservice|payservice|aiservice)\.testhut\.top') {
    throw 'AINovel local startup contains a staging endpoint.'
}

Import-Module (Join-Path $windowsRoot 'LocalRuntime.psm1') -Force
. (Join-Path $windowsRoot '../config-pair/ConfigurationPair.ps1')
$localValues = Read-ConfigPairValues -ProjectRoot (Resolve-Path (Join-Path $windowsRoot '../..')).Path
foreach ($entry in @{ ENV='local'; APP_ENV='local'; SERVER_ADDRESS='127.0.0.1'; MYSQL_HOST='localmysql.testhut.top'; REDIS_HOST='localredis.testhut.top' }.GetEnumerator()) {
    if ($localValues[$entry.Key] -cne $entry.Value) { throw "Local config pair mismatch: $($entry.Key)" }
}
if ([string]::IsNullOrWhiteSpace($localValues['EXTERNAL_GRPC_TRUST_CERT_COLLECTION'])) { throw 'Local gRPC trust path is not configured.' }
Assert-AienieLocalOnlyEnvironment -Values $localValues
Assert-AienieLocalOnlyEnvironment -Values @{
    ENV = 'local'
    APP_ENV = 'local'
    SPRING_PROFILES_ACTIVE = 'local'
    AIENIE_RUNTIME_PLANE = 'windows-local'
}
foreach ($selector in @(
        @{ ENV = 'test' },
        @{ APP_ENV = 'production' },
        @{ SPRING_PROFILES_ACTIVE = 'staging' },
        @{ AIENIE_RUNTIME_PLANE = 'local' },
        @{ AIENIE_RUNTIME_PLANE = 'container' })) {
    $rejected = $false
    try { Assert-AienieLocalOnlyEnvironment -Values $selector } catch { $rejected = $_.Exception.Message -match 'rejects' }
    if (-not $rejected) { throw 'AINovel accepted a non-local runtime selector.' }
}
foreach ($name in @(
        'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS',
        'MAVEN_OPTS', 'SPRING_APPLICATION_JSON', 'SPRING_CONFIG_IMPORT')) {
    $rejected = $false
    try { Assert-AieniePrivateEnvironmentKeyAllowed -Name $name } catch { $rejected = $_.Exception.Message -match 'protected process variable' }
    if (-not $rejected) { throw "AINovel private environment accepted process injection key: $name" }
}
$poisoned = @{
    SAFE_VALUE = 'kept'
    JAVA_TOOL_OPTIONS = '-javaagent:untrusted.jar'
    JDK_JAVA_OPTIONS = '--add-opens=all'
    _JAVA_OPTIONS = '-Doverride=true'
    MAVEN_OPTS = '-Dmaven.ext.class.path=untrusted.jar'
    SPRING_APPLICATION_JSON = '{"spring":{"profiles":{"active":"production"}}}'
    SPRING_CONFIG_IMPORT = 'file:C:\untrusted.yml'
}
Remove-AienieProcessInjectionEnvironment -Values $poisoned
if ($poisoned.Count -ne 1 -or $poisoned['SAFE_VALUE'] -cne 'kept') {
    throw 'AINovel ambient process injection sanitization is incomplete.'
}

$started = [DateTime]::UtcNow
$expected = [pscustomobject]@{
    ProcessId = 4242
    StartedUtc = $started.ToString('o')
    ExecutablePath = 'C:\Tools\java.exe'
    CommandLine = '"C:\Tools\java.exe" -f "D:\repo\pom.xml" spring-boot:run'
}
$same = [pscustomobject]@{
    ProcessId = 4242
    StartedUtc = $started.ToString('o')
    ExecutablePath = 'C:\Tools\java.exe'
    CommandLine = $expected.CommandLine
}
if (-not (Test-AienieProcessIdentitySnapshot -Expected $expected -Actual $same)) {
    throw 'AINovel exact process identity fixture was rejected.'
}
$jsonRoundTrip = $expected | ConvertTo-Json | ConvertFrom-Json
if (-not (Test-AienieProcessIdentitySnapshot -Expected $jsonRoundTrip -Actual $same)) {
    throw 'AINovel JSON DateTime coercion changed the exact process identity.'
}
$pidReuse = $same.PSObject.Copy()
$pidReuse.StartedUtc = $started.AddMinutes(1).ToString('o')
if (Test-AienieProcessIdentitySnapshot -Expected $expected -Actual $pidReuse) {
    throw 'AINovel accepted a reused PID fixture.'
}
$impostor = $same.PSObject.Copy()
$impostor.CommandLine = '"C:\Tools\java.exe" -f "D:\other\pom.xml" spring-boot:run'
if (Test-AienieProcessIdentitySnapshot -Expected $expected -Actual $impostor) {
    throw 'AINovel accepted a same-window command impostor.'
}

$utf8 = [Text.UTF8Encoding]::new($false, $true)
if (-not (Test-AienieStrictJsonHealthPayload -StatusCode 200 -BodyBytes $utf8.GetBytes('{"status":"UP"}'))) {
    throw 'AINovel strict health rejected valid UP.'
}
foreach ($invalid in @(
        '{"details":{"status":"UP"}}',
        '{"status":"DOWN"}',
        '{"status":"UP","status":"UP"}')) {
    if (Test-AienieStrictJsonHealthPayload -StatusCode 200 -BodyBytes $utf8.GetBytes($invalid)) {
        throw "AINovel strict health accepted invalid JSON: $invalid"
    }
}

$previousWriter = $env:AIENIE_PRODUCT_STATE_WRITER
$previousLocalAppData = $env:LOCALAPPDATA
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('ai-novel-local-contract-' + [Guid]::NewGuid().ToString('N'))
try {
    [IO.Directory]::CreateDirectory($testRoot) | Out-Null
    $env:LOCALAPPDATA = $testRoot
    $env:AIENIE_PRODUCT_STATE_WRITER = 'this-path-must-never-be-used'
    & (Join-Path $windowsRoot 'Get-LocalStatus.ps1') | Out-Null
} finally {
    if ($null -eq $previousWriter) { Remove-Item Env:AIENIE_PRODUCT_STATE_WRITER -ErrorAction SilentlyContinue }
    else { $env:AIENIE_PRODUCT_STATE_WRITER = $previousWriter }
    $env:LOCALAPPDATA = $previousLocalAppData
    if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}
Write-Output 'AINovel Windows local launcher contract passed.'
