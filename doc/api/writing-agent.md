# 场景自主创作 API

真实用户认证与资源归属沿用稿件接口。基路径 `/v1/manuscripts/{id}/scenes/{sceneId}/agent-runs`。阶段与真实验收结果看[路线图](../roadmap.md)，此处不把可查询材料标为已读。

| 方法及路径 | 行为 |
| --- | --- |
| `POST /preview` | 编译冻结首步、绑定身份、工具范围、必读冲突与指纹；不调用模型 |
| `POST /` | 统一报价确认后受理，返回 runId/operationId；携带 Idempotency-Key |
| `GET /?branchId=...` | 当前分支最近20个运行 |
| `GET /{runId}` | 已保存进度、候选、逐步实际输出及费用；不返回私有续接字段 |
| `GET /{runId}/steps?after=0&limit=20` | 按步骤序号分页；limit 为1–50，返回 steps 和可空 nextAfter |
| `POST /{runId}/pause` | 当前调用结束后暂停，不追加模型调用 |
| `POST /{runId}/resume` | 原输入/预算恢复；等待作者时另携带 answer |
| `POST /{runId}/cancel` | 取消后保留已完成结果和待对账预留 |
| `POST /{runId}/accept` | 主动采纳指定候选；不触发检查或写入世界事实 |

preview/start 请求：branchId、expectedVersion、sourceHtml、可选连续 selection、instruction、intent（EDIT/AUTHOR_QA）、mode（fast/crafted）、languageCheck、plotCheck、maxCostCny、maxProjectCredits、limits，以及预览返回的 contextHash/expectedSelectionRevision。EDIT 必须先保存成功；AUTHOR_QA 可以读取当前草稿但不能修改正文。采纳时原稿须仍等于冻结正文，实际绑定、规划与正文基线在同一事务中锁定复核。

`limits` 为 modelCalls/controlCalls/paidTools/revisions/localTools；默认及硬上限 24/12/12/3/32。语言与剧情检查各最多4次，并共同受12次付费工具上限约束。它们是可能调用上限，不是固定步骤。新步骤 writing_agent.control/refine 初始未配置，不能隐式选模型。

preview 返回首步实际消息 firstInput、contextHash、selectionRevision、h2、binding、工具名、issues 和预算。后续工具读取未知，因此预览不是“全程实际已读”。详情区分 MODEL 提出的工具名与 TOOL 实际回执；安全模型结果含用量、该步实际发送来源和一元执行方式，不包含 reasoning_content/provider_state。

控制请求携带 expectedRevision 与 Idempotency-Key。WAITING_AUTHOR 恢复须 answer；暂停额度不能借 resume 调高，需要新授权。采纳另携带 expectedVersion、branchId、candidateId。正文、来源或配置基线变化时409，旧结果保留。重复处置重放原决定，不重复创建版本。

状态包括 QUEUED/RUNNING/PAUSE_REQUESTED、PAUSED_USER、WAITING_AUTHOR、PAUSED_BUDGET、PAUSED_INPUT、RECOVERY_REQUIRED、COMPLETED、CANCELLED、ACCEPTED、FAILED。通用异步操作结束只表示本次执行已交付状态，不表示正式正文已保存。

预算准备失败尚未外发：`WRITING_AGENT_BUDGET_PREPARE_GRPC_<Code>` 只保留固定 gRPC 枚举；非 gRPC 准备失败为 `WRITING_AGENT_BUDGET_PREPARE_UNAVAILABLE`，授权校验失败为 `WRITING_AGENT_BUDGET_GRANT_INVALID`。这类新任务进入 PAUSED_INPUT，实际额度/次数耗尽才进入 PAUSED_BUDGET；不返回原异常描述。旧任务的原状态与错误码保留，界面可按已知准备失败显示通用待处理状态。模型步骤数表示持久记录数量，不等于供应商外发次数；已保存结果及用量另行展示。

空结果、明确输出额度耗尽、可靠证据支持的思考占满输出、截断与协议错误分别反馈；保留原回执及费用，不自动重试/修补。无法确定模型结果时保持 RECOVERY_REQUIRED，仅查询原请求。
