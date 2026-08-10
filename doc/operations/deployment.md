# 部署与配置

## 配置

仓库不再跟踪 `env.txt`。复制 `env.example` 到本地 `env.txt` 并设置为仅属主可读写的 `0600` 普通文件；`build.sh` 的部署门禁与后端容器只读取这一份文件，不允许由宿主 OS 环境变量补齐缺失键。不要提交实际值。重点分组：

- 基础设施：`MYSQL_*`、`REDIS_*`、`QDRANT_*`
- 三服务地址：`USER_HTTP_ADDR`、`USER_GRPC_ADDR`、`PAY_GRPC_ADDR`、`AI_GRPC_ADDR`
- SSO：`SSO_CALLBACK_ORIGIN`、`VITE_SSO_ENTRY_BASE_URL`
- 管理员策略：`env.txt` 中的精确 `ENV`、`AUTH_MODE`；只允许 `local/password`、`local/totp`、`test/totp`、`production/totp`
- 管理员密码：`ADMIN_USERNAME`、`ADMIN_PASSWORD_HASH`（BCrypt cost 至少 10；不接受明文密码配置）
- 管理员 TOTP 密钥环：`ADMIN_TOTP_ENCRYPTION_KEYS`、`ADMIN_TOTP_ACTIVE_KEY_VERSION`
- 管理员来源与会话：`ADMIN_TRUSTED_ORIGINS`、`ADMIN_SESSION_COOKIE_SECURE`、`ADMIN_SESSION_MINUTES`、`ADMIN_SESSION_IDLE_MINUTES`、`ADMIN_TOTP_RECOVERY_SESSION_MINUTES`、`ADMIN_TOTP_RECOVERY_SESSION_IDLE_MINUTES`；非本地环境必须启用 Secure Cookie，本地隔离 HTTP 验收可显式设为 `false`
- 外部鉴权：AI HMAC、user-service internal token、pay-service service JWT
- 数据库：`SPRING_JPA_HIBERNATE_DDL_AUTO=none`

日志与运维记录默认写入挂载目录，并可通过环境变量收紧容量：

- 应用日志：`LOGGING_MAX_FILE_SIZE`（默认 `10MB`）、`LOGGING_MAX_HISTORY`（默认 `14`）、`LOGGING_TOTAL_SIZE_CAP`（默认 `1GB`）。
- 应用文件日志使用 Spring Boot ECS JSON；异常堆栈作为单行 JSON 字符串输出，MDC `requestId` 作为结构化字段输出。console 保持现有文本展示。
- 结构化运维记录：`APP_RECORD_MAX_FILE_SIZE_BYTES`（默认 `10485760`）、`APP_RECORD_MAX_HISTORY_DAYS`（默认 `14`）、`APP_RECORD_MAX_TOTAL_SIZE_BYTES`（默认 `1073741824`）。
- 结构化运维记录落盘前会按敏感字段名和嵌入式凭据模式脱敏，并限制字段名、字符串、递归深度、容器元素数及单条记录总预算；业务目标仍使用非敏感 `targetId`（例如兑换记录数据库 ID）。
- backend/frontend 容器的 Docker `json-file` 日志：`DOCKER_LOG_MAX_SIZE`（默认 `10m`）、`DOCKER_LOG_MAX_FILE`（默认 `5`）。
- HTTP 响应返回 `X-Request-Id`；只接受最长 64 位的字母、数字、点、下划线、冒号和连字符，其他值会重新生成。console 日志级别字段和 ECS 文件日志字段均包含同一 request id；AI operation、guided creation 与 G2 evaluation 线程池会传播 MDC，并在任务结束后恢复 worker 原上下文。
- 浏览器不序列化原始异常或拒绝对象，只输出经过截断并对常见令牌、兑换码参数和邮箱模式脱敏的错误摘要。

密码模式不解析或要求 TOTP keyring。测试和生产启动时若缺少 TOTP keyring、可信来源或其他必需配置会直接失败；生产处理认证请求时若共享 Redis 限流存储不可用则失败关闭。不要把密码、摘要、密钥、验证码、恢复码、challenge、session 或 proof 写入文档、日志或提交信息；从曾经跟踪过的 `env.txt` 取出的所有实际凭据应在部署前完成轮换。

## 一键部署

```bash
printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh
```

脚本先校验 `env.txt` 是 `0600` 的普通文件，再使用同一文件完成 Compose 插值并只读挂载给后端容器加载。共享的 `ainovel-backend` / `ainovel-frontend` 已被其他工作树占用时，只有 `master` 可以自动接管；其他分支必须显式设置 `AINOVEL_ALLOW_SHARED_DEPLOY=1`。

访问入口：

- `https://localainovel.testhut.top`
- 后端端口 `11041`
- 前端端口 `11040`

## 数据库迁移

- 新库从 `V1` 顺序迁移到当前版本。
- 引入 Flyway 前已存在的旧库需要正确登记 V1 baseline。
- 当前最新版本为 V11。V5 建立 `creation_workflow_runs` 和 `async_jobs`；V6 为已基线旧库条件补齐 `slop_quality_issues` 的质量证据列；V7 补齐故事内容树级联删除；V8 持久化 AI 操作进度；V9 将历史质量问题表的限制型外键修复为级联删除；V10 建立管理员 TOTP 凭据、挑战、恢复码、会话和审计表；V11 撤销旧管理员会话，并加入策略/认证强度字段、密码阶段时间以及操作级 challenge/proof 表。
- 不要手工向 `backend/sql/schema.sql` 追加 DDL。

## 常见故障

部署和在线可用性探测使用：

- `/api/actuator/health/liveness`：进程存活状态。
- `/api/actuator/health/readiness`：应用与数据库就绪状态，不包含可选 Redis。
- `/api/actuator/health`：综合依赖诊断，仍包含 Redis，可能在 Redis 短暂重连时显示 `DOWN`。

- 网站不可达：检查域名解析、Nginx、容器状态与端口。
- SSO 成功但业务接口 403：检查 `USER_GRPC_ADDR`、internal token 和 user-service `ValidateSession` 可达性。
- 管理员登录失败：先确认 OS `ENV/AUTH_MODE` 是四个允许组合之一，再检查 `/api/v1/admin-auth/bootstrap`；确认 `ADMIN_PASSWORD_HASH` 与输入密码匹配，TOTP 模式完成密码阶段后再输入验证器动态码。恢复码仍需先通过密码阶段，并且只能进入受限重绑定流程。
- 通用积分转换失败：检查 pay-service gRPC 地址、项目标识和 service JWT。
- `curl` 出现代理相关 TLS 异常：对本地域名使用 `--noproxy '*'`。

## 管理员受控重置

同时丢失验证器与全部恢复码时，不允许使用公开 HTTP 接口或部署密码绕过。完成线下身份核验与审批后，运维只能在后端容器或等效受控运行环境中执行一次性命令：

```bash
ADMIN_TOTP_RESET_CONFIRM=RESET \
ADMIN_TOTP_RESET_APPROVAL_ID=<approved-ticket-id> \
java -jar /app/app.jar --spring.main.web-application-type=none --spring.profiles.active=admin-totp-reset
```

命令会撤销全部管理员会话、恢复码和旧验证器凭据，写入审计记录后退出；下一次访问将回到首次绑定流程。审批编号不可包含密钥、验证码或恢复码。
