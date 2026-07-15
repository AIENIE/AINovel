# 综合报告算法：Integrated Reporting Algorithm

## 目标

将各独立评分模块整合成一份可解释、可修改、低误伤的诊断报告。

最终报告不应是“AI概率”，而应是：

- 文本 slop / 模板化风险；
- 证据等级；
- 分层评分；
- 具体证据；
- 替代解释；
- 修改优先级；
- 可执行改写任务。

## 输入

来自各模块的结果：

- Evidence Confidence
- Surface Template Density
- Voice Fit
- Consistency & Assimilation
- Breath / Focus / Pacing
- Mid-book Drift
- Human Trace
- Platform / Commercial Risk

## 输出结构

```json
{
  "summary": {
    "overall_slop_risk": "low|medium|high|critical",
    "evidence_level": "E1|E2|E3|E4",
    "safe_claim": "...",
    "main_risk_layers": []
  },
  "scores": {},
  "evidence_table": [],
  "alternative_explanations": [],
  "revision_priorities": [],
  "rewrite_tasks": [],
  "platform_notes": []
}
```

## 总分合成

不建议简单平均。建议采用分层规则：

### 1. 硬错误优先

若存在 E4 元泄漏或严重 E3 设定冲突，即使表层模板分不高，也应提高整体风险。

### 2. 多信号共振

如果 Surface、Voice Fit、Consistency、Breath 同时中高，则整体风险升级。

### 3. 长篇单独判断

Mid-book Drift 只在多窗口输入时参与总分。单章不应推断长篇断层。

### 4. 平台风险不混入文本风险

Platform Enforcement Risk 单独显示，不计入文本 slop 总分。

## 建议权重

单章/片段：

```text
overall_text_slop =
  0.20 * surface_template +
  0.20 * voice_fit_risk +
  0.25 * consistency_assimilation_risk +
  0.20 * breath_focus_risk +
  0.15 * human_trace_missing_risk
```

长篇：

```text
overall_longform_slop =
  0.15 * surface_template +
  0.15 * voice_fit_risk +
  0.20 * consistency_assimilation_risk +
  0.20 * breath_focus_risk +
  0.20 * midbook_drift_risk +
  0.10 * human_trace_missing_risk
```

可按平台调整：

- 同人：提高 Voice Fit 和 Human Trace。
- 番茄升级爽文：提高 Breath/Retention。
- 悬疑：提高 Consistency/Foreshadowing。
- 短篇：提高 Hook/Emotional Activation。

## 证据表格式

| 位置 | 原文 | 问题类型 | 模块 | 证据等级 | 风险说明 | 替代解释 | 修改建议 |
|---|---|---|---|---|---|---|---|

每个高分问题必须有证据。没有证据的评价不能进入报告。

## 替代解释模块

根据命中模式自动补充：

- 如果主要是古早套话：可能是传统网文风格。
- 如果主要是开篇强刺激：可能是平台过签公式。
- 如果语病多但模板少：可能是低水平人工写作。
- 如果同人角色不贴：可能是作者理解偏差，也可能是 AI 平均同人味。
- 如果后期变差：可能是赶稿、换写手、作者疲劳、AI辅助比例变化。

## 修改优先级排序

1. E4 元泄漏：删除/彻底重写。
2. E3 设定冲突：修复事实、角色绑定、未授权新增。
3. 叙事机制：补后果、目标、代价、关系变化。
4. 角色语域：重写台词、内心、旁白视角。
5. Human Trace：补作者取舍、具体经验、不可替代选择。
6. 表层模板：删改动作链、比喻、升华句、固定句式。
7. 语言润色：最后处理。

## 改写任务生成格式

每个任务应具体：

```json
{
  "task_id": "R1",
  "priority": 1,
  "target_span": "第3-5段",
  "problem": "事件结束后无后果消化，直接进入下一冲突",
  "repair_goal": "补一段低冲突但有剧情锚定的缓冲",
  "constraints": [
    "不能新增外部敌人",
    "必须体现主角对上一事件代价的认识",
    "结尾自然引出下一目标"
  ]
}
```

## 报告模板

```markdown
# 文本 Slop 风险诊断报告

## 总览
- 整体文本模板化风险：中高
- 证据等级：E2 多信号共振
- 安全结论：该片段呈现明显模板化和叙事焦躁风险，但不足以证明作者使用 AI。

## 主要风险
1. 表层动作链重复
2. 角色语域偏分析腔
3. 事件之间缺少后果消化

## 证据表
...

## 替代解释
...

## 修改优先级
...

## 建议改写任务
...
```

## 禁止输出

- “AI概率：92%”
- “作者用了 DeepSeek”
- “这一定是 AI 文”
- “从第十章开始机写”

## 推荐输出

- “文本呈现高模板化风险”
- “证据等级 E2/E3”
- “更像模型模板 / 工作室公式 / 题材俗套 / 低水平人工中的哪一类”
- “如何修改以降低 slop 感”
