# 2026-06-15 deepseek-v4-flash 模型限定

## 目标

AINovel 的写作、润色、质量审校和 Copilot 对话统一使用 ai-service 提供的 `deepseek-v4-flash`（`DeepSeek V4 Flash`），不再允许前端或调用方通过 `modelId` 切换到其它模型。

## 变更

- 后端新增单一模型策略，`POST /api/v1/ai/chat` 和 `POST /api/v1/ai/refine` 会忽略请求中的 `modelId`，统一向 ai-service 传 `deepseek-v4-flash`。
- `GET /api/v1/ai/models` 只返回 `deepseek-v4-flash`。
- v2 模型注册与默认路由只保留 `deepseek-v4-flash`，推荐模型和 fallback 模型一致。
- 前端 `api.ai.getModels()` 只读取 `/api/v1/ai/models`，本地 fallback 也只保留 `deepseek-v4-flash`。
- `app.ai.model` 默认值改为 `deepseek-v4-flash`；旧 OpenAI base URL 默认值清空，避免后台设置产生误导。

## 验证

- `cd backend && mvn -q -Dtest=AiServiceTest,V2ModelControllerTest test`：通过。
- `cd frontend && npm run test -- src/lib/__tests__/mock-api.test.ts`：通过。
- `cd backend && mvn -q test`：通过。
- `cd frontend && npm run test`：5 个测试文件、19 个用例通过。
- `cd frontend && npm run build`：构建成功，仅保留既有 Browserslist 数据过期和 chunk size 警告。
- `sudo ./build.sh`：Docker Compose 构建并重建 `ainovel-frontend`、`ainovel-backend` 成功。
- SSO 使用 `goodboy95` 登录并兑换 access token 后：
  - `GET https://ainovel.localhut.com/api/v1/ai/models`：仅返回 `deepseek-v4-flash`。
  - `GET https://ainovel.localhut.com/api/v2/models`：仅返回 `deepseek-v4-flash`。
  - `POST https://ainovel.localhut.com/api/v1/ai/chat` 携带 `modelId=gpt-4o`：返回 200 和 `OK`，说明旧请求字段不会阻断当前单模型路径。
