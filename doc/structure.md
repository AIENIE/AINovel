# 项目目录结构说明

## 顶层目录

- `frontend/`：React 前端工程。
- `backend/`：Spring Boot 后端工程。
- `doc/`：项目文档、API、测试记录。
- `design-doc/`：设计方案与版本规划文档。
- `build.sh`：Linux 一键部署脚本（支持 hosts + nginx + HTTPS 代理）。
- `build_prod.sh`：生产部署包装脚本（调用 `build.sh`）。
- `build_local.ps1`：Windows 本地编译运行脚本。
- `backend/deploy_local.sh`：Linux 宿主机后端本地启动脚本（`start|stop|restart|status|logs`）。
- `docker-compose.yml`：AINovel 前后端容器编排。
- `env.txt`：默认环境配置（可被进程环境变量覆盖）。

## frontend 目录

- `frontend/src/App.tsx`：路由与权限守卫入口。
- `frontend/src/contexts/AuthContext.tsx`：登录态与用户资料上下文。
- `frontend/src/pages/auth/*`：SSO 登录/注册跳转与回调页面。
- `frontend/src/pages/Admin/Login.tsx`：管理员本地账密登录页（`/admin/login`）。
- `frontend/src/pages/Profile/ProfilePage.tsx`：个人中心（签到/兑换/兑换历史/积分流水）。
- `frontend/src/pages/Admin/*`：管理后台页面。
- `frontend/src/lib/mock-api.ts`：统一 API 请求层。

## backend 目录

- `backend/src/main/resources/application.yml`：后端主配置（支持 env 覆盖）。
- `backend/.vscode/launch.json`：VSCode Java F5 调试入口，直接启动 `com.ainovel.app.AiNovelApplication`。
- `backend/.vscode/tasks.json`：调试前生成 `backend/target/vscode.env` 的预启动任务。
- `backend/.vscode/prepare-env.sh`：按 `ENV_FILE` -> `backend/env.txt` -> 根 `env.txt` 解析环境并输出调试专用 env 文件。
- `backend/scripts/runtime-env.sh`：宿主机启动/调试共用的环境解析、默认值补齐、JWT 派生逻辑。
- `backend/src/main/java/com/ainovel/app/auth/*`：SSO 中转。
- `backend/src/main/java/com/ainovel/app/adminauth/*`：管理员本地登录接口（`/v1/admin-auth/*`）。
- `backend/src/main/java/com/ainovel/app/security/*`：JWT、远程会话校验、资源访问控制（BOLA 防护）。
- `backend/src/main/java/com/ainovel/app/integration/*`：Consul + 三服务客户端与外部安全启动校验。
- `backend/src/main/java/com/ainovel/app/integration/ExternalSecurityStartupValidator.java`：三服务鉴权配置 fail-fast 校验。
- `backend/src/main/java/com/ainovel/app/integration/GrpcChannelFactory.java`：统一 gRPC TLS/plaintext 通道构建。
- `backend/src/main/java/com/ainovel/app/config/AppTimeProvider.java`：统一时间源（`APP_TIME_ZONE`）。
- `backend/src/main/java/com/ainovel/app/security/ResourceAccessGuard.java`：用户资源归属校验。
- `backend/src/main/java/com/ainovel/app/economy/*`：项目积分账本与兑换核心逻辑。
- `backend/src/main/java/com/ainovel/app/user/*`：用户中心接口。
- `backend/src/main/java/com/ainovel/app/admin/*`：管理端接口。
- `backend/src/main/proto/*`：ai/user/pay gRPC 协议。
- `backend/sql/schema.sql`：数据库结构参考。
