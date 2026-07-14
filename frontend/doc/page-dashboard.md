# Dashboard 与小说入口

- `/dashboard` 通过 `GET /api/v1/user/summary` 显示小说、世界、字数和设定统计。
- 页面将用户引导到 `/novels` 或 `/worlds`。
- `/novels` 支持查看和删除故事卡；`/novels/create` 支持普通创建与一键构思。选择作品后使用 `/workbench?id=<storyId>` 进入工作台。
