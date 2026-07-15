# 18. Testset Design：正例 / 反例测试集设计

本文件定义 AI Slop MVP 检测器的测试集。目标不是训练一个“AI检测器”，而是验证算法能否稳定识别文本模板化 / slop 风险，同时避免把传统俗套、人类低水平写作、题材风格误判成 AI 使用。

---

## 1. 测试集目标

测试集用于验证：

1. 规则 pattern 是否能被正确提取；
2. 密度和共振是否比单点命中更可靠；
3. LLM 语义判断是否能结合上下文降权；
4. E1-E4 证据等级是否合理；
5. rewrite tasks 是否具体可执行；
6. 报告是否避免“作者用了 AI”的不安全结论。

---

## 2. 样本类别

### 2.1 P 类：高 slop 正例

| 类别 | 数量建议 | 特征 |
|---|---:|---|
| P1 表层模板密集 | 10 | 固定句式、动作链、意象、收尾套话高密度 |
| P2 元泄漏 / 生成残留 | 5 | “以下是修改版”“用户说过”“###”等 |
| P3 设定吸收失败 | 10 | 角色无眼镜推眼镜、未授权新增、前后矛盾 |
| P4 事件传送带 | 10 | 连续冲突无后果、无缓冲、主角固定NPC化 |
| P5 中后段断层 | 5 组 | 开头正常，后期模板密度/叙事机制突变 |

### 2.2 N 类：低风险反例

| 类别 | 数量建议 | 特征 |
|---|---:|---|
| N1 高质量人工文本 | 10 | 有角色取舍、具体经验、节奏自然 |
| N2 传统网文俗套 | 10 | 有俗套词，但密度/机制未失控 |
| N3 角色合理分析腔 | 5 | 角色身份使“不是而是”等句式合理 |
| N4 快节奏但有效后果 | 5 | 节奏快，但事件有后果和目标重置 |
| N5 文体特殊 | 5 | 论坛体、系统面板、报告体、实验文本 |

### 2.3 M 类：混合 / 边界样本

| 类别 | 数量建议 | 特征 |
|---|---:|---|
| M1 人工低水平 | 10 | 句子差、俗套多，但无明显模型残留 |
| M2 工作室公式化 | 5 | 平台爽点堆砌，不一定是 AI |
| M3 AI 辅助轻润色 | 5 | 表层变顺，但角色和因果基本保留 |
| M4 人写 + AI 中段接管 | 5 组 | 前后机制断层 |
| M5 同人文角色不贴 | 5 | 表面 CP 味有，角色内核不贴 |

M 类最重要，因为它检验算法是否会“偷懒定罪”。

---

## 3. 标注维度

每个样本至少标注：

```yaml
sample_id: P1-001
text_type: chapter|short_story|fanfic|outline
source_type: synthetic|user_provided|public_excerpt|rewritten
length_chars: 2500
target_platform: fanqie|qidian|jjwxc|lofter|unknown
genre: 都市异能
labels:
  surface_template: low|medium|high|critical
  voice_fit: low|medium|high|critical
  consistency_assimilation: low|medium|high|critical
  breath_focus_pacing: low|medium|high|critical
  midbook_drift: unavailable|low|medium|high|critical
  human_trace: low|medium|high|critical
  platform_commercial: optional
expected_evidence_levels:
  - E1
  - E2
alternative_explanations:
  - genre_cliche
notes: "为什么这样标注"
privacy: public|private|synthetic|do_not_store
```

---

## 4. 正例设计

### 4.1 P1：表层模板密集样本

构造要求：

- 1000 字内至少 8 个表层 pattern；
- 至少 3 类共现：固定句式、动作链、意象、升华；
- 不包含元泄漏，避免 E4 干扰；
- 预期风险：surface_template high；overall medium/high。

示例标注：

```yaml
expected:
  surface_template: high
  evidence_level: E2
  should_not_claim_ai_authorship: true
```

### 4.2 P2：元泄漏样本

构造要求：

- 明确出现“根据你的大纲”“以下是修改版”“用户说过”；
- 或 Markdown 标题/列表突兀进入小说正文；
- 预期：E4，overall 至少 high。

### 4.3 P3：设定吸收失败样本

构造要求：

- 提供角色/世界观约束；
- 文本违反约束；
- 例如角色无眼镜但推眼镜，古代背景出现电梯，禁止新增超能力却新增神秘力量。

预期：consistency_assimilation high，E3。

### 4.4 P4：事件传送带样本

构造要求：

- 连续 3 个以上冲突无缝接上；
- 每个事件后没有后果消化；
- 主角目标没有更新，只是刷任务；
- 预期：breath_focus_pacing high。

### 4.5 P5：中后段断层样本

构造要求：

至少三个窗口：

1. opening：人物具体、有生活纹理；
2. mid：开始模板密度上升；
3. late：事件传送带、赞美词堆叠、角色变默认主角。

预期：midbook_drift high。

---

## 5. 反例设计

### 5.1 N1：高质量人工文本

特点：

- 可以有少量常见词；
- 但角色选择不可替代；
- 事件承认后果；
- 语域稳定；
- 无模板高密度。

预期：overall low。

### 5.2 N2：传统网文俗套但非高 slop

特点：

- 有“冷笑”“眼神一凝”“心中一紧”等俗套；
- 但密度不高；
- 有明确爽点、因果、节奏；
- 预期：surface_template low/medium，不应 overall high。

### 5.3 N3：角色合理分析腔

特点：

- 角色是老师、律师、谋士、AI角色、研究员；
- 使用“不是……而是……”合理；
- 预期：规则命中但 LLM 降权。

### 5.4 N4：快节奏但有效后果

特点：

- 事件连续，但每次事件改变资源、关系、目标；
- 不应被误判为事件传送带；
- 预期：breath_focus_pacing low/medium。

### 5.5 N5：文体特殊

特点：

- 论坛体、系统面板、报告体可能天然有 Markdown/列表/分析腔；
- 预期：如果 text_type 已声明，应降权。

---

## 6. 边界样本设计

### 6.1 M1：人工低水平

目的：检验算法是否把“写得差”直接当 AI味。

标注重点：

- 语言不顺、表达重复；
- 但没有元泄漏；
- 没有明显模型默认模板；
- 替代解释应包含 human_low_skill。

### 6.2 M2：工作室公式化

目的：区分 studio formula slop 与 AI 模型味。

特征：

- 强钩子；
- 爽点堆砌；
- 人设反差；
- 平台平均爆款味。

预期：platform_commercial 或 platform_formula 可高，但不得直接归因 AI。

### 6.3 M3：AI 辅助轻润色

目的：验证系统是否只评文本结果。

特征：

- 表面更顺；
- 原有角色因果仍保留；
- slop 风险可能低。

预期：不能因为“可能AI润色”而高分。

### 6.4 M4：人写 + AI 中段接管

目的：测试多窗口断层。

标注：

- 不标注“第几章用了AI”；
- 标注“第X窗口叙事机制断层风险”。

### 6.5 M5：同人角色不贴

目的：测试 voice_fit，而不是表层词句。

特征：

- CP 关系表面正确；
- 角色价值观、说话方式、世界观反应不贴；
- 预期：voice_fit high。

---

## 7. 样本来源策略

### 7.1 合成样本

优点：可控、无版权/隐私问题。
缺点：可能过于理想化。

用途：

- 单元测试；
- 元泄漏；
- 设定冲突；
- 固定 pattern 命中。

### 7.2 用户提供样本

必须确认：

- 是否允许保存；
- 是否允许用于测试集；
- 是否需要脱敏；
- 是否只用于本地临时分析。

建议标记：

```yaml
privacy: private
do_not_train: true
storage: local_only
```

### 7.3 公开片段

注意：

- 只保留短引用；
- 标明来源；
- 不复制大段版权文本进测试集；
- 可只保留特征摘要和少量证据片段。

---

## 8. 评估指标

### 8.1 特征提取指标

- pattern 命中准确率；
- 字符位置准确率；
- 漏检率；
- 误检率。

### 8.2 评分指标

因为不是二分类检测器，不建议只用 accuracy。建议：

- 与人工风险等级的 Spearman 相关；
- high/critical 的 precision；
- 反例误伤率；
- E4 检出率；
- rewrite task 可执行率。

### 8.3 安全指标

检查报告中是否出现禁用话术：

- “作者用了 AI”；
- “这是 AI 写的”；
- “AI 概率”；
- “规避平台检测”。

安全指标必须 100% 通过。

---

## 9. 初始测试集规模

### 9.1 Smoke Test

最小 10 个样本：

- P1 x2；
- P2 x1；
- P3 x1；
- P4 x1；
- N1 x2；
- N2 x1；
- M1 x1；
- M2 x1。

### 9.2 MVP Testset

建议 50 个样本：

- P 类 20；
- N 类 15；
- M 类 15。

### 9.3 Extended Testset

建议 150+：

- 不同平台；
- 不同题材；
- 不同长度；
- 同人 / 原创；
- 单章 / 多窗口。

---

## 10. 标注流程

1. 先由规则系统跑初版；
2. 人工标注 evidence 是否成立；
3. 人工给模块风险等级；
4. 标注替代解释；
5. 检查系统报告是否安全；
6. 根据误判回调权重和规则。

标注者至少要看到：

- 原文；
- 角色/世界观上下文；
- 目标平台；
- 系统证据表；
- 修改建议。

---

## 11. 回归测试清单

每次修改规则或权重后，必须检查：

- E4 元泄漏样本仍能 high/critical；
- 单个“不是而是”不会 high；
- 传统俗套样本不会被推到 critical；
- 缺上下文时 voice_fit 置信度下降；
- 快节奏有效后果样本不会被误判事件传送带；
- platform_commercial 不计入 overall；
- 报告没有 AI 作者定罪话术。
