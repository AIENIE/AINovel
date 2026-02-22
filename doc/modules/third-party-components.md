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

- MySQL: `192.168.1.4:3306`
- Redis: `192.168.1.4:6379`
- Qdrant: `http://192.168.1.4:6333`
- Consul: `http://192.168.1.4:60000`

## 本机兜底

当远端依赖不可用且 `DEPS_AUTO_BOOTSTRAP=true`，`build.sh` 会自动启动：

- `backend/deploy/deps-compose.yml`

默认本机映射端口：

- MySQL: `3308`
- Redis: `6381`
- Qdrant: `6335`

