# 开发指南

## 环境要求

- Node.js 20+
- JDK 25
- Maven 3.9+
- Windows 本地开发使用 PowerShell 7；Docker 与 Docker Compose 仅是非 Windows 容器部署的前置条件。

## 本地检查

后端：

```powershell
mvn -q -f backend/pom.xml test
```

前端：

```powershell
npm --prefix frontend ci --legacy-peer-deps
npm --prefix frontend test
npm --prefix frontend run build
```

运行时调试与浏览器验收另按 [部署与本地运行](deployment.md) 操作：Windows 使用 `scripts/windows/Start-Local.ps1` 和 ACL 收紧的 `%LOCALAPPDATA%\Aienie\secrets\ainovel-localbase.env`，不得从仓库根目录 `env.txt` 加载秘密。

## 修改约束

- 新数据库变更新增 `backend/src/main/resources/db/migration/V{n}__*.sql`。
- API 行为变化同步更新 [`../api/`](../api/)。
- 当前架构变化同步更新 [`../architecture/`](../architecture/)。
- 后续计划只更新 [`../roadmap.md`](../roadmap.md)，不在专题文档复制第二份待办列表。
