# 人工标注规范 v1：AI Slop / 模板化风险数据集

本文件用于后续构建测试集和训练/校准评分算法。目标不是标注“是否AI生成”，而是标注文本中的可解释风险证据。

## 标注单位

建议三层标注：

1. **Span 级**：具体词句、动作、比喻、矛盾。
2. **Paragraph 级**：段落是否主体涣散、动作链膨胀、情绪总结过多。
3. **Chapter/Window 级**：事件传送带、无后果、角色成长停滞、文风断层。

## 标注目标

每条标注回答五个问题：

1. 原文证据是什么？
2. 属于哪类 pattern？
3. 证据等级 E1-E4 是什么？
4. 为什么它会造成 slop / 模板化风险？
5. 有哪些替代解释？

## 标签体系

### 表层模板类

- `PHRASE_PATTERN`：固定句式，如“不是……而是……”
- `TONE_DESCRIPTION`：语气/声音抽象标签
- `BODY_ACTION_CHAIN`：指节泛白、喉咙发紧、咬唇等
- `IMAGERY_CLICHE`：涟漪、影子拉长、羽毛等
- `ENDING_CLICHE`：夜还很长、才刚刚开始等
- `EMOTION_SUMMARY`：抽象情绪总结、宏大情绪词

### 语域适配类

- `REGISTER_MISMATCH`：文体错位
- `CHARACTER_VOICE_MISMATCH`：角色说话不像本人
- `ERA_WORLDVIEW_MISMATCH`：时代/世界观词汇错位
- `FANDOM_VOICE_MISMATCH`：同人角色不贴原作
- `POV_MISMATCH`：视角越界

### 一致性类

- `LOCAL_CONTRADICTION`：近句事实矛盾
- `ACTION_SETTING_CONFLICT`：动作与角色设定冲突
- `CHARACTER_BINDING_ERROR`：角色设定串台
- `UNAUTHORIZED_ADDITION`：未授权新增设定/能力/道具
- `PROMPT_ASSIMILATION_FAILURE`：设定只被浅层使用

### 叙事机制类

- `EVENT_CONVEYOR_BELT`：事件传送带
- `AFTERMATH_ABSENCE`：无后果消化
- `INEFFECTIVE_BUFFER`：无效日常缓冲
- `NON_STAT_GROWTH_ABSENCE`：只有数值成长
- `WORLD_REACTIVITY_ABSENCE`：世界只围主角转
- `FOCUS_DIFFUSION`：主体涣散/意象同权展开

### 长篇断层类

- `MIDBOOK_STYLE_DRIFT`：中后段文风漂移
- `RETENTION_CLIFF_PATTERN`：开头吸量后留存坍塌
- `FORESHADOWING_FORGETTING`：伏笔遗忘/回收突兀
- `ROLE_STABILITY_DECAY`：角色稳定性下降

### 作者痕迹类

- `LACK_SPECIFIC_EXPERIENCE`：缺少具体经验
- `LACK_AUTHORIAL_INTENT`：看不出作者想写什么
- `REPLACEABLE_CHARACTER_CHOICE`：角色选择可替代
- `OVER_SMOOTHED_STYLE`：过度正确平滑

### 平台/商业类

- `PLATFORM_FORMULA`：平台爆款公式
- `STUDIO_FORMULA`：人工工作室公式
- `MONETIZATION_SLOP`：变现驱动 slop
- `PLATFORM_ENFORCEMENT_RISK`：平台执行/限流风险

### 高置信元痕迹类

- `META_LEAK`：用户/指令/AI回复残留
- `ANSWER_RESIDUE`：Markdown/列表/回答格式残留
- `GENERATION_CORRECTION_RESIDUE`：角色名纠错残留

## 证据等级标注

- **E4**：元提示/生成过程泄漏。
- **E3**：结构性矛盾、设定硬冲突、角色绑定错误。
- **E2**：多信号共振。
- **E1**：单点词句/体感。

注意：标注员不能因为自己觉得像 AI 就标 E3/E4。没有硬证据时最多 E1/E2。

## 严重度 severity

- `low`：轻微风险，局部可改。
- `medium`：影响段落自然度或角色可信度。
- `high`：破坏沉浸、设定或叙事机制。
- `critical`：元泄漏、严重矛盾、整章机制崩坏。

## 替代解释标签

每条 E1-E3 标注尽量选择至少一个：

- `GENRE_CLICHE`：题材传统俗套。
- `AUTHOR_STYLE`：作者个人风格。
- `HUMAN_LOW_SKILL`：低水平人工表达。
- `STUDIO_FORMULA`：人工工作室模板。
- `PLATFORM_PRESSURE`：平台节奏/过签压力。
- `INTENTIONAL_PARODY`：有意模仿/反讽。
- `INSUFFICIENT_CONTEXT`：上下文不足。

## 标注格式 JSONL

每行一个标注：

```json
{
  "doc_id": "sample-001",
  "span_id": "s001",
  "level": "span|paragraph|window",
  "char_start": 120,
  "char_end": 168,
  "quote": "不是愤怒，而是一种更深的失望。",
  "labels": ["PHRASE_PATTERN", "EMOTION_SUMMARY"],
  "pattern_id": "A001",
  "evidence_level": "E1",
  "severity": "low",
  "risk_explanation": "该句本身可成立，但同窗口内多次出现排除式情绪升级。",
  "alternative_explanations": ["AUTHOR_STYLE", "INSUFFICIENT_CONTEXT"],
  "repair_hint": "改成角色具体行动、停顿或选择。"
}
```

## 标注流程

1. 先通读，不立刻标。
2. 第二遍标 span 级明显 pattern。
3. 第三遍标 paragraph/window 级叙事问题。
4. 为每条 E1-E3 添加替代解释。
5. 最后写整体判断：风险层级、主要问题、是否需要更多上下文。

## 一致性规则

- 单个“不是而是”通常 E1。
- 三个以上同类 pattern 在短窗口内可标 E2。
- 事实硬矛盾通常 E3。
- 元提示残留通常 E4。
- “看着累”必须拆成具体原因，否则只能作为备注。
- 平台限流/过签信息不能标成文本 AI味，只能标 `PLATFORM_ENFORCEMENT_RISK`。

## 标注员注意事项

- 不要标注作者动机。
- 不要写“这肯定是 AI”。
- 不要因为讨厌某种文风就提高证据等级。
- 对同人文要考虑原作角色资料；没有资料时标 `INSUFFICIENT_CONTEXT`。
- 对商业网文要考虑平台节奏，但平台节奏不能成为所有问题的借口。
