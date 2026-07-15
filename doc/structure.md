# 项目目录结构说明

## 顶层目录

- `frontend/`：React 前端工程。
- `backend/`：Spring Boot 后端工程。
- `doc/`：当前实现、接口、部署和验证文档。
- `user-doc/`：面向创作者和管理员的使用手册。
- `design-doc/`：设计方案与版本规划文档。
- `build.sh`：Docker Compose 构建与部署入口。
- `docker-compose.yml`：AINovel 前后端容器编排；Compose 使用 `env.txt` 做变量插值，后端容器启动时再加载完整运行时配置。
- `env.txt`：可选部署环境配置，由 `build.sh` 传给 Compose，并以只读方式挂载给后端容器启动脚本。

## frontend 目录

- `frontend/src/App.tsx`：路由与权限守卫入口。
- `frontend/src/contexts/AuthContext.tsx`：登录态与用户资料上下文。
- `frontend/src/pages/auth/*`：SSO 登录/注册跳转与回调页面。
- `frontend/src/pages/Admin/Login.tsx`：管理员本地账密登录页（`/admin/login`）。
- `frontend/src/pages/Profile/ProfilePage.tsx`：个人中心（兑换码、通用转专属、兑换历史、积分流水）。
- `frontend/src/pages/Workbench/`：9 标签创作工作台，含故事、大纲、稿件、质量、知识库与 v2 联调入口。
- `frontend/src/pages/GuidedCreation/`：G1 引导创作工作区、候选编辑和持久化草稿状态管理。
- `frontend/src/pages/Settings/`：提示词、世界观提示词、风格、模型偏好和工作台体验设置。
- `frontend/src/pages/Admin/*`：管理后台页面。
- `frontend/src/lib/api-client.ts`：统一 API 请求层。

## backend 目录

- `backend/src/main/resources/application.yml`：后端主配置（支持 env 覆盖）。
- `backend/.vscode/launch.json`：VSCode Java F5 调试入口，直接启动 `com.ainovel.app.AiNovelApplication`。
- `backend/.vscode/tasks.json`：VSCode 调试任务定义。
- `backend/src/main/java/com/ainovel/app/auth/*`：SSO 中转。
- `backend/src/main/java/com/ainovel/app/adminauth/*`：管理员本地登录接口（`/v1/admin-auth/*`）。
- `backend/src/main/java/com/ainovel/app/security/*`：JWT、远程会话校验、资源访问控制（BOLA 防护）。
- `backend/src/main/java/com/ainovel/app/integration/*`：三服务静态地址客户端与外部安全启动校验。
- `backend/src/main/java/com/ainovel/app/integration/ExternalSecurityStartupValidator.java`：三服务鉴权配置 fail-fast 校验。
- `backend/src/main/java/com/ainovel/app/integration/GrpcChannelFactory.java`：统一 gRPC TLS/plaintext 通道构建。
- `backend/src/main/java/com/ainovel/app/config/AppTimeProvider.java`：统一时间源（`APP_TIME_ZONE`）。
- `backend/src/main/java/com/ainovel/app/security/ResourceAccessGuard.java`：用户资源归属校验。
- `backend/src/main/java/com/ainovel/app/economy/*`：项目积分账本与兑换核心逻辑。
- `backend/src/main/java/com/ainovel/app/manuscript/*`：稿件、场景生成和精雕模式。
- `backend/src/main/java/com/ainovel/app/quality/*`：文本质量、剧情质量、长篇 drift 与模式采样。
- `backend/src/main/java/com/ainovel/app/g2evaluation/*`：G2 盲测活动、匿名评审、隔离样本生成与退款处理。
- `backend/src/main/java/com/ainovel/app/workflow/*`：G1 引导创作运行、候选生成、标准实体落库、持久化异步任务与恢复协调。
- `backend/src/main/java/com/ainovel/app/v2/*`：上下文、版本、导出、风格、模型和工作台体验 API。
- `backend/src/main/java/com/ainovel/app/user/*`：用户中心接口。
- `backend/src/main/java/com/ainovel/app/admin/*`：管理端接口。
- `backend/src/main/proto/*`：ai/user/pay gRPC 协议。
- `backend/src/main/resources/db/migration/`：数据库结构基线与增量迁移。
- `backend/sql/schema.sql`：历史入口说明文件，指向当前 Flyway 迁移源。
