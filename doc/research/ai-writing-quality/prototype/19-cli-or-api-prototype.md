# 19. CLI / API Prototype：命令行与 API 调用方式设计

本文件定义 AI Slop MVP 检测器的命令行和 API 原型。目标是让 pattern library + scoring + prompt workflow 可以被实际调用，而不是停留在文档层。

---

## 1. 设计目标

CLI / API 应支持三种使用方式：

1. **快速体检**：输入 txt，输出 Markdown 报告；
2. **结构化诊断**：输入 JSON，输出 JSON + Markdown；
3. **工作流集成**：作为服务 API，被写作工具调用，返回 evidence_items 和 rewrite_tasks。

---

## 2. CLI 原型

命令名建议：

```bash
slop-detect
```

或项目内：

```bash
python -m slop_detector
```

### 2.1 快速体检

```bash
slop-detect diagnose chapter.txt \
  --platform fanqie \
  --genre urban-fantasy \
  --out report.md
```

输出：

- `report.md`：诊断报告；
- 终端打印风险总览。

### 2.2 同时输出 JSON

```bash
slop-detect diagnose chapter.txt \
  --platform fanqie \
  --mode chapter \
  --json-out result.json \
  --md-out report.md
```

### 2.3 带上下文诊断

```bash
slop-detect diagnose chapter.txt \
  --context context.json \
  --platform qidian \
  --genre xuanhuan \
  --md-out report.md \
  --json-out result.json
```

`context.json` 示例：

```json
{
  "pov": "第三人称有限视角",
  "character_profiles": {
    "林照": "17岁，近视但战斗时不戴眼镜，性格谨慎，不会冷笑装逼",
    "沈棠": "外冷内热，表达克制，不会直白撒娇"
  },
  "world_setting": "近未来城市，无超自然力量",
  "constraints": [
    "不能新增异能",
    "林照当前右手受伤",
    "沈棠不知道主角的秘密"
  ]
}
```

### 2.4 长篇多窗口巡检

```bash
slop-detect drift novel_windows.json \
  --mode multi-window \
  --md-out drift-report.md \
  --json-out drift-result.json
```

`novel_windows.json` 示例：

```json
{
  "windows": [
    {"label": "opening_ch1", "text": "..."},
    {"label": "mid_50k", "text": "..."},
    {"label": "late_150k", "text": "..."}
  ],
  "context": {
    "target_platform": "fanqie",
    "genre": "都市异能"
  }
}
```

### 2.5 只提取特征，不跑 LLM

```bash
slop-detect features chapter.txt \
  --config scoring-weight-config.yaml \
  --json-out features.json \
  --no-llm
```

用途：

- 调试规则；
- 快速批处理；
- 低成本扫描。

### 2.6 生成 rewrite tasks

```bash
slop-detect tasks result.json \
  --md-out rewrite-tasks.md \
  --json-out rewrite-tasks.json
```

### 2.7 根据 evidence 改写片段

```bash
slop-detect rewrite chapter.txt \
  --diagnosis result.json \
  --task-id R1 \
  --out rewritten-R1.md
```

默认只改写目标 span，不全文重写。

---

## 3. CLI 参数

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `input` | path | 必填 | 输入 txt/json |
| `--mode` | enum | `chapter` | `passage/chapter/short_story/fanfic/multi-window` |
| `--platform` | enum | `unknown` | `fanqie/qidian/jjwxc/lofter/zhihu/unknown` |
| `--genre` | string | `unknown` | 题材 |
| `--context` | path | none | 上下文 JSON |
| `--config` | path | default | 评分权重配置 |
| `--pattern-config` | path | default | pattern 字典 |
| `--md-out` | path | none | Markdown 报告输出 |
| `--json-out` | path | none | JSON 输出 |
| `--no-llm` | flag | false | 只跑规则，不调用 LLM |
| `--llm-model` | string | config | 语义判断模型 |
| `--window-size` | int | 2500 | 窗口大小 |
| `--safe-mode` | flag | true | 禁止 AI 作者定罪话术 |
| `--include-platform-risk` | flag | false | 是否输出平台风险，不计入 overall |

---

## 4. API 原型

### 4.1 REST API

Base URL：

```text
http://localhost:8000
```

#### POST `/v1/diagnose`

请求：

```json
{
  "text": "小说正文...",
  "metadata": {
    "text_type": "chapter",
    "target_platform": "fanqie",
    "genre": "都市异能",
    "pov": "第三人称有限视角"
  },
  "context": {
    "character_profiles": {},
    "world_setting": "",
    "constraints": []
  },
  "options": {
    "run_llm": true,
    "include_rewrite_tasks": true,
    "include_platform_risk": false,
    "output_format": "json"
  }
}
```

响应：

```json
{
  "analysis_id": "local-uuid",
  "overall": {
    "overall_slop_risk": 56,
    "risk_label": "high",
    "evidence_level": "E2",
    "safe_claim": "该文本呈现 high 级模板化/slop风险；这不能证明作者使用AI。"
  },
  "module_scores": {},
  "evidence_items": [],
  "alternative_explanations": [],
  "revision_priorities": [],
  "rewrite_tasks": [],
  "safety": {
    "authorship_claim_avoided": true
  }
}
```

#### POST `/v1/features`

只跑规则和统计，不调用 LLM。

#### POST `/v1/rewrite-tasks`

根据诊断结果生成 rewrite tasks。

#### POST `/v1/rewrite`

根据指定 task 改写目标片段。

请求：

```json
{
  "text": "原文",
  "diagnosis": {},
  "task_id": "R1",
  "constraints": ["不能新增敌人"]
}
```

响应：

```json
{
  "task_id": "R1",
  "strategy": "...",
  "rewritten_text": "...",
  "change_notes": [],
  "remaining_risks": []
}
```

---

## 5. Python SDK 原型

```python
from slop_detector import SlopDetector

client = SlopDetector.from_config("scoring-weight-config.yaml")

result = client.diagnose(
    text=open("chapter.txt", encoding="utf-8").read(),
    target_platform="fanqie",
    genre="都市异能",
    context={
        "character_profiles": {...},
        "constraints": ["角色A没有眼镜"]
    },
    run_llm=True,
)

print(result.overall.risk_label)
result.save_markdown("report.md")
result.save_json("result.json")
```

---

## 6. 输出报告样式

CLI 默认 Markdown 报告：

```markdown
# AI Slop 风险诊断报告

## 安全结论
该文本呈现 high 级模板化/slop风险；这不能证明作者使用AI。

## 总览
- 总分：56/100
- 证据等级：E2
- 主要风险：表层模板密度、事件传送带

## 证据表
| 位置 | 原文 | 类型 | 等级 | 说明 | 修改建议 |

## 替代解释
- 可能是传统网文俗套；
- 可能是平台爽文公式。

## Rewrite Tasks
1. R1：第3-5段补事件后果。
2. R2：第8段减少语气标签，用潜台词改写。
```

---

## 7. 安全与隐私设计

### 7.1 安全话术拦截

报告生成前执行 forbidden claims 检查：

```text
作者一定用了AI
这是AI写的
AI生成概率
规避平台检测
```

若出现，阻断输出并重写安全结论。

### 7.2 隐私

默认：

- 不保存用户原文；
- 只在用户指定 `--save-analysis` 时保存；
- 保存时可选择脱敏；
- 测试集保存需用户明确授权。

CLI 参数：

```bash
--save-analysis
--redact-names
--local-only
```

### 7.3 平台风险

平台风险默认不输出；即使输出，也必须说明：

```text
平台风险不等于文本 AI味，也不证明作者使用 AI。
```

---

## 8. 实现阶段

### Phase 1：本地 CLI

- txt 输入；
- 规则提取；
- 密度统计；
- Markdown 报告。

### Phase 2：结构化 JSON

- JSON schema；
- evidence_items；
- rewrite_tasks；
- 配置化权重。

### Phase 3：LLM 语义判断

- voice_fit；
- consistency；
- breath_focus；
- human_trace。

### Phase 4：API 服务

- FastAPI；
- `/v1/diagnose`；
- `/v1/features`；
- `/v1/rewrite`。

### Phase 5：写作工作台集成

- 可视化证据高亮；
- 分段改写；
- 改写后复检；
- pattern library 维护界面。

---

## 9. 推荐首版命令

真正开始实现时，建议先只做这个命令：

```bash
slop-detect diagnose input.txt --md-out report.md --json-out result.json --no-llm
```

通过后再加入：

```bash
slop-detect diagnose input.txt --context context.json --run-llm
```

这样可以先验证 pattern library 和 scoring 配置，再处理 LLM 不稳定性。
