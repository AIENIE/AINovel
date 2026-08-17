# AINovel v1.0 安全与稳定性审计总览

审计日期：2026-08-13

审计对象：`master` 分支当前工作树

审计结论：**REQUEST CHANGES**。在修复发布阻断、身份绑定、传输安全、正文并发覆盖、计费一致性与任务可恢复性问题前，不建议把当前工作树作为可发布基线。

## 1. 文档导航

| 专项 | 报告 | 发现数 |
| --- | --- | ---: |
| 代码安全 | [01-security-audit.md](01-security-audit.md) | 10 |
| 代码性能与稳定性 | [02-performance-stability-audit.md](02-performance-stability-audit.md) | 21 |
| 易读性与可维护性 | [03-maintainability-audit.md](03-maintainability-audit.md) | 10 |
| 冗余文件与无用代码 | [04-redundancy-dead-code-audit.md](04-redundancy-dead-code-audit.md) | 12 |

四份专项报告共记录 **53 项未去重发现**：高 19、中 19、低 14、提示 1。跨专项存在有意重叠，例如“AI 先调用后扣费”同时属于安全滥用面和稳定性/成本一致性问题，“伪异步导出”同时属于稳定性和职责边界问题。因此不能把 53 直接理解为 53 个互不相关的缺陷，也不能用数量代替风险排序。

## 2. 总体判断

项目已经具备多项可靠的安全与工程控制：JWT 本地签名及 issuer/audience 校验、user-service 会话 fail-closed 校验、资源所有权守卫、管理员 HttpOnly Cookie 与严格 Origin/Referer 过滤、运行时密钥预检、Flyway 校验与禁用 clean、`open-in-view=false`、日志轮转，以及针对管理员认证、资源访问、质量链和 v2 持久化的测试。这些控制使本轮没有把“全局关闭 CSRF”“配置文件中的示例默认值”或 Spring/JPA 隐式入口简单误判为可利用漏洞/死代码。

但当前仍有四组系统性风险：

1. **身份、额度与共享基础设施边界不够硬**：AI 调用在额度判断前发生且缺少请求大小、速率与并发限制；本地 SSO 账户在找不到 `remote_uid` 时按可变 username 回退并可重绑；MySQL、Redis、Qdrant 的默认共享连接未强制 TLS/认证。
2. **写入与异步任务缺少并发/恢复语义**：整稿正文以单个 JSON LOB 读改写且没有版本控制；G2 与通用 AI 队列在执行器拒绝时会永久卡住；活动任务采用非原子的 check-then-insert；积分兑换远端成功后的本地落账缺少完整 Saga 恢复。
3. **关键请求路径被远程调用和全量计算放大**：每个 Bearer 请求同步探测并调用 user-service；AI/计费调用处于长事务或共享 120 秒预算；素材治理全表/O(N²)；导出在轮询/下载请求线程重复整稿生成。
4. **验证与契约门禁失效**：前端 lint/typecheck 当前失败，v2/管理端大量使用 `Map<String,Object>` 与 `any`；核心控制器、hook 和 API client 职责过载；23 个前端生产候选文件不可达，并带来一批可清理依赖。

## 3. 整改优先级

### P0：提交或发布前阻断项

- 恢复 `build.sh`、`ci/build-release.sh`、`docker/load-env-file.sh`、`scripts/lib/deployment-env.sh`、`scripts/tests/deployment-env-test.sh` 的 `100755`；当前工作树仅存在 `100755 -> 100644` 模式变化，若按现状提交会破坏 Linux 直接执行契约。
- 修复正文 `sectionsJson` 的并发覆盖：优先拆成场景独立行；过渡期至少使用 `@Version`/ETag 或条件更新，让冲突显式失败而不是静默丢稿。
- AI 调用前完成额度预留/授权，限制消息数、单条/总字符数、用户速率与并发；最终 token 结算必须幂等，并对失败释放预留。
- 以不可变 `remote_uid` 作为本地账户唯一身份；禁止已有账户仅凭 username 重绑。上线前与 user-service 明确 username 唯一、改名、删除和复用契约，并迁移/隔离冲突记录。
- 为共享 MySQL、Redis、Qdrant 强制 TLS、认证与最小网络暴露；启动预检应验证这些安全属性，而不仅验证部分变量非空/不等于模板值。
- 为远端成功、本地失败的积分兑换补齐可重放的 Saga/outbox、稳定幂等键和对账补偿；不得用单次同步调用假设跨服务原子性。

### P1：近期稳定性与可恢复性

- 把 G2/通用 AI 任务改为数据库权威队列，使用租约/CAS 认领、周期重派和年龄告警；执行器拒绝必须清理本地占位并返回明确 429/503。
- 给活动任务增加数据库级唯一/互斥约束，消除 check-then-insert 并发重复生成与重复计费。
- 将 AI、计费等远程调用移出数据库事务，给不同依赖设置独立短预算、重试边界、熔断和连接池指标。
- 把导出改为真实后台任务，持久化不可变产物/checksum，并流式下载；轮询 GET 不得承担渲染工作。
- 缓存/合并用户会话验证和本地用户/维护状态读取，同时保持封禁、注销、维护模式的明确失效语义；readiness 应反映认证关键依赖是否可服务。
- 修复素材上传任务 owner 绑定与 GET 状态变更；修复导出模板创建路径的 owner/system 校验；统一复用严格的内部路径规范化，关闭 `//host` 开放跳转。

### P2：工程基线与清理

- 先修复 8 个 TypeScript 错误，再把 `lint + typecheck + test + build` 纳入 CI；治理 578 个 `no-explicit-any` 时按领域逐步收紧，不要全局关闭规则。
- 用类型化 DTO、Bean Validation、OpenAPI/前端 schema 替换 v2/管理端动态 Map/any 契约；拆分导出控制器、工作台聚合 hook 和 1,573 行 API client。
- 按 [冗余专项](04-redundancy-dead-code-audit.md) 的批次 A 清理 23 个不可达前端文件、对应依赖、`EmailCodeDto`、`SlopQualityIssueRepository`、空 tasks 文件与配置漂移；删除公共资源前仍要确认没有外部固定 URL 引用。
- 本地签到遗留、Flyway 历史、proto 字段和 `CHECKIN` 枚举属于兼容性清理，必须先查历史数据并协调跨服务契约，不能直接删除或改写既有迁移。

## 4. 本轮验证结果

| 检查 | 结果 |
| --- | --- |
| CodeGraph | `sync/status` 通过；694 files、13,625 nodes、31,528 edges，索引与当前工作树一致 |
| 前端依赖安装 | `npm ci --legacy-peer-deps` 通过；未修改 `package.json`/lockfile |
| ESLint | **失败**：580 errors、39 warnings；其中 578 项为集中式 `no-explicit-any` 类型债 |
| TypeScript | **失败**：`tsc -b` 共 8 个错误，涉及 mock 推断、未使用变量和已漂移的测试 props |
| 前端单测 | 通过：30 个文件、122 tests |
| 前端 production build | 通过；`UserEntry` 约 1,624.66 kB，Vite 给出大 chunk 警告 |
| 后端测试 | 280 tests：273 通过、0 assertion failures、7 errors；7 项均因本机没有 Docker/Testcontainers 环境 |
| 依赖审计 | npm audit 的时间点快照见安全报告；Maven OWASP Dependency-Check 未完整生成报告，不能据此宣称 Java 依赖无漏洞 |
| Git/文档 | `git diff --check` 通过；没有修改既有 Flyway 迁移、业务代码或依赖清单 |

本轮属于只读审计与非部署验证，没有执行 Docker Compose 部署、浏览器验收、真实外部服务调用、生产数据 `EXPLAIN`、负载测试或故障注入。以上容量风险凡需真实阈值的均在专项报告标为“需压测定量”，没有把静态推断包装成实测结果。

## 5. 审计边界与复验要求

- SSO username 是否全生命周期唯一且不可复用、跨服务 token/计费幂等语义等外部契约无法仅由本仓库证明；相关高风险结论均明确写出前置条件，整改时需从 Aienie 接口事实源核验。
- 随机 UUID 降低越权对象被枚举的概率，但不能替代 owner 绑定；素材上传任务和导出模板问题仍应修复。
- npm audit 是 2026-08-13 当前 lockfile 的时间点结果；生产可达性与升级方案需逐包复核。Java SCA 因 Dependency-Check 未完成而存在覆盖缺口。
- 在具备 Docker 的环境重跑 7 个 Flyway/Testcontainers 用例；在预发布环境执行正文并发写、队列饱和/重启、AI/pay 延迟、认证依赖故障、1/10/50 MB 导出及 1k/10k 素材/用户容量矩阵。
- 修复后应逐项记录“复现用例 → 失败 → 修复 → 通过”的证据，并再次确认 `doc/roadmap.md` 的 G2 阶段门槛；本次审计本身不改变路线图阶段状态，也不启动 G2 完整方案 A。
