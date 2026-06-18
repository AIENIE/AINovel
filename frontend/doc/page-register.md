# 注册页（Register）

- **路由/文件**：`/register`
- **对应设计稿文件**：`src/pages/auth/Register.tsx`
- **布局**：SSO 跳转页；保留回跳目标并引导到统一用户服务注册。
- **交互流程**：
  - 进入 `/register` 后生成一次性 `state`，请求后端 `GET /api/v1/sso/register?next=...&state=...`。
  - 后端 `302` 到 user-service 注册页；注册/登录完成后回跳 `/sso/callback`。
  - 回调页校验 `state` 后调用 `/api/v1/sso/session` 兑换 token，再进入原目标页面。
  - 失败显示通用注册/登录失败提示，并允许重新发起 SSO。
- **依赖/权限**：公开接口，无需 Token。
- **待完善**：
  - 注册失败原因由 user-service 展示；
  - 补充 state 过期和重复回调的用户引导；
  - 补充 user-service 不可用时的降级文案。

## 开发对接指南 (Mock vs Real)

### 1. 注册逻辑
- **当前实现**：`src/pages/auth/Register.tsx` 通过后端 SSO 入口跳转到 user-service，不再提交本项目本地注册表单。
- **真实对接**：
  - 使用 `GET /api/v1/sso/register` 发起跳转，回调后通过 `/api/v1/sso/session` 兑换本项目 token。
  - 用户名/邮箱查重、密码规则和验证码由 user-service 注册页处理。

### 2. 表单验证
- **当前实现**：本项目注册页不再承载账号表单。
- **真实对接**：
  - 建议引入 `zod` 或后端验证规则，对密码强度（大小写、数字、符号）进行实时校验。
