# AINovel

AINovel 是一个前后端分离的 AI 小说创作业务项目。

- 前端：React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端：Spring Boot 3 + JPA + Redis + gRPC 客户端
- 对外端口：前端 `11040`，后端 `11041`
- 测试域名：`ainovel.seekerhut.com`
- 正式域名：`ainovel.aienie.com`

## 当前能力概览

- 统一登录（SSO）：仅保留 `/api/v1/sso/*` 中转，不提供本地登录注册表单接口。
- 用户首次登录：自动创建本地用户映射与项目积分账户（最小初始化）。
- 项目专属积分：在 AINovel 本地账本管理（签到、兑换码、AI 扣费、管理员加分）。
- 通用积分：通过 pay-service 查询并支持 1:1 兑换为项目积分。
- 积分记录：
  - 用户侧：项目积分流水 + 通用积分兑换历史（含兑换前后余额）。
  - 管理侧：兑换订单列表 + 全站项目积分流水。

## 快速启动

1. 准备 `env.txt`（仓库已提供默认模板，可直接改值）。
2. Linux 一键部署（构建前后端 + docker compose 启动 + Nginx/hosts 代理）：

```bash
sudo bash build.sh
```

3. 打开：
- `https://ainovel.seekerhut.com`
- `https://ainovel.seekerhut.com/api/v3/api-docs`

说明：
- `build.sh` 会读取 `env.txt`，环境变量可覆盖。
- 若手动执行 `docker compose`，先执行 `set -a; source env.txt; set +a`，避免容器使用到错误默认值（如 `JWT_SECRET` 不一致导致 SSO token 全部 403）。
- 当远端 `MySQL/Redis/Qdrant` 不可用且 `DEPS_AUTO_BOOTSTRAP=true` 时，会自动拉起本机依赖容器（`backend/deploy/deps-compose.yml`）。

## 关键配置

统一写在 `env.txt`（可被 shell 环境变量覆盖）：

- 依赖服务：`MYSQL_*`、`REDIS_*`、`QDRANT_*`
- Consul：`CONSUL_*`（默认对接 `192.168.1.4:60000`）
- 三服务名：
  - `USER_GRPC_SERVICE_NAME=aienie-userservice-grpc`
  - `PAY_GRPC_SERVICE_NAME=aienie-payservice-grpc`
  - `AI_GRPC_SERVICE_NAME=aienie-aiservice-grpc`
- 三服务 fallback：`USER_HTTP_ADDR`、`USER_GRPC_ADDR`、`PAY_GRPC_ADDR`、`AI_GRPC_ADDR`（默认 `*.seekerhut.com`）
- SSO：`SSO_CALLBACK_ORIGIN`、`VITE_SSO_ENTRY_BASE_URL`

## 目录说明

- `frontend/`：前端代码
- `backend/`：后端代码
- `backend/sql/schema.sql`：数据库结构参考脚本
- `backend/deploy/deps-compose.yml`：本机依赖容器编排（兜底用）
- `doc/`：项目文档、API 文档、测试记录
- `design-doc/`：设计与规划文档
- `build.sh` / `build_prod.sh` / `build_local.ps1`：部署脚本
- `docker-compose.yml`：AINovel 前后端容器编排

## 验证建议

- 后端测试：`cd backend && mvn -q test`
- 前端测试：`cd frontend && npm ci && npm run test`
- 前端构建：`cd frontend && npm run build`
- 本地域名接口验证（避免走代理）：`curl --noproxy '*' -k https://ainovel.seekerhut.com/api/v3/api-docs`
