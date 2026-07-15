# 页面地图与导航关系

| 页面 | 路由 | 实现 | 说明 |
| --- | --- | --- | --- |
| 首页 | `/` | `pages/Index.tsx` | SSO 入口和产品介绍 |
| 登录/注册/回调 | `/login`、`/register`、`/sso/callback` | `pages/auth/*` | 统一登录跳转与授权码兑换 |
| Dashboard | `/dashboard` | `pages/Dashboard.tsx` | 作品和世界观入口 |
| 小说 | `/novels`、`/novels/create` | `NovelManager.tsx`、`CreateNovel.tsx` | 故事卡管理和传统创建 |
| 引导创作 | `/novels/quick-create?run=` | `pages/GuidedCreation/*` | Step0-4 三候选、草稿恢复、自动推进和完成跳转 |
| 世界观 | `/worlds`、`/worlds/create`、`/world-editor` | `WorldManager.tsx`、`CreateWorld.tsx`、`WorldEditor.tsx` | 世界草稿、编辑和发布 |
| 工作台 | `/workbench?tab=&id=` | `pages/Workbench/Workbench.tsx` | 9 标签创作中枢 |
| 素材库 | `/materials` | `pages/Material/MaterialPage.tsx` | 列表、创建、导入和审核 |
| 设置 | `/settings` | `pages/Settings/Settings.tsx` | 提示词、风格、模型和体验设置 |
| 个人中心 | `/profile` | `pages/Profile/ProfilePage.tsx` | 资料、积分和兑换 |
| 管理后台 | `/admin/*` | `pages/Admin/*` | 独立管理员认证与运营功能 |

普通用户布局由 `AppLayout` 提供侧栏和账户菜单；管理员布局由 `AdminLayout` 提供运营导航。未知地址进入 `NotFound`。
