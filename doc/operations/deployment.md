# 部署与配置

## 配置

仓库根目录使用 `env.txt` 作为默认运行配置源，并允许环境变量覆盖。重点分组：

- 基础设施：`MYSQL_*`、`REDIS_*`、`QDRANT_*`
- 三服务地址：`USER_HTTP_ADDR`、`USER_GRPC_ADDR`、`PAY_GRPC_ADDR`、`AI_GRPC_ADDR`
- SSO：`SSO_CALLBACK_ORIGIN`、`VITE_SSO_ENTRY_BASE_URL`
- 管理员：`ADMIN_USERNAME`、`ADMIN_PASSWORD`
- 外部鉴权：AI HMAC、user-service internal token、pay-service service JWT
- 数据库：`SPRING_JPA_HIBERNATE_DDL_AUTO=none`

不要把密钥值写入文档、日志或提交信息。

## 一键部署

```bash
printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh
```

脚本会使用 Compose 构建前后端镜像、重建容器，并在存在 `env.txt` 时通过 `--env-file` 加载插值。后端容器启动脚本再加载只读挂载的完整配置。共享的 `ainovel-backend` / `ainovel-frontend` 已被其他工作树占用时，只有 `master` 可以自动接管；其他分支必须显式设置 `AINOVEL_ALLOW_SHARED_DEPLOY=1`。

访问入口：

- `https://ainovel.localhut.com`
- 后端端口 `11041`
- 前端端口 `11040`

## 数据库迁移

- 新库从 `V1` 顺序迁移到当前版本。
- 引入 Flyway 前已存在的旧库需要正确登记 V1 baseline。
- 当前最新版本为 V9。V5 建立 `creation_workflow_runs` 和 `async_jobs`；V6 为已基线旧库条件补齐 `slop_quality_issues` 的质量证据列；V7 补齐故事内容树级联删除；V8 持久化 AI 操作进度；V9 将历史质量问题表的限制型外键修复为级联删除。
- 不要手工向 `backend/sql/schema.sql` 追加 DDL。

## 常见故障

部署和在线可用性探测使用：

- `/api/actuator/health/liveness`：进程存活状态。
- `/api/actuator/health/readiness`：应用与数据库就绪状态，不包含可选 Redis。
- `/api/actuator/health`：综合依赖诊断，仍包含 Redis，可能在 Redis 短暂重连时显示 `DOWN`。

- 网站不可达：检查域名解析、Nginx、容器状态与端口。
- SSO 成功但业务接口 403：检查 `USER_GRPC_ADDR`、internal token 和 user-service `ValidateSession` 可达性。
- 管理员登录失败：检查本地管理员配置与 `/api/v1/admin-auth/login`。
- 通用积分转换失败：检查 pay-service gRPC 地址、项目标识和 service JWT。
- `curl` 出现代理相关 TLS 异常：对本地域名使用 `--noproxy '*'`。
