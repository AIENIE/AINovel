# AINovel 上手与部署指南（2026-02-25）

## 1. 环境要求

- Node.js 20+
- JDK 17+
- Maven 3.9+
- Docker + Docker Compose
- Linux 环境建议可用 `sudo`

## 2. 配置文件

项目根目录使用 `env.txt` 作为默认配置源，支持环境变量覆盖。

重点配置项：

- 依赖：`MYSQL_*`、`REDIS_*`、`QDRANT_*`
- 服务发现：`CONSUL_*`
- 三服务名：
  - `USER_GRPC_SERVICE_NAME=aienie-userservice-grpc`
  - `PAY_GRPC_SERVICE_NAME=aienie-payservice-grpc`
  - `AI_GRPC_SERVICE_NAME=aienie-aiservice-grpc`
- 三服务 fallback：`USER_HTTP_ADDR`、`USER_GRPC_ADDR`、`PAY_GRPC_ADDR`、`AI_GRPC_ADDR`
- SSO：`SSO_CALLBACK_ORIGIN`、`VITE_SSO_ENTRY_BASE_URL`
- 外部安全配置（必填）：
  - `EXTERNAL_AI_HMAC_CALLER`
  - `EXTERNAL_AI_HMAC_SECRET`
  - `EXTERNAL_USER_INTERNAL_GRPC_TOKEN`
  - `EXTERNAL_PAY_SERVICE_JWT`（或 `EXTERNAL_PAY_JWT_SECRET` + claim 配置自动生成）
- 时间与安全：
  - `APP_TIME_ZONE=Asia/Shanghai`
  - `EXTERNAL_SECURITY_FAIL_FAST=true`
  - `EXTERNAL_GRPC_TLS_ENABLED` / `EXTERNAL_GRPC_PLAINTEXT_ENABLED`
  - `JWT_SECRET`（至少 32 字节，不可占位值）
- hosts 行为：
  - `PIN_UPSTREAM_SERVICES_TO_LOCALHOST=false`（默认不强制把 userservice/payservice/aiservice 写到 `127.0.0.1`）

## 3. 一键部署（推荐）

```bash
sudo bash build.sh
```

脚本会执行：

1. 读取 `env.txt`
2. 检查远端依赖连通性（MySQL/Redis/Qdrant）
3. 远端不可用时（`DEPS_AUTO_BOOTSTRAP=true`）自动拉起本机依赖容器
4. 构建前端/后端
5. 通过 `docker-compose.yml` 启动前后端容器
6. 配置 hosts + nginx 反向代理（`ainovel.seekerhut.com` / `ainovel.aienie.com`）
7. 校验 pay-service 服务 JWT；若占位/过期/claim 不合法且配置了 `EXTERNAL_PAY_JWT_SECRET`，将自动重签发

部署成功后：

- `https://ainovel.seekerhut.com`
- `https://ainovel.seekerhut.com/api/v3/api-docs`（若 `SPRINGDOC_API_DOCS_ENABLED=true`）

## 4. 本机依赖容器（可单独启动）

```bash
docker compose -f backend/deploy/deps-compose.yml up -d
```

默认映射端口：

- MySQL: `3308`
- Redis: `6381`
- Qdrant: `6335`

> 如果你不是通过 `build.sh`，而是手动执行 `docker compose`，请先加载配置：
>
> ```bash
> set -a
> source env.txt
> set +a
> ```
>
> 否则容器会回退到 compose 默认值，常见问题是 `JWT_SECRET` 不一致导致 SSO token 全部 `403`。

## 5. 测试命令

后端测试：

```bash
cd backend
mvn -q test
```

前端测试与构建：

```bash
cd frontend
npm ci --legacy-peer-deps
npm run test
npm run build
```

## 6. 常见问题

1. 域名打不开
- 先检查 `/etc/hosts` 是否有 `ainovel.seekerhut.com`。
- 再检查 `nginx -t` 与服务状态。

2. SSO 登录后 403
- 检查 token 中 `uid/sid` 是否存在且会话未被刷新（同账号异地重新登录会导致旧 sid 失效）。
- 检查 `EXTERNAL_USER_INTERNAL_GRPC_TOKEN` 与 user-service 一致。
- 检查 `USER_GRPC_ADDR` 回退地址是否可连通。

3. 通用积分兑换失败
- 检查 `PAY_GRPC_SERVICE_NAME` 与 Consul 是否能解析实例。
- 检查 `EXTERNAL_PROJECT_KEY` 是否与 pay-service 项目一致。
- 检查 `EXTERNAL_PAY_SERVICE_JWT` claim 是否包含：
  - `role=SERVICE`
  - `iss=aienie-services`
  - `aud` 包含 `aienie-payservice-grpc`
  - `scopes` 包含 `billing.read,billing.write`

4. `curl` 本地域名偶发 `SSL connect` / `Connection established` 异常
- 通常是命令走了系统代理；请加 `--noproxy '*'`，例如：
  - `curl --noproxy '*' -k https://ainovel.seekerhut.com/api/v3/api-docs`
