# AINovel

AINovel 是一个前后端分离的 AI 小说创作业务项目。

- 前端：React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui
- 后端：Java 25 + Spring Boot 3 + JPA + Redis + gRPC 客户端
- 对外端口：前端 `11040`，后端 `11041`
- 本地域名：`ainovel.localhut.com`
- 正式域名：`ainovel.aienie.com`

## 当前能力概览

- 统一登录（SSO）：仅保留 `/api/v1/sso/*` 中转，不提供本地登录注册表单接口。
- 用户首次登录：自动创建本地用户映射与项目积分账户（最小初始化）。
- 项目专属积分：在 AINovel 本地账本管理（签到、兑换码、AI 扣费、管理员加分）。
- 通用积分：通过 pay-service 查询并支持 1:1 兑换为项目积分。
- 积分记录：
  - 用户侧：项目积分流水 + 通用积分兑换历史（含兑换前后余额）。
  - 管理侧：兑换订单列表 + 全站项目积分流水。
- 素材闭环：支持素材混合检索、查重候选、人工合并和稿件引用历史查询。
- 世界观闭环：发布预检后按模块 AI 生成缺失字段；生成失败记录明确状态与错误，不写入占位内容。
- 稿件精雕：桌面写作页可在快速与精雕模式间切换；精雕模式会注入分类型反套路约束和轮换叙事目标，保持单候选起草。
- v2 创作增强：Lorebook、知识图谱、质量分析、版本分支、导出、风格画像、模型偏好和工作台体验设置均已提供持久化 API 与前端入口。
- 提示词设置：工作区提示词和世界观提示词支持读取、保存、恢复默认和 metadata 驱动帮助页。
- 管理看板：总用户、今日新增、待审素材、项目积分消耗与轻量 API 错误率统计。

## 快速启动

1. 准备 `env.txt`（仓库已提供默认模板，可直接改值）。
2. Linux Docker Compose 部署：

```bash
bash build.sh
```

3. 打开：
- `https://ainovel.localhut.com`
- `https://ainovel.localhut.com/api/v1/sso/login?next=/workbench&state=smoke`

说明：
- `build.sh` 仅执行本项目 Docker Compose 构建与部署。
- 若发现 `ainovel-backend` / `ainovel-frontend` 已被其他工作目录的旧容器占用，脚本会先删除冲突容器，再部署当前仓库。
- 若根目录存在 `env.txt`，脚本会通过 `docker compose --env-file env.txt` 加载。
- Docker 部署中后端使用 bridge 网络监听 `BACKEND_PORT`，前端服务通过 compose 网络与后端服务名访问 `/api`。
- 部署前需确保 `MySQL/Redis/Qdrant` 已按 `env.txt` 配置提前就绪；依赖预检、hosts、Nginx、HTTPS 证书、健康检查和 E2E 不由 `build.sh` 处理。

## backend 宿主机调试

以 `backend/` 作为 VSCode 工作区时，可直接按 `F5` 选择 `Backend: Spring Boot (env.txt)`。调试环境直接读取仓库根目录 `env.txt`。

## 关键配置

统一写在 `env.txt`（可被 shell 环境变量覆盖）：

- 依赖服务：`MYSQL_*`、`REDIS_*`、`QDRANT_*`
- 默认测试部署不依赖 Consul，统一走静态域名 + 固定端口
- 三服务名：
  - `USER_GRPC_SERVICE_NAME=aienie-userservice-grpc`
  - `PAY_GRPC_SERVICE_NAME=aienie-payservice-grpc`
  - `AI_GRPC_SERVICE_NAME=aienie-aiservice-grpc`
- 三服务固定地址：
  - `USER_HTTP_ADDR=https://userservice.seekerhut.com`
  - `USER_GRPC_ADDR=static://userservice.seekerhut.com:443`
  - `PAY_GRPC_ADDR=static://payservice.seekerhut.com:443`
  - `AI_GRPC_ADDR=static://aiservice.seekerhut.com:443`
  - `EXTERNAL_GRPC_TLS_ENABLED=true`
  - `EXTERNAL_GRPC_PLAINTEXT_ENABLED=false`
- SSO：`SSO_CALLBACK_ORIGIN`、`VITE_SSO_ENTRY_BASE_URL`
- JPA：本地部署默认 `SPRING_JPA_HIBERNATE_DDL_AUTO=none`；数据库结构以 `backend/src/main/resources/db/migration/V1__baseline.sql` 与后续 `V{n}__*.sql` 迁移为准，避免启动期扫描全库元数据。

## 目录说明

- `frontend/`：前端代码
- `backend/`：后端代码
- `backend/.vscode/*`：后端宿主机 F5 调试配置。
- `backend/src/main/resources/db/migration/`：数据库结构基线与后续迁移脚本
- `backend/sql/schema.sql`：历史入口说明文件，指向当前 Flyway 迁移源
- `doc/`：项目文档、API 文档、测试记录
- `user-doc/`：创作者与管理员使用手册
- `design-doc/`：设计与规划文档
- `build.sh`：Docker Compose 构建与部署入口
- `docker-compose.yml`：AINovel 前后端容器编排

## 验证建议

- 后端测试：`cd backend && mvn -q test`
- 前端测试：`cd frontend && npm ci && npm run test`
- 前端构建：`cd frontend && npm run build`
- 本地域名接口验证（避免走代理）：`curl --noproxy '*' -k -I 'https://ainovel.localhut.com/api/v1/sso/login?next=/workbench&state=smoke'`

使用入口见 [`user-doc/README.md`](user-doc/README.md)；开发、接口和部署说明见 [`doc/README.md`](doc/README.md)。
