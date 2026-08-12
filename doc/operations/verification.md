# 验证与验收

本文件只维护可重复执行的验证步骤。日期化执行结论、截图、临时环境地址和一次性问题快照不进入仓库；具体结果由 CI、提交记录或外部交付记录保存。

## 标准验证链

```powershell
mvn -q -f backend/pom.xml clean test
npm --prefix frontend test
npm --prefix frontend run build
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
2. 执行 `mvn -q -f backend/pom.xml clean test`、`npm --prefix frontend test`、`npm --prefix frontend run build`。
3. 分别验证新库从 V1 完整迁移到 V13，以及已有 V12 数据库只执行 V13 升级；确认 `scene_generation_runs` 的字段、索引和外键完整。
4. 确认 MySQL、Redis、Qdrant 已在 `172.20.0.2` 对应端口可达，ai-service、user-service、pay-service 已在本机 loopback 启动；使用本独立工作树的 `%LOCALAPPDATA%\Aienie\secrets\ainovel-localbase.env` 和 `scripts/windows/Start-Local.ps1` 启动 AINovel。不得读取原工作树的部署改动或环境文件作为替代。
5. 检查 `/api/actuator/health/liveness`、`/api/actuator/health/readiness` 和 `/api/actuator/health`。
6. 使用真实 SSO 会话依次走通：跨章上下文预览 → fast/crafted 生成 → 等长与非等长编辑 → 标签确认 → 再生成 → 版本回滚 → 页面刷新恢复。
7. 验证同一场景的上下文预览与生成 manifest 具有相同 `scene-draft-v2`、来源顺序、固定 3500 预算占用和 hash；未来场景、禁用 Lorebook 与待复核提取不得入选。
8. 验证生成正文、`generation` 版本快照和 generation run 原子落库；故障时不得留下无法归因的正文覆盖。分别确认插入、删除、等长改写、再生成和版本回滚的增删量、保留率及 `GENERATED/EDITED/SUPERSEDED/REVERTED` 状态。
9. 在桌面和窄屏确认生成反馈面板不强制评分，八类标签、备注和长期偏好确认刷新后仍保持；切换简体中文、繁体中文和英文检查全部新增文案。
10. Beta Reader 与连续性触发器必须返回 HTTP 501/`ANALYSIS_NOT_IMPLEMENTED`，且数据库不新增任务、报告或问题。
11. 检查控制台、失败网络请求、数据库刷新后持久化、结构化错误日志及日志敏感信息；原始提示词、上下文正文、令牌和会话不得进入 generation run 或日志。

付费离线模型回归不属于 L4 默认验收。只有获得单独授权后，才可从仓库根目录显式运行：

```powershell
$envFile = Join-Path $env:LOCALAPPDATA 'Aienie\secrets\ainovel-localbase.env'
mvn -f backend/pom.xml -Pquality-regression `
  "-Dtest=QualityRegressionOfflineTest" `
  "-DqualityRegression.envFile=$envFile" `
  "-DqualityRegression.remoteUserId=<remote-uid>" test
```

该 profile 从显式指定的 Windows 安全配置文件读取既有 AI 网关地址与 HMAC 配置，只写 `backend/target/quality-regression/quality-regression-report.json`，不写 AINovel 产品表。不得将其分数计入 G2 真人票数。

## 安全与可观测性

- 执行日志敏感信息扫描，确认 token、cookie、密码、兑换码、提示词、原始响应和本地绝对路径不进入日志。
- 校验 requestId/MDC、结构化文件日志、轮转与容量配置；异步任务需验证上下文传播。
- 执行 secret 增量扫描、`git diff --check` 和文档链接检查。
