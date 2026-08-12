# 开发指南

## 环境要求

- Node.js 20+
- JDK 25
- Maven 3.9+
- Docker 与 Docker Compose

## 本地检查

后端：

```bash
mvn -q -f backend/pom.xml test
```

前端：

```bash
cd frontend
npm ci --legacy-peer-deps
npm run test
npm run build
```

以 `backend/` 作为 VSCode 工作区时，可选择 `Backend: Spring Boot (env.txt)` 进行宿主机调试；先从 `env.example` 创建本地 `env.txt`。该文件包含运行秘密并被 Git 忽略。

## Windows 本机启动（localbase WSL）

共享数据服务由 `aienie-wsl` 提供，应用始终通过 `localbase.testhut.top` 访问，不直接使用 IP。首次启动前，请以管理员身份执行：

```powershell
.\scripts\windows\Ensure-LocalbaseHosts.ps1
```

该脚本会幂等地维护 `172.20.0.2 localbase.testhut.top` 的带标记 hosts 条目。随后从项目根目录执行 `.\scripts\windows\Start-Native.ps1`；它只按字面 `NAME=value` 读取被 Git 忽略的 `env.txt`、验证 localbase 解析和数据端口，并将前后端限制在 `127.0.0.1:11041` 和 `127.0.0.1:11040`。使用 `.\scripts\windows\Stop-Native.ps1` 停止；停止脚本不会删除 hosts 映射，日志与 PID 状态位于 `.native-run/`。

Linux Docker Compose 通过非敏感 `LOCALBASE_HOST_IP=172.20.0.2`（默认值相同）仅将容器内的 `localbase.testhut.top` 数据服务别名指向 WSL provider；`localuserservice`、`localpayservice`、`localaiservice` 仍保持各自的 `host-gateway` 映射。

## 修改约束

- 新数据库变更新增 `backend/src/main/resources/db/migration/V{n}__*.sql`。
- API 行为变化同步更新 [`../api/`](../api/)。
- 当前架构变化同步更新 [`../architecture/`](../architecture/)。
- 后续计划只更新 [`../roadmap.md`](../roadmap.md)，不在专题文档复制第二份待办列表。
