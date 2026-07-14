# 2026-07-14 Flyway v2 持久化回补验证

## 背景

运行中的旧库已登记 Flyway `V1` baseline 和 `V2` 精雕模式库，但缺少早期 v2 的 24 张持久化表。原因是 `V1__baseline.sql` 由后续完整 schema 整理而来；旧库在这些表存在之前执行 baseline 后，Flyway 不会重新执行 V1。`workspace_layouts` 的运行时查询因此返回 MySQL 1146。

## 修复

- 新增 `V3__backfill_baselined_v2_persistence.sql`。
- V3 仅补齐缺失的 Lorebook、图谱、风格、分析、版本、导出、模型偏好和工作台体验表，并在缺失时补 `manuscripts.current_branch_id` 与其外键。
- 若旧库已有 `current_branch_id` 但其值没有对应的分支记录，迁移会在建外键前只清空这些不可恢复的悬挂值；有效关联不受影响。
- 完整 V1 基线库执行 V3 不会重建或修改已有表；迁移仅登记为已执行。

## 验证

在 MySQL 8.0 Testcontainers 中执行 `mvn -Dtest=FlywaySchemaGovernanceTest clean test`，共 3 条路径通过：

1. 空库从 V1、V2、V3 完整建库。
2. 完整旧库先 baseline V1 后执行 V2、V3，不重复执行 V1。
3. 仅含 pre-v2 结构、已 baseline V1 的旧库执行 V2、V3 后，确认 `workspace_layouts`、`manuscript_branches`、`current_branch_id` 及当前分支外键存在。

## 本地部署复核

- 已对本地 `ainovel` 库完成 Flyway repair 后重新执行 V3；历史为 V1、V2、V3，均为成功状态。
- 已确认 24 张回补表存在、当前分支外键存在、悬挂分支引用为 0；本次清除了 2 条没有对应分支记录的历史值。
- `ainovel-backend` 健康检查返回 `UP`。浏览器通过统一登录进入工作台，`GET /api/v2/users/me/workspace-layouts` 返回 200，未再出现 `workspace_layouts` 不存在的错误。
