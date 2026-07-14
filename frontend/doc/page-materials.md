# 素材库页

- 路由：`/materials`；实现：`src/pages/Material/MaterialPage.tsx`。
- 标签：素材列表、手动创建、批量导入、审核台。
- 列表调用 `GET /api/v1/materials`，当前仅提供标题/标签筛选和菜单占位，不提供普通用户编辑、删除、合并或引用历史操作。
- 创建调用 `POST /api/v1/materials`；TXT 导入调用上传接口并轮询任务状态。
- 审核台调用待审、通过和驳回接口。管理员后台还提供重复候选、合并和引用治理。
