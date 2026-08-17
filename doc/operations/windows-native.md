# Windows-native on-demand operations

Use PowerShell 7 from the repository checkout. These entrypoints run the
application directly on Windows; they do not invoke WSL or container tooling.

| Operation | Command |
| --- | --- |
| Build | `.\scripts\windows\Build-Local.ps1` |
| Start | `.\scripts\windows\Start-Local.ps1 -EnvironmentFile <private-env-file>` |
| Status | `.\scripts\windows\Get-LocalStatus.ps1` |
| Stop | `.\scripts\windows\Stop-Local.ps1` |
| L1/L2 checks | `.\scripts\windows\Test-Local.ps1 -Level L1` / `-Level L2` |

`Start-Local.ps1` starts only the requested local application components,
requires a 2xx response from backend
`/api/actuator/health/readiness` and the frontend root, and records owned process IDs
under `%LOCALAPPDATA%\Aienie\native-runs\ainovel`. `Stop-Local.ps1` stops only
processes whose recorded PID and start time still match. Run `Build-Local.ps1`
before the first native start so frontend dependencies are present.

The environment file is runtime-private and is never copied into the
repository. Browser L4 workflows are intentionally separate: start a verified
runtime first, then run the matrix-owned browser acceptance flow against it.
Successful start, stop, status, and test commands also update the shared
product operational-state metric when its configured writer is available.

The native launcher always uses the shared service plane on the VM identified
operationally by `ssh aienie-wsl`: MySQL `23306`, Redis `26379`, Qdrant
`26333`, and the user/pay/AI TLS gRPC ingresses `12001/12021/12011`. The
application does not SSH at runtime. Its gRPC clients load the fixed Aienie
local CA root from `C:\ProgramData\AieniePki\pki\root`; plaintext and direct
VM container ports are not local-start fallbacks.
