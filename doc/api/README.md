# HTTP API 索引

后端控制器使用 `/v1` 或 `/v2` 路径，浏览器经前端反向代理访问时增加 `/api` 前缀。

## v1 业务接口

- [`sso.md`](sso.md)：统一登录中转与会话交换。
- [`user.md`](user.md)：用户资料、积分、兑换码和流水。
- [`guided-creation.md`](guided-creation.md)：G1 引导创作。
- [`story.md`](story.md)、[`manuscript.md`](manuscript.md)、[`world.md`](world.md)：故事、大纲、正文和世界观。
- [`material.md`](material.md)：素材、导入、检索、审核、合并和引用。
- [`ai.md`](ai.md)、[`settings.md`](settings.md)：AI、提示词与设置。
- [`g2-evaluations.md`](g2-evaluations.md)：G2 投稿与匿名评审。
- [`admin.md`](admin.md)：管理员认证、运营和治理。

## v2 增强接口

- [`v2-context.md`](v2-context.md)：Lorebook、图谱、实体和上下文预览。
- [`v2-style.md`](v2-style.md)：风格画像与角色声音。
- [`v2-analysis.md`](v2-analysis.md)、[`v2-quality.md`](v2-quality.md)：分析、文本质量、drift 和剧情质量。
- [`v2-version.md`](v2-version.md)、[`v2-export.md`](v2-export.md)：版本分支与导出。
- [`v2-models.md`](v2-models.md)、[`v2-workspace.md`](v2-workspace.md)：模型偏好、布局、会话、目标和快捷键。

跨服务正式契约不在本目录复制，AINovel 的消费边界见 [`../architecture/integrations.md`](../architecture/integrations.md)。
