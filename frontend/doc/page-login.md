# 登录页

- 路由：`/login`；实现：`src/pages/auth/Login.tsx`。
- 读取站内 `next` 参数，生成一次性 `state` 后跳转 `GET /api/v1/sso/login`。
- 已有本地 token 时直接跳转目标；回调页校验 `state` 后调用 `POST /api/v1/sso/session`，再由 `AuthContext.acceptToken` 拉取 profile。
- 页面不提供 AINovel 本地用户名密码登录。
