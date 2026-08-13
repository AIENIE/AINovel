# AINovel v1.0 代码易读性与可维护性审计

审计日期：2026-08-13

审计范围：当前工作树中的 `backend/src`、`frontend/src`、构建/类型/测试配置及当前架构、API、运维文档。仅审计，不修改业务代码。

方法：先通过 CodeGraph 1.5.0（694 个文件、13,625 个符号）确认模块与调用关系，再核对真实源码、文档、静态检查和既有测试。仓库明确匹配 `项目归属：aienie`。

结论：**REQUEST CHANGES**。没有发现仅凭可维护性就应判为 CRITICAL 的问题；有 3 项 HIGH、5 项 MEDIUM、2 项 LOW。主要风险不是命名或格式，而是前后端契约失去静态约束、常规验证链不能阻止类型/静态检查回归，以及核心模块职责过载。

## 摘要

| 严重级别 | 数量 | 主要主题 |
| --- | ---: | --- |
| CRITICAL | 0 | 无 |
| HIGH | 3 | 动态 API 契约；类型/静态门禁失效；控制器承载导出执行引擎 |
| MEDIUM | 5 | 质量链重复；工作台聚合 hook；失败静默；前端配置漂移；API 文档不可执行 |
| LOW | 2 | API 客户端超大聚合；后端依赖声明可审计性不足 |

建议顺序：先建立可通过的 `typecheck + lint` 基线和端到端 DTO，再拆后端导出控制器与前端工作台聚合；质量链去重和文档/配置治理可随后推进。不要在修复这些问题时启动路线图所禁止的 G2 完整方案 A。

## 发现

### MNT-01 [HIGH] v2/管理端契约以 `Map<String,Object>` 与 `any` 贯穿前后端，编译器无法保护字段演进

- 位置：`backend/src/main/java/com/ainovel/app/v2/V2ContextController.java:34`、`:44-49`、`:202-216`、`:250-281`；`backend/src/main/java/com/ainovel/app/v2/V2ContextPersistenceService.java:41-53`、`:117-128`、`:135-179`；`frontend/src/lib/api-client.ts:1324-1355`；`frontend/src/pages/Workbench/tabs/KnowledgeGraphTab.tsx:46-58`。
- 证据：后端生产代码共有 **628** 处 `Map<String, Object>`（50 个文件），其中 28 个 `@RequestBody Map<String,Object>`（7 个文件）；前端排除测试、生成式 UI 基础组件和 locale 后仍有 **466** 个 `any`（48 个文件），仅 `api-client.ts` 就有 210 个。关系接口靠字符串 `source/target/relationType` 取值，前端还兼容 `sourceId/from`、`targetId/to` 等未声明别名。
- 维护成本：字段改名、nullability、枚举或嵌套结构变化会从编译期错误退化为运行时分支；测试与 UI 需要手工猜测响应形状。`Map` 还迫使每个服务重复 `str/intVal/boolVal/list/map` 转换并弱化 Bean Validation/OpenAPI。
- 建议：按领域建立 request/response record（Lorebook、Graph、Extraction、Version、Export、Workspace、Model），在 controller 边界使用 `@Valid`；返回 DTO 而不是手工 Map。前端为相同领域导出接口类型，并用 Zod（依赖已存在）只在网络边界解析 `unknown`；禁止业务组件继续接收 `any`。先迁移最活跃的 v2 context/version/export，再逐域收紧。

### MNT-02 [HIGH] 仓库的标准验证链不运行 lint/typecheck，且当前两项均失败

- 位置：`frontend/package.json:6-11`；`frontend/tsconfig.app.json:18-23`；`frontend/eslint.config.js:10-26`；`doc/operations/verification.md:7-12`；`doc/operations/development.md:20-24`；`README.md:40-42`。
- 证据：文档和标准命令只执行 Vitest 与 Vite build；`package.json` 没有 `typecheck` 脚本，验证文档也未调用 `npm run lint`。实际 `npm run lint` 在 16.14 秒内报告 **580 errors / 39 warnings（75 个源码/测试/配置文件）**：其中 `frontend/src` 是 579 errors / 39 warnings；整个命令多出的 1 个 error 来自 `tailwind.config.ts` 的 CommonJS `require`。分类为 578 个 `no-explicit-any`、1 个空对象类型、1 个 CommonJS require，以及 31 个 `react-hooks/exhaustive-deps`、8 个 refresh 警告。这些多数是集中式既有类型债/规则违规，不代表 580 个独立功能 bug。`npx tsc -b --pretty false` 在 7.99 秒内失败：`api-client.test.ts:68-69` mock 元组推断错误，`Admin/Login.tsx:153` 未使用变量，`DesktopPanels.test.tsx:76`、`MobileWorkbenchPanel.test.tsx:84/120`、`sidebarPanels.test.tsx:63/129` 的测试 props 已落后于组件契约。
- 上下文验证：Vite build 仍通过，说明 `vite build` 不承担完整项目 typecheck；Vitest 122 项通过也不能发现测试代码自身的 TypeScript 契约漂移。
- 维护成本：PR 可以在生产构建与运行时测试全绿时继续累积类型错误、hook 闭包风险和失效测试夹具；新成员无法从文档得到真正的合入门槛。
- 建议：新增 `typecheck: tsc -b --pretty false`，把 `lint`、`typecheck`、`test`、`build` 全部纳入 CI 与 `doc/operations/verification.md`。先建立一次性基线修复，不要通过全局关闭 `no-explicit-any` 掩盖；测试夹具应改用类型化 factory/`satisfies`，减少每次新增 prop 修改多份对象字面量。

### MNT-03 [HIGH] `V2ExportController` 同时承担 HTTP、状态机、持久化补丁和四种文件渲染

- 位置：`backend/src/main/java/com/ainovel/app/v2/V2ExportController.java:37-50`、`:54-120`、`:181-274`、`:276-350`、`:357-421`、`:492-560`。
- 证据：控制器共 591 行；请求读取时调用 `refreshJob`，按创建时间模拟 queued/processing 状态，再同步生成 TXT/DOCX/EPUB/PDF，写 `contentBytes`、构造文件内容、解析稿件 JSON、清理 HTML，并用 Map 补丁回写持久化服务。项目已有 `ControllerLayerArchitectureTest:20-58`，但该测试只禁止 repository、`@Transactional` 和私有 `currentUser`，未限制控制器中的领域/IO 逻辑。
- 维护成本：增加格式、改变任务生命周期或修正文档渲染都必须修改同一个 Web 层类；只能通过直接实例化 controller 测试内部执行引擎，异步/重试/失败状态难单测并易造成行为与持久化分叉。
- 建议：拆为 `ExportJobApplicationService`（状态机与幂等）、`ManuscriptExportAssembler`（章节/场景选择）、`ExportRenderer` 策略（txt/docx/epub/pdf）、`ExportJobMapper`。Controller 只做鉴权、DTO 映射与 HTTP 响应；为每个 renderer 加金样/结构测试，并扩展架构测试限制 controller 私有业务方法或非 HTTP 依赖。

### MNT-04 [MEDIUM] 三条质量链复制场景解析、上下文收集、HTML/JSON 容错，规则可能逐步漂移

- 位置：`SlopDiagnosticService.java:88-165`、`:403-442`、`:592-639`（662 行）；`PlotQualityService.java:90-111`、`:370-499`、`:540-590`（606 行）；`SlopDriftService.java:77-130`、`:316-412`（474 行）。
- 证据：三类 service 都自行读取 `Outline.contentJson`/`Manuscript.sectionsJson`、转换 `List<Map<...>>`、解析 UUID、剥离 HTML、截断文本和解析 AI JSON；文本诊断与剧情诊断还分别复制 `resolveScene`、最近两场前文、角色卡摘要。两者当前的截断预算相近，但不是共享契约。
- 维护成本：大纲 schema、HTML 正规化、前文窗口或角色上下文一旦变化，至少三处需同步；漏改会让相同场景在文本/剧情/drift 诊断看到不同事实，且复制的大方法增加审阅负担。
- 建议：抽出类型化 `ManuscriptSceneContextReader` 与 `QualityPromptContext`，统一 scene traversal、plain-text normalization、previous-scene window 和 character summary；AI JSON 解析/围栏剥离放入共享 `StructuredAiResponseParser`，各质量服务只保留领域评分/持久化。用同一 fixture 对三条链做契约测试。

### MNT-05 [MEDIUM] `useManuscriptSidebarData` 聚合五个领域并暴露 75 项接口，修改扩散到多层组件与测试

- 位置：`frontend/src/pages/Workbench/hooks/useManuscriptSidebarData.ts:64-173`、`:178-244`、`:299-569`、`:605-680`；`frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx:227-369`；`DesktopSidebarPanel.tsx:16-107`。
- 证据：该 hook 681 行，包含 17 个 state、24 个 callback、25 次 v2 API 调用；同时负责版本/分支/差异、导出/模板/下载、目标、统计、上下文预览，并返回约 75 个字段/动作。typecheck 已显示新增 `abandonBranch`、`downloadExport`、`updateBranch`、`exportDownloadingJobId` 后至少 3 份面板测试夹具未同步。
- 维护成本：领域互不相关的变更都会改变同一返回对象和 `DesktopSidebarPanelProps`，造成巨型 props drilling、重复测试桩与高回归面；代码所有权难按领域划分。
- 建议：拆为 `useVersionSidebarModel`、`useExportSidebarModel`、`useGoalsModel`、`useStatsModel`、`useContextPreviewModel`，每个返回稳定的 `{state, actions, query}`；面板直接消费领域 model 或窄 Context。为测试提供每域 factory，禁止复制完整 75 项对象。

### MNT-06 [MEDIUM] 持久化与会话失败被静默吞掉，支持人员无法区分离线、服务失败和正常状态

- 位置：`frontend/src/pages/Workbench/hooks/useWorkbenchLayoutPersistence.ts:56-78`、`:92-104`；`frontend/src/pages/Workbench/hooks/useWritingSession.ts:74-99`、`:126-138`。
- 证据：服务端布局读取失败、布局同步失败、写作 session 启动失败、自动版本创建失败均用空 `catch` 或 `.catch(() => {})`；仅损坏的 localStorage 缓存有可理解的“本地降级”注释。布局文档声称这些能力均持久化，但代码对远端失败没有日志、状态或用户反馈。
- 维护成本：布局/统计/自动快照丢失会表现为偶发“不记忆”，无法从前端错误报告、请求上下文或 UI 判断原因；测试也倾向只验证 happy path。
- 建议：对可容忍失败返回显式 degraded state，并调用现有 `reportClientError`（附领域动作，不含 payload）；对自动保存给出非打扰式状态/重试，对 session start/end 做有界重试或待同步队列。只有缓存解析错误可以静默降级，但应计数。

### MNT-07 [MEDIUM] Tailwind/shadcn 配置已经与实际样式入口漂移

- 位置：`frontend/package.json:80`；`frontend/tailwind.config.ts:95`；`frontend/src/components/editor/TiptapEditor.tsx:79-84`、`:299-300`；`frontend/components.json:6-9`；`frontend/src/main.tsx:1-2`。
- 证据：`@tailwindcss/typography` 已声明且编辑器使用 `prose`、`prose-*` 修饰符，但 Tailwind 仅加载 `tailwindcss-animate`；因此这些 typography utility 不会由官方插件生成。shadcn `components.json` 仍指向不存在的 `src/index.css`，实际入口是 `globals.css`。
- 维护成本：编辑器主题/排版类在代码审查中看似生效但构建产物缺失；以后运行 shadcn CLI 可能更新错误 CSS 文件或失败，配置成为不可信来源。
- 建议：在 Tailwind 使用 ESM import 并注册 typography 插件，增加构建产物/编辑器渲染测试；把 shadcn CSS 路径改为 `src/globals.css`。配置引用应纳入轻量一致性测试。

### MNT-08 [MEDIUM] v2 API 文档只有路由清单，无法约束动态请求/响应字段

- 位置：`doc/api/v2-context.md:5-25`；对应代码 `V2ContextController.java:43-58`、`:201-216`、`:249-281`；相同模式见 `doc/api/v2-export.md`、`v2-workspace.md`、`v2-models.md`。
- 证据：Graph 文档仅写“创建图谱关系”，未声明必需字段 `source`、`target`、禁止同源及响应字段；Lorebook 只列部分字段，未说明默认值/响应形状；Extraction 虽列最小字段但没有枚举、nullability、错误语义。代码同时用 Map，因而文档是唯一可读契约却不完整。
- 维护成本：前端维护者必须读取 Java 实现或保留兼容别名；OpenAPI annotation 也统一使用无信息的 `@Operation(summary = "v2 API endpoint")`，不能自动补偿。
- 建议：DTO 落地后由注解生成结构化 OpenAPI，并在 `doc/api` 保留语义/示例/错误；增加 controller 契约测试或 OpenAPI snapshot，检查文档链接与 endpoint/字段覆盖。跨服务契约仍按项目规则维护在 `aienie-doc`，本项只针对 AINovel 自身 HTTP API。

### MNT-09 [LOW] 单一 `api-client.ts` 聚合全部领域，文件变更冲突与测试范围不断扩大

- 位置：`frontend/src/lib/api-client.ts:1-40`、`:146-288`、`:726-1573`；`frontend/src/lib/__tests__/api-client.test.ts`（889 行）。
- 证据：客户端 1,573 行，导入约 39 个类型，统一对象包含 auth、admin、AI、story、world、material、guided creation、quality 及全部 v2 子域；CodeGraph 显示 `api` 被至少 13 个业务入口直接依赖。请求基础设施和大量领域 mapper/DTO 兼容代码同文件共存。
- 维护成本：任何 API 修改都触碰热点文件；模块边界、循环依赖和按域测试难控制，review diff 噪声大。
- 建议：保留 `transport.ts`（鉴权、428 proof、错误、blob/SSE），按 `clients/{story,world,quality,admin,v2-context,...}.ts` 拆领域；`index.ts` 仅组合兼容的 `api` facade。mapper 与 schema 靠近领域客户端并独立测试。

### MNT-10 [LOW] 后端依赖分析无法作为可靠门禁，直接依赖和 starter/运行时依赖未建立明确政策

- 位置：`backend/pom.xml:15-98`、`:118-151`。
- 证据：默认 Maven dependency plugin 3.8.1 不能读取 Java 25 class（major 69）；显式 3.9.0 可以完成，但报告大量“used undeclared”（Spring、Hibernate、Jackson、gRPC、SLF4J 等传递依赖）以及“unused declared”（全部 Spring Boot starters、JDBC/runtime drivers、Testcontainers 等）。这些列表包含大量 starter、反射、ServiceLoader、注解和运行时发现的预期误报，不能直接据此删依赖；同时源码确实直接导入多个传递 API，升级 starter 时存在隐含耦合。
- 维护成本：没有版本固定和 ignore/direct-dependency 政策时，报告既噪声大又掩盖真正的传递依赖使用，依赖升级/裁剪难评估。
- 建议：固定支持 Java 25 的 dependency plugin；明确“直接导入则直接声明”与“starter/runtime/反射依赖 allowlist”，用 `ignoredUnusedDeclaredDependencies`/`usedDependencies` 校准后再纳入 CI。任何删除必须结合源码、Spring 条件装配和运行测试，不采纳当前原始列表作为删除清单。

## 正向观察

- 包结构已按 `workflow/story/manuscript/world/material/quality/v2/admin/integration` 分域，`ResourceAccessGuard` 统一所有权校验；`ControllerLayerArchitectureTest` 已阻止 controller 直接持有 repository/transaction。
- Flyway 是数据库事实源，`backend/sql/schema.sql` 已明确降为历史说明；当前 V1-V12 与部署文档一致。
- 多个高风险链已有聚焦测试：管理员认证、资源访问、G1/G2、质量、v2 持久化与工作台 hooks；异步 MDC、异常映射和日志脱敏也有专门测试。
- 前端已经开始把稿件工作台拆成领域 hooks 与子面板，这是正确方向；本报告建议继续缩窄 model/props，而不是推翻现有结构。

## 验证结果与局限

| 命令 | 结果 | 耗时/说明 |
| --- | --- | --- |
| `codegraph sync` | 通过 | 4.9 秒；索引与当前根目录匹配、无 pending changes |
| `npm ci --legacy-peer-deps` | 通过 | 最终串行安装 126.06 秒；前置并行尝试曾留下不完整 `node_modules`，已由最终安装恢复；未修改 lockfile |
| `npm run lint` | **失败** | 16.14 秒；75 个源码/测试/配置文件，580 errors、39 warnings；其中 `src` 为 579/39，配置文件另 1 error；未计 `dist`/`node_modules`。多数为集中式类型债，不等于独立功能 bug，主因见 MNT-02 |
| `npx tsc -b --pretty false` | **失败** | 7.99 秒；8 个类型错误，代表文件与行见 MNT-02；生成的两个未跟踪 `.tsbuildinfo` 已删除 |
| `npm run test -- --maxWorkers=2` | 通过 | 34.52 秒；30 个 test files、122 tests 全部通过。限制 worker 只降低并发，不改变测试集合 |
| `npm run build` | 通过 | 10.01 秒；2,623 modules。Vite 对 1,624.66 kB 的 `UserEntry` chunk 给出 >500 kB 警告（性能报告处理） |
| `mvn -q -f backend/pom.xml test` | **环境受限** | Surefire 88 suites / 280 tests：**273 pass，0 assertion failures，7 errors**；7 个错误全部来自 `FlywaySchemaGovernanceTest` 无法找到 Docker/Testcontainers 环境。报告跨度 197.75 秒；不能据此判定迁移逻辑失败，也不能宣称后端全绿 |
| `mvn ... dependency:3.8.1:analyze` | 工具不兼容 | 68.36 秒；不支持 Java 25 major 69 |
| `mvn ... maven-dependency-plugin:3.9.0:analyze` | 完成并告警 | 58.39 秒；结果包含 starter/反射/运行时误报，按 MNT-10 仅作交叉证据 |

本轮未部署、未启动浏览器或外部服务、未修改业务文件。测试覆盖率未采集：前端未配置 coverage 脚本，后端未配置 JaCoCo，因此不能用“测试数”推断语句/分支覆盖率。正式关闭本审计前，应在可用 Docker 环境复跑 7 个 Flyway/Testcontainers 用例。

## 分阶段重构建议

1. **基线治理（先做）**：修复 8 个 typecheck 错误；为 `lint/typecheck/test/build` 建 CI 门禁；校准 eslint（不关闭核心规则）并逐域消除 578 个显式 `any`。
2. **契约治理**：先为 v2 context/version/export 建后端 DTO + Bean Validation + OpenAPI，再生成/手写前端 schema，删除兼容别名；补 API 契约快照。
3. **边界拆分**：把导出执行引擎移出 controller；把工作台 sidebar hook 拆为领域 models；把 `api-client` 拆 transport 与领域 clients。
4. **共享质量上下文**：抽出 scene/context/AI-JSON 解析公共组件，以相同 fixture 保护 Slop、Plot、Drift 语义一致性。
5. **配置与可观测性**：修复 typography/shadcn 配置漂移；把布局、session、自动快照的静默失败变成可观测 degraded state；校准 Maven 依赖分析。
