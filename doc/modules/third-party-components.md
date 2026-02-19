# 第三方组件对齐说明

## 对齐基线来源

以下基线来自 `user-service`、`fireflyChat`、`fireflychat_studio` 当前配置：

- MySQL
- Redis
- Qdrant
- Consul

## AINovel 对齐结果

- 服务本体配置（`docker-compose.yml`、`docker-compose.windows.yml`、`backend/src/main/resources/application.yml`）已统一包含上述四类组件对应环境变量。
- 默认连接统一为 `127.0.0.1` + 默认端口 `+2`：
  - MySQL：`3308`
  - Redis：`6381`
  - Qdrant：`6335`
  - Consul：`8502`

## 依赖部署脚本

- `deploy/docker-compose.yml`：独立编排 `MySQL + Redis + Qdrant + Consul`
- `deploy/build.sh`：独立启动/重启依赖容器

本项目当前无额外独立第三方组件（例如 Neo4j），因此无需新增额外 deploy 子脚本。
