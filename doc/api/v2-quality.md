# V2 Quality API

- 鉴权：Bearer Token（稿件所有者）
- 文本质量基础路径：`/api/v2/manuscripts/{manuscriptId}/quality-runs`
- 长篇 drift 基础路径：`/api/v2/manuscripts/{manuscriptId}/slop-drift-runs`
- 剧情质量基础路径：`/api/v2/manuscripts/{manuscriptId}/plot-quality-runs`

## 语言自然度检查与逐项修订（zh-naturalness-v2）

新增接口与原 Slop、drift、剧情接口并存，使用同一稿件所有权和 `AiOperation` 任务机制。旧请求/报告结构保持兼容；旧报告没有可信的分支/正文来源，不回填伪造来源，界面仅展示历史结论，不作为当前正文通过的证明。

以下路径以 `/api/v2/manuscripts/{manuscriptId}` 为前缀；读写都要求稿件所有者。

| 方法与路径 | 请求／返回 |
|---|---|
| `GET /quality-runs/language/settings` | 作品级开关 `generationStandard`、`checkAfterGeneration`，环境可用状态 `available`，规范 `standardVersion` |
| `PUT /quality-runs/language/settings` | `{generationStandard, checkAfterGeneration}`；两个工作级开关初始均为 false |
| `GET /quality-runs/language?sceneId=…` | 最近 20 份报告；每份的完整 issues 不设旧 12 条上限 |
| `GET /quality-runs/language/{reportId}` | 单份报告、覆盖、候选及当前适用状态 |
| `POST /scenes/{sceneId}/quality-runs/language/operations` | `{expectedBranchId, expectedVersion}`，202 `{operationId}`；由正文来源和规范版本去重 |
| `POST /quality-runs/language/{reportId}/issues/{issueId}/suggestions/operations` | 空对象，202 `{operationId}`；同一问题恢复已有候选，不再次推理 |
| `GET /quality-runs/language/{reportId}/patches/{patchId}` | 原句范围、候选、前后完整上下文、双维度复核和适用状态 |
| `POST /quality-runs/language/{reportId}/patches/{patchId}/accept` | `Idempotency-Key`、`{expectedBranchId, expectedVersion}`；只应用服务端候选 |
| `POST …/{patchId}/reject` | 同上；只保存拒绝记录 |
| `POST …/{patchId}/undo` | 同上；反向修改创建新版本，冲突时拒绝覆盖 |

处置返回 `{patch, bodyVersion, branchId, content}`；正文、版本与处置回执在同一事务提交。幂等键限定 8–80 个字母、数字、冒号、下划线或连字符；同键不同请求 409，同键相同请求返回原回执。报告或候选越权/不属于稿件返回 404；来源、分支、版本、位置或内容复核不允许应用时返回 409。

报告 `source` 包含 `branchId`、`bodyVersion`、不可变 `snapshotId`、`projectionVersion`、`htmlHash`、`textHash`、`contextVersion`、`standardVersion`。新投影为 `QUALITY_PARAGRAPH_V2`，位置为 UTF-16 半开区间，不修改 `QUALITY_V1` 等历史投影。保留段落、原始空白、换行和解码实体到 HTML 的映射；复杂结构及跨文本节点的格式边界只提供手动审阅。

`status` 枚举：`UNCHECKED`、`CHECKING`、`ISSUES`、`NO_CLEAR_ISSUES`、`INCOMPLETE`、`FAILED`、`LOCAL_ONLY`、`STALE`。`NO_CLEAR_ISSUES` 仅表示完整扫描后未发现明确问题，不宣称没有 AI 味。覆盖 `coverage` 逐块记录核心/上下文 start/end、`PENDING/COMPLETE/PARTIAL/FAILED/SKIPPED` 与原因；超出完整段落及相邻上下文预算时明确 SKIPPED，不截尾。取消、未知调用结果、解析失败、预算不足不能降级成无问题。

问题 `kind` 为 `LANGUAGE/STYLE/OBSERVATION`，`category` 为 `OMISSION/COMPRESSION/CHOPPY/AWKWARD/PARAGRAPH`，包含连续 `quote`、阅读影响 `impact`、修改方向 `direction`、原始与当前位置、完整原段落及定位状态。不存在、歧义或无效范围的位置不允许补丁。诊断只使用保存的本场正文，不读取 H2 隐藏资料、人物私密卡、未来规划或额外资料检索。

候选 `status` 为 `GENERATING/READY/FAILED/ACCEPTED/REJECTED/UNDONE`；`applicability` 区分 `APPLICABLE/UNCERTAIN/BLOCKED/MANUAL_ONLY/STALE` 及未完成原因。`review.language` 与 `review.meaning` 独立为 `PASS/UNCERTAIN/FAIL`。明确失败禁用采纳，不确定显示给作者核对，模型通过也不自动采纳。`original/replacement` 为完整差异输入，`beforeContext/afterContext` 保留自然段与相邻段。

普通编辑/自动保存/回滚/合并/换分支不触发付费诊断；读取时按正文版本、分支和 HTML 哈希即时判旧报告失效。已知同批次补丁可确定性平移未受影响的建议；同段或相邻上下文影响的建议失效。`canContinueBatch` 只授权处理这些未受影响的项目，不表示原报告适用于新正文。采纳/撤销均产生新版本，外部编辑后不得强行反向覆盖。

运行配置：`app.language.enabled=false` 为总开关；`generation-enabled`、`diagnosis-enabled` 可分别关闭（默认 true，仍受总开关与作品开关限制）。`input-token-budget=24000` 是输入预算，按 UTF-8 字节作保守 token 上界，同时受 AI admission 的消息/总字符上限约束。开启作品语言流程后，普通正文生成保留本地 Slop 规则，旧的付费 Slop 门禁及自动改稿退出该路径；冻结盲测提示不改变。关闭开关不删除报告、候选或已采纳正文。

本地验收预算额外支持 `app.ai.validation.maximum-provider-attempts`；未设置时保留原先随 `maximum-calls` 的限制。当前专题账本必须为 40 次业务调用和 120 次供应商尝试，实际运行前另行核实 `provider-attempts-per-rpc`，不能仅凭默认重试数开始付费验证。

## 旧版文本质量门禁

- `GET /quality-runs`：查询稿件最近 20 条反 slop 质量门禁记录。
- `GET /quality-runs?sceneId={sceneId}`：查询指定场景最近 20 条记录。
- `POST /scenes/{sceneId}/quality-runs`：对当前场景执行一次手动文本 Slop 风险诊断。该接口只保存诊断记录，不修改正文。
- `POST /scenes/{sceneId}/quality-runs/operations`：同一诊断的异步进度版本。

返回项包含：
- `status`：`ACCEPTED` / `REVISED` / `ACCEPTED_WITH_ISSUES` / `DEGRADED`。
- `overallRiskScore`：0-100 风险分。
- `maxSeverity`：`LOW` / `MEDIUM` / `HIGH` / `BLOCKING`。
- `revised` / `revisionCount`：是否执行过保守修订。
- `issues`：维度、严重级别、证据片段、原因和最小修复建议。

文本诊断扩展返回项：
- `analysisMode`：`manual_scene` 表示工作台手动诊断；`generation_gate` 表示生成链路门禁记录。
- `riskLabel`：`low` / `medium` / `high` / `critical`。
- `evidenceLevel`：`E1` / `E2` / `E3` / `E4`，分别表示单点弱信号、多信号共振、结构性矛盾、元提示/生成残留。
- `safeClaim`：安全结论，只评价文本 slop 风险，不推断作者是否使用 AI。
- `moduleScoresJson`：模块评分 JSON，覆盖 `surface_template`、`voice_fit`、`consistency_assimilation`、`breath_focus_pacing`、`human_trace`；保留键 `_shadow_pattern_hits` 记录不参与评分的影子规则命中摘要。
- `alternativeExplanationsJson`：替代解释，例如传统网文俗套、人工低水平写作、工作室公式化、题材/平台惯例、作者个人文风。
- `revisionPrioritiesJson`：修改优先级。
- `rewriteTasksJson`：证据驱动改写任务。

`generation_gate` 记录会复用上述扩展字段。低风险本地规则记录可能只包含本地模块分和默认替代解释；触发 AI review 后会持久化 AI 返回的模块分、证据等级、修改优先级和 rewrite tasks。即使存在 rewrite tasks，生成链路也只允许最多一次保守修订。

手动诊断和生成链路门禁会把故事 active 风格画像与角色声音作为 `voice_fit` 判断语境。未配置风格/声音时，服务端会明确传入空语境，诊断不得虚构角色语域不贴合问题。

手动文本诊断的 `issues` 额外包含：
- `charStart` / `charEnd`：证据在原文中的字符位置，可能为空。
- `quote`：原文证据。
- `module`：证据所属模块。
- `patternId` / `issueType`：规则或语义问题标识。
- `evidenceLevel`：单条证据等级。
- `alternativeExplanationsJson`：单条证据替代解释。
- `repairHint`：最小修复建议。

## 长篇 drift 巡检

- `GET /slop-drift-runs`：查询稿件最近 20 条长篇 drift 巡检记录。
- `POST /slop-drift-runs`：按当前稿件正文构建多个章节/字数窗口，执行一次 LLM 窗口对比巡检。
- `POST /slop-drift-runs/operations`：同一巡检的异步进度版本。

返回项包含：
- `status`：`COMPLETED` / `INSUFFICIENT_TEXT` / `DEGRADED`。
- `overallRiskScore`：0-100 长篇 drift 风险分。
- `riskLabel`：`low` / `medium` / `high` / `critical` / `unavailable`。
- `safeClaim`：安全结论，只评价文本中后段模板化、角色漂移、事件传送带、伏笔遗忘和叙事机制断层风险。
- `totalCharacters` / `windowCount`：本次参与分析的正文字符数和窗口数。
- `windowSummariesJson`：窗口观察摘要。
- `metricCurvesJson`：指标曲线 JSON，建议覆盖 `template_density`、`causal_coherence`、`role_stability`、`foreshadow_memory`、`breath_score`。
- `driftPointsJson`：断层点与变化指标。
- `evidenceItemsJson`：可回溯证据。
- `alternativeExplanationsJson`：替代解释，例如赶稿、换写手、剧情高潮、平台节奏、作者疲劳、题材/平台惯例。
- `rewriteTasksJson`：面向作者的修复任务。

短稿或无法形成至少 3 个有效窗口时，服务端保存 `INSUFFICIENT_TEXT`，不会调用 AI。巡检不修改正文，不生成自动修订，不输出“AI率”“作者用了 AI”“从第 X 章开始机写”等作者归因。

## 生成链路行为

`POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate` 在正文写入前自动执行质量门禁：
- 低风险：直接保存候选正文并记录 `ACCEPTED`。
- 中高风险：调用 AI 诊断 JSON，必要时执行一次保守修订。
- 修订约束：不改变剧情事件、角色决策、人物关系和关键设定；只处理重复、套话、AI 输出伪迹、局部承接和轻量风格漂移。
- 修订失败或风险未下降：保存最佳候选，并记录 `ACCEPTED_WITH_ISSUES` 供前端展示。

## 剧情质量诊断

- `GET /plot-quality-runs`：查询稿件最近 20 条剧情质量诊断记录。
- `GET /plot-quality-runs?sceneId={sceneId}`：查询指定场景最近 20 条记录。
- `POST /scenes/{sceneId}/plot-quality-runs`：对当前场景生成一次剧情诊断。
- `POST /scenes/{sceneId}/plot-quality-runs/operations`：同一诊断的异步进度版本。
- `GET /plot-quality-trends`：按场景聚合每个场景最新诊断，返回平均风险、高风险场景数、维度计数和趋势点。
- `POST /plot-quality-runs/{runId}/revision-candidate`：基于诊断结果生成一份候选修订文本，只保存候选，不自动写回稿件。
- `POST /plot-quality-runs/{runId}/revision-candidate/operations`：候选修订生成的异步进度版本。
- `POST /plot-quality-runs/{runId}/apply-revision`：采纳候选修订并写回对应场景正文。

剧情诊断返回项包含：
- `status`：`ACCEPTED` / `ACCEPTED_WITH_ISSUES` / `DEGRADED`。
- `overallRiskScore`：0-100 剧情风险分。
- `maxSeverity`：`LOW` / `MEDIUM` / `HIGH` / `BLOCKING`。
- `chapterTitle` / `sceneTitle` / `chapterOrder` / `sceneOrder`：用于趋势排序和前端定位。
- `summary`：本次诊断摘要。
- `issues`：剧情维度、严重级别、证据、影响原因和最小修复建议。
- `rewritePlan` / `surgicalFixes`：面向作者的重写计划和局部修正动作。
- `revisionCandidateText` / `revisionApplied` / `revisionAppliedAt`：候选修订及采纳状态。

剧情维度覆盖：
- `GOAL_CONFLICT`：场景目标与冲突是否清楚。
- `CAUSALITY`：事件因果链是否成立。
- `AGENCY`：角色决策是否主动且符合设定。
- `STAKES`：风险、收益和代价是否具体。
- `FORESHADOW_PAYOFF`：伏笔与回收是否存在断裂。
- `REPETITION`：是否重复同类桥段或套路。
- `SCENE_FUNCTION`：场景是否承担推进、揭示或转折功能。
- `READER_CURIOSITY`：悬念、问题和期待是否持续。

## 候选修订安全规则

- 生成候选和采纳候选都会校验 `sourceTextHash`，如果场景正文已经变化，应重新诊断后再生成候选。
- 采纳候选前服务端会复用文本 `SlopQualityGate`，避免候选引入明显套话、重复和 AI 伪迹。
- 前端工作台的 `plot` 侧栏只做人工确认入口；除用户点击“采纳候选”外，不会自动覆盖场景正文。

## 文本诊断安全规则

- 文本诊断不输出“AI率”“作者用了 AI”“从第 X 章开始机写”等作者归因。
- 单点黑名单命中只能作为 `E1` 弱信号；短窗口密度或多信号共振才能升级为 `E2`。
- 本地密度窗口固定为 500 字：同类 3 次或 3 类共现升级到 58/E2；同类 5 次或 4 类共现升级到 72/HIGH/E2。预期文体语境中的单点命中降为 28/E1。
- 设定硬冲突和元提示残留优先于表层套话，分别按 `E3` / `E4` 处理。
- `_shadow_pattern_hits` 不得改变 `overallRiskScore`、`maxSeverity`、`issues`、AI review 或 `safeClaim`，也不包含模型归因结论。
- 平台合规、AIGC 标识和商业风险不计入文本 slop 总分。


## 语言示例 v3 与信息不足结果（2026-10-08）

语言检查沿用既有路由；新报告的 source.standardVersion 为 zh-naturalness-v3。任务登记时固定规范、示例及提示版本，报告 JSON 保存 profileHash；生成操作的服务端内部 payload 保存 languageProfile（版本、哈希、生成／后台检查开关）；快照以 languageProfileVersion、languageProfileHash、languageGenerationEnabled、languageDiagnosisEnabled 四个标量保存相同来源信息。客户端生成请求无需增加字段。恢复沿用记录版本和计费标识，不能还原的历史任务进入待对账状态，不换版本重推理。

补丁响应增加可选、可空的 reason 字符串。status 和 applicability 新增 NEEDS_CONTEXT：问题成立但缺少安全改写所需信息，例如无法确定行动者；replacement、review 均为 null。界面展示原因，没有采纳入口；该结果正常结束操作，不再调用复核。相同问题返回已有操作和结果，可拒绝；作者编辑正文后需主动新查，不自动重试。

v3 模型内部修订输出为 {"outcome":"CANDIDATE","replacement":"完整局部替换"} 或 {"outcome":"NEEDS_CONTEXT","reason":"具体信息缺口","replacement":null}。这是内部模型协议，不是新增 HTTP 接口。v1／v2 继续解析原 replacement 格式，旧报告不伪造来源或哈希。新增字段使用现有 JSON 存储，无新迁移。历史候选的采纳／撤销仍校验所有权、正文、版本、分支与原句。
