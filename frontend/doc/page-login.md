# 登录页（Login）

- **路由/文件**：`/login`
- **对应设计稿文件**：`src/pages/auth/Login.tsx`
- **布局**：SSO 跳转页；保留回跳目标并引导到统一用户服务登录。
- **交互流程**：
  - 进入 `/login` 后生成一次性 `state`，请求后端 `GET /api/v1/sso/login?next=...&state=...`。
  - 后端 `302` 到 user-service 登录页；user-service 登录后回跳 `/sso/callback`。
  - 回调页校验 `state` 后调用 `/api/v1/sso/session` 兑换 token，写入 `localStorage.token` 并恢复 `AuthContext`。
  - 失败：展示登录失败提示，并允许用户重新发起 SSO。
  - 首次加载若已认证则自动重定向 `/workbench`（useEffect）。
- **依赖/权限**：无需 Token；其他页面通过 `AuthContext` 共享登录态。
- **待完善**：
  - 增加 SSO 异常状态的细分提示；
  - 明确 state 过期和重复回调的用户引导；
  - 补充 user-service 不可用时的降级文案。

## 开发对接指南 (Mock vs Real)

### 1. 认证接口
- **当前实现**：`src/pages/auth/Login.tsx` 通过后端 SSO 入口跳转到 user-service，不再提交本项目本地账密登录。
- **真实对接**：
  - 使用 `GET /api/v1/sso/login` 发起跳转，回调后通过 `/api/v1/sso/session` 兑换本项目 token。
  - **安全注意**：生产环境建议使用 HttpOnly Cookie 存储 Token，或者在前端使用更安全的 Token 存储策略。如果使用 Header 传输，需确保 `api.ts` 的拦截器中正确注入 `Authorization: Bearer <token>`。

### 2. 错误处理
- **当前实现**：SSO 跳转失败、state 校验失败、session 兑换失败会落到登录失败提示。
- **真实对接**：
  - 需细分错误码：账号不存在、密码错误、账户被锁、验证码错误等，并在 UI 上给予具体反馈。
