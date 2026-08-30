# Windows local direct runtime

Use PowerShell 7 from the repository checkout. These five entrypoints run
AINovel directly on Windows and never call Config Center, the release plane,
or the monitoring state writer.

| Operation | Command |
| --- | --- |
| Build | `.\scripts\windows\Build-Local.ps1` |
| Start | `.\scripts\windows\Start-Local.ps1` |
| Status | `.\scripts\windows\Get-LocalStatus.ps1` |
| Stop | `.\scripts\windows\Stop-Local.ps1` |
| L1/L2 checks | `.\scripts\windows\Test-Local.ps1 -Level L1` / `-Level L2` |

The default private input is
`%LOCALAPPDATA%\Aienie\secrets\ainovel.env`. It must be a regular,
non-reparse file, but the local launcher does not change or require a special
Windows ACL, elevation, or UAC workflow. Override `-EnvironmentFile` when the
file is stored elsewhere. It supplies credentials and caller-auth material;
the launcher supplies only reviewed local routing values.

Startup accepts only exact local selectors and forces `ENV`, `APP_ENV`, and
`SPRING_PROFILES_ACTIVE` to `local` while identifying this distinct instance
as `AIENIE_RUNTIME_PLANE=windows-local`. A different runtime plane or a
`test`, `staging`, or `production` selector is rejected before a child process is created. The backend and
frontend bind to the documented Windows loopback ports `11041` and `11040`.
Readiness requires HTTP 200, plus top-level `status=UP` for the backend.
Owned PID state stays under `%LOCALAPPDATA%\Aienie\native-runs\ainovel`.

The Windows processes are one local application instance. Their shared data
and public AI/User/Pay dependencies are different instances hosted by the WSL
runtime identified operationally as `aienie-wsl`: `localbase.testhut.top` and
the three `local*.testhut.top` TLS services. The Java clients use the active
JDK/JVM trust store; the launcher does not inject a CA file, trust-all mode,
or plaintext public-service fallback.
