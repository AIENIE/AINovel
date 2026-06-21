# AI Slop 文本质量诊断

## 后续分期 Backlog（继续开发优先读这里）

1. Phase 7（可选）：阈值运营与样本导入。基于 Phase 6 已审核样本，再决定是否新增批量导入、统计报表和线上阈值调整能力。

## Phase 6：后台标注台和长期样本审核（2026-06-21）

- 新增管理员 Slop 校准审核样本表 `slop_review_samples`，记录手工样本或从 `slop_quality_runs` 沉淀的样本快照。
- 后台 `/admin/quality` 新增“样本审核”区域：手工创建样本、从文本 Slop 运行记录沉淀样本、按状态/不匹配/高风险筛选，并可标记 `APPROVED`、`REJECTED` 或 `NEEDS_DISCUSSION`。
- 样本创建和审核更新都会用 `LocalSlopHeuristics` 重新计算观测证据等级、AI review 触发、风险分和最大严重级别，并保存 `matchesExpected`。
- 本期不做阈值在线调整、不做批量导入、不调用 AI，不改变手动诊断、生成门禁或长篇 drift 的用户侧行为。

## Phase 5：标注集和阈值校准（2026-06-18）

- 已新增后端校准样本集 `backend/src/test/resources/quality/slop-calibration-samples.jsonl`，样本均为合成/脱敏文本，用于回归 E1/E2 升级和误伤边界。
- `LocalSlopHeuristics` 新增上下文输入与阈值策略：手动诊断和生成门禁可传入题材、语气、角色/风格上下文；旧 `evaluate(String)` 入口保持可用。
- 单个通用模板短句只作为 E1 弱信号并封顶低风险；短窗口多模板密集、重复或明确元泄漏才升级为 E2/E4。
- `SlopQualitySignals` 不再因为多个低风险 E1 issue 自动升级 E2，避免把传统网文俗套、人工低水平或平台公式化直接判成高风险。
- 本期不新增后台标注页面、不改数据库 schema、不改变 `/api/v2/*` 或质量诊断 HTTP 接口。

## 本期能力

- 工作台手动触发“文本 Slop 诊断”，服务端记录到 `slop_quality_runs`。
- 工作台质量分析页可触发“长篇 drift 巡检”，服务端按稿件窗口记录到 `slop_drift_runs`。
- 设置页风格画像、场景风格覆盖、角色声音和风格分析任务已使用数据库持久化；同一故事同一时间只有一个 active 风格画像，同一角色只有一份声音设定。
- 输出对象是文本层面的模板化、工业化和 slop 风险，不输出 AI 概率，不判断作者是否使用 AI。
- 诊断结果包含风险分、风险标签、证据等级、安全结论、模块分、证据表、替代解释、修改优先级和 rewrite tasks。
- 长篇 drift 巡检输出全稿窗口摘要、指标曲线、断层点、证据表、替代解释和 rewrite tasks；短稿会保存 `INSUFFICIENT_TEXT`，不调用 AI。
- 手动诊断不修改正文；自动生成链路会在 `generation_gate` 记录中保存同类证据等级、模块分、替代解释、修改优先级和 rewrite tasks。
- 自动生成链路仍只允许最多一次保守修订，不自动大改剧情事件、角色决策、人物关系或关键设定。
- 手动诊断和自动生成门禁都会读取故事 active 风格画像和角色声音，用作 `voice_fit` 判断语境；未配置时不会虚构角色语域问题。
- 本地规则会使用题材/语气/角色语境做保守降权，降低传统网文俗套、分析腔、快节奏平台文的误伤。

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
- 长篇 drift：`backend/src/main/java/com/ainovel/app/quality/SlopDriftService.java`、`backend/src/main/java/com/ainovel/app/quality/SlopDriftController.java`
- 生成门禁：`backend/src/main/java/com/ainovel/app/quality/SlopQualityGate.java`、`backend/src/main/java/com/ainovel/app/quality/AiSlopJudgeClient.java`、`backend/src/main/java/com/ainovel/app/quality/JpaSlopQualityRecorder.java`
- 风格/声音持久化：`backend/src/main/java/com/ainovel/app/style/StyleService.java`、`backend/src/main/java/com/ainovel/app/style/StyleContextProvider.java`
- 前端：`frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx`
- 全稿分析前端：`frontend/src/pages/Workbench/tabs/AnalysisDashboard.tsx`
- 类型与 API mapper：`frontend/src/types/index.ts`、`frontend/src/lib/mock-api.ts`
- Phase 5 校准样本：`backend/src/test/resources/quality/slop-calibration-samples.jsonl`
- Phase 5 测试：`backend/src/test/java/com/ainovel/app/quality/SlopCalibrationCorpusTest.java`、`backend/src/test/java/com/ainovel/app/quality/SlopQualitySignalsTest.java`
- Phase 6 后台审核样本：`backend/src/main/java/com/ainovel/app/quality/SlopReviewSampleService.java`、`frontend/src/pages/Admin/QualityInspection.tsx`
- 研究来源：`ai_novel_ai_taste_research/algorithms/`
