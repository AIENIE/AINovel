# AI 写作质量原型规格索引

本目录保存可解释诊断算法、数据结构和验证规格。当前代码采用情况见上级 [`README.md`](../README.md)。

## 诊断模块

| 文件 | 作用 |
| --- | --- |
| `01-evidence-confidence-algorithm.md` | E1-E4 证据等级与安全措辞 |
| `02-surface-template-density-algorithm.md` | 表层模板密度 |
| `03-voice-fit-algorithm.md` | 角色、叙述者和题材语域适配 |
| `04-consistency-and-assimilation-algorithm.md` | 事实一致性与设定吸收 |
| `05-breath-focus-pacing-algorithm.md` | 呼吸感、主体聚焦和节奏 |
| `06-midbook-drift-algorithm.md` | 长篇中后段断层 |
| `07-human-trace-algorithm.md` | 具体经验与作者取舍 |
| `08-platform-commercial-risk-algorithm.md` | 平台与商业风险研究 |
| `09-integrated-reporting-algorithm.md` | 综合报告与修订优先级 |

## 数据与执行规格

| 文件 | 作用 |
| --- | --- |
| `10-pattern-dictionary-v1.md` | 模式字典与误判风险 |
| `11-output-json-schema.md` | 结构化诊断输出 |
| `12-annotation-guidelines.md` | 人工标注规范 |
| `13-sample-diagnostic-report.md` | 样例诊断报告 |
| `14-revision-workflow-prompt-templates.md` | 诊断与改写提示词 |
| `15-minimum-viable-detector-spec.md` | 最小可用检测器规格 |
| `16-feature-extraction-rules.md` | 特征提取规则 |
| `17-scoring-weight-config.yaml` | 初始权重示例 |
| `18-testset-design.md` | 正例、反例和边界测试集 |
| `19-cli-or-api-prototype.md` | 独立 CLI/API 原型 |

推荐执行顺序为：证据边界 -> 单章特征 -> 长篇漂移 -> 可选平台风险 -> 综合报告。原型接口和独立服务形态目前仅供研究，不替代 AINovel 现有质量 API。
