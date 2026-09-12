# AINovel

AINovel 是一个前后端分离的 AI 小说创作业务项目。

- 前端：React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端：Java 25 + Spring Boot 3 + JPA + Flyway + Redis + gRPC 客户端
- 本地域名：`localainovel.testhut.top`
- 预发布域名：`ainovel.testhut.top`
- 生产环境目标域名：`ainovel.seekerhut.com`（切换验收完成前不是当前入口）
- 历史生产兼容域名：`ainovel.aienie.com`（仅作迁移兼容；切换验收后至少保留 12 个月，不作为新配置或默认入口）
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
- 场景上下文与生成归因：以 `scene-draft-v2` 编译跨章上下文，记录生成版本、上下文指纹、作者编辑和反馈状态，不保存原始提示词。
- 项目积分：本地项目专属积分、通用转专属、兑换码、流水和 AI 用量扣费。
- 管理后台：独立本地账号、密码优先 TOTP/本地单密码策略、服务端会话和操作级二次验证；覆盖运营、用户、素材、资产、质量、G2、积分、运维观测和维护模式。

## Windows 原生按需运行

Windows 本地开发与静态验证使用 PowerShell 7，不调用 WSL 或 Docker：

```powershell
.\scripts\windows\Build-Local.ps1
.\scripts\windows\Start-Local.ps1      # 后端+前端一次拉起；成功后自动在默认浏览器打开本地域名主页
.\scripts\windows\Get-LocalStatus.ps1
.\scripts\windows\Stop-Local.ps1
```

私有环境文件保留在仓库外。首次启动前先执行 Build；`Start-Local.ps1` 重复运行安全（已运行的组件自动跳过，`-NoBrowser` 跳过打开浏览器），并可从 Windows PowerShell 5.1 直接运行（自动转投 pwsh）。L1/L2 入口分别为
`.\scripts\windows\Test-Local.ps1 -Level L1` 和 `-Level L2`。完整边界见
[`doc/operations/windows-native.md`](doc/operations/windows-native.md)。

## 发版中心部署（Linux Docker）

Linux 服务器发布只通过发版中心执行仓库入口 `scripts/ci/build-release.sh`：Resolve 节点解析并缓存依赖，断网 Build 节点完成 L2 编译测试并按 `AIENIE_RELEASE_ENVIRONMENT` 组装 staging/production 运行时包，契约见 [`scripts/ci/README.md`](scripts/ci/README.md)。

运行时配置由 config-center 提供：发版中心把对应环境的 `env.txt` 以 `0600` 普通文件只读挂载进后端容器，仓库与发布产物不携带任何运行时配置、密钥或证书。MySQL、Redis、Qdrant、三服务、域名、证书和反向代理必须由目标环境提前提供。

## 验证

```bash
mvn -q -f backend/pom.xml test
cd frontend && corepack pnpm@11.22.0 install --frozen-lockfile && corepack pnpm@11.22.0 run test && corepack pnpm@11.22.0 run build
```

## 目录

- `frontend/`：React 前端。
- `backend/`：Spring Boot 后端、proto 与 Flyway 迁移。
- `doc/`：架构、API、运维、路线图、提案和研究文档。
- `user-doc/`：创作者与管理员使用手册。
- `scripts/ci/`：发版中心两阶段构建入口与 staging/production 运行时契约。
- `scripts/windows/`：Windows 原生按需 Build/Start/Status/Stop/Test 入口，与发版中心部署互相独立。
- `scripts/docker/`：发版运行时包使用的 staging/production 环境加载器。

研发文档见 [`doc/README.md`](doc/README.md)，用户手册见 [`user-doc/README.md`](user-doc/README.md)，后续工作见 [`doc/roadmap.md`](doc/roadmap.md)。

## 本地 L2 验证记录（2026-09-12）

Windows 根入口为 `start.ps1`，参数为 `-Action Start|Build|Test|Status|Stop`、`-Level L1|L2|L3`、`-Component All|Backend|Frontend`，默认 `Start/L2/All`。本地端口为前端 `11040`、后端 `11041`，主页为 `https://localainovel.testhut.top/`。

实际 L2 结果：退出码 `0`；后端 `336` 项通过、`12` 项条件跳过，前端 lint、类型检查、构建通过，Vitest `34` 文件/`136` 项通过。跳过项涉及外部 MySQL/Testcontainers 与另行授权的付费质量回归。VS Code 从包含 `start.ps1` 的仓库根目录打开，使用根任务启动调试；本轮前后端断点均已命中。完整启动的健康检查、HTTPS 与基础 API 已通过。

AISocialGame 的真人验收仍在进行中，已实现功能不等同于整体验收通过。
