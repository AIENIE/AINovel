# AI Slop 文本质量诊断

## 后续分期 Backlog（继续开发优先读这里）

1. Phase 2：增强自动生成门禁。把本期的证据等级、密度共振和 rewrite tasks 接入生成后的 `SlopQualityGate`，但仍保持“最多一次保守修订、不自动大改剧情”的安全边界。
2. Phase 3：长篇 drift 巡检。按章节/字数窗口比较中后段断层、角色漂移、事件传送带和伏笔遗忘，输出全稿趋势，不推断“从某章开始用 AI”。
3. Phase 4：风格/角色声音持久化整合。补齐 `style_profiles`、`character_voices`、`style_analysis_jobs` 的持久化实现，再把语域贴合和角色声音用于 slop 诊断。
4. Phase 5：标注集和阈值校准。基于 `ai_novel_ai_taste_research/algorithms/12-annotation-guidelines.md` 建人工标注样本，校准 E1/E2 升级规则和平台/题材误伤。

## 本期能力

- 工作台手动触发“文本 Slop 诊断”，服务端记录到 `slop_quality_runs`。
- 输出对象是文本层面的模板化、工业化和 slop 风险，不输出 AI 概率，不判断作者是否使用 AI。
- 诊断结果包含风险分、风险标签、证据等级、安全结论、模块分、证据表、替代解释、修改优先级和 rewrite tasks。
- 手动诊断不修改正文；自动生成链路原有的轻量质量门禁仍保持独立。

## 诊断层级

- `surface_template`：固定句式、动作链、意象套件、升华收尾、对话尾巴等表层模板密度。
- `voice_fit`：角色台词、内心独白、旁白语域和题材/时代是否贴合。
- `consistency_assimilation`：事实矛盾、角色绑定错误、未授权新增设定、提示吸收失败。
- `breath_focus_pacing`：事件传送带、后果缺席、焦点扩散、缓冲无效、非数值成长缺失。
- `human_trace`：具体经验、作者取舍、不可替代角色选择和过度平滑风险。

## 证据等级

- `E1`：单点弱信号，只能提示轻微模板化风险。
- `E2`：短窗口密度或多信号共振，建议优先修改。
- `E3`：设定、角色或局部事实硬冲突。
- `E4`：元提示、Markdown、生成过程或非正文残留。

## 安全边界

- 禁止输出“AI率”“作者用了 AI”“某模型生成”“从第 X 章开始机写”。
- E1-E3 必须显示替代解释，例如传统网文俗套、人工低水平写作、工作室公式化、题材/平台惯例、作者个人文风。
- 平台合规、AIGC 标识和商业风险不混入文本 slop 总分；后续如加入平台风险，也应单独展示。

## 主要代码入口

- 后端：`backend/src/main/java/com/ainovel/app/quality/SlopDiagnosticService.java`
- API：`backend/src/main/java/com/ainovel/app/quality/SlopQualityController.java`
- 前端：`frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx`
- 类型与 API mapper：`frontend/src/types/index.ts`、`frontend/src/lib/mock-api.ts`
- 研究来源：`ai_novel_ai_taste_research/algorithms/`
