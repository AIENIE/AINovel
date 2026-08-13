# AINovel v1.0 代码安全审计

审计日期：2026-08-13

审计对象：`master` 分支当前工作树

审计方式：认证/授权与数据流静态审查、配置和容器基线检查、依赖时间点扫描、CodeGraph/全文检索交叉验证；未修改业务代码、未部署、未调用真实生产依赖

结论：**REQUEST CHANGES**。未确认 CRITICAL；确认或条件性确认 HIGH 3 项、MEDIUM 3 项、LOW 4 项。

## 1. 执行摘要

最需要优先处理的不是传统 SQL 注入，而是三条跨边界风险：

1. AI 推理在额度检查/扣费前已经发生，入口又没有消息体积、速率或并发上限；低余额账户仍可消耗上游资源，并可通过并发放大成本和线程占用。
2. SSO 本地账户在找不到 `remote_uid` 时按 username 回退，并可把已有账户重绑到新的远端 UID。若 user-service 的 username 可改名、删除后复用、大小写碰撞或跨租户重复，会演变成本地资产接管。外部 username 不变量无法由本仓库证明，因此这是**条件性高风险**，不是已经证明可由当前 user-service 任意利用的结论。
3. 默认共享 MySQL/Redis/Qdrant 连接没有形成强制的加密认证契约：MySQL 默认 `useSSL=false`，Redis 没有 TLS 配置且密码可为空，Qdrant 默认 HTTP 且客户端不发送认证头。运行时预检只覆盖部分变量非空/不等于模板值，不能证明传输与服务端认证安全。

其他问题包括：上传任务 owner 缺失、已登录用户开放跳转、当前 lockfile 的已知依赖告警、导出模板单点越权、异步错误细节回显、容器以 root 运行，以及 bearer token 长期保存在 `localStorage` 且没有仓库内 CSP 防线。

## 2. 风险总览

| ID | 严重度 | 状态 | 发现 |
| --- | --- | --- | --- |
| S-01 | HIGH | 已证实 | AI 先推理后扣费，且入口无体积、速率和并发限制 |
| S-02 | HIGH | 外部契约条件性 | SSO 按 username 回退并可重绑已有账户的 `remote_uid` |
| S-03 | HIGH | 配置结构已证实 | 共享 MySQL/Redis/Qdrant 未强制 TLS 与认证 |
| S-04 | MEDIUM | 已证实，UUID 泄露前置 | 上传任务缺 owner，越权查询还能由 GET 改变状态 |
| S-05 | MEDIUM | 已证实，需已登录态 | Login/Register 对 `//host` 形式的 `next` 形成开放跳转 |
| S-06 | MEDIUM | 时间点扫描 | 当前前端 lockfile 有生产/开发依赖告警，Java SCA 未完成 |
| S-07 | LOW | 已证实，UUID 泄露前置 | 创建导出任务可引用并回显其他用户的自定义模板配置 |
| S-08 | LOW | 已证实 | 异步任务把原始异常消息持久化并返回资源 owner |
| S-09 | LOW | 防御纵深 | 前后端运行镜像默认 root，运行时权限未收缩 |
| S-10 | LOW | 防御纵深 | 用户 bearer token 存于 `localStorage`，仓库内未设置 CSP |

## 3. 详细发现

### S-01（HIGH）：AI 先调用后扣费，缺少滥用与成本边界

- **位置**：`backend/src/main/java/com/ainovel/app/ai/AiController.java:41-68`；`backend/src/main/java/com/ainovel/app/ai/dto/AiChatRequest.java:8-22`；`backend/src/main/java/com/ainovel/app/ai/AiService.java:37-75,97-116`；`backend/src/main/java/com/ainovel/app/economy/EconomyService.java:195-225`。
- **证据**：`AiService.chat` 先执行 `chatCompletions/chatCompletionsStream`，拿到 token 用量后才调用 `chargeAiUsage`。余额不足在 `EconomyService` 第 215-217 行才被拒绝，此时上游推理已完成。请求校验只要求至少一条非空 user 消息，没有限制消息数、单条/总字符数、上下文体积、单用户速率或在途并发；Controller 也没有限流/配额注解或网关契约。
- **攻击/故障场景**：任一有效 SSO 用户可并发提交大量或超长请求；低余额用户每次在上游成功后都可让本地扣费失败。即使 ai-service 自身另有限额，本仓库也没有把该外部假设声明为安全契约或处理本地成本不一致。
- **影响**：上游资源/费用消耗、后端请求和 AI 线程占用、用户积分与实际推理成本不一致；并发请求还会扩大扣费竞争和拒付窗口。
- **建议**：在调用前按模型/最大输出做额度预留，结束后按真实 token 幂等结算并释放差额；校验消息角色、数量、单条和总字节/字符数；按 user/remoteUid 设置 token bucket、在途 semaphore 与全局熔断；将稳定 request/idempotency key 贯穿 ai-service、pay-service 与本地账本。为“余额不足、并发超限、超大 body、上游成功后本地失败”补集成测试和审计指标。

### S-02（HIGH，条件性）：username 回退可把已有本地账户重绑到新的远端 UID

- **位置**：`backend/src/main/java/com/ainovel/app/auth/SsoTokenExchangeService.java:71-95`；`backend/src/main/java/com/ainovel/app/security/JwtAuthFilter.java:64-80`；`backend/src/main/java/com/ainovel/app/user/SsoUserProvisioningService.java:27-35,51-65`；`backend/src/main/resources/db/migration/V1__baseline.sql:118-128`。
- **证据**：token exchange 把 user-service 返回的 `userId/username/sessionId` 签入本地 JWT。请求过滤器验证远程会话后调用 provisioning。provisioning 先按 `remoteUid` 查；找不到时按 username 查到已有本地 `User`，随后直接把其 `remoteUid` 改为当前 UID。数据库只分别保证 username、remote_uid 唯一，没有一张不可变外部身份映射或“已有 remote_uid 不得变化”的约束。
- **必要前置**：两个不同远端 UID 能在不同时间或命名空间得到同一规范化 username，例如改名后旧名复用、删除重建、大小写/Unicode 归一化不一致或跨租户重复。本仓库未包含 user-service 的正式身份生命周期契约，无法证明该前置在当前环境成立，也无法证明永远不成立。
- **影响**：一旦前置成立，后登录 UID 会复用原本地 user 主键，从而继承其故事、稿件、素材、积分和其他外键资产；原用户再次登录还可能来回重绑。
- **建议**：`remote_uid` 一旦绑定即不可由普通登录改变；仅对“明确标记为未绑定的历史账户”执行一次受审计迁移。冲突时 fail closed，不能按 username 自动合并。建立 `(issuer, remote_uid)` 外部身份表并记录 verified username 作为可变属性；与 user-service 确认 username 的唯一、改名、删除/复用、大小写和租户语义，再用冲突数据演练验证。

### S-03（HIGH）：共享数据服务未形成强制加密认证基线

- **位置**：`backend/src/main/resources/application.yml:5-8,23-32,137-140`；`backend/src/main/java/com/ainovel/app/config/RuntimeEnvironmentPreflight.java:20-60`；`backend/src/main/java/com/ainovel/app/material/QdrantMaterialVectorIndex.java:27-42,45-64,114-128`；`backend/src/main/java/com/ainovel/app/integration/ExternalSecurityStartupValidator.java:33-64`。
- **证据**：默认 MySQL URL 指向共享主机并显式 `useSSL=false`；虽然预检要求 `MYSQL_PASSWORD` 非空且不能等于模板值，但不验证 `DB_URL` 是否启用 TLS、证书校验或最小权限。Redis 默认共享主机，配置没有 SSL，密码默认为空；`REDIS_PASSWORD` 在模板清单中会拒绝示例值，但不在运行预检的 REQUIRED 列表。Qdrant 默认 `http://base.seekerhut.com`，启用默认 true，客户端只发送 Content-Type，没有 API key/auth 配置。外部 gRPC 默认启用 TLS，但校验器允许显式 plaintext 模式，且它不覆盖上述三种数据服务。
- **影响**：跨主机链路上的凭据、会话/限流状态、素材向量和元数据可能被窃听或篡改；未认证的 Qdrant 若网络暴露还可能被直接读写/删除集合。真实可达性取决于网络 ACL，但网络隔离不能替代协议认证。
- **建议**：MySQL 使用 `sslMode=VERIFY_IDENTITY`（或驱动等价配置）与受信 CA，删除代码中的真实共享地址/弱口令默认；Redis 使用 TLS、ACL 用户与必填密码；Qdrant 使用 HTTPS、API key/mTLS 和最小网络暴露。把 scheme、TLS verify、认证变量、禁止默认共享地址/口令加入启动预检及部署脚本契约测试；轮换当前环境凭据并核查服务端访问日志。

### S-04（MEDIUM）：上传任务没有 owner，越权 GET 还会推进状态

- **位置**：`backend/src/main/java/com/ainovel/app/material/MaterialController.java:55-64`；`backend/src/main/java/com/ainovel/app/material/MaterialService.java:103-138`；`backend/src/main/java/com/ainovel/app/material/model/MaterialUploadJob.java:10-21`；`backend/src/main/resources/db/migration/V1__baseline.sql:41-50`。
- **证据**：`MaterialUploadJob`/表没有 `user_id`。状态查询只有在 `resultMaterialId` 非空且目标素材仍存在时，才借素材 owner 做间接校验；processing/orphan 任务或素材已删除后没有归属检查。查询方法还在 `@Transactional` GET 内把进度改为 100、状态改成 completed。
- **可利用性边界**：jobId 使用 Hibernate 随机 UUID，不可现实枚举；攻击者需先从日志、浏览器历史、监控、引用或其他泄漏获得 ID。成功后可读取文件名、状态、进度、message，并改变任务状态；当前主要是任务元数据与完整性影响，而不是直接读取上传正文。
- **建议**：任务表新增非空 owner 外键，Repository 查询使用 `findByIdAndUserId`；所有状态读取都先校验 owner/admin。只有 worker/显式状态机可转换状态，GET 必须无副作用。补“素材删除后、orphan、跨用户、并发查询”测试。

### S-05（MEDIUM）：已登录用户访问登录/注册链接时可被开放跳转

- **位置**：`frontend/src/pages/auth/Login.tsx:13-31`；`frontend/src/pages/auth/Register.tsx:13-31`；对照 `frontend/src/lib/sso.ts:15-18,48-57`。
- **证据**：页面只检查 `next.startsWith('/')`，因此 `//attacker.example` 被接受；检测到 `localStorage` 已有 token 时直接 `window.location.replace(next)`，浏览器会把双斜杠解释为协议相对外站。未登录分支调用 `buildSsoUrl` 时会再次经过正确的 `startsWith('//')` 拒绝逻辑，因此漏洞主要影响已经有 token 的用户。
- **影响**：可构造看似可信的 AINovel 登录链接把已登录用户带到钓鱼站，便于凭据/支付/二次验证诱导。它不会直接把 bearer token 附加给目标站，除非另有泄漏链。
- **建议**：导出并唯一复用 `normalizeNextPath`；只允许单斜杠开头，拒绝反斜杠、控制字符和解析后 origin 变化，或直接使用 React Router 内部导航。为 `/login?next=//host`、`/\\host`、编码/双重编码和合法 query/hash 补测试。

### S-06（MEDIUM）：当前 lockfile 存在依赖告警，Java 依赖扫描有覆盖缺口

- **位置**：`frontend/package.json:68-72,94-99`；`frontend/package-lock.json` 中的 React Router、Vite/Rollup/esbuild、PostCSS/nanoid/picomatch、Vitest 依赖节点；`backend/pom.xml`。
- **证据**：本轮一次成功的 `npm audit --omit=dev` 时间点扫描汇总为 **9（5 high、4 moderate）**，全量 `npm audit` 汇总为 **16（1 critical、11 high、4 moderate）**。这些是 npm 报告的受影响包节点计数，不等同于独立 CVE 数。React Router 属运行时直接依赖；PostCSS/nanoid/picomatch 等在本项目主要位于构建链，Vitest 的 critical 告警针对其 UI/server 能力而不是当前 `vitest run` 测试命令，因此生产可利用性必须逐条判断。OWASP Dependency-Check 因数据下载/分析未完成，没有生成可用于判净的 Java 报告；后续一次 npm audit 独立复验也遇到 registry TLS 连接失败。
- **影响**：运行时路由问题可能影响跳转/路由安全，构建工具漏洞可能影响开发机/CI 或不可信输入构建场景；Java 侧存在“未完成扫描”的未知风险，而不是 0 漏洞。
- **建议**：保存 JSON/SBOM 与 advisory ID，在隔离分支逐包升级到修复版本并跑 typecheck、单测、build、路由/SSO 回归；不要直接使用 `npm audit fix --force`。为 Java SCA 配置稳定镜像/缓存与受控 suppressions，输出 CycloneDX SBOM，并把扫描时间和失败状态作为 CI 证据。

### S-07（LOW）：创建导出任务可引用其他用户的自定义模板

- **位置**：`backend/src/main/java/com/ainovel/app/v2/V2ExportController.java:54-68`；`backend/src/main/java/com/ainovel/app/v2/V2ExportPersistenceService.java:75-105,172-177,194-216`。
- **证据**：Controller 已校验稿件 owner，但 `createJob` 对 payload 中的 `templateId` 仅全局 `findById`，没有要求模板属于当前用户或为系统模板；未提供 config 时会复制该模板的 `configJson`，创建响应又回显 `templateId/config`。update/delete 模板路径有 owner 校验，说明这是创建路径的单点遗漏。
- **边界/影响**：模板 UUID 为随机值，正常列表只返回本人+系统模板，需先泄露 UUID；模板配置通常低敏，但仍可能暴露作者/导出偏好并允许未经授权复用。
- **建议**：Repository 使用 `(id,user_id)` 或 `user is null` 条件查询；响应只返回必要字段。补跨用户随机/已知 UUID 和系统模板测试。

### S-08（LOW）：后台任务回显底层异常消息

- **位置**：`backend/src/main/java/com/ainovel/app/aioperation/AiOperationService.java:260-278`；`backend/src/main/java/com/ainovel/app/workflow/GuidedCreationJobService.java:423-426`；`backend/src/main/java/com/ainovel/app/aioperation/AiOperationDtos.java:7-15`；`backend/src/main/java/com/ainovel/app/workflow/dto/CreationWorkflowDtos.java:49-80`。
- **证据**：AI operation 和 guided creation 将 `RuntimeException.getMessage()` 截断后持久化，随后通过用户可查的 progress/job DTO 返回。全局同步异常处理已把 gRPC/RuntimeException 映射为通用错误，因此异步路径的策略不一致。
- **影响**：上游 SDK、SQL/JSON 解析或配置异常可能包含 endpoint、模型名、内部状态或片段。仅返回资源 owner 降低影响，且日志已有 stack-only 安全封装，所以定为 LOW。
- **建议**：持久化稳定的公开错误码/用户文案；原始异常只写脱敏日志并关联 request/operation ID。测试使用包含 URI、token-like 文本和用户内容的异常，断言 DTO 不回显。

### S-09（LOW）：容器运行权限与供应链基线偏宽

- **位置**：`frontend/Dockerfile:1-15`；`backend/Dockerfile:1-17`；`docker-compose.yml:1-43`。
- **证据**：两个运行阶段均未创建/切换非 root 用户；Compose 没有 `read_only`、`cap_drop`、`security_opt:no-new-privileges` 或资源限制。前端运行镜像在构建时全局安装 `serve@14`，运行依赖不由项目 lockfile 完整约束；基础镜像使用可移动 tag 而非 digest。
- **影响**：一旦应用/依赖出现 RCE，攻击者在容器内拥有更宽权限和可写面；供应链重建的可复现性较弱。容器并非自动等于宿主 root，故这是防御纵深而非直接宿主提权结论。
- **建议**：多阶段构建后使用固定 UID/GID 非 root、只读根文件系统、临时目录 tmpfs、drop all capabilities、no-new-privileges 和 CPU/内存/PID 限额；把静态服务依赖锁定在项目或改用受治理的 Web server 镜像；发布物记录镜像 digest、SBOM 和签名。

### S-10（LOW）：bearer token 存在 localStorage，且缺少 CSP 降低 XSS 后果

- **位置**：`frontend/src/contexts/AuthContext.tsx:27-54`；`frontend/src/lib/api-client.ts:46-47,146-185`；`frontend/Dockerfile:10-15`。
- **证据**：用户访问令牌写入并长期读取 `localStorage`，任意同源脚本执行都可读取并外传；仓库内静态服务配置没有 Content-Security-Policy。审计没有发现当前生产入口中可达的 `dangerouslySetInnerHTML` 注入点：唯一 JSX 用例位于不可达的死模块 `components/ui/chart.tsx`；`shared.ts` 的 `div.innerHTML` 用于 DOM 解析后读取 text，不把该 DOM 插入页面。因此不能把本项表述为已存在可利用 XSS。
- **影响**：未来任一一方脚本、依赖或业务 XSS 会直接升级为 bearer token 盗取。JWT 有 issuer/audience/签名和远程 session fail-closed 校验，但有效期内仍有窗口。
- **建议**：优先评估同源 `Secure; HttpOnly; SameSite` 用户会话/BFF 模式；若继续使用 bearer，缩短 TTL、强化撤销、避免第三方脚本，并在反向代理设置严格 CSP（逐步用 nonce/hash，禁止 unsafe-inline/eval）、frame-ancestors、Referrer-Policy 等安全头。

## 4. 已核验的正向控制与排除项

- `JwtService` 验证本地签名、issuer 和 audience；`JwtAuthFilter` 对普通用户还要求 `uid/sid` 并通过 user-service 远程会话校验，失败时不建立认证。没有把未验签 token 交给远程服务作 fallback。
- 管理员使用 HttpOnly、Secure、SameSite Cookie；`AdminTrustedOriginFilter` 对管理路径严格校验 Origin/Referer。因此虽然 Spring Security 全局关闭 CSRF，本轮没有把管理员路径误报为“无 CSRF 防护”。
- 管理 Controller 使用 `AUTH_LOCAL_ADMIN`，普通素材 review/duplicate 等 Service 也再次断言 admin；故事、稿件、素材、质量与大多数 v2 入口有 owner guard。
- `RuntimeEnvironmentPreflight` 在 Spring 创建连接前要求核心管理员/JWT/外部服务密钥，并拒绝 `env.example` 模板值；`env.txt` 被 Git 忽略，`git ls-files -ci --exclude-standard` 为空。本轮未发现已跟踪真实密钥。
- Swagger/OpenAPI 默认关闭，Flyway `clean-disabled=true`、`validate-on-migrate=true`，上传限制 20 MB；日志和管理运维记录有脱敏/轮转控制。
- 静态检索没有发现 native SQL 字符串拼接、命令执行、Java 反序列化或生产可达的任意 HTML 注入证据。`ExternalSecurityStartupValidator` 对 pay JWT 的 payload 解码只用于启动预检，不把该未验签 payload 当作业务请求认证，因此不作为签名绕过报告。

## 5. 验证与局限

已执行/核验：

- CodeGraph sync/status：索引 up to date；结合 controller → service → repository/外部客户端调用链复核授权边界。
- `rg` 定向检查认证、管理员权限、资源 owner、危险 DOM、SQL/命令执行、token/secret、网络 scheme、Docker USER/security options。
- `npm audit --omit=dev` 与全量 `npm audit` 各有一次成功的时间点汇总，结果见 S-06；后续复验曾因 registry TLS 连接失败而未返回 JSON。
- `git ls-files -ci --exclude-standard` 为空；未读取或输出 `env.txt` 内容。
- 与全量测试结果交叉：前端 122 tests 通过；后端 280 tests 中 273 通过、0 assertion failures、7 个仅因本机无 Docker/Testcontainers 环境报错。

未完成/不能证明：

- OWASP Dependency-Check 未完整生成 Java 报告，不能宣称 Java 依赖无已知漏洞。
- 未对真实 user-service/pay-service/ai-service、MySQL、Redis、Qdrant 或预发布域名发起攻击/配置探测；外部身份唯一性、服务端限流、网络 ACL 和证书状态均需跨服务核验。
- 未做 DAST、浏览器 CSP 验证、容器镜像扫描、SBOM/签名验证、并发滥用或故障注入。随机 UUID IDOR 的实际利用仍依赖 ID 泄露。
- 依赖漏洞数据随时间变化；修复时必须重新扫描并保存 advisory ID、工具版本、时间和网络失败状态。

## 6. 建议修复顺序

1. **发布前**：处理 S-01 额度预留/限额、S-02 不可变身份绑定、S-03 数据服务 TLS/认证；轮换并验证共享环境凭据。
2. **同一安全迭代**：修复 S-04/S-05/S-07 三个授权/跳转边界；统一 owner 查询和内部路径规范化。
3. **依赖与运行基线**：完成 S-06 Java/Node SCA，升级并回归；按 S-09 收缩容器权限并固定可验证制品。
4. **防御纵深**：统一异步公开错误码，迁移用户会话或缩短 bearer 暴露窗口，设置/验证 CSP 和其他响应头。
