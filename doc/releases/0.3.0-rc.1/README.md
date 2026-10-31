# AINovel 0.3.0-rc.1

本候选整合世界观、自主创作基础、写作与故事助手、模型配置、篇幅候选恢复，以及已确认的全仓代码清理。H/G 阶段与文学效果验收继续以路线图为准。

## 发布身份与接口

- 活动分支 `release/0.3.0`，附注标签 `v0.3.0-rc.1`。发版中心使用标签解析的完整 SHA；平台日期编号独立生成。
- 前后端版本均为 `0.3.0`；后端可执行制品统一为 `ai-novel-backend.jar`，容器内仍为 `/app/app.jar`。
- 已退役的触发、旧认证提示与 ES 查询接口返回 404。失败或未完成检查的风险分、严重程度及无有效结果的风险均值返回 `null`；真实 0 保留。
- 历史任务、正文、报告及作者审阅材料继续读取。新模型功能沿用显式配置；验收不追加真实付费调用。

## 数据库批次

staging 起点 V35，目标 V50。[迁移清单](migrations.json)记录原始字节 SHA-256、Flyway 校验和、执行顺序和受影响表；[当前结构](schema.sql)来自本候选的真实 MySQL V35→V50 回放，仅含 DDL；[后置核验](postconditions.sql)按目标库执行。

V36–V50 原文件随候选 tag 冻结。逐项执行原 SQL，成功并核验后追加对应账本。V43 的实际执行记录须先于 V45；V45 使用该记录校正基线时间。现有 V1–V35 的 SQL、校验和及账本保持。DDL 可能部分提交，失败时保留执行 ID 和实际进度，先处理未完成项。

| 迁移 | 新建表 | 改表 | 数据处理 |
| --- | --- | --- | --- |
| [V36](../../../backend/src/main/resources/db/migration/V36__scene_generation_candidates.sql) | — | `narrative_generation_candidates` | — |
| [V37](../../../backend/src/main/resources/db/migration/V37__writing_assistant.sql) | `writing_assistant_proposals`、`writing_assistant_sessions`、`writing_assistant_turns` | — | — |
| [V38](../../../backend/src/main/resources/db/migration/V38__story_assistant.sql) | `story_assistant_proposals`、`story_assistant_receipts`、`story_assistant_sessions`、`story_assistant_turns` | `character_cards` | — |
| [V39](../../../backend/src/main/resources/db/migration/V39__ai_feature_model_configuration.sql) | `ai_model_catalog_state`、`ai_model_options`、`ai_model_quotes` | `ai_credit_reservations`、`ai_operation_runs`、`async_jobs`、`creation_workflow_runs`、`model_registry`、`scene_generation_runs`、`task_model_routing`、`user_model_preferences` | `ai_model_catalog_state` |
| [V40](../../../backend/src/main/resources/db/migration/V40__slop_review_snapshot.sql) | — | `slop_quality_runs` | — |
| [V41](../../../backend/src/main/resources/db/migration/V41__local_validation_money_budget.sql) | — | `ai_validation_budgets`、`ai_validation_calls` | — |
| [V42](../../../backend/src/main/resources/db/migration/V42__world_drafts_and_versions.sql) | `world_entries`、`world_publish_receipts`、`world_relations`、`world_versions` | `manuscripts`、`outlines`、`stories`、`worlds` | — |
| [V43](../../../backend/src/main/resources/db/migration/V43__world_legacy_baselines.sql) | — | — | `manuscripts`、`outlines`、`stories`、`world_entries`、`world_versions`、`worlds` |
| [V44](../../../backend/src/main/resources/db/migration/V44__world_baseline_content_digest.sql) | — | — | `world_versions` |
| [V45](../../../backend/src/main/resources/db/migration/V45__world_baseline_utc_timestamp.sql) | — | — | `world_versions` |
| [V46](../../../backend/src/main/resources/db/migration/V46__world_discussions_and_overview.sql) | `world_assistant_proposals`、`world_assistant_receipts`、`world_assistant_sessions`、`world_assistant_turns` | `world_prompt_templates`、`worlds` | — |
| [V47](../../../backend/src/main/resources/db/migration/V47__world_writing_context.sql) | `outline_ai_results`、`world_context_preferences` | — | — |
| [V48](../../../backend/src/main/resources/db/migration/V48__world_timeline_and_management.sql) | `story_world_eras`、`world_copy_receipts` | `worlds` | — |
| [V49](../../../backend/src/main/resources/db/migration/V49__ai_response_metadata.sql) | — | `ai_credit_reservations` | — |
| [V50](../../../backend/src/main/resources/db/migration/V50__writing_agent_runs.sql) | `writing_agent_runs`、`writing_agent_steps` | `ai_credit_reservations` | — |

## 验证与部署

候选执行标准 Windows 完整 L2、完整构建和 V35→V50 外部 MySQL 回放；此前 134 项隔离 MySQL 报告按源码关系复用，条件跳过分别记录。

数据库工作台核验并升级后，通过现行发版中心发布 AINovel staging。沿用现有配置对及 catalog 运行模板；配置、凭据和证书留在配置中心与目标环境。发布前保存旧版本制品和配置身份，恢复走平台历史版本入口，数据库处理依据实际迁移进度。

部署后核对实际 SHA、镜像摘要、容器归属、V50 账本和 HTTPS，再以既定 staging 测试账号验证 SSO、保存刷新、历史、失败反馈、输入保留及 390px 布局。过程记录与截图放在工作区外的发布证据目录。

staging 验收后删除已合入的 `codex/ainovel-code-cleanup` 本地及远端分支。保留活动 release、master、develop、历史标签和现有工作树目录。

## 安全事项

无新增或调整。沿用认证、TOTP、TLS、配置分工、日志脱敏及既有恢复机制；继续执行已批准的 S1–S23。
