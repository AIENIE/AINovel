# Codex 代码库阅读入口

本仓库是 Aienie 的 AI 小说业务项目。读取顺序：

1. 读取根目录 `AGENTS.md`，确认项目边界、域名、端口、部署和当前门槛。
2. 读取 `doc/README.md`，按任务进入架构、API、运维、路线图、提案或研究目录。
3. 涉及公共服务时，读取 `/home/duwei/aienie-doc/interfaces/<service>/`；缺少契约目录时，用本仓库 proto 与客户端定位当前实现，但不把运行时发现当正式契约。
4. 结构和调用链工具只用于定位，修改前必须打开真实源码和相关文档。

常用入口：

- `backend/src/main/java/com/ainovel/app/`：Spring Boot 后端。
- `backend/src/main/proto/`：本项目固定使用的 gRPC 契约副本。
- `backend/src/main/resources/db/migration/`：Flyway 数据库事实源。
- `frontend/src/`：React + TypeScript 前端。
- `doc/architecture/`：当前实现与边界。
- `doc/api/`：HTTP API。
- `doc/operations/`：开发、部署与验证。
- `doc/roadmap.md`：唯一后续工作账本。
- `user-doc/`：创作者与管理员手册。
