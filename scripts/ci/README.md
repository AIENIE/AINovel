# Aienie 仓库两阶段构建契约

当前发版中心按 catalog 执行 Fetch → 镜像 Build/Push → Publish，控制面执行 SSH 部署和健康检查；它不调用本仓库的两阶段脚本。2026-10-10 stag 恢复经任务确认使用现有平台流程，不能将该次 Jenkins 构建表述为下面的 Resolve/离线 Build 或安全扫描已执行。AINovel 的完整 L2 和隔离 MySQL 验证由本地入口单独完成，当前 catalog 验证器未覆盖 AINovel。

以下是仓库保留的 `scripts/ci/build-release.sh` 契约。恢复平台对该入口的调用需要独立的平台变更，不得通过手工部署绕过发版中心。入口只接受一个位置参数，且必须与 `AIENIE_CI_OUTPUT_DIR` 指向同一目录。

## Resolve

Resolve 节点可以访问批准的软件仓库，但不能持有业务配置或发布凭据：

```bash
AIENIE_CI_PHASE=resolve \
AIENIE_CI_SOURCE_COMMIT=<immutable-commit> \
AIENIE_CI_TARGET_ARCHITECTURE=linux/arm64 \
AIENIE_CI_CACHE_DIR=<empty-cache-dir> \
AIENIE_CI_OUTPUT_DIR=<empty-resolve-output-dir> \
AIENIE_DEPENDENCY_MANIFEST=<resolve-output-dir>/repository-dependency-manifest.json \
scripts/ci/build-release.sh <resolve-output-dir>
```

该阶段解析并缓存依赖，执行安全审计，并生成 schema 为
`aienie-repository-dependency-manifest-v1` 的清单。清单绑定源码提交、目标架构、模块、工具链、
依赖输入文件 SHA-256、安全报告 SHA-256 和缓存清单摘要。缺锁文件、空缓存、错误架构、审计未完成或重复输出均会失败关闭。

所有 PNPM 模块执行真实 `pnpm audit --json`（包含开发依赖）。Maven 模块在 resolve 中以
`-DskipTests package` 生成待审 JAR，不启动应用或加载运行配置；扫描器将每个 `BOOT-INF/lib`
依赖的 SHA-256 与 Maven 缓存匹配，提取 Maven 坐标、内嵌元数据和 gRPC shaded Netty 的实际版本，
通过 OSV HTTPS API 查询这些包坐标。扫描器不上传源码、JAR、业务数据或秘密。

`scripts/ci/dependency-security.py` 只依赖 Python 3 标准库，公网依赖元数据源为
`https://api.osv.dev/v1/` 和 PNPM 已配置 registry 的 audit API，需在 resolve 网络策略中允许。
网络失败、响应缺字段、空组件库存、未知/高危/严重风险均失败关闭；中低危仍记录。
原 `AIENIE_CI_NPM_AUDIT` 开关不能关闭本仓库的必选扫描，也不再能直接生成 `passed`。
`security-exceptions.json` 默认为空；例外必须精确绑定公告和包版本，具有责任人、理由及到期日，
到期失败。不得用通配包或永久例外消除风险。

报告保存在缓存 `security/{module}.json`，绑定源码提交、POM/锁文件、PNPM 工作区配置与扫描器/策略哈希。
离线 build 只验证原始报告和报告哈希，不再访问 OSV/registry；编译后及最终 bundle 再逐项对比实际
JAR 内库的 SHA-256 与坐标，防止发版辅助脚本换入未经扫描的依赖。
平台镜像、OS 包及没有 Maven 身份的内嵌本机二进制仍需平台 SCA，不能由本检查宣称全部覆盖。

扫描器本地定向测试：`python scripts/ci/test_dependency_security.py`。覆盖网络/空响应/未知库存失败、
shaded Netty 识别、报告绑定、到期例外及离线无网络验证。两阶段 Linux 全链验证仍须在规范 CI 节点执行。

## Build

Build 节点必须断网，并消费 Resolve 的原样缓存和清单：

```bash
AIENIE_CI_PHASE=build \
AIENIE_CI_NETWORK_MODE=offline \
AIENIE_CI_SOURCE_COMMIT=<same-immutable-commit> \
AIENIE_CI_TARGET_ARCHITECTURE=linux/arm64 \
AIENIE_CI_CACHE_DIR=<read-only-resolved-cache-dir> \
AIENIE_CI_OUTPUT_DIR=<payload-dir-containing-only-manifest> \
AIENIE_DEPENDENCY_MANIFEST=<payload-dir>/repository-dependency-manifest.json \
scripts/ci/build-release.sh <payload-dir>
```

平台必须先移除缓存整树和清单的全部写位；payload 目录初始只能含固定名称、只读的仓库清单。
Build 在执行仓库 L2 编译/测试前重新验证清单、工具链、锁文件、缓存摘要和只读模式，并把缓存
复制到会在退出时清理的工作目录；Maven 使用 `-o`，npm/pnpm 使用各自的离线模式。最终再次
核对原始缓存与清单未变化，清单保留在 payload 根供平台复核并由平台在封包前移除，工作缓存不
进入产物。任何缺失输入、写权限或联网降级都会直接失败。运行时配置、秘密、证书和 Config
Center 文件都不是这两个阶段的输入，也不得进入产物。

## Production bundle

Build 阶段设置 `AIENIE_RELEASE_ENVIRONMENT=production` 后，入口生成 production Compose、
`release/production-runtime-contract.json` 和完整 Flyway ledger。可写目录只允许位于
`/srv/aienie-products/ai-novel`，版本化 artifact 从 release root 只读挂载；生产包不挂载本地
`/etc` CA，数据库、Redis、Qdrant 和公共服务均使用审核过的 `seekerhut.com` TLS authority。
夜间备份只包含 records；logs 按 `operational-log` 分类排除，env/admin 等保护配置不在备份集合内。
production bundle 固定携带 `frontend/nginx.conf`，以非特权容器端口 10010 提供 SPA 和 `/healthz`。

## 本仓库模块

- Maven: `backend`
- PNPM: `frontend`（Node.js `22.23.2`、PNPM `11.22.0`；Windows Build/Test/Start 入口同样强制检查）
