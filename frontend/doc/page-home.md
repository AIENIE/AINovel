# 首页

- 路由：`/`；实现：`src/pages/Index.tsx`。
- 未登录用户可发起统一登录或注册；已登录用户进入 Dashboard。
- 首页不直接请求业务 API。`AuthContext` 在应用启动时通过 `GET /api/v1/user/profile` 恢复有效登录态。
- “查看演示”当前没有绑定导航或业务动作，不应作为可用功能描述。
