# 第三方组件对齐说明

## 组件清单

AINovel 对接以下基础组件与外部服务：

- MySQL
- Redis
- Qdrant
- user-service / pay-service / ai-service

## 对接原则

- 运行时参数统一来自 `env.txt`，并允许环境变量覆盖；Compose 先用它做部署插值，后端容器启动时再加载完整应用配置。
- 三服务直接使用显式地址（本地默认 `*.localhut.com`），不再通过 Consul 发现。

## 默认目标（可覆盖）

- MySQL: `base.seekerhut.com:23306`
- Redis: `base.seekerhut.com:26379`
- Qdrant: `http://base.seekerhut.com:26333`

## 部署约束

- `build.sh` 只执行 Docker Compose 构建与部署；存在 `env.txt` 时通过 Compose `--env-file` 读取插值变量；若 `ainovel-backend` / `ainovel-frontend` 已被其他工作目录的旧容器占用，会先删除冲突容器。
- 依赖服务需由外部环境提前提供，部署脚本不再负责额外创建中间件实例。
- 依赖地址统一来自后端容器启动时加载的 `env.txt` 中 `MYSQL_*`、`REDIS_*`、`QDRANT_*` 配置。
- 三服务直连地址统一来自后端容器启动时加载的 `USER_HTTP_ADDR`、`USER_GRPC_ADDR`、`PAY_GRPC_ADDR`、`AI_GRPC_ADDR`。
- 后端容器监听 `BACKEND_PORT` 并通过 compose 服务名暴露；前端容器按内部网络访问后端 API。
- 本地部署默认关闭 Hibernate 自动 DDL（`SPRING_JPA_HIBERNATE_DDL_AUTO=none`），避免启动期通过远程 MySQL 元数据扫描阻塞；结构变更统一新增 `backend/src/main/resources/db/migration/V{n}__*.sql`，`backend/sql/schema.sql` 仅保留历史入口说明。
