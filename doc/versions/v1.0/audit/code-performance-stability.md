# AINovel 代码性能与稳定性审计

- 审计日期：2026-09-26（Asia/Shanghai）。
- 基准：`develop`，HEAD `2a1fb98a9e85d9de9feee6e2d820ce7961797a82`。审计对象为当前工作区；未修改业务代码和已有用户修改。
- 方法：CodeGraph 结构与调用关系定位、源码与 SQL/配置交叉检查、一次真实 React hook 的离线缺陷复现。未连接数据库、Redis、Qdrant 或公共服务，未执行部署、付费 AI、压力测试和线上故障注入。
- 路径说明：以下 `文件:行号` 均相对仓库根目录；行号对应本次基准。P1 为应优先修复的正确性/稳定性问题；P2 为明显的规模、资源隔离或条件性稳定性问题。严重性不代表已观测到生产故障。

## 结论与发现清单

发现 4 项 P1、5 项 P2。已离线复现切换稿件导致草稿跨稿件显示并保存；任务终态、跨进程领取和长事务还存在可由源码确认的竞争窗口。当前已有 AI 准入、有限执行队列、稿件版本冲突保护和导出大小上限，但这些措施没有完整覆盖上述窗口。

| 编号 | 级别 | 问题 | 证据状态 / 触发边界 |
|---|---|---|---|
| PERF-01 | P1 | 编辑器草稿仅按场景缓存，切稿可覆盖另一个稿件 | 真实 hook 离线复现；同一大纲下至少两个稿件 |
| PERF-02 | P1 | AI 取消状态可被晚到进度回调覆盖 | 源码确认；取消与工作线程回调并发，单实例也存在 |
| PERF-03 | P1 | 部分异步任务领取不具备数据库原子性 | 源码确认；两个进程连接同库并同时调度时 |
| PERF-04 | P1 | AI 远程调用处于数据库长事务且持用户行锁 | 源码确认；世界模块生成、质量诊断并发或依赖变慢 |
| PERF-05 | P2 | 素材索引在共享调度线程串行执行远程 I/O | 源码/本地依赖配置确认；索引积压或 AI/Qdrant 变慢 |
| PERF-06 | P2 | 单场景自动保存重写并返回整本稿件 | 源码确认；正文规模及保存次数增长 |
| PERF-07 | P2 | 后台资产列表先全表读取再限制 200 条 | 源码确认；全站资产增长，包含大正文 JSON |
| PERF-08 | P2 | 导出下载在客户端传输期间占用数据库连接 | 源码确认；多个慢下载并发 |
| PERF-09 | P2 | 素材查重全量两两比较且反复分词 | 条件性容量风险；目前本地管理员权限问题遮蔽正常入口 |

## PERF-01：跨稿件草稿污染并可能覆盖正文（P1）

**定位与调用链**

- `frontend/src/pages/Workbench/hooks/useManuscriptEditorState.ts:35`：`sceneDrafts`、`dirtyScenes` 仅以 `sceneId` 为键；第 38–39 行优先读取 `sceneDrafts[selectedSceneId]`。
- 同文件第 48–52 行在稿件变化时只更新 `manuscriptRef`；第 105–115 行的 effect 仍优先读取旧场景草稿，没有按稿件切分或清理草稿。
- `frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx:129` 创建这一 hook；第 390 行稿件下拉直接调用 `setSelectedManuscriptId`，不会重新挂载 hook。第 374 行附近的 `key` 属于子面板，不是 hook/`ManuscriptWriter` 边界。
- `useManuscriptEditorState.ts:140` 保存时读取当前稿件 ID 和当前版本，将传入正文发往 `api.manuscripts.saveSection`。后端 `ManuscriptService.java:141` 的版本校验因此可以通过：客户端提交的确实是 B 的有效版本，只是正文来自 A。

**触发与影响**：同一大纲的稿件 A、B 共享场景 ID。编辑 A 后切换到 B，编辑器显示 A 的缓存内容；再点击保存或继续编辑触发自动保存，会将 A 正文写到 B。即便 A 已保存，其 `sceneDrafts` 也仍保留。普通版本冲突保护无法识别这一客户端身份混淆。

**已执行验证**：`evidence/draft-isolation.repro.test.tsx` 直接加载真实 hook，mock 网络/写作会话，在 A 编辑后 rerender B。断言观察到 `content === "A edited"`，且手动保存参数为 `("manuscript-b", "shared-scene", "A edited", 7)`。该测试断言的是当前缺陷；“通过”表示成功复现，不表示修复。测试配置与 JSON 结果一并保留在 `evidence/`。

**建议**：所有草稿、脏状态、定时器和保存队列按 `manuscriptId + sceneId` 分区；切稿时明确处理未保存内容，切换前后都验证保存任务所属稿件。不要只在 effect 中清空草稿而引入首帧串稿或丢弃未保存内容。回归至少覆盖切稿、切稿时请求在途、同场景 ID、A/B 各有未保存内容、切回 A。

## PERF-02：取消与进度更新缺少终态保护和执行代次隔离（P1）

**定位与调用链**：`backend/src/main/java/com/ainovel/app/aioperation/AiOperationService.java:157` 的 `cancel` 将状态保存为 `CANCELLED` 后调用 `Future.cancel(true)`；第 257–264 行 `onStarted` 无条件写 `STREAMING`，第 277–284 行 `onCompleted` 无条件写 `RUNNING`；第 348–350 行通用 `update` 只按 ID 读取/修改，未检查终态、当前租约持有者或 attempt。`AiOperationRun.java:11` 实体没有 `@Version`。成功和失败分支虽然检查 `CANCELLED`（第 315、337 行附近），进度分支却没有同样保护。

**触发与影响**：取消请求和已开始的回调交错，或者任务/依赖没有及时响应线程中断，回调在取消写入之后提交。`Future.cancel(true)` 只是请求中断，不能替代数据库状态仲裁。取消终态可能重新变成运行态，再被置为成功；SSE 已结束与数据库继续更新也可能不一致。同一个 run 重试时，旧执行的回调也未绑定 attempt，存在污染新一次执行的窗口。此问题不依赖多实例。

**现有保护与局限**：任务注册表、线程中断和成功/失败分支的取消判断有效缩小窗口，但不能阻止进度回调覆盖终态；不能据此宣称每次取消都会失败。本次没有运行真实 gRPC 取消实验。

**建议与验证**：为每次执行分配不可复用的执行 token/attempt；所有更新使用 `WHERE id=? AND lease_owner=? AND attempt=? AND status IN (...)` 或锁内状态机校验。取消和业务结果应用应共享明确的原子仲裁规则。用可控 latch 的假 handler，在取消后投递 started/delta/completed 回调，再模拟旧 attempt 在重试后结束，断言终态和业务正文均不回退。

## PERF-03：任务领取先读后写，多实例可重复执行（P1，条件性）

**定位与调用链**

- `AiOperationService.java:174` 各进程定时扫描 `QUEUED`；第 208–225 行 `tasks.putIfAbsent` 仅在当前 JVM 去重；第 235–246 行事务内使用普通 `repository.findById`，检查 `QUEUED` 后直接改 `RUNNING`。
- `AiOperationRepository.java:11` 已提供悲观锁版本 `findByIdForUpdate`，但领取路径未使用；`AiOperationRun.java:11` 无乐观锁字段。`V14__audit_high_remediation.sql:67` 起的幂等/active-scope 唯一索引阻止重复创建，不阻止同一行被同时领取。
- 同型模式还出现在 `backend/src/main/java/com/ainovel/app/v2/V2ExportJobService.java:132` 的导出领取和 `backend/src/main/java/com/ainovel/app/material/MaterialRetrievalService.java:79` 的素材索引领取。导出有完成时 lease_owner 条件，但渲染工作已发生；素材在向量写入前没有执行代次栅栏。

**触发与影响**：两个 JVM 同时读取同一条 queued 记录，各自均进入执行；租约字段本身不构成原子领取。可能重复调用模型、重复扣费/产物处理或交叉覆盖状态。普通 AI 调用在 `AiService.java:60` 无指定 usageContext 时会生成新的幂等键，不能假定全部 handler 的重复调用均由远端合并。

**边界**：仓库 Compose 当前各声明一个 backend 服务，未实证现网双实例，因此这是横向扩容、重叠启动或恢复期间的条件性 P1；不是“当前单进程每个任务均重复”。`GuidedCreationJobService.java:175–176` 已采用 `findByIdForUpdate`，说明项目内存在可复用的领取范式。

**建议与验证**：统一原子条件更新或悲观锁领取；每次租约使用执行 token，所有副作用和完成操作校验该 token，必要时加入续租。用两个 service 实例及真实测试数据库，通过 barrier 同步竞争同一任务，断言 handler 只执行一次；增加租约到期后旧进程返回的用例。不要仅用同一个 JVM 的 map 去重测试代替。

## PERF-04：AI 调用持有长事务、数据库连接和用户行锁（P1）

**定位与调用链**

- `backend/src/main/java/com/ainovel/app/world/WorldService.java:223` 的 `generateModule` 整体 `@Transactional`，第 225 行先查询数据库，第 235–238 行逐字段调用 `generateField`，第 279 行调用 `aiService.refine`。
- `backend/src/main/java/com/ainovel/app/quality/SlopDiagnosticService.java:70–83`、`quality/PlotQualityService.java:68–80` 的诊断方法同样包住数据库上下文读取及远程 AI。
- `backend/src/main/java/com/ainovel/app/ai/AiService.java:80–89` 在远程调用前预留积分；`backend/src/main/java/com/ainovel/app/economy/EconomyService.java:213–219` 的 `reserveAiUsage` 使用默认传播的 `@Transactional`，加入调用方事务，并通过 `lockUser`（第 666–671 行）执行用户行悲观写锁；锁声明见 `user/UserRepository.java:17–19`。
- `backend/src/main/resources/application.yml:9–13` 默认 Hikari 上限 20、借连接等待 5 秒；AI 单次默认 deadline 为 60 秒（同文件 `app.external.ai-timeout-ms`，客户端 `AiGatewayGrpcClient.java:201`）。这只是配置上限，不是实测响应时间。

**触发与影响**：世界模块多个字段串行生成或诊断依赖变慢时，事务/连接在等待模型期间不释放，用户行锁也不能在预留方法返回时释放。同一用户的其他积分操作可被阻塞；不同用户并发可能消耗连接池，影响普通查询和健康检查。一次业务事务回滚还会回滚本地额度记录，但外部模型调用已经发生，扩大重试与账务一致性处理难度。异步 executor 只把等待从 HTTP 线程移走，并未缩小数据库事务。

**现有保护**：AI 准入限制和有限 worker 限制并发，Hikari/远程 deadline 避免部分无限等待；但 AI 全局默认并发 32 与连接池 20 也不是同一资源预算，且其他业务共享连接。

**建议与验证**：采用“短事务读取快照/预留 → 事务外调用 → 短事务校验版本/应用结果”的结构；明确额度预留与结算独立事务及失败补偿。稿件 `ManuscriptService.java:98–137` 已采用生成前后两个短事务，可参考。用延迟假 gateway 并发启动诊断/世界模块生成，同时执行普通读及同用户积分操作，观测 active/pending 连接、锁等待、请求分位耗时；确保模型等待期不持业务行锁。本次未做这些负载试验。

## PERF-05：素材索引阻塞共享调度器（P2）

**定位与调用链**：`MaterialRetrievalService.java:70–75` 的 `@Scheduled` 每次取 20 个任务后在调度线程逐个 `process`；第 94–97 行每个 chunk 同步 embedding、Qdrant upsert。`material/MaterialChunker.java:12–34` 使用 900 字符窗口、120 重叠，没有任务级 chunk 数上限；`material/AiServiceTextEmbeddingClient.java:21–26` 直接调用阻塞 gRPC，`integration/AiGatewayGrpcClient.java:201–202` 设的是单次调用 deadline。

**配置证据**：仓库未发现自定义 `TaskScheduler`、`SchedulingConfigurer`、调度池大小或虚拟线程配置。已读取本机 Maven 缓存的 Spring Boot **3.5.14** `spring-boot-autoconfigure` JAR 内 `META-INF/spring-configuration-metadata.json`，`spring.task.scheduling.pool.size` 默认值为 **1**。这是仓库默认运行配置的结论，外部运行时配置可能覆盖，未读取或假定部署秘密配置。

**触发与影响**：一个大素材需要许多同步远程调用，或某个调用接近 deadline，整个批次会长时间占据调度线程。AI queued 分发、导出恢复/清理、积分兑换对账及引导流程 reconcile 的定时任务共享调度器时一并延迟。5 秒 `fixedDelay` 并不意味着每 5 秒完成一批；其间没有任务级总时间预算。单个远程 timeout、最多 20 条查询和重试退避属于局部保护，未隔离调度资源。

**建议与验证**：调度器只做 bounded claim/dispatch，将索引放入独立有限 worker；限制单素材大小/chunk 数、批量 embeddings、给完整任务设置总预算和最大重试策略。先修复 PERF-03 的领取再增加并行度。用延迟 embedding stub 和一个调度心跳记录其他定时任务延迟，断言索引耗时不会串行阻塞其他业务调度；在不同外部调度池配置下重复。

## PERF-06：正文保存产生整本读写与响应放大（P2）

**定位与调用链**：`frontend/src/pages/Workbench/hooks/useManuscriptEditorState.ts:172–181` 每次输入停顿 1.2 秒安排保存；`backend/src/main/java/com/ainovel/app/manuscript/ManuscriptService.java:141–156` 对一个场景修改时，解析整个 `sectionsJson`、重写整个 JSON、保存后再返回 `toDto`；第 223–233 行 DTO 仍包含所有 sections。`manuscript/model/Manuscript.java:25` 将整本内容存为单个 LOB。加载列表 `ManuscriptService.java:60–63` 也返回该大纲下每个稿件的完整内容；前端 `useManuscriptSelectionData.ts:59–60,115–123` 缓存这些完整稿件。

**触发与影响**：长篇或同大纲多稿件时，改动一个小段也按全稿件大小承担数据库读写、JSON 解析/序列化、网络响应和 React Query 缓存替换。设整稿正文大小为 B、自动保存次数为 S，则这一链路的主体数据工作量随 B×S 增长，不能只按本次修改字数预算。前端输入本身还在 `components/editor/TiptapEditor.tsx:72–75,130–138` 获取完整场景 HTML，并在 `useManuscriptEditorState.ts:59` 统计全文字数；这些是 O(场景长度) 工作，未做浏览器 profile，不声称已量化卡顿。

**现有保护**：1.2 秒 debounce、串行 `saveQueue`、在途编辑保留、409 冲突停止自动重试和后端 `@Version` 均是有效保护，不能把本问题误写成“每次键盘事件直接发请求”或“完全没有并发保护”。

**建议与验证**：场景正文独立存储/更新，保存响应返回场景及新版本，列表采用摘要 DTO，选择稿件/场景后按需获取正文；保留跨场景快照及版本一致性设计。用相同单场景修改分别在不同总正文体积下测 SQL 字节、响应大小、堆占用及 UI long task。没有实测前，不给出吞吐量或允许的最大章节数。

## PERF-07：后台列表在内存中全量排序/过滤（P2）

**定位与调用链**：`backend/src/main/java/com/ainovel/app/admin/AdminOperationsQueryService.java:62–91` 的 stories/worlds/manuscripts 使用 `findAll().stream().sorted(...).limit(200)`；`admin/AdminConsoleService.java:90–98` 用户搜索也在 `findAll()` 后过滤，且无分页。`manuscript/repo/ManuscriptRepository.java:15` 继承 `JpaRepository`，这些 `findAll` 路径不是 SQL 限制/DTO 投影；普通详情的 join fetch 并不作用于继承的 `findAll`。

**触发与影响**：用户/资产总量增长时，即使页面只显示 200 行，后台也先加载完整实体，包括稿件正文、世界设定等 LOB，再排序。资产 owner、outline/story 的 lazy 访问可能额外产生关联查询；这一 N+1 部分取决于持久化上下文命中情况，未做 SQL trace，不能断言固定查询数。主体全表实体加载可直接由代码确认。

**建议与验证**：分页、排序、搜索下推数据库；为列表使用仅包含标题、状态、归属等字段的投影，按排序/筛选条件建合适索引。用大正文+大量实体的固定数据集记录查询条数、读取行数、分配内存；断言返回第一页不会读取全站正文。对照 `qualityRuns()` 第 97–108 行已采用数据库 Top100 的方式，避免将所有后台查询一概判定为全表扫描。

## PERF-08：慢速导出下载持续占用数据库连接（P2）

**定位与调用链**：`backend/src/main/java/com/ainovel/app/v2/V2ExportController.java:62–74` → `V2ExportJobService.java:90–105`。`StreamingResponseBody` 内进入 `jdbc.execute(ConnectionCallback)`，打开 ResultSet 后直接 `input.transferTo(output)`，直到 HTTP 输出结束才退出 callback 释放连接。

**触发与影响**：多个客户端慢速接收导出文件时，业务数据库连接随传输时间持续占用。驱动是否在客户端缓冲 BLOB 不改变 callback 持有 connection 的事实。连接池默认 20，下载可和登录/正文/任务处理竞争同一池。生成路径每用户最多 3 个 active job（第 69–74 行）以及 5 MiB 输入/25 MiB 输出上限（第 40–41 行）并不限制已完成文件的并发下载次数。

**边界**：未实测网关缓冲、全局传输限额或 MVC async executor 的部署行为，不能认定任意慢客户端必然耗尽池；源码本身未把数据库生命周期与网络生命周期分离。

**建议与验证**：先将有界 BLOB 复制到受限临时文件/对象存储并关闭 DB 资源，再响应；设置独立的用户/全局下载并发与资源预算，不用无限扩大连接池。以慢速接收端并发下载，同时观察 Hikari active/pending 与普通读延迟，验证连接在下载开始前可释放；检查断开连接时临时文件清理。

## PERF-09：查重按所有素材两两比较，未设候选预算（P2，入口受限）

**定位与调用链**：`backend/src/main/java/com/ainovel/app/material/MaterialService.java:184–208` 加载所有未 rejected 素材后双层循环比较全部组合，保存并排序全部命中候选；第 284 行 `duplicateScore` 对每一对素材重新解析 tags、对 summary/content 分词并计算 Jaccard；第 362 行 `tokens` 对中文构造二元/三元片段。`frontend/src/pages/Admin/MaterialsGovernance.tsx:35–52` 页面加载主动请求查重，第 164 行的 `slice(0,20)` 发生在全部计算和传输完成之后。

**触发与影响**：对于 N 个素材需要 N(N−1)/2 次比较，并反复处理正文。N=10,000 时为 49,995,000 对，这是算法计数而非压测结果。相似素材多时响应与候选内存还可按平方增长；管理员刷新和审核后刷新会重新计算。

**当前可达性限制**：`findDuplicates` 第 185 行先 `assertAdmin`，`security/ResourceAccessGuard.java:44` 只认 `ROLE_ADMIN`，当前本地管理员采用不同 authority。合法本地管理员可能在进入算法前被拒绝（详见安全审计），所以此项不宣称普通管理员当前每次加载均已运行平方级算法；修复权限映射后会暴露容量问题，具有正确角色的服务调用也可触发。

**建议与验证**：先按类型、标题/token 指纹、倒排召回等方式生成受限候选，再计算精确相似度；将分词预计算，改为有上限、可取消的后台任务并分页返回。用 mock 权限守卫或合法受控管理员测试 N 梯度，记录比较次数、总时间、候选数和峰值内存；避免在真实数据上直接运行大规模查重。

## 已检查的现有保障与覆盖边界

| 范围 | 本次实际检查与已有保障 | 剩余验证边界 |
|---|---|---|
| 异步任务 | AI、GuidedCreation、G2、导出、素材索引领取/调度；AI/Guided executor 队列 64，G2 24，导出 32，拒绝后保留 queued；Guided claim 使用悲观锁 | 未启动双实例，未做进程崩溃/数据库隔离级别试验；有限队列不代表持久化 queued backlog 有业务配额 |
| 远程调用 | AI/Billing/session deadline、AI Redis 准入、Qdrant timeout；区分 chat 准入与直接 embedding 路径 | 没有实际公共服务延迟、错误率、限额和重试成本数据；embedding 直连未复用 chat 的准入，纳入素材索引容量治理 |
| 持久化 | `open-in-view=false`、Hikari 明确大小/超时、稿件 `@Version`、任务幂等唯一索引、数据库分页与非分页路径区分 | 没有生产索引基数、执行计划、慢查询和锁等待证据；素材 `searchVisible` 的 `%LIKE%` 与逐个向量结果查验（最多 60 条）应做 explain/SQL 计数后再定优化优先级 |
| 前端 | React Query staleTime 60 秒、关闭 focus refetch、按选中实体启用请求、保存 debounce/排队/冲突保留、编辑器事件/RAF 清理 | 已做 hook 复现；未做浏览器 CPU/heap profile、真实长篇输入、断网/刷新未保存草稿恢复验收 |
| 部署脚本与资源 | 只读审阅 `scripts/ci/README.md`、Compose、nginx；后端预发布/生产模板均有 1 GiB、2 CPU、256 PID 边界和 healthcheck，生产前端有限额；发布路径为两阶段构建，未运行 Linux 发布脚本 | 模板不是当前运行实例的实测配置；不声称已验证 readiness、重启恢复、备份还原或网关缓冲；Windows 用户已有修改保留 |
| 可观测性 | `application.yml` 的 metrics 默认 `all:false`，只单独启用启动、进程存活与 HTTP 请求等；日志/records 有容量和保留配置 | 本次未发现同处显式开启 Hikari/JVM 指标；压测前需核实 exporter 实际暴露，否则连接池/GC 假设难以量化 |

全量 L1/L2 由主审统一执行并记录，避免多个 agent 重复运行；本报告的“通过”仅适用于明确列出的离线 hook 复现。前端 build 的初始包大小由主审证据记录，未将静态 bundle 字节直接推断成某个网络条件下的首屏秒数。中文 PDF 导出正确性问题由可维护性审计记录，本报告不重复计数。

## 建议执行顺序与关闭标准

1. 优先关闭 PERF-01（实际覆盖正文风险）与 PERF-02（取消/重试状态一致性），将本次缺陷复现改为期望隔离行为的长期回归测试。
2. 在多实例或重叠启动之前关闭 PERF-03；随后对 PERF-04 建立事务外远程调用边界和独立额度结算。先通过确定性竞争测试，再运行受控集成测试。
3. 对 PERF-05、06、07、08 建立代表性正文/资产规模和慢依赖测试；同时采集连接池、堆/GC、任务队列等待、SQL 和端到端响应指标。资源配置调整必须依据测量结果。
4. 修复管理员权限后，查重入口启用前验证 PERF-09 候选预算。每项修复都应写明测试数据大小、并发、依赖 stub/真实程度和通过门槛；本次未定义未经测量的容量承诺。

本次为审计交付，不代表 H 阶段验收通过，不更改路线图完成状态。
