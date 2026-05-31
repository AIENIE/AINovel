# 第三方组件对齐说明

## 组件清单

AINovel 对接以下基础组件与外部服务：

- MySQL
- Redis
- Qdrant
- user-service / pay-service / ai-service

## 对接原则

- 运行时参数统一来自 `env.txt`，并允许环境变量覆盖。
- 三服务直接使用显式地址（本地默认 `*.localhut.com`），不再通过 Consul 发现。

## 默认目标（可覆盖）

- MySQL: `base.seekerhut.com:23306`
- Redis: `base.seekerhut.com:26379`
- Qdrant: `http://base.seekerhut.com:26333`

## 部署约束

- `build.sh` 只执行 Docker Compose 构建与部署；若 `ainovel-backend` / `ainovel-frontend` 已被其他工作目录的旧容器占用，会先删除冲突容器。
- 依赖服务需由外部环境提前提供，部署脚本不再负责额外创建中间件实例。
- 依赖地址统一来自 `env.txt` 中的 `MYSQL_*`、`REDIS_*`、`QDRANT_*` 配置。
- 三服务直连地址统一来自 `USER_HTTP_ADDR`、`USER_GRPC_ADDR`、`PAY_GRPC_ADDR`、`AI_GRPC_ADDR`。
