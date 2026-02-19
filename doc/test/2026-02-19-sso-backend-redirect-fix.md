# 2026-02-19 SSO 后端中转修复与回归

## 1. 范围
- 修复 `/login`、`/register` 进入统一登录时的链路：前端不再直连 user-service，改为走 AINovel 后端 `/api/v1/sso/*` 中转。
- 回调页新增 `state` 强校验，防止伪造回调落地 token。

## 2. 执行环境
- 前端：`http://127.0.0.1:11040`
- 后端：`http://127.0.0.1:11041`
- user-service：`http://192.168.1.3:10002`
- 部署脚本：`build_local.ps1`

## 3. 自动化与单元测试
- 前端：`npm run test`（10/10 通过）
- 前端构建：`npm run build`（通过）
- 后端：`mvn test`（32/32 通过）

## 4. Playwright 验收
1. 访问 `http://127.0.0.1:11040/login?next=/workbench`
2. 前端先跳转后端 `/api/v1/sso/login?...`
3. 后端返回 302 到 user-service，页面落在：
   - `.../sso/login?redirect=http://127.0.0.1:11040/sso/callback?next=/workbench&state=<nonce>`
4. 负向验证：手工访问
   - `http://127.0.0.1:11040/sso/callback#access_token=test-token&state=bad-state`
   - 结果：未落地 token，重新回到登录链路（`localStorage.token = null`）

## 5. 证据
- Playwright 截图：`artifacts/test/2026-02-19-sso-redirect.png`

## 6. 结论
- SSO 登录/注册入口已切到后端中转。
- 回调 `state` 校验生效。
- 本地 Vite 代理场景下，回调地址已修复为前端源站 `11040`，不再错误回跳到后端端口。
