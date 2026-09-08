# 验证与验收

本文件只维护可重复执行的验证步骤。日期化执行结论、截图、临时环境地址和一次性问题快照不进入仓库；具体结果由 CI、提交记录或外部交付记录保存。

## 标准验证链

Windows 原生 L1/L2 统一入口：

```powershell
.\scripts\windows\Test-Local.ps1 -Level L1
# 仅在明确选择 L2 时使用：
.\scripts\windows\Test-Local.ps1 -Level L2
```

以下命令是 Linux/CI 的等价项目级验证链：

```bash
mvn -q -f backend/pom.xml clean test
corepack pnpm@11.22.0 --dir frontend test
corepack pnpm@11.22.0 --dir frontend run build
```

运行时与浏览器验收是独立步骤，不属于默认代码验证。Windows 本地验收按 `operations/deployment.md` 使用 PowerShell 原生启动入口，并确认当前工作树、安全配置文件和外部依赖后再操作；非 Windows Docker 部署另行执行。

## 通用验收约束

- 普通用户流程必须使用真实 SSO 会话；管理员流程必须使用 `/admin/login` 和当前认证策略，不得绕过鉴权。
- 涉及创建、上传、生成、发布、兑换、积分或管理员写操作时，使用隔离测试数据并在验证后通过产品接口清理。
- 后端至少执行全量单元/集成测试；数据库迁移变更应从空库和受支持的历史版本分别验证。
- 前端至少执行全量测试与生产构建；关键页面同时覆盖桌面和窄屏。
- 对异步 AI 流程验证任务创建、事件/轮询进度、终态、刷新恢复和失败重试，不以单个 2xx 响应代替完整闭环。
- 对删除和级联行为同时验证接口状态、列表刷新和持久化结果。
- 记录失败时只保留可复现步骤和当前有效限制；修复后更新对应长期文档，不在仓库新增日期化结果报告。

## 国际化（i18n）

1. 三语 locale 键集一致性与插值变量一致性：使用 Windows 原生 `python` 遍历 `src/i18n/locales/*.ts`，比对三语键集合与 `{{var}}` 插值变量，应无差异。
2. key 引用完整性：递归扫描 `src` 下用户侧 `.ts/.tsx` 中 `t("...")` 静态键（排除 `Admin`、测试、locale 文件），所有引用键应在 zh-CN 键表中存在。
3. 语言切换：切换 zh-CN / zh-TW / en，确认全部业务文案即时更新、不刷新页面；刷新后语言保持（`aienie.user.locale.v1`）。
4. 管理后台隔离：构建后确认 `dist/assets/AdminEntry-*.js` 不含 locale 资源（如 `app.title`、业务 key 字面量）与 i18n 引用；管理后台界面保持简体中文。
5. 错误映射：构造 API 错误（4xx/5xx），确认前端展示稳定错误类别文案，不泄露后端原始 message。
6. 复数与数字：确认 `{{count}}` 键在 zh 语言有 `_other` 变体；大数字展示（如 token 计数）使用预格式化变量，千分位格式与文案一致。

## G1 引导创作

1. 访问 `/novels/quick-create`，分别创建逐步选择和自动模式草稿。
2. 确认候选生成、编辑、重写和章节/场景预览满足当前产品约束。
3. 关闭页面后重新进入，确认草稿和后台进度可恢复。
4. 确认故事、可选世界、角色和大纲写入标准实体。
5. 检查异步任务状态、幂等键、恢复路径和账本引用。
6. 在桌面与窄屏视口检查布局、错误态和完成态。

## G2 盲测

1. 管理员创建活动、邀请评审并推进活动状态。
2. 作者提交场景，确认隔离文本不覆盖正文、版本或剧情质量记录。
3. 不同受邀账号只能看到匿名 A/B；作者不能评审自己的样本。
4. A、B 和中性票统计符合规则。
5. 失败样本的扣费与退款在本地账本相互抵销。

## 单开发者质量基础

收到开发者明确的“开始部署和验证”通知后，按以下 L4 顺序执行；在此之前不得运行本节命令或运行时步骤：

1. 运行受影响的上下文、归因、迁移、API 与前端面板测试。默认质量测试应校验 36 个 fixture 的冻结分布、六类单缺陷、6 个 holdout、逐字证据及上下文引用，且不得访问网络。
2. 执行 `mvn -q -f backend/pom.xml clean test`、`corepack pnpm@11.22.0 --dir frontend test`、`corepack pnpm@11.22.0 --dir frontend run build`。
3. 分别验证新库从 V1 完整迁移到 V13，以及已有 V12 数据库只执行 V13 升级；确认 `scene_generation_runs` 的字段、索引和外键完整。
4. 确认 MySQL、Redis、Qdrant 已在 `aienie-wsl` 对应 VM 的 `23306`、`26379`、`26333` 端口可达，ai-service、user-service、pay-service 的 local TLS/gRPC 入口分别为 `12011`、`12001`、`12021`；使用当前工作树的私有环境文件和 `scripts/windows/Start-Local.ps1 -EnvironmentFile <private-env-file>` 启动 AINovel，不得读取其他工作树的部署改动或环境文件作为替代。
5. 检查 `/api/actuator/health/liveness`、`/api/actuator/health/readiness` 和 `/api/actuator/health`。
6. 使用真实 SSO 会话依次走通：跨章上下文预览 → fast/crafted 生成 → 等长与非等长编辑 → 标签确认 → 再生成 → 版本回滚 → 页面刷新恢复。
7. 验证同一场景的上下文预览与生成 manifest 具有相同 `scene-draft-v2`、来源顺序、固定 3500 预算占用和 hash；未来场景、禁用 Lorebook 与待复核提取不得入选。
8. 验证生成正文、`generation` 版本快照和 generation run 原子落库；故障时不得留下无法归因的正文覆盖。分别确认插入、删除、等长改写、再生成和版本回滚的增删量、保留率及 `GENERATED/EDITED/SUPERSEDED/REVERTED` 状态。
9. 在桌面和窄屏确认生成反馈面板不强制评分，八类标签、备注和长期偏好确认刷新后仍保持；切换简体中文、繁体中文和英文检查全部新增文案。
10. Beta Reader 与连续性触发器必须返回 HTTP 501/`ANALYSIS_NOT_IMPLEMENTED`，且数据库不新增任务、报告或问题。
11. 检查控制台、失败网络请求、数据库刷新后持久化、结构化错误日志及日志敏感信息；原始提示词、上下文正文、令牌和会话不得进入 generation run 或日志。

付费离线模型回归不属于 L4 默认验收。只有获得单独授权后，才可从仓库根目录显式运行：

```powershell
$envFile = '<private-env-file>'
mvn -f backend/pom.xml -Pquality-regression `
  "-Dtest=QualityRegressionOfflineTest" `
  "-DqualityRegression.envFile=$envFile" `
  "-DqualityRegression.remoteUserId=<remote-uid>" test
```

该 profile 从显式指定的 Windows 安全配置文件读取既有 AI 网关地址与 HMAC 配置，只写 `backend/target/quality-regression/quality-regression-report.json`，不写 AINovel 产品表。不得将其分数计入 G2 真人票数。

## H1 证据与状态

1. 使用 `scripts/windows/Test-Local.ps1 -Level L2` 运行完整前后端验证。H1 专项覆盖 Unicode 引文、纯格式变化、作者纠错、缺失证据候选、幂等回执、取消、数据库重载、分支隔离、回滚/合并和保护快照；既有 36 个质量 fixture 的结构检查仍在默认套件内，付费模型回归不在本期预算内。
2. 通过 [隔离 MySQL profile](deployment.md#数据库迁移) 验证 V1 → V15 和 V14 → V15。测试仅创建并删除 `ainovel_verify_<uuid>` 临时库；业务账号无权限时记录阻塞。检查五张 `narrative_*` 表、`manuscript_versions.narrative_protected` 及所属数据删除后的级联结果，不能改写 V1–V14。
3. 使用 Windows 标准 Build/Start/Status 入口，核对回环端口、就绪状态和 `https://localainovel.testhut.top`；以真实 SSO 登录自有测试账户。
4. 自有正文覆盖准备偷钥匙、声称桥断、误以为背叛、梦境、反讽和未知时间。确认普通保存不调用 AI，再明确确认正文；首轮真实抽取不超过六次。分别记录真实模型语义错误、作者修正和实际用量，离线模拟不替代真实结果。
5. 在待审阅中修改类型/持有人/证据，拒绝无效项并补充遗漏；刷新恢复后提交，核对账本修订只增加一次、原候选及审阅保留。点击证据时只高亮不可变版本，中文生僻字和 emoji 后定位准确。
6. 修改旧稿、生成写回、回滚、合并、删除/重排，核对来源及已消费依赖进入待复核，历史修订仍可读，恢复文字不自动恢复记录。新分支为空账本，同故事不同稿件隔离，合并不导入来源分支状态。
7. 覆盖桌面与窄屏、简中/繁中/英文，以及控制台、失败请求、页面刷新和任务恢复。跨用户或分支访问、版本冲突、超时/取消和格式错误不得产生部分有效状态；旧角色变化触发器为 501，历史日志标记 `UNVERIFIED_LEGACY`。
8. 通过产品接口清理自有测试数据，并用标准 Stop/Status 入口恢复按需停止状态。实际阻塞与验收状态只登记到 [路线图](../roadmap.md)。

## 安全与可观测性

- 执行日志敏感信息扫描，确认 token、cookie、密码、兑换码、提示词、原始响应和本地绝对路径不进入日志。
- 校验 requestId/MDC、结构化文件日志、轮转与容量配置；异步任务需验证上下文传播。
- 执行 secret 增量扫描、`git diff --check` 和文档链接检查。
# v1.0 审计整改补充（2026-08-17）

- Windows L1：后端编译，前端 lint、typecheck、production build。
- Windows L2：包含 L1，并运行前端全量 Vitest 与后端全量 Maven 测试。
- L3：依次运行 `Build-Local.ps1`、`Start-Local.ps1 -EnvironmentFile .\env.txt`，验证 `127.0.0.1:11040`、后端 liveness/readiness 及 `https://localainovel.testhut.top`，最后运行 `Stop-Local.ps1`。
- Docker/Testcontainers 不可用属于验收阻塞，不得把跳过测试记录成通过。
