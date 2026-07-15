# G1 引导创作模块

## 当前边界

G1-A P0 覆盖 `/novels/quick-create` 的 Step0-4：一句话起点、故事方向、世界设定、角色阵容和章节大纲。Step5 分章正文草稿未实现，统一记录在 `design-doc/v3-proposals/PENDING-PHASES.md`。

每个生成步骤必须返回恰好三个候选。角色候选每套包含 3-5 名角色；大纲候选严格匹配用户选择的 3-12 章，每章包含 2-4 个场景。世界设定允许跳过。

## 状态与持久化

- `creation_workflow_runs` 保存用户、当前步骤、已确认上下文、标准实体引用、自动模式和乐观版本号。
- `async_jobs` 每个运行/步骤仅一条任务，以幂等键约束重复入队，记录进度、尝试次数、用量、错误和租约。
- 任务状态为 `QUEUED -> RUNNING -> CALLING_AI -> SUCCEEDED/FAILED`。
- 启动恢复会重新入队尚未调用 AI 的任务；停在 `CALLING_AI` 的任务改为 `RECOVERY_REQUIRED`，必须由用户明确重试，避免不确定调用被静默重放。
- 周期协调器补偿自动模式中“候选已生成但未确认”或“已进入下一步但未入队”的中间状态。

## 实体落库

用户确认后按事务写入现有标准实体：

- 故事方向 -> `Story`
- 世界设定 -> `World`，并关联 `Story.worldId`
- 角色阵容 -> 3-5 个 `CharacterCard`
- 章节大纲 -> `Outline`，章节和场景写入规范化 `contentJson`

向导不创建第二套故事模型；现有 `/v1/conception`、传统新建和 Workbench 均保持可用。

## AI 与计费

候选提示词按步骤版本化，当前版本前缀为 `g1.quick-book.*.v1`。AI 调用通过现有 `AiService` 和实际用量计费，使用 `AiUsageContext("G1_WORKFLOW", jobId, step)` 写入项目积分账本；同一任务重试复用计费幂等键。若首次输出无法通过 JSON 解析或严格结构校验，系统最多追加一次 `step:json_repair` 结构修复调用，其用量独立记录；修复结果仍不合格时任务失败。
