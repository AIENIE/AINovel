# AINovel

AINovel 是一个前后端分离的 AI 小说创作业务项目。

- 前端：React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端：Java 25 + Spring Boot 3 + JPA + Flyway + Redis + gRPC 客户端
- 本地域名：`localainovel.testhut.top`
- 预发布域名：`ainovel.testhut.top`
- 生产环境目标域名：`ainovel.seekerhut.com`（切换验收完成前不是当前入口）
- 前端端口：`11040`
- 后端端口：`11041`

## 当前能力

- 统一登录：通过 `/api/v1/sso/*` 中转 user-service SSO，不提供本地普通用户登录接口。
- G1 引导创作：从一句话开始，在故事、世界、角色和大纲四步生成三候选，支持编辑、跳过、自动后台推进和草稿恢复。
- 故事与工作台：故事卡、角色卡、章节场景、大纲、富文本正文与多稿件管理。
- 世界观：模块定义、字段精修、发布预检、缺失字段生成与失败重试。
- 素材：创建、TXT 导入、混合检索、审核、查重、人工合并和稿件引用查询。
- 文本质量：生成门禁、手动诊断、长篇 drift、剧情质量、候选修订与精雕模式。
- G2 盲测：匿名快速/精雕对照样本、邀请评审、作者隔离和失败退款。
- v2 能力：Lorebook、图谱、风格、角色声音、版本分支、导出、模型偏好和工作台设置。
- 项目积分：本地项目专属积分、通用转专属、兑换码、流水和 AI 用量扣费。
- 管理后台：独立本地账号、密码优先 TOTP/本地单密码策略、服务端会话和操作级二次验证；覆盖运营、用户、素材、资产、质量、G2、积分、运维观测和维护模式。

## Windows 本地运行

Windows 本地开发、测试和验收使用 PowerShell 7，不再依赖 WSL。请从项目根目录在 PowerShell 7 中创建默认安全配置，并立即将文件 ACL 收紧为启动脚本接受的三个主体：

```powershell
$secretDirectory = Join-Path $env:LOCALAPPDATA 'Aienie\secrets'
$secretFile = Join-Path $secretDirectory 'ainovel-localbase.env'
if (Test-Path -LiteralPath $secretFile) {
  throw "安全配置已存在，不会覆盖：$secretFile"
}
New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null
$currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$approvedSids = @($currentSid, 'S-1-5-18', 'S-1-5-32-544')
$icacls = Join-Path $env:SystemRoot 'System32\icacls.exe'
$directoryItem = Get-Item -LiteralPath $secretDirectory -Force
if (-not $directoryItem.PSIsContainer -or
    (($directoryItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
  throw '安全配置目录必须是非重解析的普通目录。'
}
$directoryAcl = Get-Acl -LiteralPath $secretDirectory
$directoryOwnerSid = $directoryAcl.GetOwner(
  [Security.Principal.SecurityIdentifier]).Value
if ($directoryOwnerSid -notin @($currentSid, 'S-1-5-32-544')) {
  throw '安全配置目录 owner 必须是当前用户或本机 Administrators；不会自动夺取所有权。'
}
& $icacls $secretDirectory '/grant:r' `
  ("*{0}:(OI)(CI)(F)" -f $currentSid) `
  '*S-1-5-18:(OI)(CI)(F)' `
  '*S-1-5-32-544:(OI)(CI)(F)' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '无法设置安全配置目录 ACL。' }
& $icacls $secretDirectory '/inheritance:r' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '无法关闭安全配置目录的 ACL 继承。' }
$unexpectedDirectorySids = @((Get-Acl -LiteralPath $secretDirectory).GetAccessRules(
    $true, $false, [Security.Principal.SecurityIdentifier]) |
  ForEach-Object { ([Security.Principal.SecurityIdentifier]$_.IdentityReference).Value } |
  Where-Object { $_ -notin $approvedSids } |
  Sort-Object -Unique)
foreach ($sid in $unexpectedDirectorySids) {
  & $icacls $secretDirectory '/remove' ("*{0}" -f $sid) | Out-Null
  if ($LASTEXITCODE -ne 0) { throw '无法移除安全配置目录的非批准 ACL。' }
}

Copy-Item -LiteralPath .\env.example -Destination $secretFile
$fileOwnerSid = (Get-Acl -LiteralPath $secretFile).GetOwner(
  [Security.Principal.SecurityIdentifier]).Value
if ($fileOwnerSid -notin @($currentSid, 'S-1-5-32-544')) {
  throw '安全配置文件 owner 必须是当前用户或本机 Administrators；不会自动夺取所有权。'
}
& $icacls $secretFile '/grant:r' `
  ("*{0}:(F)" -f $currentSid) `
  '*S-1-5-18:(F)' `
  '*S-1-5-32-544:(F)' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '无法设置安全配置文件 ACL。' }
& $icacls $secretFile '/inheritance:r' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '无法关闭安全配置文件的 ACL 继承。' }
$unexpectedFileSids = @((Get-Acl -LiteralPath $secretFile).GetAccessRules(
    $true, $false, [Security.Principal.SecurityIdentifier]) |
  ForEach-Object { ([Security.Principal.SecurityIdentifier]$_.IdentityReference).Value } |
  Where-Object { $_ -notin $approvedSids } |
  Sort-Object -Unique)
foreach ($sid in $unexpectedFileSids) {
  & $icacls $secretFile '/remove' ("*{0}" -f $sid) | Out-Null
  if ($LASTEXITCODE -ne 0) { throw '无法移除安全配置文件的非批准 ACL。' }
}
```

随后编辑 `$secretFile`，替换全部模板占位值。目录和文件的 owner 必须是当前 Windows 用户或本机 Administrators；目录必须关闭 ACL 继承，并以 `(OI)(CI)` 将 Full Control 只授予当前用户、SYSTEM 和本机 Administrators；文件必须是非重解析的普通文件、关闭 ACL 继承，并且恰好包含同三个主体的显式 Full Control 许可。初始化命令不会自动夺取所有权；启动脚本会拒绝错误 owner、额外主体、拒绝规则、缺失键和模板占位值。完整要求见 [`doc/operations/deployment.md`](doc/operations/deployment.md)。

本地依赖布局固定为：MySQL、Redis、Qdrant 位于 `172.20.0.2`；user-service、ai-service、pay-service 在本机 loopback 启动。三个服务和前端产物准备完成后启动 AINovel：

```powershell
npm --prefix frontend run build
.\scripts\windows\Start-Local.ps1
```

打开 `http://127.0.0.1:11040`。结束后只停止由本工作树记录的 AINovel 前后端进程：

```powershell
.\scripts\windows\Stop-Local.ps1
```

## 非 Windows Docker 部署

`build.sh` 只保留给非 Windows 主机执行 Docker Compose 构建与部署。该路径使用仓库内 Git 忽略、权限为 `0600` 的 `env.txt`，不作为 Windows 本地开发或验收入口。MySQL、Redis、Qdrant、三服务、域名、证书和反向代理仍由外部环境提前提供。

## 验证

```powershell
mvn -q -f backend/pom.xml test
npm --prefix frontend ci --legacy-peer-deps
npm --prefix frontend test
npm --prefix frontend run build
```

## 目录

- `frontend/`：React 前端。
- `backend/`：Spring Boot 后端、proto 与 Flyway 迁移。
- `doc/`：架构、API、运维、路线图、提案和研究文档。
- `user-doc/`：创作者与管理员使用手册。
- `scripts/windows/`：Windows 原生本地启动与停止入口。
- `build.sh`：非 Windows Docker 部署入口。
- `docker-compose.yml`：前后端容器编排。

研发文档见 [`doc/README.md`](doc/README.md)，用户手册见 [`user-doc/README.md`](user-doc/README.md)，后续工作见 [`doc/roadmap.md`](doc/roadmap.md)。
