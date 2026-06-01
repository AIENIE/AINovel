# V2 Quality Gate API

- 鉴权：Bearer Token（稿件所有者）
- 基础路径：`/api/v2/manuscripts/{manuscriptId}/quality-runs`

## 查询质量门禁记录

- `GET /quality-runs`：查询稿件最近 20 条反 slop 质量门禁记录。
- `GET /quality-runs?sceneId={sceneId}`：查询指定场景最近 20 条记录。

返回项包含：
- `status`：`ACCEPTED` / `REVISED` / `ACCEPTED_WITH_ISSUES` / `DEGRADED`。
- `overallRiskScore`：0-100 风险分。
- `maxSeverity`：`LOW` / `MEDIUM` / `HIGH` / `BLOCKING`。
- `revised` / `revisionCount`：是否执行过保守修订。
- `issues`：维度、严重级别、证据片段、原因和最小修复建议。

## 生成链路行为

`POST /api/v1/manuscripts/{id}/scenes/{sceneId}/generate` 在正文写入前自动执行质量门禁：
- 低风险：直接保存候选正文并记录 `ACCEPTED`。
- 中高风险：调用 AI 诊断 JSON，必要时执行一次保守修订。
- 修订约束：不改变剧情事件、角色决策、人物关系和关键设定；只处理重复、套话、AI 输出伪迹、局部承接和轻量风格漂移。
- 修订失败或风险未下降：保存最佳候选，并记录 `ACCEPTED_WITH_ISSUES` 供前端展示。
