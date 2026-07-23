# V2 Quality API

- 鉴权：Bearer Token（稿件所有者）
- 文本质量基础路径：`/api/v2/manuscripts/{manuscriptId}/quality-runs`
- 长篇 drift 基础路径：`/api/v2/manuscripts/{manuscriptId}/slop-drift-runs`
- 剧情质量基础路径：`/api/v2/manuscripts/{manuscriptId}/plot-quality-runs`

## 文本质量门禁

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
- `moduleScoresJson`：模块评分 JSON，覆盖 `surface_template`、`voice_fit`、`consistency_assimilation`、`breath_focus_pacing`、`human_trace`。
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
- 设定硬冲突和元提示残留优先于表层套话，分别按 `E3` / `E4` 处理。
- 平台合规、AIGC 标识和商业风险不计入文本 slop 总分。
