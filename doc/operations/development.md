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

以 `backend/` 作为 VSCode 工作区时，可选择 `Backend: Spring Boot (env.txt)` 进行宿主机调试；配置直接读取仓库根目录 `env.txt`。

## 修改约束

- 新数据库变更新增 `backend/src/main/resources/db/migration/V{n}__*.sql`。
- API 行为变化同步更新 [`../api/`](../api/)。
- 当前架构变化同步更新 [`../architecture/`](../architecture/)。
- 后续计划只更新 [`../roadmap.md`](../roadmap.md)，不在专题文档复制第二份待办列表。
