# AINovel 代码性能与稳定性审计

> 审计日期：2026-08-13
>
> 审计对象：`master` 分支当前工作树（含审计开始前已存在的未提交文件模式变化）
>
> 审计方式：静态调用链审查、CodeGraph 影响分析、配置与迁移核验、非部署命令验证；未修改业务代码
>
> 严重度：高 13 项、中 8 项、低 0 项

## 1. 结论摘要

当前项目的主要风险不是单个慢查询，而是多条关键链路把“整稿 JSON 读改写、长时外部调用、固定小队列、同步认证探测、伪异步导出”叠加在同一个请求或数据库事务中。低并发下多数路径可以工作，但在多标签编辑、AI 服务变慢、线程池队列满、素材或用户数据增长、容器启动失败等条件下，会分别表现为正文静默丢失、任务永久停留、数据库连接池耗尽、外部积分与本地账本不一致、请求线程/堆内存被占满，以及部署脚本假成功。

最高优先级应当是：

1. 修复稿件正文的并发覆盖，建立数据库级版本或分场景写模型。
2. 为 G2 与通用 AI 任务建立可恢复的数据库队列；拒绝执行不能只留一条 `PENDING/QUEUED` 记录。
3. 将远程 AI/计费调用移出数据库事务，补齐积分兑换的可恢复 Saga 与 AI 调用前额度预留。
4. 把导出改为真实后台任务和持久化产物，避免轮询、下载请求同步重复生成整稿。
5. 恢复 5 个 shell 文件的 executable bit，并让部署等待真实 readiness；否则当前工作树一旦以现状提交，Linux 交付链可能直接失效。

“已证实”表示代码结构足以确定故障或复杂度；“需压测定量”表示风险路径已证实，但实际饱和点、延迟或内存峰值需要生产等价数据量与依赖故障注入才能量化。

## 2. 发现总览

| ID | 严重度 | 状态 | 发现 | 现实触发条件 |
| --- | --- | --- | --- | --- |
| PS-01 | 高 | 已证实 | 整稿 JSON 并发读改写会静默覆盖其他场景正文 | 多场景/多标签/AI 生成与保存并发 |
| PS-02 | 高 | 结构已证实，需压测定量 | AI 远程调用位于数据库事务内，可能长期占用连接与锁 | AI 高尾延迟或多个生成任务并发 |
| PS-03 | 高 | 已证实 | G2 执行器拒绝后样本永久停留 `PENDING` | 2 个线程与 24 个队列槽全部占满，或提交瞬时失败 |
| PS-04 | 高 | 已证实 | 通用 AI 执行器拒绝后既卡住 DB 任务，又泄漏本地 `tasks` 占位 | 4 个线程与 64 个队列槽全部占满，或启动恢复洪峰 |
| PS-05 | 高 | 已证实 | 通用 AI “查活动任务再插入”无原子约束，可并发重复生成与计费 | 双击、客户端重试、多标签或网关重放 |
| PS-06 | 高 | 已证实 | 导出任务实际在轮询/下载线程同步整稿生成，且每次下载重复生成 | 大稿件或多用户并发轮询/下载 |
| PS-07 | 高 | 已证实 | 素材检索每次全库加载、重分块，并且 Qdrant 请求无超时 | 素材库增长或 Qdrant 黑洞连接 |
| PS-08 | 高 | 已证实 | 计费 RPC 使用 120 秒共享超时并即时重试，且部分调用发生在事务内 | pay-service 故障/高尾延迟并叠加请求并发 |
| PS-09 | 高 | 已证实 | 通用积分兑换在远端成功后缺少本地原子落账与恢复机制 | 远端扣款成功后本地 DB/进程失败 |
| PS-10 | 高 | 已证实 | AI 推理先发生、余额校验后发生，余额不足仍消耗上游资源 | 低余额用户调用或多个请求并发消费余额 |
| PS-11 | 高 | 已证实 | 每个普通 Bearer 请求同步探测 user-service 并重复查询本地用户/设置 | user-service 变慢/不可用或页面并行请求放大 |
| PS-12 | 高 | 条件性交付阻断，尚非已发布缺陷 | Compose 无健康检查，`build.sh` 在应用就绪前返回成功 | 使用当前部署流程且新容器启动失败/迟迟未就绪 |
| PS-13 | 高 | 当前工作树交付阻断，尚未发布 | 5 个 Linux 交付脚本从 `100755` 变为 `100644` | 将当前模式变化提交并在 Linux 直接执行脚本 |
| PS-14 | 中 | 已证实，需压测定量 | 向导流式输出每个 delta 都开启事务并锁同一任务行 | 高频小 delta、多任务并发流式生成 |
| PS-15 | 中 | 已证实 | 管理端用户/资产列表全量加载，存在 DB 与远程 RPC N+1 | 用户/资产增长或 pay-service 降级 |
| PS-16 | 中 | 已证实，需数据量验证 | 素材治理多条路径全表/O(n²)/全文扫描，上传创建缺少原子事务 | 素材/稿件增长或上传中间写失败 |
| PS-17 | 中 | 已证实 | 普通前端请求没有统一超时/取消，周期请求缺少 single-flight | 网络黑洞、后端长响应、页面快速切换 |
| PS-18 | 中 | 静态已证实，体积需重建验证 | 用户路由、工作台标签、管理端页面未按路由拆包 | 首次访问、移动网络或低端设备 |
| PS-19 | 中 | 已证实 | 写作会话在关闭/切换竞态下遗留开放记录，统计全量扫描历史 | 关闭/崩溃标签页、快速切故事、长期使用 |
| PS-20 | 中 | 已证实，告警效果需演练 | 请求指标每次做线性队列计数，错误率口径失真，readiness 覆盖不足 | 稳定流量、短时错误突增或 user-service 故障 |
| PS-21 | 中 | 已证实 | G2 活动列表对每个活动执行 7 次聚合查询 | 活动历史增长并频繁刷新列表 |

## 3. 详细发现

### PS-01（高）：整稿 JSON 并发保存会静默覆盖正文

- **位置**：`backend/src/main/java/com/ainovel/app/manuscript/model/Manuscript.java:25-29`；`backend/src/main/java/com/ainovel/app/manuscript/ManuscriptService.java:76-96`；`backend/src/main/java/com/ainovel/app/manuscript/repo/ManuscriptRepository.java:25-27`；`frontend/src/pages/Workbench/hooks/useManuscriptEditorState.ts:108-143`。
- **证据**：所有场景正文存放在单个 `sectionsJson` LOB。保存场景时先读整份 JSON、只改一个 key、再回写整份 JSON；实体没有 `@Version`。仓库虽声明 `findByIdForUpdate`，`updateSection` 和 `generateForScene` 均未使用。前端为每个场景维护独立定时器，允许不同场景的请求同时在途，而且每个响应都会用整份服务端 Manuscript 替换本地对象。
- **触发条件**：用户快速切换并编辑场景 A/B、多标签或多设备编辑，或 AI 生成与自动保存重叠。两个事务读取同一旧快照后，最后提交者会把另一事务刚写入的场景恢复成旧值。
- **影响**：正文静默丢失；`isSaving` 也可能在仍有请求在途时提前变为 false。该问题不是仅在同一场景发生，任何共享 `sectionsJson` 的修改都会互相覆盖。
- **建议**：优先把场景正文规范化为独立行；过渡期为 Manuscript 增加乐观版本号/ETag，并在冲突时拒绝而不是覆盖。服务端写回要使用版本比较或行锁，AI 生成应基于快照并在最终提交时校验版本。前端按 manuscript 串行/合并保存、忽略过期响应，并补充跨场景并发与多标签冲突测试。

### PS-02（高）：长时 AI 调用处于数据库事务内

- **位置**：`backend/src/main/java/com/ainovel/app/manuscript/ManuscriptService.java:76-85`；`backend/src/main/java/com/ainovel/app/manuscript/SceneGenerationService.java:69-115`；`backend/src/main/java/com/ainovel/app/g2evaluation/G2EvaluationGenerationWorker.java:39-74`；`backend/src/main/java/com/ainovel/app/manuscript/SceneGenerationService.java:131-180`；`backend/src/main/resources/application.yml:5-8,104-106`。
- **证据**：`generateForScene` 的整个方法为 `@Transactional`，读取稿件后在同一事务内执行最多 3 次 AI 草稿调用及质量处理，再写回正文。G2 worker 同样以单个事务包住一对 fast/crafted 候选；每个候选最多 3 次 AI 调用。外部调用共享默认 120 秒 deadline，而数据源没有项目级 Hikari 容量/获取超时配置。
- **触发条件**：AI 延迟、重试、质量修复或多个 G2/正文生成并发。
- **影响**：连接在远程等待期间不能归还连接池；G2 的状态更新也直到长事务提交才可见。连接池耗尽后，与 AI 无关的登录后查询和保存也会排队或失败。实际耗尽并发数需压测量化，但长事务结构已经确认。
- **建议**：拆为“短事务读取不可变快照/认领任务 → 无事务外部调用 → 短事务带版本条件写回”。为 Hikari 明确 maximum-pool-size、connection-timeout、leak-detection 等观测配置，并用依赖延迟注入验证 95/99 分位和连接池等待。

### PS-03（高）：G2 队列拒绝后任务永久 `PENDING`

- **位置**：`backend/src/main/java/com/ainovel/app/g2evaluation/G2EvaluationAsyncConfig.java:14-20`；`backend/src/main/java/com/ainovel/app/g2evaluation/G2EvaluationService.java:141-170,215-225`；`backend/src/main/java/com/ainovel/app/g2evaluation/G2EvaluationGenerationWorker.java:39-47`。
- **证据**：执行器为 core=1、max=2、queue=24，未设置拒绝策略，故使用默认 abort。`submitSample` 先提交 `PENDING` 记录，事务提交后直接 `executor.execute`，没有捕获 `RejectedExecutionException`。仓库内未找到启动时或定时扫描 G2 `PENDING` 的恢复器。
- **触发条件**：约 26 个长任务占满线程和队列后继续投稿，或提交瞬间执行器关闭/异常。
- **影响**：数据库已提交、请求侧可能收到异常，但样本永久 `PENDING`；重启也不会重派，盲测统计长期显示待处理。
- **建议**：把 DB 状态视为权威队列，用定时 dispatcher/outbox 以 `SELECT ... FOR UPDATE SKIP LOCKED` 或 CAS 认领；拒绝只记录指标并保留待派发状态。增加 PENDING age、队列深度、拒绝次数告警和恢复测试。

### PS-04（高）：通用 AI 队列拒绝导致永久卡住与内存占位

- **位置**：`backend/src/main/java/com/ainovel/app/aioperation/AiOperationAsyncConfig.java:14-20`；`backend/src/main/java/com/ainovel/app/aioperation/AiOperationService.java:59-82,110-128,141-164`。
- **证据**：执行器 core=2、max=4、queue=64，未配置拒绝处理。`submit` 先保存 `QUEUED`，`dispatch` 又先 `tasks.putIfAbsent`，随后才 `executor.execute`。若 execute 拒绝，FutureTask 不会运行，因此 finally 不会清理 map；同进程后续 dispatch 因 map 已有键直接跳过。用户重试接口只接受 `FAILED/RECOVERY_REQUIRED`，不接受 `QUEUED`；定时重派也不存在，启动恢复仅运行一次。
- **触发条件**：约 68 个长任务占满线程与队列，或启动恢复同时提交过多历史 `QUEUED`。
- **影响**：请求可能 500，数据库任务卡在 `QUEUED`，本地 map 持续保留 FutureTask；当前进程内无法自行恢复。
- **建议**：捕获拒绝并原子移除 map 项，返回明确 429/503；更根本地改为数据库队列的周期认领与租约恢复。暴露 executor active/queue/rejected 指标，并为饱和、重启恢复和重复派发编写测试。

### PS-05（高）：通用 AI 活动任务检查不是原子的

- **位置**：`backend/src/main/java/com/ainovel/app/aioperation/AiOperationService.java:35-37,59-80`；`backend/src/main/resources/db/migration/V8__ai_operation_progress.sql:23-26`；`backend/src/main/java/com/ainovel/app/aioperation/AiFeatureOperationController.java:116-122`。
- **证据**：服务先查询相同 user/scope 的活动记录，未命中后再 insert；方法无锁、无请求幂等键。表上只有普通 `(scope_type,scope_id,status)` 索引，没有保证同 user/scope 只有一个活动任务的唯一约束。
- **触发条件**：双击、浏览器重试、两个标签页或网关重放同一操作。
- **影响**：两个请求都可能通过检查并各自调用 AI，造成重复写入、重复计费和额外队列压力。
- **建议**：接收并持久化 `Idempotency-Key`，以数据库唯一键保证同一业务请求只创建一次；若业务要求每 scope 单活动任务，可使用独立 scope-lock 表/状态槽或事务锁，而不是 check-then-insert。

### PS-06（高）：导出是请求线程中的伪异步任务

- **位置**：`backend/src/main/java/com/ainovel/app/v2/V2ExportController.java:54-68,73-136,181-272,337-350,387-421`；`backend/src/main/java/com/ainovel/app/v2/V2ExportPersistenceService.java:75-117,132-151,194-216`。
- **证据**：创建接口只写 `queued` DB 记录。list/get 根据创建时间模拟进度，超过 4.5 秒后在当前 HTTP 线程调用 `generateFile`。持久化 patch 只保存 status、虚构 `filePath` 和 size，不保存 bytes；从 DB 映射时 `contentBytes` 永远为 null，所以每次下载都会重新同步生成。TXT/DOCX/EPUB/PDF 均在内存构建 `byte[]`，DOCX 使用 `reduce("", String::concat)`，PDF 为求偏移反复把增长中的 StringBuilder 转成整份字节数组。活动任务限制还是“先数后插”，并发请求可越过 3 个上限。
- **触发条件**：轮询恰逢 4.5 秒阈值、大稿件、多用户并发下载、容器重启后下载 completed job，或导出创建后正文继续变化。
- **影响**：轮询 GET 被 CPU/内存重活阻塞；同一 job 每次下载内容可不同且重复耗资源；`filePath` 没有对应文件，completed 不能表示产物已持久化；大稿件可能导致长 GC/OOM。
- **建议**：真实 worker 生成一次并流式写入对象存储/受控文件目录，DB 原子记录 artifact key、checksum、snapshot/version、size 与完成时间；下载流式传输持久化产物。使用数据库约束/锁实现并发配额，并把过期清理放到批量定时任务。

### PS-07（高）：素材检索绕过索引并可能无限等待 Qdrant

- **位置**：`backend/src/main/java/com/ainovel/app/material/MaterialRetrievalService.java:53-102,153-159`；`backend/src/main/java/com/ainovel/app/material/QdrantMaterialVectorIndex.java:18-25,114-128`；`backend/src/main/java/com/ainovel/app/material/MaterialService.java:153-164`。
- **证据**：每次检索都 `findAll()` 后在 JVM 过滤可见素材、重新对全部素材分块并做关键词扫描；向量结果返回后又重建全部 chunk map 才能映射 ID。Qdrant `HttpClient` 没有 connect timeout，单个 `HttpRequest` 也没有 request timeout，并同步 `send`。素材审核事务内同步逐块 embedding/upsert。
- **触发条件**：素材库增长、长素材、Qdrant 黑洞连接或 embedding/Qdrant 变慢。
- **影响**：每次查询成本与全库正文总量线性增长，Qdrant 失效时 fallback 可能因无限等待而迟迟不能发生；审核事务长期占连接。实际 QPS/内存拐点需以真实语料压测。
- **建议**：在 DB 层按 owner/status 过滤并只投影必要字段；把权限/owner/material/chunk 元数据存入 Qdrant payload，直接映射返回结果。为 HTTP 设置 connect/request timeout、熔断和有限重试；索引写入改为 after-commit outbox/后台批处理。

### PS-08（高）：计费调用可在事务内阻塞约 240 秒

- **位置**：`backend/src/main/resources/application.yml:104-106`；`backend/src/main/java/com/ainovel/app/integration/BillingGrpcClient.java:59-120,321-323`；`backend/src/main/java/com/ainovel/app/economy/EconomyService.java:138-187,550-570`。
- **证据**：AI 与 pay-service 共用 `EXTERNAL_TIMEOUT_MS=120000`；所有 Billing blocking stub 使用该 deadline。`fetchPublicBalance` 无退避地连续尝试两次。`redeem` 已修改并锁住本地账户、兑换码与 usage 后再取远程余额；`currentBalance` 也以 read-only 事务包住远程调用。
- **触发条件**：pay-service 故障、网络黑洞或高尾延迟。
- **影响**：单次余额路径最坏接近 2×120 秒，期间占用连接，`redeem` 还可能延长行锁；调用者堆积后拖垮业务连接池。立即重试会加剧依赖故障。
- **建议**：为 AI、session、billing 分离 deadline；余额查询应使用短总预算、仅对明确的瞬态状态重试，并有指数退避/抖动、熔断和短期 stale cache。所有远程调用移出本地事务，写路径拆成准备/远程/确认阶段。

### PS-09（高）：积分兑换缺少可恢复的跨服务事务

- **位置**：`backend/src/main/java/com/ainovel/app/economy/EconomyService.java:257-351`。
- **证据**：`convertPublicToProject` 没有 `@Transactional`。它先多次保存 PENDING 订单，再调用 pay-service 扣除通用积分，成功后分别更新账户、账本、用户快照和 SUCCESS 订单。远端成功后的任何本地异常都没有补偿/对账；再次使用同一幂等键只会因订单处于 PENDING/FAILED 而报“正在处理或已失败”。仓库内未找到 PENDING 对账调度器。
- **触发条件**：远端成功后 DB 短暂故障、进程退出、唯一键冲突或本地任一步保存失败。
- **影响**：通用积分已扣但项目积分未完整入账，或账户/账本/订单彼此不一致；用户无法用原幂等键安全恢复。
- **建议**：实现持久化 Saga：短事务创建/锁定订单，携带稳定 remoteRequestId 调远端，再以短事务原子完成账户、账本和订单；定时对账 PENDING，并能按远端请求 ID 查询结果或补偿。对账失败必须告警和支持人工处置。

### PS-10（高）：AI 调用后才检查余额

- **位置**：`backend/src/main/java/com/ainovel/app/ai/AiService.java:37-63`；`backend/src/main/java/com/ainovel/app/economy/EconomyService.java:200-225`。
- **证据**：`AiService.chat` 先完成 AI gateway 推理，再调用 `chargeAiUsage`；后者到取得账户锁并计算实际费用后才检查余额。
- **触发条件**：用户项目积分不足、多个 AI 请求并发消耗同一余额，或客户端绕过前端余额禁用逻辑。
- **影响**：上游模型成本和线程时间已经发生，但响应最终因余额不足失败；重复请求可形成资源/成本放大。并发请求还会在推理完成后争用账户锁。
- **建议**：调用前在本地事务中进行额度预授权/预留，生成后按实际 token 结算并释放余量；预授权、结算和失败补偿都使用同一业务幂等键。网关层同时执行项目级 quota，不能依赖 UI 按钮禁用。

### PS-11（高）：认证热路径同步依赖 user-service 并重复查库

- **位置**：`backend/src/main/java/com/ainovel/app/security/JwtAuthFilter.java:52-90`；`backend/src/main/java/com/ainovel/app/security/remote/UserSessionValidator.java:53-106`；`backend/src/main/java/com/ainovel/app/user/SsoUserProvisioningService.java:20-85`；`backend/src/main/java/com/ainovel/app/user/UserService.java:17-25`；`backend/src/main/java/com/ainovel/app/security/SystemGuardFilter.java:25-58`；`backend/src/main/java/com/ainovel/app/security/SecurityConfig.java:79-83`。
- **证据**：每个普通 Bearer 请求在建立认证前先做 TCP reachability，再执行最长 5 秒的 blocking gRPC session validation；随后 best-effort provision、`loadUserByUsername`，SystemGuard 又查一次 user，并查询最新 global settings。该过滤器链位于所有受保护业务之前，没有会话/用户/维护状态短缓存。
- **触发条件**：正常登录后的所有并行 API 请求；user-service 慢/不可达时尤其明显。
- **影响**：单次页面加载被放大成多次 user-service RPC 和多轮本地查询；user-service 故障会让整个已认证业务入口同步失效，而应用 readiness 仍可能为 UP。TCP 预探测还为每次请求额外建立连接。
- **建议**：去掉逐请求 TCP 预探测，复用 gRPC channel 的连接与 deadline；对 `(uid,sid)` 有界短缓存验证结果并支持撤销事件/短 TTL。一次加载本地用户和维护状态后在请求上下文复用；维护配置用可失效缓存。压测页面并行请求，并注入 user-service 50 ms/500 ms/超时场景。

### PS-12（高，条件性交付阻断）：部署命令在服务就绪前成功返回

- **位置**：`docker-compose.yml:2-43`；`build.sh:41-52`；`backend/src/main/resources/application.yml:61-74`。
- **证据**：前后端服务均只有 `restart: unless-stopped`，没有 healthcheck、依赖条件、资源边界或滚动/回滚策略。`build.sh` 只重试 `docker compose up -d --build`；Compose 接受容器启动后脚本立即 `exit 0`，没有等待 Spring readiness 或前端可访问。项目已有 `/api/actuator/health/readiness`，但 Compose 没使用。
- **触发条件**：env 错误、Flyway/DB 失败、端口冲突、JVM 启动后崩溃，或应用启动时间显著变长。
- **影响**：CI/操作者收到成功结果，但容器随后 crash-loop 或业务仍不可用；旧实例已被替换时形成直接中断。无资源限制时，导出/AI 峰值还可能挤压宿主其他服务。
- **定性边界**：这是使用当前流程且新实例启动失败时触发的交付阻断；审计没有证据表明当前已发布环境正在故障。
- **建议**：为 backend 配置 readiness healthcheck、frontend 配置静态 HTTP healthcheck；部署使用 `docker compose up --wait --wait-timeout`（或等价轮询），再执行关键路径 smoke，失败时明确退出并保留/恢复上一可用版本。配置 stop grace period、JVM/容器内存与 CPU 边界并做故障演练。

### PS-13（高，当前工作树交付阻断）：当前工作树丢失 5 个可执行位

- **位置**：`build.sh:1`；`ci/build-release.sh:1`；`docker/load-env-file.sh:1`；`scripts/lib/deployment-env.sh:1`；`scripts/tests/deployment-env-test.sh:1`。
- **证据**：`git diff --summary` 对这 5 个文件均显示 `mode change 100755 => 100644`，`git diff --numstat` 为 `0 0`，即内容未变、仅文件模式变化。这些变化在本次审计前已经存在，本审计未修改它们。
- **触发条件**：将当前模式变化提交并在 Linux 通过 `./build.sh`、CI 直接执行脚本，或容器 entrypoint 直接执行 loader。
- **影响**：直接执行 build/CI/test 脚本时可能报 permission denied，属于当前工作树的发布阻断风险，而不是脚本内容缺陷。需注意当前 Compose 以 `. /app/bin/load-env-file.sh` source loader，`build.sh` 也 source 公共库，这两个既有调用点本身不依赖 executable bit；风险落在直接执行入口、其他调用者和交付契约，不应笼统表述为所有 source 调用都会失败。
- **定性边界**：该模式变化尚未提交/发布；只有把当前工作树的 mode 变化带入 Linux 交付物才会触发。
- **建议**：提交前恢复 `100755`，在 Linux checkout 后用 `git ls-files -s`、`test -x` 和脚本契约测试验证。不要仅在 Windows 上依据文件可读性判断执行权限。

### PS-14（中）：向导流式 delta 造成数据库写放大

- **位置**：`backend/src/main/java/com/ainovel/app/integration/AiGatewayGrpcClient.java:112-135`；`backend/src/main/java/com/ainovel/app/workflow/GuidedCreationJobWorker.java:37-49`；`backend/src/main/java/com/ainovel/app/workflow/GuidedCreationJobService.java:210-217`；对照 `backend/src/main/java/com/ainovel/app/aioperation/AiOperationService.java:202-211`。
- **证据**：每个 `CONTENT_DELTA` 都调用 `updateStreamProgress`；该方法每次开启事务并 `findByIdForUpdate` 锁同一任务行。通用 AI operation 路径已经以 250 ms 做节流，向导路径没有。
- **触发条件**：ai-service 以 token 或很小片段高频推送，多任务并发流式生成。
- **影响**：大量短事务和同一行锁竞争，反向增加生成尾延迟、binlog/redo 与连接池压力。具体写 QPS 需用真实 stream 粒度验证。
- **建议**：在 worker 内按 250~1000 ms、token 增量或进度变化合并，完成时强制 final flush；进度可放 Redis/内存并按较低频率持久化，业务最终状态仍以 DB 为准。

### PS-15（中）：管理列表存在全量加载与 N+1/RPC fan-out

- **位置**：`backend/src/main/java/com/ainovel/app/admin/AdminConsoleService.java:86-94,133-157`；`backend/src/main/java/com/ainovel/app/economy/EconomyService.java:183-187,550-570`；`backend/src/main/java/com/ainovel/app/admin/AdminOperationsQueryService.java:62-93`。
- **证据**：用户列表 `findAll` 后内存过滤；每个用户再查项目账户、最多两次远程 public balance、故事 count、世界 count。资产列表同样 `findAll` 后内存排序再 limit 200；稿件会加载大 LOB，访问 owner 还可能触发 lazy N+1。
- **触发条件**：用户/故事/稿件数据增长，或 pay-service 降级。
- **影响**：用户页形成 O(N) DB + O(N) 串行远程 RPC，资产页在 limit 前已加载全部实体/LOB；管理端慢查询会长期占连接与堆。
- **建议**：所有列表使用数据库分页、排序和 DTO projection；用 grouped aggregate/join 一次取计数，余额批量查询或短 TTL cache，并允许显示 stale/unknown，绝不能逐行远程调用。

### PS-16（中）：素材治理接口随数据量超线性退化，上传创建非原子

- **位置**：`backend/src/main/java/com/ainovel/app/material/MaterialService.java:103-121,141-150,183-207,246-302`；`backend/src/main/resources/db/migration/V1__baseline.sql:41-66`。
- **证据**：pending 接口全表读取后过滤；duplicate 对所有非 rejected 素材两两比较，并在每一对中重新 tokenize 标题/标签/全文，复杂度至少 O(N²×正文处理)；citations 扫描用户全部稿件并解析/去 HTML 后逐信号匹配。材料表只有 user_id 索引，没有 status 复合索引。`createUploadJob` 无事务，依次保存 job、material、job，任一步失败可留 processing 孤儿或无法关联的素材。
- **触发条件**：素材/稿件数量和正文长度增长、并发上传，或中间 DB 写失败。
- **影响**：管理治理接口出现 CPU/堆峰值和长响应，上传状态永久 processing 或数据不一致。阈值需用真实数据量验证。
- **建议**：pending 用 `(status,user_id)` 索引分页；duplicate 预计算 fingerprint/MinHash/向量近邻并异步批处理；citations 建立引用索引或搜索索引，返回分页结果。上传 job 与 material 创建放入一个短事务并定义失败状态/恢复清理。

### PS-17（中）：普通前端请求无统一 deadline 或取消

- **位置**：`frontend/src/lib/api-client.ts:116-195`；`frontend/src/pages/Workbench/hooks/useWritingSession.ts:103-123`。
- **证据**：JSON、Form、Void 和管理员二次验证请求直接调用 fetch，没有默认 `AbortSignal`/超时；只有 SSE 方法显式接收 signal。30 秒 heartbeat 没有 in-flight guard；beforeunload 使用普通异步 fetch，没有 `keepalive`/beacon。
- **触发条件**：网络黑洞、反向代理不及时断开、后端长事务或用户快速切换/关闭标签页。
- **影响**：页面 loading 永不结束，组件卸载后请求仍执行；周期请求可重叠并写入过期状态，关闭时 endSession 大概率不能作为可靠交付协议。
- **建议**：在 api client 按请求类型设置统一 deadline，并允许调用者传入 signal；组件卸载时取消，轮询/心跳使用 single-flight。SSE/真实异步任务使用独立长预算；写作会话关闭使用有幂等键的 `sendBeacon`/`fetch keepalive` 作为尽力通知，服务端仍需 stale recovery。

### PS-18（中）：主要页面没有路由/标签级代码拆分

- **位置**：`frontend/src/App.tsx:12-32,65-108`；`frontend/src/pages/Workbench/Workbench.tsx:7-16`；`frontend/src/AdminApp.tsx:9-21`；`frontend/vite.config.ts:6-29`。
- **证据**：用户 App 静态导入所有公开/业务页，Workbench 静态导入 9 个标签，AdminApp 静态导入所有后台页；Vite 没有 manual chunk 或 bundle budget。当前已有但被 gitignore 的 `frontend/dist` 快照中 `UserEntry-*.js` 为 1,624,656 raw bytes、约 454,397 gzip bytes；该快照不是本审计重新构建，数字只作指示，静态导入事实已确认。
- **触发条件**：首次访问首页/登录页或只使用单一工作台标签，尤其在移动网络和低端设备。
- **影响**：下载、解析和执行未访问页面及 Tiptap/recharts 等重依赖，延迟首屏和增加内存。精确 LCP/INP 影响需 production build + 浏览器性能测试。
- **建议**：使用 `React.lazy`/动态 import 按路由与 Workbench 标签拆包，Suspense 提供轻量骨架，并对高概率下一页预取。CI 建立 raw/gzip chunk budget 和 Lighthouse/Web Vitals 基线。

### PS-19（中）：写作会话存在逻辑泄漏且统计全量扫描

- **位置**：`frontend/src/pages/Workbench/hooks/useWritingSession.ts:74-124`；`backend/src/main/java/com/ainovel/app/v2/V2WorkspacePersistenceService.java:95-134`；`backend/src/main/java/com/ainovel/app/v2/V2WorkspaceController.java:120-164`；`backend/src/main/java/com/ainovel/app/v2/repo/V2WritingSessionRepository.java:10-12`。
- **证据**：story 变化即异步创建新 session，没有请求序列/取消；cleanup 与 beforeunload 只尽力异步 end。服务端每次 start 都插入新行，没有“同 tab/story 仅一个活动会话”约束或 stale 自动关闭。统计先加载用户全部历史 session，再在 Java 聚合日/周/月。
- **触发条件**：关闭/崩溃标签页、快速切换故事、start 响应乱序、长时间使用。
- **影响**：`endedAt=null` 的会话不断累积，时长和目标统计失真；统计请求成本随用户全部历史线性增长。
- **建议**：引入 clientSessionId/idempotency key、heartbeatAt 和 stale auto-close；前端使用 generation token 丢弃旧 start 响应。统计改为数据库按日期范围聚合并分页历史，增加 `(user_id,started_at)` 与活动会话查询索引。

### PS-20（中）：观测指标本身有线性开销，错误率与 readiness 不能反映当前故障

- **位置**：`backend/src/main/java/com/ainovel/app/metrics/ApiRequestMetrics.java:17-79`；`backend/src/main/java/com/ainovel/app/metrics/ApiRequestMetricsFilter.java:20-30`；`backend/src/main/resources/application.yml:61-74`；`backend/src/main/java/com/ainovel/app/admin/ops/AdminOpsController.java:52-72,113-116`；`backend/src/main/java/com/ainovel/app/admin/ops/DependencyHealthService.java:72-83,96-133`。
- **证据**：每个请求都向 `ConcurrentLinkedDeque` 添加对象，并调用线性 `size()` 来裁剪 2000 项队列；延迟分位使用最近 2000 次，而 `errorRate` 使用进程生命周期累计值，旧成功流量会稀释当前事故。Actuator 只暴露 health，readiness 只含 state+db，不含认证必需的 user-service。管理观测页的一次加载会并行调用 summary/dependencies/alerts，而这三个端点各自重复串行探测 7 个依赖并同步写探测文件，不是持续采集。
- **触发条件**：稳定流量、短时错误突增、user-service 故障，或管理员打开观测页。
- **影响**：热路径产生可避免的遍历/分配；告警错误率不能代表当前窗口；应用可能 readiness=UP 但所有普通认证请求失败。实际 CPU 占比和告警阈值需流量回放/故障演练。
- **建议**：使用 Micrometer Timer/Counter/DistributionSummary 和时间窗口告警，暴露受保护的 Prometheus/OTel；增加连接池、线程池队列/拒绝、任务年龄、外部 RPC 指标。缓存一次依赖探测快照并后台刷新；按“流量是否应摘除”决定 user-service 是否进入 readiness，其他依赖暴露 feature/degraded health。

### PS-21（中）：G2 活动列表为 1+7N 聚合查询

- **位置**：`backend/src/main/java/com/ainovel/app/g2evaluation/G2EvaluationService.java:108-117,265-282`。
- **证据**：列表取出所有活动后逐个调用 `responseFor`；每次调用分别统计 READY、PENDING、RUNNING、总票、精雕票、distinct reviewers、invite，共 7 次聚合查询。
- **触发条件**：活动历史增长，用户/管理端频繁刷新活动列表。
- **影响**：查询数线性放大，DB 往返延迟与负载随活动数增长。
- **建议**：用一次/少数 grouped projection 返回各状态和票数，或在投票/样本状态转换时维护原子计数；列表必须分页，并为计数一致性补测试。

## 4. 缓存、资源释放与正向控制

- `spring.cache.type=redis` 已配置（`application.yml:28-32`），但代码中未发现 `@EnableCaching/@Cacheable/@CacheEvict/@CachePut`；Redis 当前主要被管理员限流显式使用，因此不能抵消认证、管理列表、素材检索的重复读取。若引入缓存，必须明确 owner/role/maintenance/session 的失效与隔离键，不能只为追求命中率缓存授权结果。
- SSE emitter 在 completion/timeout/error 时移除，终态也主动清理（`AiOperationService.java:95-106,286-312`）；AI、billing 和 session validator 的 gRPC channel 都有 `@PreDestroy`。本轮未发现这些路径的常规连接泄漏。
- `GuidedCreationRuntime` 会捕获执行器拒绝，并每 5 秒扫描 DB 中 QUEUED 任务重派（`GuidedCreationRuntime.java:43-71`），因此它与 PS-03/PS-04 不同，不会仅因一次拒绝就永久丢任务。
- `spring.jpa.open-in-view=false`、应用日志与 Docker json-file 日志均有限额/轮转，是稳定性正向项；但不能替代连接池、任务队列和依赖 RPC 指标。

## 5. 建议的容量与故障验证矩阵

以下验证尚未执行，建议在隔离的预发布环境完成：

| 场景 | 最小验证方式 | 通过标准 |
| --- | --- | --- |
| 稿件并发写 | 两场景并发 PUT，再叠加 AI 生成/多标签 | 不丢任一场景；冲突可见且可恢复 |
| AI/G2 队列饱和 | 分别提交 69/27 个受控慢任务并重启实例 | 无永久 QUEUED/PENDING；拒绝、年龄、恢复均可观测 |
| 数据库池 | AI/pay 延迟注入 0.5/5/120 秒，混合普通 CRUD | 非 AI 请求仍满足 SLO，连接等待/超时受控 |
| 认证依赖 | user-service 注入 50/500 ms、拒绝连接和超时 | 明确 fail-open/closed 策略；readiness 与真实可服务性一致 |
| 导出 | 1/10/50 MB 稿件，多格式并发创建与重复下载 | 生成一次、checksum 稳定、堆受控、下载可流式恢复 |
| 素材 | 1k/10k 素材与长正文，Qdrant 黑洞 | 搜索/治理分页有界，fallback 在预算内返回 |
| 管理列表 | 1k/10k 用户及资产，pay-service 降级 | 查询数与页面大小近似常数，无逐行 RPC |
| 观测/部署 | 启动失败、DB/user-service 故障、OOM/kill 演练 | 部署非零退出或回滚；告警在窗口内触发且能定位队列/池/依赖 |

## 6. 实际验证命令、结果与局限

已执行：

- `PowerShell $PSVersionTable`：PowerShell 7.6.3，平台 Win32NT。
- `CODEGRAPH_DIR=.codegraph-win codegraph sync`：`Already up to date`。
- `CODEGRAPH_DIR=.codegraph-win codegraph status . --no-color`：694 files、13,625 nodes、31,528 edges，索引为 up to date。
- `codegraph explore "ManuscriptService updateSection concurrent save"` 与 `codegraph explore "AiOperationService dispatch executor"`：确认调用入口和影响面；CodeGraph 也提示上述关键方法缺少针对并发/拒绝的直接覆盖测试。
- `git diff --check`：退出码 0。
- `git diff --summary/--numstat`：确认 5 个 shell 文件只有 `100755 -> 100644` 模式变化、无内容差异。
- 对已有 `frontend/dist` 快照做只读 gzip 估算：`UserEntry-*.js` 1,624,656 raw bytes / 约 454,397 gzip bytes；由于未重新构建，仅作为拆包风险的辅助证据。

未成功或未执行：

- `npm run lint`：失败，当前共享工作树的 `frontend/node_modules` 中没有 eslint 可执行文件（`eslint is not recognized`）。
- `npm test`：失败，当前共享工作树的 `frontend/node_modules` 中没有 vitest 可执行文件（`vitest is not recognized`）。本审计未执行 `npm install/npm ci`，避免改动共享依赖目录。
- `bash -n` 尝试：Windows 的 `bash.exe` 因 WSL 服务错误 `Bash/0x80070422` 无法运行，因此没有执行 `scripts/tests/deployment-env-test.sh`。
- 未执行 Maven build/test、Vite build、Docker Compose、部署、真实 DB `EXPLAIN`、浏览器性能测试和负载测试；原因是本子任务限定只写审计文档、不产生业务构建产物且不得部署。故所有容量数值均标注为“需压测定量”，没有把静态推断包装成实测结论。

## 7. 修复顺序建议

1. **提交/部署前**：恢复 PS-13 executable bit；补 PS-12 health/wait，确保失败部署不会被报告为成功。
2. **数据正确性与任务可恢复性**：PS-01、PS-03、PS-04、PS-05、PS-09。
3. **连接池与外部依赖隔离**：PS-02、PS-08、PS-10、PS-11、PS-14。
4. **重资源功能治理**：PS-06、PS-07、PS-15、PS-16、PS-21。
5. **客户端与可观测性基线**：PS-17、PS-18、PS-19、PS-20，并按第 5 节建立可重复容量/故障演练。
