# AI 文本质量与精雕生成

## 当前能力

- `SlopQualityGate`：生成前后的轻量门禁与最多一次保守修订。
- `SlopDiagnosticService`：用户手动发起的结构化文本诊断。
- `SlopDriftService`：长稿多窗口巡检，定位中后段模板化、角色漂移和叙事断层。
- `PlotQualityService`：剧情维度诊断、趋势和候选修订。

这些能力评价文本风险，不输出 AI 概率或作者归因。E1-E4 证据等级、替代解释和最小修复建议用于降低误伤。

## G2 Step 1

- 场景生成支持 `mode=fast|crafted`，默认保持 `fast`。
- `crafted` 从 `slop_patterns` 按类别稳定采样 15 条约束，并轮换 2 条人味叙事目标。
- 当前仍是单候选起草，不包含节拍规划、多候选竞技场、Manual 选择或采样参数透传。
- 模式库由 Flyway `V2__slop_patterns.sql` 初始化，后续变化必须使用新迁移。

## 安全边界

- 自动修订不得改变剧情事件、角色决策、人物关系和关键设定。
- 手动诊断与长篇巡检不修改正文。
- 剧情候选只有用户采纳后写回，并重新检查正文哈希和文本质量。

研究来源和未产品化规格见 [`../research/ai-writing-quality/README.md`](../research/ai-writing-quality/README.md)，后续 G2 阶段见 [`../roadmap.md`](../roadmap.md)。
