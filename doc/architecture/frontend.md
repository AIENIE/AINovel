# 前端架构与页面地图

## 技术与请求层

- React 18、TypeScript、Vite、Tailwind CSS 和 shadcn/ui。
- `frontend/src/App.tsx` 是路由与权限守卫的唯一入口。
- `frontend/src/lib/api-client.ts` 负责 `/api` 请求、令牌注入、错误映射和 v1/v2 DTO。
- `AuthContext` 通过 `GET /api/v1/user/profile` 恢复普通用户会话；管理员令牌独立维护。
- TanStack Query 提供共享请求缓存，工作台内部状态由领域 hooks 管理。

## 页面地图

| 页面 | 路由 | 实现入口 |
| --- | --- | --- |
| 首页、定价 | `/`、`/pricing` | `pages/Index.tsx`、`pages/Pricing.tsx` |
| 登录、注册、回调 | `/login`、`/register`、`/sso/callback` | `pages/auth/*` |
| Dashboard | `/dashboard` | `pages/Dashboard.tsx` |
| 小说与传统新建 | `/novels`、`/novels/create` | `NovelManager.tsx`、`CreateNovel.tsx` |
| 引导创作 | `/novels/quick-create` | `pages/GuidedCreation/GuidedCreationPage.tsx` |
| 世界观 | `/worlds`、`/worlds/create`、`/world-editor` | `WorldManager.tsx`、`CreateWorld.tsx`、`WorldEditor.tsx` |
| 工作台 | `/workbench?tab=&id=` | `pages/Workbench/Workbench.tsx` |
| 素材库 | `/materials` | `pages/Material/MaterialPage.tsx` |
| 设置与帮助 | `/settings`、`/settings/prompt-guide`、`/settings/world-prompts/help` | `pages/Settings/*` |
| 个人中心 | `/profile` | `pages/Profile/ProfilePage.tsx` |
| G2 匿名评审 | `/g2-evaluations/:id/review` | `pages/G2EvaluationReview.tsx` |
| 管理后台 | `/admin/*` | `pages/Admin/*` |

## 响应式边界

- G1 引导创作在桌面显示草稿轨道、候选编辑区和上下文侧栏；移动端将步骤与上下文折叠为紧凑控件。
- 稿件桌面端提供生成、快速/精雕切换和完整右侧工具；移动端只提供大纲、编辑和参考窗格。
- 管理后台桌面使用侧栏，窄屏使用顶部栏和抽屉导航。

页面级操作说明不在本目录重复维护，统一见 [`../../user-doc/README.md`](../../user-doc/README.md)。
