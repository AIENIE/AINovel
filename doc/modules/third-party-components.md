# 第三方组件对齐说明

## 组件清单

AINovel 对接以下基础组件与外部服务：

- MySQL
- Redis
- Qdrant
- Consul
- user-service / pay-service / ai-service

## 对接原则

- 运行时参数统一来自 `env.txt`，并允许环境变量覆盖。
- 服务发现优先通过 Consul（`CONSUL_HOST` / `CONSUL_PORT`）。
- 三服务使用固定 Consul 服务名：
  - `aienie-userservice-grpc`
  - `aienie-payservice-grpc`
  - `aienie-aiservice-grpc`
- fallback 地址优先使用域名（`*.seekerhut.com`），不在代码硬编码 IP。

## 默认目标（可覆盖）

- MySQL: `192.168.5.208:3306`
- Redis: `192.168.5.208:6379`
- Qdrant: `http://192.168.5.208:6333`
- Consul: `http://192.168.5.208:60000`

## 部署约束

- `build.sh` 现在只校验 MySQL / Redis / Qdrant 连通性。
- 依赖服务需由外部环境提前提供，部署脚本不再负责额外创建中间件实例。
- 依赖地址统一来自 `env.txt` 中的 `MYSQL_*`、`REDIS_*`、`QDRANT_*` 配置。
