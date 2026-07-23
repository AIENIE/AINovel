# AI 写作质量研究

本目录保存中文小说模板化、AI 味和长篇漂移研究。它是设计输入，不是独立产品路线图；产品后续工作只以 [`../../roadmap.md`](../../roadmap.md) 为准。

## 当前采用状态

| 研究方向 | 当前状态 | 对应实现 |
| --- | --- | --- |
| E1-E4 证据等级与替代解释 | 已落地 | `SlopQualitySignals`、`SlopDiagnosticService` |
| 表层模板、动作链与模式字典 | 已落地 | `LocalSlopHeuristics`、`quality/slop-patterns/*.json` |
| 生成门禁与保守修订 | 已落地 | `SlopQualityGate` |
| 风格与角色声音适配 | 已落地 | `StyleContextProvider` |
| 长篇中后段漂移 | 已落地 | `SlopDriftService` |
| 标注集、独立 CLI/API、平台商业风险 | 研究规格 | 未作为当前产品承诺 |
| 旧版整体软件与功能建议 | 已取代 | 由当前代码、v3 提案和路线图取代 |

## 内容索引

- `source-notes/foundation.md`：早期平台与社区来源记录。
- `source-notes/current.md`：后续扩展来源与观察。
- `source-notes/pattern-registry-sources.md`：规则来源分级、模型观察矩阵和 ACTIVE/SHADOW 采用边界。
- `source-notes/legacy-reports.md`：已删除旧综合报告保留下来的来源索引。
- `pattern-library.md`：词句、动作、意象、结构和叙事模式。
- `algorithm-notes.md`：评分维度、证据和反例思路。
- `workflow-notes.md`：诊断、改写和人工复核方法。
- `prototype/README.md`：算法与原型规格索引。

## 使用规则

- 输出文本风险，不判断作者是否使用 AI。
- 单个词语只作弱信号，多维证据共振才提高风险等级。
- 每项结论保留证据位置、替代解释和最小修复建议。
- 研究文档中的伪代码、权重和接口均不自动成为产品待办。
