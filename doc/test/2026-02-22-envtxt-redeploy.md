# 2026-02-22 env.txt 配置化改造与重部署验收

## 1. 变更目标

1. 将项目环境文件统一为 `env.txt`（不再读取 `.env`）。
2. `build.sh`、`build_prod.sh`、`build_local.ps1` 全部支持加载 `env.txt`。
3. 将 `ai-service`、`pay-service`、`user-service`、SSO 跳转入口/回调 origin 改为环境变量配置。

## 2. 执行步骤

1. 运行前端单测：
   - `npm run test -- src/lib/__tests__/sso.test.ts`
2. 运行后端 SSO 相关单测：
   - `mvn -q "-Dtest=SsoControllerTest,SsoEntryServiceTest" test`
3. 执行本地重部署：
   - `powershell -ExecutionPolicy Bypass -File .\build_local.ps1`
4. 验收：
   - `http://127.0.0.1:11040` 可访问（200）。
   - `http://127.0.0.1:11041/api/v3/api-docs` 可访问（200）。
   - `GET /api/v1/sso/login` 返回 302，`Location` 包含 `state` 与 `redirect` 参数。
   - Playwright 打开首页并点击“登录”，成功跳转至 user-service SSO URL。

## 3. 验收结果

- 本地部署成功，前后端服务可用。
- SSO 跳转链路正常生成，已走后端 `/api/v1/sso/login` 302。
- 外部 SSO 页面在当前环境返回 `502 Bad Gateway`，属于外部 user-service 可用性问题，不是本次配置化改造引入。

## 4. 证据

- 截图：`artifacts/test/2026-02-22-envtxt-sso-redirect-502.png`
- 启动日志：
  - `tmp/local-run/backend.err.log`
  - `tmp/local-run/frontend.err.log`
