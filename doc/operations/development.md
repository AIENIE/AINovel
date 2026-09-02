# 开发指南

## 环境要求

- Node.js 22.23.2
- pnpm 11.22.0（通过 Corepack 调用）
- JDK 25
- Maven 3.9+

## 本地检查

后端：

```bash
mvn -q -f backend/pom.xml test
```

前端：

```bash
cd frontend
corepack pnpm@11.22.0 install --frozen-lockfile
corepack pnpm@11.22.0 run test
corepack pnpm@11.22.0 run build
```

标准本地运行入口是 [`windows-native.md`](windows-native.md) 的 `scripts/windows/` 契约，其私有环境文件位于仓库外。如需宿主机 IDE 调试，可从 `env.example` 复制出本地、Git 忽略的 `env.txt` 作为键清单对照；该文件包含运行秘密，不得提交。

## 修改约束

- 新数据库变更新增 `backend/src/main/resources/db/migration/V{n}__*.sql`。
- API 行为变化同步更新 [`../api/`](../api/)。
- 当前架构变化同步更新 [`../architecture/`](../architecture/)。
- 后续计划只更新 [`../roadmap.md`](../roadmap.md)，不在专题文档复制第二份待办列表。
