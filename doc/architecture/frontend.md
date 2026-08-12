# 前端架构与页面地图

## 技术与请求层

- React 18、TypeScript、Vite、Tailwind CSS 和 shadcn/ui。
- `frontend/src/App.tsx` 是路由与权限守卫的唯一入口。
- `frontend/src/lib/api-client.ts` 负责 `/api` 请求、令牌注入、错误映射和 v1/v2 DTO。
- `AuthContext` 通过 `GET /api/v1/user/profile` 恢复普通用户会话；管理后台使用同源 HttpOnly Cookie，不在 JavaScript 或浏览器存储中维护管理员令牌，高风险写入按 428 挑战完成 TOTP operation proof 后重试一次。
- TanStack Query 提供共享请求缓存，工作台内部状态由领域 hooks 管理。
- `AiOperationProgressPanel` 与 `ai-operation-store` 跟踪当前长任务；SSE 断开后自动轮询，刷新页面后从 `sessionStorage` 恢复。面板统一展示当前步骤、已完成/剩余步骤和当前步骤输出 token。

## 国际化（i18n）

- 支持 `zh-CN`（默认/fallback）、`zh-TW`、`en`；语言选择写入 `localStorage`（key `aienie.user.locale.v1`），切换立即生效，不重新加载页面。
- 实现位于 `frontend/src/i18n/index.ts`（i18next + react-i18next）：`keySeparator: false`、`initImmediate: false`、`useSuspense: false`，不使用浏览器语言检测与 Accept-Language。
- 入口隔离：`main.tsx` 按路径动态选择 `bootstrap/UserEntry`（用户，`I18nextProvider` + 本地化 fallback）或 `bootstrap/AdminEntry`（管理员，`document.documentElement.lang = "zh-CN"`，不加载 i18n）。生产构建中 `AdminEntry` chunk 不含任何 locale 资源。
- 业务文案统一 key 化：UI 组件用 `useTranslation`，纯函数/hooks 用 `import { t } from "@/i18n"`；locale 文件位于 `frontend/src/i18n/locales/{zh-CN,zh-TW,en}.ts`，三语键集严格一致。
- 错误文案：`lib/error-messages.ts` 按 HTTP 状态映射稳定错误类别（`localizedErrorMessage`），后端原始 message 从不直接展示；成功/失败提示走 `utils/toast.ts` 的 `showSuccess`/`showError`。
- 复数约定：zh 语言需显式提供 `_other` 变体；token 计数等数字展示使用预格式化变量 `formatted`（`count` 会走 Intl 千分位导致不匹配）。
- 边界：用户创作内容、AI 生成内容/提示词、用户名、品牌、模型名不翻译；`pages/Admin/**`、`AdminLayout.tsx` 固定简体中文；`api-client.ts` 的数据规范化兜底值与 428 二次验证相关消息保持字面量（该模块被 admin 使用，不能引入 i18n 依赖）。
- 批量合并新增键用 `frontend/scripts/merge_i18n.py <additions.json>`（结构 `{"zh-CN": {key: val}, ...}`），已存在键不覆盖。

## 页面地图

页面的功能树、职责边界、浏览器审查范围和已知限制统一维护在 [page-function-tree.md](page-function-tree.md)。本节只保留路由与实现入口。

| 页面 | 路由 | 实现入口 |
| --- | --- | --- |
| 首页、定价 | `/`、`/pricing` | `pages/Index.tsx`、`pages/Pricing.tsx` |
| 登录、注册、回调 | `/login`、`/register`、`/sso/callback` | `pages/auth/*` |
| Dashboard | `/dashboard` | `pages/Dashboard.tsx` |
| 小说与传统新建 | `/novels`、`/novels/create` | `NovelManager.tsx`、`CreateNovel.tsx` |
| 引导创作 | `/novels/quick-create` | `pages/GuidedCreation/GuidedCreationPage.tsx` |
| 世界观 | `/worlds`、`/worlds/create`、`/world-editor` | `WorldManager.tsx`、`CreateWorld.tsx`、`WorldEditor.tsx` |
| 工作台 | `/workbench?tab=&storyId=&outlineId=&manuscriptId=&sceneId=` | `pages/Workbench/Workbench.tsx` |
| 素材库 | `/materials` | `pages/Material/MaterialPage.tsx` |
| 设置与帮助 | `/settings`、`/settings/prompt-guide`、`/settings/world-prompts/help` | `pages/Settings/*` |
| 个人中心 | `/profile` | `pages/Profile/ProfilePage.tsx` |
| G2 匿名评审 | `/g2-evaluations/:id/review` | `pages/G2EvaluationReview.tsx` |
| 管理后台 | `/admin/*` | `pages/Admin/*` |

## 响应式边界

- G1 引导创作在桌面显示草稿轨道、候选编辑区和上下文侧栏；移动端将步骤与上下文折叠为紧凑控件。
- 稿件桌面端提供生成、快速/精雕切换和完整右侧工具；移动端使用大纲、编辑和参考窗格，并提供保存、快速/精雕生成和 G2 投稿入口。
- 管理后台桌面使用侧栏，窄屏使用顶部栏和抽屉导航。

面向创作者和管理员的逐步操作说明见 [`../../user-doc/README.md`](../../user-doc/README.md)；工程侧页面职责和已知限制见 [page-function-tree.md](page-function-tree.md)。
