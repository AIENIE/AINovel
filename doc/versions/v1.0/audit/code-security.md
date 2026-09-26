# AINovel 代码安全审计

## 结论与范围

本次发现 6 项需要处理的问题：2 项高危的已命中依赖漏洞（实际利用具有环境/输入前提）、3 项中危代码或交付控制缺陷、1 项低危条件性信息越权。没有证据支持“普通用户可直接取得管理员权限”“已发生秘密泄露”或“产品存在已验证的远程代码执行”。依赖扫描返回的 Critical 数量也不能直接等同于生产漏洞数量。

- 审计时间：2026-09-26；基线：`develop` / `2a1fb98a9e85d9de9feee6e2d820ce7961797a82`，包含审计时已有工作树状态。
- 范围：Spring Security、普通 SSO 与独立管理员登录、资源归属、素材审核/上传/检索、AI 准入、SQL/文件/HTTP 调用危险入口、前端渲染与令牌存储、依赖与发版脚本。重点沿调用链抽查，并非逐行证明所有实现均安全。
- 依据：`aienie-dev-workflow`、`security-review`、`aienie-integration`；公共认证语义参照 `aienie-doc/service-integration/user-service/{README,http}.md`。标准 L2 由总审计统一执行，结果见总报告。本次未执行系统矩阵验证或运行态探测，不作拓扑变更、环境决策或生产安全认证。
- 未读取 `env.txt` 或 `local-trust/` 的秘密，未连接业务数据库、未部署/启动应用、未发起真实 AI 请求、未修改业务代码或依赖锁文件。只新增本报告及其证据文件。

“已确认”表示代码路径或已解析产物直接证实；“条件性”表示漏洞/行为存在，但实际攻击需要额外前提；“未验证”表示本次没有运行攻击或真实环境验收，不能当成阴性结论。

| 编号 | 级别 | 问题 | 证据与利用状态 |
|---|---|---|---|
| SEC-01 | 高 | 前端工具链锁定有已知漏洞的 Vite/Vitest 等组件 | 版本和审计命中已确认；开发服务暴露等利用前提未实测 |
| SEC-02 | 中 | PNPM 迁移遗漏依赖审计，清单可能虚报已审计 | 控制流已确认 |
| SEC-03 | 中 | 本地管理员权限与素材服务检查不一致，治理操作被拒绝 | 完整调用链已确认并由主审交叉复核 |
| SEC-04 | 中 | 普通所有者可以自行修改素材审核状态 | 写入与索引路径已确认；范围限定自有素材 |
| SEC-05 | 低 | 上传结果被删除后，任务状态查询失去所有者检查 | 条件分支已确认；需要已知他人任务 UUID |
| SEC-06 | 高 | 后端实际产物包含受 CVE-2024-7254 影响的 protobuf-java | 产物版本和官方范围已确认；恶意 protobuf 输入可达性未验证 |

## SEC-01：已知漏洞仍保留在前端锁文件

**位置与证据**：`frontend/pnpm-lock.yaml:206` 锁定 Vite `6.4.1`，`:209` 锁定 Vitest `4.0.16`；`frontend/vite.config.ts:14` 设置 Windows 本地开发服务，`scripts/windows/Invoke-Local.ps1:53` 运行 `pnpm run dev`。实际在 `frontend` 执行 `pnpm audit --json` 返回退出码 1，共 **55 条：Critical 1 / High 27 / Moderate 26 / Low 1**。原始机器输出：[pnpm-audit.json](evidence/pnpm-audit.json)。这是包/公告命中统计，其中同一个公告可能覆盖多个包或版本，不是 55 个独立可利用入口。

**实际相关的高危条件**：Vite `6.4.1` 同时落入以下维护者公告范围：

- [GHSA-p9ff-h696-f583 / CVE-2026-39363](https://github.com/vitejs/vite/security/advisories/GHSA-p9ff-h696-f583)：网络可达的开发 WebSocket 可绕过文件访问范围校验。公告修复分支包含 `6.4.2`。
- [GHSA-fx2h-pf6j-xcff / CVE-2026-53571](https://github.com/vitejs/vite/security/advisories/GHSA-fx2h-pf6j-xcff)：Windows 特殊路径可绕过敏感文件拒绝规则。此问题需要升级至至少 `6.4.3` 才覆盖相应 6.x 修复。

当前配置绑定 `127.0.0.1`，因此**不能断言开发端口已经对外暴露**。但项目的标准 Windows 入口确实使用开发服务；若产品网关转发该服务的 WebSocket/文件请求，必须把网关链路算入攻击面。`allowedHosts` 校验主机名，不等同于登录认证。本次未访问网关或敏感文件，未验证代理是否满足这些条件。

**Critical 命中的边界**：[Vitest GHSA-5xrq-8626-4rwp](https://github.com/vitest-dev/vitest/security/advisories/GHSA-5xrq-8626-4rwp) 依赖可访问的 Vitest UI 服务等前提；本项目脚本是 `vitest run`，未见启用 UI 的当前入口，因此不能据此宣布产品 RCE。其余命中包括构建/测试工具与浏览器依赖；`dev:false` 也不保证代码实际进入生产 bundle，例如 CSS 构建依赖通过生产依赖的 peer 链可能被归入此类。

**影响**：开发机文件机密性、构建/测试执行环境完整性及依赖输入拒绝服务风险；浏览器库的具体 XSS/重定向可达性需要逐条继续验证。审计没有构造利用、安装升级或修改 lock。

**修复建议**：优先更新 Vite 到覆盖上述公告的受支持版本，更新 Vitest 和锁文件中的其他命中包，按公告的当前修复范围处理；对于 Tiptap 等可能需要跨大版本的升级，先做兼容验证，不能只批量强行 override。为审计命中逐项记录生产/开发用途、调用入口和有期限的例外理由。

**验证方法**：重新执行 `pnpm audit --json` 保存结果，运行前端全量类型检查、测试和构建；仅在隔离测试实例上使用人工创建的无敏感内容文件复测 HTTP/WebSocket 文件访问边界。核对开发服务经网关是否对未认证者可达，禁止用真实 `env.txt` 证明漏洞。

## SEC-02：PNPM 构建分支没有执行依赖审计

**位置**：`scripts/ci/build-release.sh:5–6` 设置 `AIENIE_CI_NPM_MODULES=()`、`AIENIE_CI_PNPM_MODULES=(frontend)`；`scripts/ci/aienie-ci-phase.sh:327–340` 的 `npm audit` 只在 NPM 模块循环中执行；`:347–350` 的 PNPM 分支仅 fetch；`:450–453` 仅离线 install。`:384` 的 `resolve_checks.npm_audit` 却仅依据开关是否为 `true` 决定写入 `passed`。

**触发前提与影响**：当前项目走 PNPM 时，无论设置审计开关与否，前端不会进入 NPM 审计循环；若开关为 `true`，还能在没有审计执行的情况下生成 `passed`。SEC-01 的真实命中证明这不是没有实际依赖风险的形式问题。漏洞包可继续通过仓库提供的构建流程；最终发版中心是否另有外部扫描不在本次观察范围，不据此否定外部防线。

**修复建议**：在联网 resolve 阶段增加 PNPM 对锁文件的审计，并以命令实际成功/失败、模块和审计输入哈希生成结果；离线 build 阶段校验该证据。不要让一个全局布尔值代表各模块检查通过。统一记录开发依赖与生产依赖的分级处置规则。

**验证方法**：用固定含已知漏洞的测试 lock 和 clean lock 验证门禁退出码与清单，覆盖“NPM 数组为空、PNPM 非空且开关开启”用例；扫描失败或未运行不得写 `passed`。本次只做了脚本控制流审查，没有在 Windows 执行 Linux 发版脚本。

## SEC-03：合法本地管理员无法完成素材治理

**位置与调用链**：

1. `AdminSessionAuthFilter.java:40–48` 只赋予 `AUTH_LOCAL_ADMIN`（恢复会话是另一权限）。
2. `AdminOperationsController.java:20` 正确要求该权限，然后 `:40–77` 调用素材 pending/review/duplicates/merge/citations。
3. `MaterialService.java:155–156`、`:184–185` 再调用 `ResourceAccessGuard.assertAdmin()`。
4. `ResourceAccessGuard.java:40–45,72–75` 只接受 `ROLE_ADMIN`，与本地会话权限不一致。pending 在 `MaterialService.java:142–151` 退回按管理员用户名过滤；merge/citations 也退回所有者检查。

上述文件均位于 `backend/src/main/java/com/ainovel/app/` 下。这是完整服务链的静态确认，并经过主审独立复核。`backend/src/test/java/com/ainovel/app/admin/AdminOperationsControllerTest.java:21` mock 了 MaterialService，不能证明该链可用。

**触发与影响**：合法的完整本地管理员会话审核或查重时确定遭到 403；待审队列可能为空，跨所有者合并和引用查询也会错误拒绝。安全治理功能因认证迁移后的旧检查失效，属于授权正确性与稳定性问题。

**修复建议**：建立明确的后台服务入口/权限策略，允许经过本地管理域验证的管理员执行治理，同时保持普通业务资源的所有者校验。不要把 `AUTH_LOCAL_ADMIN` 简单映射成一个可到处绕过资源归属的通用 `ROLE_ADMIN`，也不要去掉服务层检查来“修复”403。梳理旧 `/v1/materials/.../review` 等路径是否仍应存在。

**验证方法**：使用真实过滤链和真实 MaterialService/ResourceAccessGuard、仅 mock 持久化与外部客户端，覆盖完整管理员审核成功、普通用户和 SSO `ROLE_ADMIN` 在后台路径被拒、恢复会话被拒、跨用户普通读写被拒，以及 merge 所需操作 proof。不得只 mock service 验 controller。

**排除的误报**：`SsoUserProvisioningService` 仍有从 ADMIN 到 ROLE_ADMIN 的映射，ResourceAccessGuard 也仍给该角色全局放行；但 `SsoTokenExchangeService.java:88–94` 当前明确只签 `role=USER`，所以本次不把“普通 SSO 可获取管理员角色”列为已确认漏洞。遗留合法 ADMIN 令牌存在与否未查业务数据。

## SEC-04：素材所有者可以绕过审核状态机

**位置**：`material/MaterialController.java:47–49` 接收普通更新；`material/dto/MaterialUpdateRequest.java:10` 暴露 `status`；`material/MaterialService.java:81–92` 仅检查 owner 后直接写入任意 status，并安排索引。相反，真正的审核入口 `MaterialService.java:155–164` 明确要求管理员；上传在 `:118` 初始化为 `pending`。

**触发前提**：已登录普通用户拥有一条 pending/rejected 素材，可对自己的素材执行 `PUT /api/v1/materials/{id}` 并提交 `{"status":"approved"}`；即使 UI 不显示状态编辑字段，API 仍接受。

**影响**：用户可将被驳回/待审内容自行变为通过，并使 worker 的 approved 分支对它建立向量索引（`MaterialRetrievalService.java:93–99`）。该行为绕过管理员审核与对应审计事件。归属过滤仍然存在，**不代表可以修改他人素材或把私有素材变成公共素材**。当前手动创建接口本身默认 approved，说明产品也需要明确“私有素材自管”和“需要运营审核”的状态语义；此问题针对现有显式审核/驳回边界。

**修复建议**：从普通更新 DTO 去掉审核字段或拒绝该字段，只在专属管理员服务里执行允许的状态转换；内容被编辑后是否应重新待审应按素材类型明确。对已有状态和实体元数据字段使用类型化白名单。

**验证方法**：普通 owner 修改 title/content 成功，修改 pending/rejected→approved 被拒；完整本地管理员审核成功且写审计、触发正确索引；其他用户的 PUT 始终拒绝。用隔离数据库与 stub embedding 完成，避免实际 AI 花费。

## SEC-05：删除素材后上传任务查询检查失效

**位置**：`material/MaterialService.java:126–139` 只有在 `resultMaterialId` 存在且其素材仍可查到时才校验 owner。`material/model/MaterialUploadJob.java:16–20` 没有独立 owner；`MaterialService.delete():97–101` 删除素材后没有删除任务或保留任务归属。查询入口是 `MaterialController.java:62–64`。

**触发与影响**：攻击者持有效普通会话且已知他人任务 UUID；原素材已经删除，或任务处于尚无 resultMaterialId 的异常中间状态。此时能够读取文件名、状态、进度和消息；GET 还会将未完成记录设为 completed。UUID 难猜降低可利用性，但不替代授权。这不是对小说正文的直接读取漏洞。

**修复建议**：任务创建时持久化不可变 owner，并按 owner+jobId 查询；素材删除后任务仍需鉴权，或执行明确的级联删除。缺少归属应拒绝访问，不应开放读取。状态变更由实际任务执行器驱动。

**验证方法**：A 上传后删除素材，B 用已知 jobId 查询仍必须 403/404；覆盖无 resultMaterialId、结果已删、正常完成、同一 owner 的四种状态，检查拒绝请求不能改变任务状态。

## SEC-06：后端实际打包了脆弱 protobuf 运行库

**证据**：读取本轮标准构建生成的 `backend/target/ai-novel-backend-0.1.0.jar` 的 ZIP 目录（文件修改时间 2026-09-26 15:16:37），确认包含 `BOOT-INF/lib/protobuf-java-3.25.1.jar`。仅提取库名保存为 [backend-packaged-libraries.txt](evidence/backend-packaged-libraries.txt)，未解压或启动应用。`backend/pom.xml:72–83` 固定 gRPC `1.63.0`；`:157` 的 protoc `3.25.3` 是生成工具版本，不能据此推断运行库安全。

维护者的 [GHSA-735f-pc8j-v9w8 / CVE-2024-7254](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8) 确认特定未知字段/嵌套 group 解析存在无限递归，3.25 分支的修复包含 `3.25.5`。实际 `3.25.1` 命中范围。

**影响与限制**：解析攻击者构造的 protobuf 消息可能引发栈溢出/拒绝服务。AINovel 当前主要作为 gRPC 客户端，来自普通 HTTP 用户的输入不会自动变成任意原始 protobuf；需恶意/受影响上游、代理链或其他输入路径才能触发。本次没有构造恶意 gRPC 响应，不将它写成可匿名从产品 HTTP 直接利用的 DoS。

**修复建议**：统一管理 gRPC/Protobuf 依赖族与生成工具，升级到覆盖此公告且仍受支持的版本；对最终产物而非仅 POM 做 SCA/SBOM 扫描。`grpc-netty-shaded` 内嵌依赖不能由 Spring Boot 管理的普通 Netty 版本自动替代，应在完整组件扫描中一并识别。

**验证方法**：检查最终 JAR 不再包含受影响 protobuf；离线执行维护者针对未知 group/map 解析的有限资源回归用例，并重跑所有生成 proto 与客户端兼容性测试。完整后端漏洞库扫描尚未执行，不能把本次一项核实当成后端依赖已经全部清零。

## 已检查的防线与未列为缺陷的情况

| 审计维度 | 观察 | 结论边界 |
|---|---|---|
| 签名与登录 | JwtService 校验签名、issuer、audience；拒绝占位/过短密钥；SSO 换票后本地签 USER；远程会话不可用返回 503 | 未发现未验签 fallback；关闭 session-validation 的配置分支需额外治理，未读取实际开关 |
| 管理认证 | 不透明随机会话、服务端哈希、TOTP、一次性 operation proof、可信 Origin 过滤及 SameSite/HttpOnly Cookie | `csrf.disable()` 不能脱离这些防线直接报 CSRF；恢复会话权限有隔离 |
| 多租户 | 故事/稿件/世界/素材主要服务检查 owner，v2 控制器检查 owned story/manuscript，向量结果再次核对数据库可见性 | 本次未证明所有资源组合都安全；SEC-05 是已发现的生命周期缺口 |
| 注入 | 检索 SQL/JPA 使用参数、管理员 JdbcTemplate 使用占位绑定；未见普通输入流入 Runtime/ProcessBuilder/Java 反序列化危险入口 | 基于静态检索与关键调用链抽样，不是完整污点分析 |
| 文件与上传 | TXT multipart 有 20 MB 上限；文件名作为数据存储，导出使用随机临时路径；DOCX/EPUB 文本经过 XML 转义 | 未发现把上传文件名直接拼成可执行/落盘路径；资源耗尽风险另见性能报告 |
| TLS 与默认值 | 正式环境 preflight 检查 MySQL VERIFY_IDENTITY、Redis TLS、Qdrant HTTPS；JWT 默认占位会被拒绝 | application.yml 的开发默认值不等于实际正式环境使用弱密码/明文；未读取或验证生产配置 |
| 日志 | SafeLogThrowable 隐去异常消息，OpsRecordFileSink 对敏感字段脱敏并限制记录大小 | 未扫描现存运行日志，不声称历史日志无泄露 |
| 前端 | 未发现生产 `dangerouslySetInnerHTML`；普通 token 在 localStorage，管理员依赖 HttpOnly Cookie | localStorage 会放大未来 XSS 后果，但本次没有证明可利用的 XSS；Tiptap 审计项尚需调用可达性分析 |
| 秘密 | 对 Git 跟踪文本执行私钥头、AWS access key、GitHub token、OpenAI 风格 token 模式扫描，只输出命中位置，未命中 | 不扫描历史提交、排除运行秘密与证书容器；不是专业秘密扫描器的全量结论 |

## 证据质量与覆盖缺口

1. CodeGraph 用于结构/调用关系，随后核对实际文件与精确行号；首次误用 explore 的 `--json` 选项失败，改用受支持参数后成功。部分初始 PowerShell 通配文件/工作目录读取失败，均按正确路径重新读取，没有把失败当成阴性证据。
2. PNPM 审计在线查询依赖元数据，未发送源代码或业务数据；初次标准输出过长，重新执行并完整保存 JSON。未使用 `npm audit` 代替当前 PNPM 锁文件，也未执行 install/fix。
3. 已读取本轮实际 JAR 库目录并核实一个后端公告；未完成全量 Maven/SBOM 漏洞关联、历史秘密扫描、SAST 自动污点流或动态渗透。不存在“依赖全量通过”的结论。
4. 未探测线上/本地应用、管理会话、代理规则、网关头、WebSocket、实际 TLS 链、业务数据库或数据可见性。攻击步骤是后续隔离测试设计，**不是本次已执行的 PoC**。
5. 总审统一执行的 L2 单元测试通过不能覆盖本报告未纳入现有测试的逻辑缺口，也不能证明 G2/H 系列运营或真实环境验收完成。本审计不改变路线图阶段状态。

## 建议整改顺序

1. 先更新实际相关的 Vite 与后端 protobuf，补上 PNPM/Maven 产物审计；复核 Windows 开发入口经网关的暴露面。
2. 在同一批修复中统一素材管理权限、移除普通更新的审核状态写权限，并补齐真实服务链的正反向授权测试。
3. 给上传任务补独立所有者，覆盖删除/异常生命周期；随后完成未验证依赖公告的可达性和全量后端 SCA。

除已经解释的条件性公告外，不根据包版本老旧、单个危险关键词或扫描器严重级别直接推导项目已被攻破。
