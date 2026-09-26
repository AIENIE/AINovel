# 审计验证记录

审计日期：2026-09-26。本记录由主审统一整理；发现详情与复测设计见三个专题报告。本次只新增审计文档、扫描证据和隔离复现代码，没有修复业务实现。

## 基线与方法

- Git：`develop`，HEAD `2a1fb98a9e85d9de9feee6e2d820ce7961797a82`。
- 审计对象为工作区：开始时已有 `doc/operations/verification.md`、`env.example`、`scripts/windows/Invoke-Local.ps1`、`scripts/windows/tests/Test-LocalContract.ps1` 的修改，以及未跟踪 `scripts/windows/local-trust/`。这些内容未被本次改写。文件哈希见 [verification-baseline.json](evidence/verification-baseline.json)。
- 控制器：Windows `Win32NT`，PowerShell 7.6.6。CodeGraph 1.5.0 的 `.codegraph-win` 索引匹配当前项目，complete、819 个索引文件、17,114 个节点、40,814 条边，查询时没有待同步变更。
- 三名独立 agent 分别审计安全、性能与稳定性、可读性与可维护性；主审复核关键代码路径、证据和结论边界。CodeGraph 用于结构定位，实际源码、配置、SQL 和测试用于确认。
- 检查不等于逐行形式证明。文件规模不是覆盖率；未执行全量自动污点分析、全量 Maven SCA、历史秘密扫描或生产渗透。

## 已执行验证

| 项目 | 命令/方式 | 结果与边界 |
|---|---|---|
| Windows 标准入口 | `./scripts/windows/Test-Local.ps1 -Level L2` | 退出码 0；入口分派、6 项 endpoint mock、local contract 均通过 |
| 后端编译打包 | 上述入口内部执行 Maven package，跳过测试后另跑 test | 通过；未启动业务服务 |
| 后端测试 | 上述入口内部执行 Maven test | 120 个 suite，409 项：392 通过、17 条件跳过、0 failure、0 error |
| 前端首次检查 | 同一标准入口 | lint、typecheck、build、40 文件/169 测试通过；当时系统 Node 为 24.13.0，与仓库要求不同，因此另用 Node 22 补验 |
| 前端匹配工具链补验 | `./scripts/windows/Test-Local.ps1 -Component Frontend -Level L2`，当前进程 PATH 优先使用已安装 Node 22.23.2 | 退出码 0；lint、typecheck、build、40 文件/169 项测试全部通过；见 [前端日志](evidence/frontend-node22-l2.txt) 和 [机器摘要](evidence/frontend-verification.json)。没有改全局 Node、项目脚本或锁文件 |
| 前端依赖安全 | `pnpm audit --json` | 退出码 1，55 条命中：Critical 1 / High 27 / Moderate 26 / Low 1；不是 55 个独立产品漏洞，详见安全报告 |
| 后端依赖取证 | 读取本轮 JAR 的 `BOOT-INF/lib` 目录 | 确认实际依赖版本；核实 protobuf 官方公告，未完成所有后端组件漏洞关联 |
| 跨稿件缓存复现 | 真实 React hook + renderHook，网络与 writing session mock | 1 项复现断言通过：A 的草稿被提交给 B 的保存接口；这个“通过”证明缺陷存在，不表示已修复 |

后端结果来自本轮 15:17:06–15:17:35 更新的 Surefire XML，而非引用历史路线图中的通过数字。各 suite 的名称、数量和报告时间已保存到 [verification-baseline.json](evidence/verification-baseline.json)。

17 项跳过分别为：ExternalMySqlMigrationVerificationTest 4 项、FlywaySchemaGovernanceTest 8 项、ProductionNovelMigrationExternalMySqlTest 1 项、H2FrozenRetryExportTest 1 项、NarrativeServiceTest 的条件导出 2 项、QualityRegressionOfflineTest 1 项。它们不计入通过数。Windows 常规 L2 没有证明真实 MySQL 迁移或模型输出验收通过。

前端构建记录的主要 JS 分块：UserEntry 1,700.36 kB（gzip 474.56 kB）、textarea 308.01 kB（gzip 90.83 kB）、index 144.37 kB（gzip 46.66 kB）、AdminEntry 83.97 kB（gzip 24.47 kB）。这是本次产物大小，不能转换成未实测的浏览器首屏耗时、吞吐量或性能 SLA。

构建/测试中出现大分块、Browserslist 数据陈旧、React Router future flag、JDK 原生访问/Unsafe 的警告；这些不是本轮测试失败。故意覆盖异常路径的后端测试还会记录 ERROR，结果以 Surefire 的 failure/error 为准。

## 复现与证据使用

- [draft-isolation.repro.test.tsx](evidence/draft-isolation.repro.test.tsx)：验证现有串稿行为的独立测试，不属于生产测试集。
- [vitest.audit.config.mjs](evidence/vitest.audit.config.mjs)：限定只运行上述审计测试；不启动 Vite 服务。
- [draft-isolation-result.json](evidence/draft-isolation-result.json)：本轮机器结果。
- [pnpm-audit.json](evidence/pnpm-audit.json)：在线依赖公告查询结果。没有发送源码、配置秘密或业务数据。
- [backend-packaged-libraries.txt](evidence/backend-packaged-libraries.txt)：最终后端产物内的库名清单。

在仓库根、已安装依赖和匹配 Node 的条件下，可重跑隔离复现：

```powershell
node frontend/node_modules/vitest/vitest.mjs run --config doc/versions/v1.0/audit/evidence/vitest.audit.config.mjs --reporter=verbose
```

修复串稿后，应把该测试改为断言稿件隔离，并纳入正式测试，而不是继续让“复现缺陷”的断言通过。

## 未执行事项

未启动或停止现有业务服务，未部署，未使用本地 Docker/WSL，未连接真实业务数据库或调用付费 AI，未访问真实用户作品或运行秘密。未进行浏览器 L4、真实 MySQL 升级、负载/压力测试、网络故障注入、恶意 protobuf 输入或开发服务漏洞利用。系统矩阵验证、网关/TLS 的运行态验证也未执行；本审计不构成任何环境的上线认证。

本次不改变 `doc/roadmap.md` 的阶段状态和 G2/H 系列门槛；整改建议是审计输入，不代表相应功能或验收阶段已经完成。
