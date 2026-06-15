# 2026-06-15 SSO 回调登录修复验收

## 问题

本地测试服 `https://ainovel.localhut.com/login?next=/workbench` 完成 userservice 登录后会回到登录页，无法进入工作台。

## 根因

- `/sso/callback` 的 React effect 会在 `acceptToken()` 更新认证状态后再次执行，第二次执行时一次性 state 已被删除，导致误判为 state 校验失败。
- 前端用 `URL.toString()` 重建 `/api/v1/sso/session` 的 `redirect` 参数时，把 `next=/workbench` 变成 `next=%2Fworkbench`；userservice 的授权码按原始 redirect 字符串校验，因此返回 `INVALID_SSO_CODE`。

## 修复

- 新增单次 SSO callback processor，防止同一 callback URL 被 effect 重入重复处理。
- 重建 token-exchange redirect 时删除 `code/state`，但保留 `next` 路径斜杠，确保与授权码绑定的 callback URL 一致。
- 稳定 `AuthProvider` 暴露的 `acceptToken/logout/refreshProfile` 函数引用，降低认证状态更新造成的 effect 依赖抖动。

## 验收

- `cd frontend && npm run test -- src/lib/__tests__/sso-callback.test.ts src/lib/__tests__/sso.test.ts src/lib/__tests__/mock-api.test.ts`：12 个测试通过。
- `cd frontend && npm run build`：构建成功，仅保留既有 Browserslist/chunk size 警告。
- `sudo ./build.sh`：Docker Compose 构建并重建 `ainovel-frontend`、`ainovel-backend` 成功。
- Headless Chrome 访问 `https://ainovel.localhut.com/login?next=/workbench`，使用 `goodboy95` 测试账号登录后进入 `https://ainovel.localhut.com/workbench`。
- 浏览器网络验证：`POST /api/v1/sso/session` 单次返回 200，`GET /api/v1/user/profile` 返回 200，`localStorage.token` 已写入。
