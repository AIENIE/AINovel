# AI Slop 评分系统 JSON 输出 Schema v1

本文件定义算法原型的统一输出结构。目标是让不同评分模块的结果可以合并，最终生成报告或驱动改写 workflow。

## 顶层结构

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

## metadata

```json
{
  "schema_version": "1.0",
  "analysis_id": "uuid-or-local-id",
  "created_at": "ISO-8601",
  "language": "zh-CN",
  "text_type": "novel|fanfic|outline|chapter|short_story|unknown",
  "target_platform": "fanqie|qidian|jjwxc|lofter|zhihu|unknown",
  "analysis_mode": "single_passage|chapter|multi_window_longform"
}
```

## input_summary

```json
{
  "text_length_chars": 0,
  "estimated_chapters": 1,
  "provided_context": {
    "character_profiles": true,
    "world_setting": true,
    "previous_summary": false,
    "target_platform": true
  },
  "windows": [
    {
      "window_id": "w1",
      "label": "chapter_1",
      "char_start": 0,
      "char_end": 3000
    }
  ]
}
```

## overall

```json
{
  "overall_slop_risk": 0,
  "risk_label": "low|medium|high|critical",
  "evidence_level": "E1|E2|E3|E4",
  "safe_claim": "该文本呈现……风险，但不能证明作者使用 AI。",
  "main_risk_layers": [
    "surface_template",
    "voice_fit",
    "consistency",
    "breath_focus",
    "human_trace"
  ],
  "confidence_notes": "..."
}
```

## module_scores

```json
{
  "surface_template": {
    "score": 0,
    "subscores": {
      "phrase_pattern_density": 0,
      "body_action_density": 0,
      "imagery_cliche_density": 0,
      "emotion_summary_density": 0,
      "ending_cliche_density": 0,
      "dialogue_tail_ratio": 0
    }
  },
  "voice_fit": {
    "score": 0,
    "subscores": {
      "register_mismatch": 0,
      "character_mismatch": 0,
      "genre_platform_mismatch": 0,
      "era_worldview_mismatch": 0,
      "pov_mismatch": 0
    }
  },
  "consistency_assimilation": {
    "score": 0,
    "subscores": {
      "local_contradiction": 0,
      "character_binding_error": 0,
      "unauthorized_addition": 0,
      "constraint_violation": 0,
      "prompt_assimilation_failure": 0
    }
  },
  "breath_focus_pacing": {
    "score": 0,
    "subscores": {
      "event_conveyor_belt": 0,
      "aftermath_absence": 0,
      "focus_diffusion": 0,
      "ineffective_buffer": 0,
      "non_stat_growth_absence": 0,
      "world_reactivity_absence": 0
    }
  },
  "midbook_drift": {
    "score": null,
    "available": false,
    "reason_if_unavailable": "需要多章节窗口"
  },
  "human_trace": {
    "score": 0,
    "subscores": {
      "lack_of_specific_experience": 0,
      "lack_of_authorial_intent": 0,
      "replaceable_character_choices": 0,
      "weak_consequence_understanding": 0,
      "over_smoothed_style": 0
    }
  },
  "platform_commercial": {
    "included_in_overall": false,
    "platform_fit_score": 0,
    "platform_enforcement_risk": 0,
    "disclosure_risk": 0,
    "sustainability_risk": 0,
    "monetization_slop_risk": 0
  }
}
```

## evidence_items

每条证据必须能回溯到原文。

```json
{
  "id": "EVID-001",
  "window_id": "w1",
  "char_start": 120,
  "char_end": 168,
  "quote": "不是愤怒，而是一种更深的失望。",
  "module": "surface_template",
  "pattern_id": "A001",
  "issue_type": "phrase_pattern",
  "evidence_level": "E1",
  "severity": "low|medium|high",
  "risk_explanation": "近500字内第3次出现排除式情绪升级句式。",
  "alternative_explanations": ["作者个人修辞习惯", "角色正在辩论或分析"],
  "repair_hint": "改成角色具体行动或判断。"
}
```

## alternative_explanations

```json
[
  {
    "type": "genre_cliche|studio_formula|human_low_skill|platform_pressure|author_style",
    "description": "这些特征也可能来自传统网文俗套，而非 AI。",
    "applies_to_evidence_ids": ["EVID-001"]
  }
]
```

## revision_priorities

```json
[
  {
    "priority": 1,
    "problem_layer": "consistency",
    "reason": "设定硬冲突优先于文风润色。",
    "target_evidence_ids": ["EVID-003"],
    "action": "修复角色动作与设定冲突"
  }
]
```

## rewrite_tasks

```json
[
  {
    "task_id": "R1",
    "priority": 1,
    "target_span": {
      "window_id": "w1",
      "char_start": 500,
      "char_end": 1100
    },
    "problem": "事件结束后直接进入下一冲突，缺少后果消化。",
    "repair_goal": "补一段有剧情锚定的缓冲：处理上一事件代价，并自然引出下一目标。",
    "constraints": [
      "不能新增外部敌人",
      "必须体现主角对上一事件代价的认识",
      "结尾保留下一事件钩子"
    ],
    "suggested_method": "aftermath_buffer_rewrite"
  }
]
```

## platform_notes

```json
[
  {
    "type": "platform_enforcement_risk",
    "target_platform": "fanqie",
    "risk": "medium",
    "note": "该风险来自平台生态和披露变量，不等同于文本 AI味。"
  }
]
```

## safety

```json
{
  "forbidden_claims": [
    "作者一定用了AI",
    "这篇就是AI写的",
    "从第X章开始机写"
  ],
  "allowed_claim_template": "该文本呈现{risk_label}的模板化/工业化风险，证据等级为{evidence_level}。这些证据只能评价文本表现，不能单独证明作者使用AI。",
  "requires_human_review": true,
  "false_positive_warning": "存在传统网文俗套、人工工作室公式或低水平写作造成相似特征的可能。"
}
```

## 最小可用输出

如果只做快速诊断，至少输出：

```json
{
  "overall": {
    "overall_slop_risk": 0,
    "risk_label": "medium",
    "evidence_level": "E2",
    "safe_claim": "..."
  },
  "module_scores": {},
  "evidence_items": [],
  "revision_priorities": [],
  "safety": {}
}
```
