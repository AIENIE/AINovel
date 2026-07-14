# 设置页

- 路由：`/settings?tab=&storyId=`；实现：`src/pages/Settings/Settings.tsx`。
- 标签：`workspace` 工作区提示词、`world` 世界观提示词、`style` 风格画像、`models` 模型偏好、`experience` 工作台体验。
- 不提供本地 API Key、Base URL 或模型池管理；这些能力归属外部服务与运维配置。
- 风格、模型和体验使用 `/api/v2/*`；两类提示词继续使用 `/api/v1/prompt-templates*` 与 `/api/v1/world-prompts*`。
