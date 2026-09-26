# AINovel v1.0 代码易读性及可维护性审计

审计日期：2026-09-26。基线：`develop` / `2a1fb98a9e85d9de9feee6e2d820ce7961797a82`，以本次工作区实际文件为准。本报告只提出问题与验证办法，不修改业务实现、数据库、部署配置或路线图。

## 结论与优先级

项目已有按领域组织的 Spring 服务、前端工作台 hooks、统一异常处理、请求标识、Flyway 迁移、鉴权与隔离测试，并不是缺少工程基础。当前主要维护风险是：动态数据契约绕过类型约束、正文处理存在多套不等价实现、容错工具被用于不可丢数据的写入路径，以及部分测试只能证明调用或格式存在，不能证明交付内容正确。

本维度确认 **8 项发现：P1 × 1、P2 × 5、P3 × 2**。P1 应优先修复；P2 为有具体后果的中等风险；P3 为降低后续误用与维护成本的改进。没有仅因文件长或使用 Map 就判定缺陷，也没有将有权限用户的错误输入称为越权。

| 编号 | 级别 | 发现 | 证据性质 |
|---|---|---|---|
| MAINT-01 | P1 | PDF 导出主动把中文替换为问号，测试仅验证 TXT | 确定的实现行为；未执行 PDF 渲染器或视觉验收 |
| MAINT-02 | P2 | 分支操作缺少字段契约，未知合并策略进入全量覆盖 | 确定的控制流；UI 限制了正常可选值 |
| MAINT-03 | P2 | 存储 JSON 错误降级为空集合后可进入正文写回 | 条件性风险；未证明现有数据库已有损坏数据 |
| MAINT-04 | P2 | 前端通用网络类型实际为 any，成功响应缺少边界校验 | 确定的类型与解析行为 |
| MAINT-05 | P2 | 多套 HTML 正文转换规则不一致 | 确定的转换差异；已有关联研究待决策 |
| MAINT-06 | P2 | 条件迁移测试中的预期版本数量已过时 | 静态核实；本轮 Windows L2 未执行容器路径 |
| MAINT-07 | P3 | OpenAPI 注解覆盖检查接受大量无信息量说明 | 确定的文档与测试缺口 |
| MAINT-08 | P3 | 代理入口阶段提示与 CI 模块说明发生漂移 | 确定的文档差异；权威路线图仍然清楚 |

## 范围、方法与限制

使用 `aienie-dev-workflow` 的仓库审查路径，设置 `CODEGRAPH_DIR=.codegraph-win` 后检查索引：项目匹配，状态 complete，819 个索引文件、17,114 个节点、40,814 条边，待变更为 0。通过 CodeGraph 的 `node`、`explore`、符号依赖追踪定位代码，再逐段阅读具体实现。精确字面量、SQL、PowerShell 和文档采用 `rg` 与文件读取。

以下为 `git ls-files` 范围中的工作区物理行数，不是覆盖率，也不意味着逐行精读全部文件。前端总数包含测试；脚本统计后缀为 ps1、psm1、py、sh、java。

| 范围 | 文件数 | 物理行数 |
|---|---:|---:|
| 后端生产 Java | 465 | 37,892 |
| 后端 Java 测试 | 121 | 14,851 |
| 前端 src 内 TS/TSX（含测试、语言资源） | 205 | 35,372 |
| 其中前端测试 | 40 | 4,998 |
| scripts 下脚本 | 45 | 4,701 |
| Flyway SQL 迁移 | 18 | 2,186 |
| doc / user-doc 内受跟踪 Markdown | 102 | 9,801 |

重点追踪了稿件编辑和生成、分支与版本、导出、正文规范化、叙事上下文、前端 API 边界、侧栏状态、测试门禁、Windows/发版构建入口及路线图契约。安全专属检查和负载/性能分析由同目录对应报告承载。

本次没有启动应用、连接业务数据库、执行 Docker/Testcontainers、发布、付费 AI 调用或真实数据写入。未把现有用户变更当成本次审计修复：`env.example`、`doc/operations/verification.md`、`scripts/windows/Invoke-Local.ps1`、`scripts/windows/tests/Test-LocalContract.ps1` 及 `scripts/windows/local-trust/` 均保留原状。统一 L1/L2 结果见总报告；主审提供的本轮后端结果为 409 项、392 通过、17 条件跳过、0 失败/错误，不能据此宣称下述被跳过迁移场景通过。

## 具体发现

### MAINT-01 / P1：PDF 导出确定性损坏中文，格式级测试未覆盖输出语义

**证据**

- `frontend/src/pages/Workbench/tabs/manuscript-writer/ExportSidebarPanel.tsx:85` 向用户提供 PDF 选项。
- `backend/src/main/java/com/ainovel/app/v2/V2ExportDtos.java:11` 接受 `pdf`；`V2ExportRenderer.java:34` 调用 PDF 实现。
- `backend/src/main/java/com/ainovel/app/v2/V2ExportRenderer.java:140` 将每个大于 255 的 Unicode 码点转换为 `?`；`:113–122` 手工拼接 PDF，固定一个页面和 Helvetica 字体，最终按 ISO-8859-1 编码。
- `backend/src/test/java/com/ainovel/app/v2/V2ExportRendererTest.java:17–34` 仅对 TXT 的 LF/CRLF、中文、段落和实体进行字节断言；没有 PDF 文本完整性或分页断言。

**触发与后果**：作者按当前 UI 导出中文小说时，“中文”必定成为“??”；中文标题也会损坏。再长的正文也只构造一个 PDF 页面，未实现分页。原数据库稿件不因此改变，但用户下载的正式产物无法保留作品内容。将多种文档格式压在简易字符串拼接器里，且只测 TXT，掩盖了不同格式的能力边界。

**修复建议**：把格式渲染拆为独立实现与明确能力契约；PDF 使用支持中文字体嵌入、Unicode 与分页的实现。在修复完成前，应通过能力声明禁用未达到完整性要求的格式，避免返回看似成功的损坏产物。选择具体库时另行审查许可证、依赖安全与离线构建条件。

**验证建议**：离线生成含中文、英文、标点、补充平面字符和多章长文的 PDF；用独立解析器回读并与规范化正文比对，同时实际渲染首页、中间页、末页检查分页和文字可见性。增加 PDF 专属回归，不能只判断文件非空、以 `%PDF` 开头或接口返回成功。

### MAINT-02 / P2：V2 请求外壳没有字段约束，错误策略可以意外覆盖主线

**证据**

- `backend/src/main/java/com/ainovel/app/v2/V2RequestPayload.java:17–35` 只验证请求为 JSON object，随后转为 `Map<String,Object>`，不约束字段、枚举或 null。
- `backend/src/main/java/com/ainovel/app/v2/V2VersionController.java:123–130` 完成作品归属检查后把该 Map 交给 `mergeBranch`。
- `backend/src/main/java/com/ainovel/app/v2/V2VersionPersistenceService.java:315–348` 只有 `SCENE_SELECT` 进入逐场选择，其余所有 strategy 都执行 `clear()` + `putAll(sourceSections)`；`:343` 对非空且不是 `target` 的解决值一律选择 source。
- 同文件 `:282–296` 的更新分支操作直接把 name/status `.toString()` 后落库；只有主分支 abandoned 的特殊判断，没有状态枚举或合法转换检查。

**触发与后果**：有权操作本作品的 API 调用者若发送 `{"strategy":"SCENE_SELET"}`，接口不报字段错误，反而按照全量覆盖合并；`{"name":null}` 或 `{"status":null}` 会触发空指针；任意 status 可使后续只接受 active 的操作失败。新增客户端或后续重构中，字段拼写和状态约定只能靠人工维持。

**已存在的缓解**：`VersionSidebarPanel.tsx:156–163` 只提供两个合法策略；hook `useManuscriptSidebarData.ts:84` 也使用联合类型。因此正常现有 UI 不会产生拼错策略。版本历史可用于恢复已有检查点，本报告不认定所有正文不可恢复，也不认定存在跨用户访问。合并按钮 `VersionSidebarPanel.tsx:166` 直接调用操作，没有额外确认；服务 `V2VersionPersistenceService.java:358–368` 创建的是合并后版本，并非对当前稿件执行一份专门的合并前快照。

**修复建议**：为 CreateBranch、UpdateBranch、MergeBranch 定义独立 DTO 和 enum；仅在字段缺省时使用有文档的默认值，对显式未知值和 null 给出稳定 4xx。把分支状态迁移封装为领域操作，避免通用 Map 任意改 status。

**验证建议**：补真实 HTTP 反序列化与持久化边界测试：未知策略、空值、错误 scene resolution、重复名称和非法状态转换均不改变稿件、分支或版本。现有 `V2VersionControllerTests.java:105–115` 主要验证委托，`V2PersistenceServiceTest.java:138–162` 覆盖合法创建与保存，不能代替这些输入契约测试。

### MAINT-03 / P2：坏 JSON 与合法空正文被混同，条件性错误可被写回放大

**证据**

- `backend/src/main/java/com/ainovel/app/common/JsonColumnCodec.java:15–30` 对读取/写入的所有 Exception 返回 fallback，未区分缺值、格式损坏和序列化失败。
- `backend/src/main/java/com/ainovel/app/v2/V2Json.java:21–47` 另有一套同类降级，返回 `{}`、空 List 或空 Map。
- `backend/src/main/java/com/ainovel/app/manuscript/ManuscriptService.java:290–295,314–315` 用该 codec 处理正文；`:141–153` 先解析整个 sections，再放入一个场景并写回。
- `backend/src/main/java/com/ainovel/app/v2/V2VersionPersistenceService.java:599–604` 同样把 sections 解析异常降为空 Map；`:316–354` 将结果用于分支合并及稿件覆盖。
- `backend/src/main/resources/db/migration/V1__baseline.sql:32,504`、`V3__backfill_baselined_v2_persistence.sql:241` 将 sections 存为 longtext，而非由数据库类型保证对象结构。

**触发与后果**：假设历史导入、人工维护、旧程序或存储数据异常已产生不可解析 sections，用户再保存一个场景时，读取层返回空 Map，写入层可能将整稿替换为只有该场景的合法 JSON。读取失败已经发生的条件下，系统把问题伪装成“原稿为空”，阻碍定位并扩大损坏。序列化异常也可能被伪装为成功保存空对象。

**界限**：本轮没有查看真实稿件表，不证明线上已存在坏 JSON；正常由 ObjectMapper 写出的正文不会仅因包含中文就触发该问题。风险是异常处理策略与不可丢数据的写入用途冲突，不能按已发生的数据丢失事故表述。

**修复建议**：区分 `readOptional` 与 `readRequired`；关键正文、版本和证据读写遇到错误应终止事务，并返回不含原文的稳定错误码和定位标识。只有明确允许缺省的配置可降级，且应记录脱敏诊断。逐步统一两套 codec，避免各领域自行决定空值语义。

**验证建议**：在隔离测试实体中注入坏 JSON、错误 JSON 形状和模拟序列化异常；调用保存、合并、回滚/差异读取，断言数据库原字段未被改写、事务回滚且能识别存储错误。`JsonColumnCodecTest.java:20–30,53–59` 当前恰好把 fallback 行为写为预期，需要增加调用方的数据完整性保护测试，而不能仅改工具测试通过。

### MAINT-04 / P2：前端 `NetworkObject` 绕过类型检查，响应异常在页面深处暴露

**证据**

- `frontend/src/lib/api-client.ts:52–56` 使用 `z.record(z.string(), z.any())` 推导 `NetworkObject`；字段类型实际为 any。
- 同文件 `:163–184` 的 `requestJson<T>` 把 `resp.json()` 直接断言为 T；成功响应未调用领域 schema。
- 同文件 `:486,500` 对 `dto.chapters`、`c.scenes` 使用 `(value || []).map(...)`，未验证是否数组。若服务变更、代理返回或历史 DTO 导致 truthy 非数组值，会出现运行时 TypeError。
- 同文件 `:1575–1602` 的版本/分支请求和响应大量使用 NetworkObject；该类型在前端 24 个文件出现。CodeGraph 显示整个 api-client 被 89 个文件依赖；这两个数分别代表文本使用和图依赖，不是同一种度量。

**后果**：字段拼写、数组形状与枚举变更不会可靠地被 TypeScript 编译发现。接口变更同时牵涉请求、认证、错误解析、DTO 转换及众多领域方法，失败通常在页面渲染或操作后才出现。问题不在 Zod 本身，而是当前 schema 没有表达领域契约。

**修复建议**：优先给稿件、分支、导出、异步任务终态定义具体 DTO/schema；请求端用独立类型，响应端在网络边界校验并返回带接口上下文的可诊断错误。剩余开放字段使用 unknown 并显式收窄。按 auth/transport 与业务领域拆分 api-client，但保留单一认证及错误处理实现。

**验证建议**：在 API 测试中覆盖 `chapters:{}`、`scenes:"bad"`、未知 status 和缺少关键 ID；断言错误在 API 边界可预测地返回，且不覆盖已有页面内容。对已有合法旧 DTO 做兼容性回归。不能把 `typecheck` 通过当作这些运行时数据已验证。

### MAINT-05 / P2：正文转换重复且规则不等价，后续修复容易只覆盖一个入口

**证据**

- `backend/src/main/java/com/ainovel/app/quality/SlopDiagnosticService.java:600–610`、`PlotQualityService.java:529–539`、`SlopDriftService.java:365–375` 各自复制正则去标签及少量实体替换。
- `backend/src/main/java/com/ainovel/app/manuscript/context/SceneDraftContextCompiler.java:876–889` 把标签替换为空格，还解码引号并合并空白。
- `backend/src/main/java/com/ainovel/app/v2/V2ExportRenderer.java:130–137` 保留块/换行并使用 HtmlUtils 解码。
- `backend/src/main/java/com/ainovel/app/narrative/NarrativeText.java:19–50` 使用解析器，保留块、处理 br、跳过 script/style/head；`:16` 明确证据位置为 Unicode code point。
- `frontend/src/pages/Workbench/tabs/manuscript-writer/shared.ts:82–90` 又用 DOM textContent 和 JS 字符串长度。

**触发与后果**：`<p>甲</p><p>乙</p>` 在三种质量服务中成为 `甲乙`，旧上下文编译器中成为 `甲 乙`，TXT 中保留段落；`&quot;` 和 `&#x4FE1;` 在质量服务中不会完整解码。同一篇作品因此在审阅、生成上下文、导出和证据界面具有不同文本表示；后续引文、位置、哈希或全文处理改造容易修好一条路径却遗漏其他路径。

**界限**：不同展示目的允许不同渲染形式，不能强制所有接口共用相同空白和位置单位，也不能直接把历史 H1 code point 证据改为 UTF-16。风险在于缺少共同基础与显式版本/转换契约。`doc/research/slop-20260926/implementation-plan.md:32` 已提出版本化正文抽取方案；路线图仍将相应业务改造列为待决策，审计不替代该阶段决策。

**修复建议**：抽出受支持富文本到结构化正文块的基础转换，明确每个调用方的段落、实体、空白、Unicode 和截断策略；用版本号及适配器兼容旧证据。把相同的三个质量服务实现首先收敛，再按批准范围推进全文/偏移映射。

**验证建议**：用一套共享语料覆盖 p/div/li/br、命名/数字实体、中文、组合字符、补充平面字符及重复引文，分别断言每种契约的预期表示；增加跨路径差异测试，区分“允许的呈现差异”和“信息丢失”。

### MAINT-06 / P2：被条件跳过的迁移测试包含陈旧断言

**证据**

- 当前迁移目录是完整 `V1` 至 `V18`；`backend/src/test/java/com/ainovel/app/config/FlywaySchemaGovernanceTest.java:43` 的空库预期为 18。
- 同文件 `:82–90`、`:112–119`、`:146–153` 先 baseline 到 V1，再执行不设 target 的 migrate，仍预期执行 **16** 个迁移。按当前资源应为 V2 至 V18，共 **17** 个。
- 同文件 `:289–312` 的 `upgradesV12DatabaseWithSceneGenerationAttributionSchema` 先迁到 V12，再不设 target 地迁到最新，仍预期 **1** 个；当前应为 V13 至 V18，共 **6** 个。
- 类级 `:26` 为 `@Testcontainers(disabledWithoutDocker = true)`。外部 MySQL 路径 `ExternalMySqlMigrationVerificationTest.java:20` 又需要显式启用；常规本地 L2 并不替代这两类真实迁移验收。

**后果**：在容器路径真正执行且迁移正常完成的环境中，这些断言仍会失败，制造与 SQL 缺陷无关的红灯；Windows 条件跳过又让这些测试长期不暴露陈旧状态。修复迁移或发版时需要先辨别测试自身是否可信。

**修复建议**：明确每个测试是验证固定版本还是验证当前最新版：固定 V13 断言则设置 `.target("13")`；验证最新版则以受控迁移清单/统一最新版本常量计算预期，并保留关键表、索引和历史数据断言，不能只删数量断言。报告中分列执行、条件跳过与未执行。

**验证建议**：在允许的隔离 MySQL 验收环境执行空库、完整旧库 baseline、缺表旧库和 V12 升级路径。本轮没有运行该容器测试，本项结论是静态核实的断言漂移，**不是本轮已观察到 Maven 实际失败**。

### MAINT-07 / P3：OpenAPI 注解检查保证存在性，未保证 API 文档可用

**证据**：`backend/src/test/java/com/ainovel/app/quality/OpenApiAnnotationCoverageTest.java:28–49` 枚举十个 Controller 并只检查 Tag/Operation 非空。生产代码中 80 处使用完全相同的 `@Operation(summary = "v2 API endpoint")`，例如 `backend/src/main/java/com/ainovel/app/v2/V2VersionController.java:32,41,61,122`；版本接口返回值还多为 Map，见 `:124`。

**后果**：新增维护者可以看到路径，却无法从摘要了解操作目的、默认合并行为、冲突返回、状态机与恢复方式。该测试通过只说明有注解，不能称接口契约完备。显式列举 Controller 也不会自动发现新增领域是否遗漏文档约束。

**修复建议**：给高风险写操作补具体摘要、请求/响应类型和错误码，优先覆盖合并、回滚、生成任务与导出；将测试目标改为关键接口的生成 OpenAPI 契约断言，拒绝统一占位摘要。无需为了文档覆盖率给每个简单方法增加大量注解。

**验证建议**：检查生成的 API 描述能区分合并与回滚，并包含允许的策略、冲突响应和请求字段；对新增写接口验证发现机制，避免只维护手工列表。

### MAINT-08 / P3：入口说明与权威状态/构建配置不一致

**证据**

- `AGENTS.md:21` 仍要求“先完成 H1 … L4 验收”；`doc/roadmap.md:79` 已记录 H1 完成，`:105` 记录 H2 验收中且 H3 未启动。
- `scripts/ci/README.md:58` 将前端列为 npm 模块；`scripts/ci/build-release.sh:5–6` 明确 npm 模块为空、frontend 属于 pnpm；`frontend/package.json:5–8` 固定 pnpm 11.22.0。

**后果与界限**：代理进入项目时会同时遇到过时的下一步提示与正确的权威路线图，人工排查离线依赖缓存时也可能选错包管理器分支。AGENTS 已明确路线图优先，所以不会据此推翻当前 H2 状态，风险主要是额外核对成本和误执行倾向。

**修复建议**：让入口只链接唯一权威阶段状态，减少复制阶段文字；CI 模块说明与实际数组保持一致。此项只修文档表述，不改变 H 系列、G2 或其他既有门槛。

**验证建议**：文档链接/一致性检查中增加少量真正的契约项，如包管理器、当前入口域名和权威路线图链接；不要依赖脆弱的整段字符串匹配来替代业务测试。

## 结构观察与建议顺序

以下是重构候选，不额外计入缺陷数量：

- `frontend/src/lib/api-client.ts` 1,674 行，既承担认证、管理员二次证明、SSE/轮询，又承担多领域 DTO 转换和端点定义。先实施 MAINT-04 的边界类型，再按领域拆分，避免只移动文件而保留 any 传播。
- `frontend/src/pages/Workbench/hooks/useManuscriptSidebarData.ts` 687 行，`:82–104` 同时管理版本、分支、差异、导出和目标状态，`:267–574` 聚合这些领域的写操作。可以分解为版本/导出/目标三个 hook，保留统一缓存键和已存在的切换竞态测试；不能仅以行数要求一次性重写。
- `SceneDraftContextCompiler.java` 1,049 行、75 个索引符号，横跨候选选择、实体读取、历史窗口、预算分配与 manifest。它已有专属测试与 H2 隔离路径，应先固定文本契约和输入/输出样本，再分别抽出确定性的选择、预算和渲染函数。
- 项目已有 Controller 依赖限制测试和真实持久化测试，继续保留。`ControllerLayerArchitectureTest.java:36–57` 检查 Repository 字段、Transactional 和特定方法名，这些是有价值的结构规则，但不能证明 Controller 没有业务编排或状态变换。

建议先修 PDF 完整性和 MAINT-02 的写入契约，再修 MAINT-03 的失败关闭策略与 MAINT-06 的测试可信度；随后推进前端领域类型与正文转换契约，最后处理占位 API 文档和入口漂移。重构应随这些具体问题做小批次切片，不扩大为 H3/H4、新自动改稿或其他未经批准的功能建设。

## 验收边界

报告的静态发现均给出了文件与触发条件；P1/P2 关闭时需对应业务结果测试，单纯编译、lint、HTTP 2xx 或文件非空不足以关闭。真实 MySQL、浏览器和文档渲染未在本维度执行，不能据此宣称数据库升级、所有导出格式或用户端完整流程已经通过。
