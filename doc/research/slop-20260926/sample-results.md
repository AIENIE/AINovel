# 24 个冻结片段的逐例结果

标签为代理编辑判断；原句、理由及来源见 samples.json，全文上下文见 scenes.json / reading-corpus.json。触发定义为 requiresAiReview=true，低风险提示不计阳性。

| ID | 类别 | 集合 | 来源 | 期望介入 | 风险 | 触发 | 结果 |
|---|---|---|---|---|---:|---|---|
| F01 | negative_parallel | development | agent_constructed | True | 72 | True | TP |
| F02 | negative_parallel | development | agent_constructed | True | 0 | False | FN |
| F03 | negative_parallel | development | real_model_excerpt | False | 0 | False | TN |
| F04 | negative_parallel | holdout | agent_constructed | False | 28 | False | TN |
| F05 | dialogue_tail | development | agent_constructed | True | 72 | True | TP |
| F06 | dialogue_tail | development | agent_constructed | True | 0 | False | FN |
| F07 | dialogue_tail | development | real_model_excerpt | False | 0 | False | TN |
| F08 | dialogue_tail | holdout | agent_constructed | False | 0 | False | TN |
| F09 | body_action | development | agent_constructed | True | 72 | True | TP |
| F10 | body_action | development | agent_constructed | True | 0 | False | FN |
| F11 | body_action | development | agent_constructed | False | 0 | False | TN |
| F12 | body_action | holdout | real_model_excerpt | False | 0 | False | TN |
| F13 | imagery | development | agent_constructed | True | 72 | True | TP |
| F14 | imagery | development | agent_constructed | True | 0 | False | FN |
| F15 | imagery | development | real_model_excerpt | False | 0 | False | TN |
| F16 | imagery | holdout | real_model_excerpt | False | 0 | False | TN |
| F17 | abstract_summary | development | agent_constructed | True | 58 | True | TP |
| F18 | abstract_summary | holdout | agent_constructed | True | 0 | False | FN |
| F19 | abstract_summary | development | real_model_excerpt | False | 0 | False | TN |
| F20 | abstract_summary | holdout | real_model_excerpt | False | 0 | False | TN |
| F21 | rhythm_repetition | development | agent_constructed | True | 0 | False | FN |
| F22 | rhythm_repetition | holdout | agent_constructed | True | 0 | False | FN |
| F23 | rhythm_repetition | development | real_model_excerpt | False | 0 | False | TN |
| F24 | rhythm_repetition | holdout | agent_constructed | False | 0 | False | TN |
