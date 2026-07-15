# 前端当前实现说明

## 技术与请求层

- React 18、TypeScript、Vite、Tailwind CSS 和 shadcn/ui。
- 路由由 `react-router-dom` 管理；普通用户使用 `ProtectedRoute`，管理员使用 `AdminRoute`。
- `frontend/src/lib/api-client.ts` 负责请求、令牌注入、DTO 映射和 v1/v2 API 调用。
- 普通用户登录态由 `AuthContext` 调用 `/api/v1/user/profile` 恢复；管理员令牌独立维护。

## 页面范围

- 公开页：首页、SSO 登录/注册跳转、回调、定价和 404。
- 创作者页：Dashboard、小说、引导创作、世界观、工作台、素材、设置和个人中心。
- 管理员页：运营、用户、素材治理、资产、质量、积分、运维观测和维护模式。

## 文档边界

- 本目录描述页面与实现入口。
- API 细节见 `doc/api/`，用户操作步骤见根目录 `user-doc/`。
- 页面中不再保留 Mock-vs-Real 对接说明；当前文档只描述仓库已接入的行为。
