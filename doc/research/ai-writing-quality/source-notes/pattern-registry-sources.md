# 本地模式注册表来源与模型观察矩阵

本文记录 `quality/slop-patterns/*.json` 的证据来源与采用边界。模型名称只说明候选现象在哪里被观察过，不构成模型归因器，也不进入 API 结论。

## 来源分级

| 层级 | 用途 | 当前来源 |
| --- | --- | --- |
| A：可复现产物 | 可直接进入高置信 `ARTIFACT` | OpenAI `turn0search` / `oai_citation`、Gemini `[cite: n]` / span 标记、Grok card/render 标记、DeepSeek `【n†Lx-Ly】` |
| B：跨域研究 | 决定保守阈值、影子观测和非归因边界 | [HC3](https://arxiv.org/abs/2301.07597)、[M4](https://arxiv.org/abs/2305.14902)、[RAID](https://aclanthology.org/2024.acl-long.674/)、[Excess vocabulary](https://arxiv.org/abs/2406.07016) |
| C：社区候选 | 发现待校准表达，不能单独证明 AI 来源 | [Wikipedia: Signs of AI writing](https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing) 与项目既有中文写作复核样本 |

HC3 在其问答语料中观察到 ChatGPT 文本更长、词汇密度更低、显式连接与逻辑组织更多；这只能支持连接词密度和词汇变化进入影子观测。M4 与 RAID 都显示检测器对未见模型、领域、解码参数和改写攻击的泛化不稳定，因此本项目不输出“AI 概率”或作者/模型归因。

## 模型观察矩阵

| 模型/产品 | 可复现渲染残留 | 风格候选 | 产品状态 |
| --- | --- | --- | --- |
| ChatGPT / OpenAI | `turn0search`、`oai_citation` | 排除式对照、连接词、抽象总结等跨模型候选 | 渲染残留 ACTIVE/E4；风格只按跨模型文本规则或 SHADOW |
| Gemini | `[cite: n]`、`[span_n][start_span]` | 语气解释、身体动作、通用意象等候选 | 渲染残留 ACTIVE/E4；风格不归因 |
| Grok | `grok_card`、`grok_render_citation_card_json` | 当前公开稳定证据不足 | 渲染残留 ACTIVE/E4；风格假设 SHADOW |
| DeepSeek | `【n†Lx-Ly】` | 排除式解释、抽象总结等候选 | 渲染残留 ACTIVE/E4；风格不归因 |
| Claude | 未找到稳定公开专属残留 | 社区候选与其他模型高度重叠 | 证据不足；不建立专属计分规则 |
| 豆包 Doubao | 未找到稳定公开专属残留 | 中文网文动作/情绪模板候选 | 证据不足；不建立专属计分规则 |
| GLM | 未找到稳定公开专属残留 | 中文总结/升华候选 | 证据不足；不建立专属计分规则 |
| Kimi | 未找到稳定公开专属残留 | 中文总结/语气模板候选 | 证据不足；不建立专属计分规则 |

## 激活原则

1. `ACTIVE` 表层规则仍是文本风险信号：单点只到 E1，只有短窗口密度/跨类别共振才到 E2。
2. 可复现的界面、引用、协议和提示词残留可到 E4，但只能说明正文混入了生成/工具产物，不能说明作者身份或具体模型。
3. 统计特征和供应商风格假设先进入 `SHADOW`，只积累匿名命中摘要。没有真实标注和误伤校准前不得转为计分规则。
4. 人物动机、设定吸收、事件传送带、身份漂移等语义问题继续交给 Judge/Drift；配置库只保留对应生成约束。
5. 社区资料用于发现候选，规则转为 ACTIVE 前必须有正例、预期语境反例、边界测试和可解释修复建议。
