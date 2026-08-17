# AINovel 冗余文件与无用代码审计

审计日期：2026-08-13

审计对象：`master` 分支当前工作树（包含审计开始前已有的 5 个部署脚本未提交修改）

审计性质：只读审计；本文不授权直接删除数据库迁移、跨服务契约或业务兼容代码

## 1. 结论摘要

本轮确认了以下清理空间：

- 前端入口可达性分析发现 **23 个生产候选文件不可达**，合计约 **89.7 KB**；其中包括一个完整的 mock 大纲编辑器、一个未接入的世界选择器、18 个未使用的 shadcn/ui 模块、一个只被死模块使用的移动端 hook，以及 Vite 模板遗留样式。
- 上述死模块对应至少 **18 个可复核的直接依赖清理候选**；必须在删除文件后重新生成 lockfile，并通过 typecheck、测试和 production build 验证。
- 后端确认 2 个可直接清理的无引用类型：`EmailCodeDto` 与 `SlopQualityIssueRepository`。另有一组“本地签到”代码处于明确禁用、无调用状态，但涉及历史表、枚举数据和 pay-service proto，只能分阶段清理。
- 确认 1 个未被产品代码引用的公共占位图、1 个空 VSCode tasks 文件、1 个仅保留说明文字的旧 schema 入口，以及 1 处 shadcn 配置路径漂移。
- Git 忽略规则有效：未发现“已跟踪但本应被忽略”的构建产物或密钥文件。`env.txt`、`node_modules/`、`target/`、`dist/`、`logs/` 均未被跟踪。

建议先执行不涉及数据兼容的前端死文件/依赖清理和两个后端无引用类型清理；本地签到遗留应单独建数据兼容清理任务。

## 2. 范围与方法

### 2.1 覆盖范围

- Git 跟踪文件：832 个。
- CodeGraph 当前索引：694 个代码/配置文件。
- 后端生产 Java：389 个文件。
- 前端 `src/`：206 个 TypeScript、TSX、JavaScript、CSS、JSON 文件。
- 同时检查了根目录、Docker/部署脚本、公开静态资源、IDE/工具配置、Flyway 迁移、测试与文档引用。

### 2.2 证据方法

1. 以 `frontend/src/main.tsx` 为唯一浏览器入口，解析静态 `import`、`export ... from` 与字面量动态 `import()`，解析 `@/` alias 和相对路径。
2. 对 173 个非测试生产候选做可达性计算：150 个可达，23 个不可达。
3. 对不可达文件再用 `rg` 全仓精确搜索组件名、路径和资源名，排除仅被测试、脚本或其他生产入口使用的情况。
4. 对 Java 类型做简单名引用筛查，再逐项排除 Spring 扫描、JPA 实体、Repository 代理、Controller、Configuration 等反射/框架入口；没有仅凭“零显式调用”判定框架类型为死代码。
5. 用 `git ls-files -ci --exclude-standard` 检查误跟踪的忽略文件，结果为空。

### 2.3 局限

- 静态入口分析无法证明外部系统是否直接请求 `public/` 下的固定 URL，因此公共资源删除前仍需核对 CDN、运营内容和浏览器书签。
- Java 反射、Spring Bean 扫描、JPA 关系映射和序列化可能形成隐式使用；本文仅把证据充分的类型列为“确认可清理”。
- Flyway 历史迁移、已发布 proto 字段和数据库枚举值属于兼容事实源，即使当前业务没有调用也不能直接删除。
- `doc/planning/`、`doc/research/`、`user-doc/` 是仓库明确保留的设计依据或用户文档，不因不在运行时 import 图中而被视为冗余。

## 3. 已确认的前端死代码与文件

### R-01（中）未接入的 mock 大纲编辑器形成完整死代码岛

证据：

- `frontend/src/components/outline/OutlineEditor.tsx:22-28` 内置 4 条 `MOCK_CHAPTERS`；`49-50` 仍保留“真实应用应调用 API”的注释代码。
- `OutlineEditor` 在全仓没有入站引用；`SortableChapterItem` 只被 `OutlineEditor` 引用。
- 该实现与当前已接入路由的 `frontend/src/pages/Workbench/tabs/OutlineWorkbench.tsx` 功能重叠，但没有持久化或产品入口。

可清理文件：

- `frontend/src/components/outline/OutlineEditor.tsx`
- `frontend/src/components/outline/SortableChapterItem.tsx`

连带清理：`@dnd-kit/core`、`@dnd-kit/sortable`、`@dnd-kit/utilities` 在当前源码中只由这两个死文件使用，对应 `frontend/package.json:15-17`。

建议：整组删除，不要把 mock 数据或注释 API 迁移到当前工作台；删除后重新生成 `package-lock.json`。

### R-02（中）18 个未使用的 shadcn/ui 模块仍被跟踪

以下模块均不在从 `main.tsx` 出发的用户端或管理员端 import 图中，全仓也没有其他入站引用：

```text
frontend/src/components/ui/accordion.tsx
frontend/src/components/ui/alert.tsx
frontend/src/components/ui/aspect-ratio.tsx
frontend/src/components/ui/breadcrumb.tsx
frontend/src/components/ui/calendar.tsx
frontend/src/components/ui/carousel.tsx
frontend/src/components/ui/chart.tsx
frontend/src/components/ui/collapsible.tsx
frontend/src/components/ui/drawer.tsx
frontend/src/components/ui/form.tsx
frontend/src/components/ui/hover-card.tsx
frontend/src/components/ui/input-otp.tsx
frontend/src/components/ui/menubar.tsx
frontend/src/components/ui/navigation-menu.tsx
frontend/src/components/ui/pagination.tsx
frontend/src/components/ui/radio-group.tsx
frontend/src/components/ui/sidebar.tsx
frontend/src/components/ui/toggle-group.tsx
```

`frontend/src/hooks/use-mobile.ts` 只被不可达的 `sidebar.tsx` 引用，也应随之删除。

依赖清理候选（经源码 import 交叉核验）：

- `@radix-ui/react-accordion`
- `@radix-ui/react-aspect-ratio`
- `@radix-ui/react-collapsible`
- `@radix-ui/react-hover-card`
- `@radix-ui/react-menubar`
- `@radix-ui/react-navigation-menu`
- `@radix-ui/react-radio-group`
- `@radix-ui/react-toggle-group`
- `embla-carousel-react`
- `input-otp`
- `react-day-picker`
- `react-hook-form`
- `vaul`
- `@hookform/resolvers`（全源码无 import）
- `zod`（全源码无 import）

注意：不要按组件目录整体删除；例如 `alert-dialog.tsx`、`toggle.tsx`、`recharts`、`@radix-ui/react-dialog` 等仍有生产调用。

建议：按上述精确清单删除文件，再逐项卸载只服务于死模块的依赖。用 `npm ci`、`tsc -b`、`npm run test`、`npm run build` 验证，避免 shadcn 代码生成或 peer dependency 造成误删。

### R-03（低）未接入的世界选择器与其专属翻译

`frontend/src/pages/WorldBuilder/components/WorldSelectorPanel.tsx:15-60` 定义并导出组件，但全仓无入站引用；当前世界管理使用 `WorldManager`/`WorldEditor` 路径。

删除组件后，可同步删除三个 locale 中只由死组件使用的：

- `worldSelector.myWorlds`
- `worldSelector.newWorld`
- `worldSelector.noWorlds`
- `worldSelector.noWorldsHint`

对应位置为 `frontend/src/i18n/locales/{en,zh-CN,zh-TW}.ts:1288-1291`。

### R-04（低）Vite 模板样式与死组件专属翻译残留

- `frontend/src/App.css:1-42` 是默认 Vite logo/card 示例样式；`main.tsx` 只加载 `globals.css`，全仓没有 import `App.css`。
- 删除 R-01 后，`outline.editSummary`、`outline.logicCheck`、`outline.addChapter`、`outline.empty` 与 `common.rename` 将失去全部引用。三个 locale 共可删除 15 个条目；`outline.title`、`outline.newChapter` 和 `common.delete` 仍有其他调用，不应删除。

### R-05（低）未引用的公共占位图

`frontend/public/placeholder.svg` 在前端与文档中均无引用，是通用模板占位图。Vite 会把它原样复制到产物，形成无业务用途的公开 URL。

建议：先核对是否存在外部运营内容直接引用 `/placeholder.svg`；若无则删除。不要将无源码引用的 `favicon.ico` 或 `robots.txt` 一并删除：它们会被浏览器或爬虫按约定路径直接请求。

## 4. 已确认的后端无用类型

### R-06（低）`EmailCodeDto` 完全无引用

`backend/src/main/java/com/ainovel/app/admin/dto/EmailCodeDto.java:6-15` 是无注解 record，除自身声明外全仓没有引用。当前管理员认证与重置流程也不消费该 DTO。

建议：直接删除该文件。它包含 `email` 与明文 `code` 字段，保留一个未使用的敏感 DTO 还会增加未来被误接入响应的风险。

### R-07（低）`SlopQualityIssueRepository` 从未注入或调用

`backend/src/main/java/com/ainovel/app/quality/repo/SlopQualityIssueRepository.java:8` 只是空的 `JpaRepository` 扩展，全仓没有注入或方法调用。质量问题通过 `SlopQualityRun` 的关系与业务服务持久化，`SlopQualityIssue` 实体本身仍被 `JpaSlopQualityRecorder`、`SlopDiagnosticService`、mapper 和运行实体引用。

建议：只删除 Repository 接口，保留 `SlopQualityIssue` 实体、表、迁移与关系映射。

## 5. 需兼容性确认的遗留代码

### R-08（中，条件清理）本地签到能力已被明确禁用，但多层遗留仍存在

当前事实：

- `backend/src/main/java/com/ainovel/app/economy/EconomyService.java:133-136` 的 `checkIn` 无条件抛出 `CHECKIN_DISABLED`，唯一调用来自 `EconomyServiceTests.java:69-72` 对“必须禁用”的断言。
- `CheckInRecord` 实体与 `CheckInRecordRepository` 之间互相引用，但没有任何业务 Service/Controller 使用。
- `BillingGrpcClient.checkin`（`59-75`）、`checkinStatus`（`96-108`）以及对应结果 record（`339-360`）没有调用方。
- `CreditLedgerType.CHECKIN` 当前没有写入调用。
- V1 已创建 `checkin_records` 表，pay-service proto 仍包含签到 RPC，生产数据也可能保留历史 `CHECKIN` 流水。

建议分两阶段：

1. 代码阶段：删除永远抛错的 `EconomyService.checkIn` 及其测试、无调用的本地 Entity/Repository、无调用的 BillingGrpcClient wrapper 与不再需要的 import/record。
2. 数据兼容阶段：先查询历史 `checkin_records` 和 `project_credit_ledger.entry_type='CHECKIN'` 数据，再决定是否用新 Flyway 迁移退役表/枚举。不要修改 V1，不要从本仓库 proto 副本中擅自删跨服务字段。

在历史数据审计完成前保留 `CreditLedgerType.CHECKIN` 是更安全的选择，否则读取旧流水可能枚举反序列化失败。

### R-09（低，条件清理）旧 schema 说明文件与文档重复

`backend/sql/schema.sql:1-14` 已不含 DDL，只说明 Flyway 才是事实源。相同规则又存在于：

- `doc/architecture/overview.md:46`
- `doc/operations/deployment.md:49`

这是一个为“旧文档/人工入口”保留的 tombstone，而非可执行 schema。若已没有外部脚本或团队书签依赖该路径，建议删除该文件，并把两处文档改为正向指向 `backend/src/main/resources/db/migration/`，不要继续维护三份相同说明。

## 6. 配置与工作区清理项

### R-10（低）空 VSCode task 文件

`backend/.vscode/tasks.json:1-4` 的 `tasks` 数组为空，且无文档引用。`launch.json` 已提供实际调试入口并被 `doc/operations/development.md` 记录。

建议：删除空 `tasks.json`，保留 `launch.json`。

### R-11（低）shadcn 配置指向不存在的 CSS 文件

`frontend/components.json:8` 指定 `src/index.css`，但仓库不存在该文件；真实入口在 `frontend/src/main.tsx:2` 引入 `globals.css`。

建议：把配置改为 `src/globals.css`。这不是删除项，但属于模板遗留，会让后续 shadcn 生成命令写入错误位置或失败。

### R-12（提示）本地生成物和工具状态未进入 Git，但可做工作站清理

审计时观察到以下未跟踪/已忽略内容：

- `.codegraph/` 与 `.codegraph-win/` 各约 45 MB；Aienie Windows 流程当前使用 `.codegraph-win/`，旧 `.codegraph/` 可在确认没有其他工具使用后清理。
- `.serena/cache/`、`.serena/memories/`、`backend/target/`、`frontend/node_modules/`、`frontend/dist/`、`logs/` 均已正确忽略。
- 根目录存在 56 字节的 `deep-research-report.md:Zone.Identifier` Windows 元数据旁车文件（文件名在 NTFS/PowerShell 中显示为特殊冒号变体），已被忽略，可删除。
- `design-doc/` 是空的本地目录，不会进入 Git，可删除。

这些不是仓库提交内容，不应为了清理它们而修改顶层 `.gitignore`；`.codegraph-win/` 的生成 `.gitignore` 也不应提交。

## 7. 不应删除的“疑似冗余”内容

- `backend/src/main/resources/db/migration/V*.sql`：不可按当前代码引用判断；是已发布数据库历史。
- `backend/src/main/proto/` 的 deprecated 字段：属于跨服务兼容契约，必须通过 `aienie-doc/interfaces/<service>/` 协调后变更。
- `backend/.classpath`、`backend/.project`：虽为 IDE 元数据，但 `launch.json` 的 `projectName` 与团队 Java/VSCode 工作流可能依赖，未形成足够删除证据。
- `frontend/public/favicon.ico`、`robots.txt`：约定路径资源，不需要源码 import。
- `doc/planning/`、`doc/research/`：路线图明确把它们作为设计证据保留；不能替代 `doc/roadmap.md`，但并非无用文件。
- `NoopTextEmbeddingClient`、Controller、Configuration、JPA Entity 等零显式构造类型：由 Spring/JPA 隐式发现，不能按普通引用计数删除。

## 8. 建议清理批次

### 批次 A：无数据风险

1. 删除 R-01、R-02、R-03、R-04、R-05 中确认无外部引用的文件与专属翻译。
2. 卸载已失去用途的直接依赖，重新生成 `package-lock.json`。
3. 删除 `EmailCodeDto`、`SlopQualityIssueRepository`、空 `tasks.json`。
4. 修正 `components.json` 的 CSS 路径。
5. 运行前端 typecheck、lint、全量单测、production build 与后端全量单测。

### 批次 B：兼容性清理

1. 核对签到历史数据与 pay-service 正式契约。
2. 删除无调用的本地签到 wrapper/Entity/Repository/禁用方法。
3. 如要退役表或枚举，新增 Flyway 迁移并提供回滚/导出策略。
4. 确认没有外部脚本依赖后，删除旧 `backend/sql/schema.sql` 入口并同步文档。

## 9. 删除验收清单

- `rg` 不再找到被删组件、专属翻译 key 或后端类型引用。
- `npm ci` 后 `npm ls --depth=0` 无 invalid/extraneous 直接依赖问题。
- `tsc -b`、`npm run lint`、`npm run test`、`npm run build` 全部通过。
- `mvn -f backend/pom.xml test` 全部通过。
- Flyway 历史文件 checksum 不变；没有编辑既有迁移。
- Git 差异仅包含明确清理目标、依赖 lockfile 和同步文档，不包含 `env.txt`、`.codegraph*`、构建输出或日志。
