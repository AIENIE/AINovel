# 15. Minimum Viable Detector Spec：中文小说 AI Slop MVP 检测器实现方案

> 目标：把前期的 pattern library、评分算法、JSON schema、prompt workflow 转成一个最小可运行的诊断器。
> 定位：评估文本的“模板化 / 工业化 / AI味 / slop 风险”，不判断作者是否使用 AI。

---

## 1. MVP 边界

### 1.1 MVP 要做什么

MVP 检测器输入一段中文小说文本，输出：

1. 总体 slop 风险等级；
2. 分模块评分；
3. 可回溯原文的证据表；
4. 替代解释；
5. 修改优先级；
6. rewrite tasks；
7. 可选：根据 rewrite tasks 生成分段改写 prompt。

### 1.2 MVP 不做什么

MVP 不做：

- 不输出“作者是否用了 AI”；
- 不输出“AI 写作概率”；
- 不做平台规避检测；
- 不给“绕过平台检测”的建议；
- 不把单个词、单个句式命中当成定罪证据；
- 不默认所有俗套都是 AI 味。

### 1.3 核心判断对象

MVP 判断的是文本风险层：

```text
文本 slop 风险 = 表层模板密度 + 语域不适配 + 设定吸收失败 + 叙事机制空转 + 作者痕迹不足
```

而不是：

```text
文本 slop 风险 ≠ AI 使用概率
```

---

## 2. 输入输出

### 2.1 输入格式

MVP 支持 JSON 和纯文本两种输入。

#### JSON 输入

```json
{
  "text": "小说片段或单章文本",
  "text_type": "chapter",
  "target_platform": "fanqie",
  "genre": "都市异能",
  "pov": "第三人称有限视角",
  "character_profiles": "可选：角色档案",
  "world_setting": "可选：世界观设定",
  "previous_summary": "可选：上一章摘要",
  "constraints": ["角色A没有眼镜", "不能新增超能力"]
}
```

#### 纯文本输入

```bash
slop-detect chapter.txt --platform fanqie --genre urban-fantasy
```

纯文本输入时，缺失上下文的模块应降权或标记 unavailable。

### 2.2 输出格式

输出必须同时支持：

1. `report.md`：人类可读诊断报告；
2. `result.json`：结构化结果，遵循 `11-output-json-schema.md`。

最小 JSON 输出：

```json
{
  "metadata": {},
  "input_summary": {},
  "overall": {},
  "module_scores": {},
  "evidence_items": [],
  "alternative_explanations": [],
  "revision_priorities": [],
  "rewrite_tasks": [],
  "platform_notes": [],
  "safety": {}
}
```

---

## 3. MVP 模块

### 3.1 模块总览

| 模块 | 类型 | MVP 是否必须 | 说明 |
|---|---|---:|---|
| 文本预处理 | deterministic | 是 | 切句、切段、窗口化、字符位置映射 |
| 规则匹配 | regex/keyword | 是 | 固定句式、动作链、意象、收尾套话、元泄漏 |
| 密度统计 | deterministic | 是 | 每 500/1000 字命中密度、聚集度、共现 |
| 语义判断 | LLM | 是 | 语域适配、Human Trace、事件传送带 |
| 一致性检查 | hybrid | 可选但建议 | 有上下文时检查角色/设定冲突 |
| 证据分级 | deterministic + LLM | 是 | E1-E4 |
| 评分聚合 | deterministic | 是 | 按权重生成模块分和总体分 |
| 报告生成 | template + LLM | 是 | Markdown 报告、rewrite tasks |
| 改写执行 | LLM | 可选 | MVP 可只生成改写任务，不直接改写 |

### 3.2 推荐目录结构

```text
slop_detector/
  configs/
    scoring-weight-config.yaml
    pattern-dictionary.yaml
  slop_detector/
    __init__.py
    cli.py
    preprocessing.py
    feature_rules.py
    semantic_judges.py
    scoring.py
    evidence.py
    reporting.py
    rewrite_tasks.py
    schemas.py
  prompts/
    diagnose_surface.md
    diagnose_voice_fit.md
    diagnose_breath_focus.md
    rewrite_by_evidence.md
  tests/
    fixtures/
    test_feature_rules.py
    test_scoring.py
    test_schema.py
```

---

## 4. 处理流程

### Step 1：文本预处理

输入文本后，先生成结构化窗口：

```json
{
  "windows": [
    {
      "window_id": "w1",
      "char_start": 0,
      "char_end": 3000,
      "paragraphs": [],
      "sentences": []
    }
  ]
}
```

预处理要求：

- 保留原文字符偏移；
- 按段落、句子、窗口三层组织；
- 默认窗口 1500-3000 字；
- 长篇模式可按章节或滑动窗口处理；
- 不改变原文标点，避免证据回溯失败。

### Step 2：规则特征提取

规则提取适合：

- 固定句式；
- 高频动作链；
- 意象套件；
- 升华收尾；
- Markdown 残留；
- 元提示泄漏；
- 部分近句矛盾的硬规则。

输出统一特征项：

```json
{
  "feature_id": "F-0001",
  "pattern_id": "A001",
  "module": "surface_template",
  "category": "phrase_pattern",
  "quote": "不是愤怒，而是一种更深的失望",
  "char_start": 120,
  "char_end": 137,
  "base_weight": 2,
  "raw_confidence": 0.8
}
```

### Step 3：密度与共振统计

MVP 必须计算：

1. 每 500 字 pattern 总数；
2. 每 1000 字 pattern 总数；
3. 同类 pattern 聚集；
4. 跨类 pattern 共现；
5. 对话尾巴比例；
6. 高风险证据是否集中在同一窗口。

共振示例：

```text
500字内同时出现：
- 3 次“不是……而是……”
- 2 次语气标签
- 2 次器官轮班
- 1 个抽象升华收尾
=> 表层模板风险升权
```

### Step 4：LLM 语义判断

LLM 负责规则难以判断的层：

- 角色语域是否不贴；
- 旁白是否变成 AI 分析腔；
- 事件是否机械续接；
- 缓冲段是否有效；
- 主角是否只有数值成长；
- 文本是否缺乏作者痕迹；
- pattern 是否在当前语境下应降权。

LLM 输出必须引用原文证据，不允许只给抽象评价。

### Step 5：证据等级

使用 `01-evidence-confidence-algorithm.md` 的 E1-E4：

| 等级 | 含义 | 示例 |
|---|---|---|
| E1 | 弱信号 | 单个固定句式、单个动作链 |
| E2 | 密度/共振信号 | 短窗口内多类模板共现 |
| E3 | 文本内部强异常 | 角色设定冲突、近句事实矛盾、未授权新增 |
| E4 | 元泄漏/生成残留 | “用户说过”“以下是修改版”、Markdown残留 |

### Step 6：评分聚合

每个模块先产生 0-100 分风险，再汇总为总体风险。

建议模块：

```text
surface_template
voice_fit
consistency_assimilation
breath_focus_pacing
midbook_drift
human_trace
platform_commercial  # 不计入 overall，只单独提示
```

聚合原则：

- E4 证据直接设置最低风险等级为 high；
- E3 高密度时可推到 high/critical；
- E1 不得单独推高总体；
- 缺上下文时 consistency/voice_fit 置信度下降；
- platform_commercial 不计入文本 slop 总分。

### Step 7：报告生成

报告结构：

```markdown
# AI Slop 风险诊断报告

## 1. 安全结论
## 2. 总体评分
## 3. 分模块评分
## 4. 证据表
## 5. 替代解释
## 6. 修改优先级
## 7. Rewrite Tasks
## 8. 人工复核点
```

### Step 8：改写任务生成

MVP 可先不直接改写，只输出 rewrite tasks：

```json
{
  "task_id": "R1",
  "priority": 1,
  "problem_layer": "breath_focus_pacing",
  "target_span": {"char_start": 1000, "char_end": 1800},
  "problem": "事件结束后直接进入下一冲突，缺少后果消化。",
  "repair_goal": "补一个有剧情锚定的缓冲段。",
  "constraints": ["不能新增敌人", "必须承认上一事件代价"]
}
```

---

## 5. 风险标签

| 分数 | 标签 | 说明 |
|---:|---|---|
| 0-24 | low | 少量弱信号，整体风险低 |
| 25-49 | medium | 有可见模板化问题，但可能是题材俗套/人工低水平 |
| 50-74 | high | 多层信号共振，影响阅读体验，建议重点修改 |
| 75-100 | critical | 存在强证据或严重结构性 slop，需重写部分段落 |

注意：分数是文本 slop 风险，不是 AI 使用概率。

---

## 6. MVP 验收标准

### 6.1 功能验收

MVP 完成时，应能：

- 读取 `.txt` 或 `.json` 输入；
- 输出 `.md` 和 `.json`；
- 对固定 pattern 做规则命中；
- 计算密度和共现；
- 调用 LLM 做至少 3 个语义判断；
- 输出证据表，每条证据有原文引用；
- 输出替代解释；
- 输出 rewrite tasks；
- 不输出作者是否使用 AI 的定性判断。

### 6.2 质量验收

至少使用 20 个测试样本：

- 5 个明显 AI味样本；
- 5 个传统网文俗套但非 AI味样本；
- 5 个高质量人工文本；
- 5 个混合/改写/中段断层样本。

检查：

- 是否把单个俗套词误判为高风险；
- 是否能抓出元泄漏和事实矛盾；
- 是否能区分表层词句问题和深层叙事机制问题；
- rewrite tasks 是否可执行。

---

## 7. 伪代码

```python
def diagnose(input_doc, config):
    windows = preprocess(input_doc.text, config.windowing)
    rule_features = extract_rule_features(windows, config.patterns)
    density_features = compute_density(rule_features, windows)
    semantic_features = run_semantic_judges(
        windows=windows,
        context=input_doc.context,
        prompts=config.prompts,
    )
    evidence_items = build_evidence_items(
        rule_features,
        density_features,
        semantic_features,
        config.evidence_rules,
    )
    module_scores = score_modules(evidence_items, config.weights)
    overall = aggregate_overall(module_scores, evidence_items, config.weights)
    rewrite_tasks = generate_rewrite_tasks(evidence_items, module_scores)
    return build_report(overall, module_scores, evidence_items, rewrite_tasks)
```

---

## 8. 实施顺序建议

1. 先实现规则匹配和 Markdown 报告；
2. 再加入 JSON schema；
3. 再加入 LLM 语义判断；
4. 再加入评分权重；
5. 最后加入 rewrite tasks 和改写 prompt。

最小第一版可以只做：

```text
txt 输入 → pattern 命中 → 密度统计 → 证据表 → Markdown 报告
```

但最终 MVP 应包含 LLM 语义判断，否则无法处理语域、呼吸感、Human Trace 等核心问题。
