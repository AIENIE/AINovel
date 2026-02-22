# AINovel 上手与部署指南

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

部署成功后：

- `https://ainovel.seekerhut.com`
- `https://ainovel.seekerhut.com/api/v3/api-docs`

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
- 检查 `CONSUL_*` 与 `USER_GRPC_*` 是否可连通。
- 检查 token 中 `uid/sid` 是否存在。

3. 通用积分兑换失败
- 检查 `PAY_GRPC_SERVICE_NAME` 与 Consul 是否能解析实例。
- 检查 `EXTERNAL_PROJECT_KEY` 是否与 pay-service 项目一致。

4. `curl` 本地域名偶发 `SSL connect` / `Connection established` 异常
- 通常是命令走了系统代理；请加 `--noproxy '*'`，例如：
  - `curl --noproxy '*' -k https://ainovel.seekerhut.com/api/v3/api-docs`
