# AI 文本质量与精雕生成

## 当前能力

- `SlopQualityGate`：生成候选的只读检查，原样返回候选；不再调用整稿修订。
- `SlopDiagnosticService`：用户手动发起的结构化文本诊断。
- `SlopDriftService`：长稿多窗口巡检，定位中后段模板化、角色漂移和叙事断层。
- `PlotQualityService`：剧情维度诊断、趋势和候选修订。

新 Slop 报告分开保存本地线索与语义确认，使用 `slop-evidence-v2`；文风、剧情及输出残留不能生成语言候选。历史 E1–E4 记录保留可读。

Beta Reader 与通用连续性分析的两个占位触发接口已删除，旧路径返回 HTTP 404；历史任务和报告继续读取，不创建固定分数或固定风险的伪报告。

## 单开发者回归基线

- `backend/src/test/resources/quality/novel-quality-regression-fixtures.json` 冻结 36 个仓库自有中文样本，包含 12 个无缺陷基线和六类各 4 个单缺陷变体；6 个 holdout 不参与日常调参。
- 默认测试只校验 fixture 数量、类型分布、逐字证据和上下文引用，不访问模型或网络。

## 本地规则注册表

- 运行时入口为 `backend/src/main/resources/quality/slop-patterns/index.json`；它按正文规则、高置信输出残留、影子观测和生成约束拆分 JSON 资源。
- 应用启动时由 `SlopPatternRegistry` 一次性加载并校验 schema 版本、规则 ID、分类、状态、正则边界、样本和单调阈值；配置错误会阻止启动，不静默丢规则。
- `ACTIVE` 规则参与本地线索评分：单点弱信号为 34/E1，声明的预期文体语境降为 28/E1；500 字窗口内同类 3 次或 3 类共现为 58/E2，同类 5 次或 4 类共现为 72/HIGH/E2。这些数值不直接进入新报告的确认问题风险。
- `ARTIFACT` 高置信残留按不同规则分别输出 88/HIGH/E4，包括通用助手/Markdown/提示词残留，以及可复现的 OpenAI、Gemini、Grok、DeepSeek 渲染标记。它们是文本残留证据，不用于反推具体模型。
- `SHADOW` 规则只写入 `moduleScoresJson._shadow_pattern_hits`，不进入 issue、风险分、严重级别、AI review 或面向用户的结论。当前观察项为三段式、句长过度均匀、低词汇变化、连接词密度和未验证的模型风格候选。
- 所有字符位置都基于原文；同一 matcher 的每次出现都会计数。每个普通类别只保留最高密度 issue，高置信残留按不同 artifact 保留，总 issue 上限为 12。
- 人工诊断分别保存本地线索与语义判定，已排除的本地高分不再支配报告；最终风险只汇总具有有效证据的确认语言问题及输出残留。
- 角色动机、事件传送带、设定吸收和人物身份漂移不由正则裁决，继续由 Judge/Drift 语义层处理。

## G2 Step 1

- 场景生成支持 `mode=fast|crafted`，默认保持 `fast`。
- `crafted` 从版本化 JSON 注册表按类别稳定采样 15 条约束，并轮换 2 条人味叙事目标。
- 当前仍是单候选起草，不包含节拍规划、多候选竞技场、Manual 选择或采样参数透传。
- 旧 Flyway `V2__slop_patterns.sql` 和表保留为历史事实；运行时不再依赖对应 JPA 实体或 MySQL 数据。规则改动随代码评审、样本和版本化资源发布。

## 安全边界

- 质量门不再自动修订；局部候选必须分别复核语言改善与原意保留，作者逐项决定。
- 手动诊断与长篇巡检不修改正文。
- 剧情候选只有用户采纳后写回；采纳校验来源哈希，保存审阅稿，不再调用文本质量审校或改稿。

逐项语言检查、作者决定和未完成验收边界见[现行语言机制](language-quality.md)，后续阶段见[路线图](../roadmap.md)。
