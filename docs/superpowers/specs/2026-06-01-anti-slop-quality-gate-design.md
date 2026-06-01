# AINovel 反 Slop 生成质量门禁设计

## Summary

第一版把反 slop 能力嵌入场景生成链路，在 AI 生成正文写入稿件前执行自动质量门禁。系统不做“AI率”总分类器，而是输出可解释的风险维度，并在中高风险时执行一次保守修订。

## Key Decisions

- 复用现有 ai-service `ChatCompletions`，不新增 GPU、Python 边车或本地模型服务。
- 第一版只允许保守修订：去重复、降套话、删除 AI 输出伪迹、补局部承接，不改变剧情事件、角色决策、人物关系或关键设定。
- P0 维度为 `repetition`、`genericity`、`artifact`、`local_coherence`、`style_drift_light`。
- 当前 ai-service proto 不支持原生 JSON schema，因此 LLM 诊断使用提示词 JSON 输出 + 后端严格解析 + 失败降级。

## Implementation Shape

- `quality` 后端域负责本地规则、LLM 诊断、保守修订和持久化记录。
- `ManuscriptService.generateSceneSectionHtml()` 在候选正文满足字数范围后调用 `SlopQualityGate`，保存通过版或最佳候选版。
- `slop_quality_runs` 记录每次门禁的范围、状态、风险分、修订次数和文本哈希。
- `slop_quality_issues` 记录维度、严重级别、证据片段和最小修复建议。
- 前端写作页在生成后查询最近质量记录，展示 `通过`、`已自动修订` 或 `仍有建议`。

## Test Plan

- 后端单元测试覆盖本地规则识别重复、套话和伪迹。
- 后端门禁测试覆盖高风险候选触发一次保守修订并记录结果。
- 前端类型和 API 封装随现有 Vitest/build 验证。
- 完整验收使用 Maven test、前端 test/build、`build.sh` 本地部署与 localhut 写作 smoke。
