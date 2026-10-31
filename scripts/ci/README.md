# AINovel 发布与构建入口

Linux 发布通过发版中心执行。当前平台按 catalog 检出固定源码提交，构建并推送 backend/frontend 镜像；控制面消费运行模板、挂载配置对并执行部署与健康检查。实际目标环境以发布记录为准。

## 当前保留入口

- `backend/Dockerfile` 与 `backend/start-backend.sh`：后端镜像与启动入口。
- `ainovel-runtime-compose.yml`：catalog 使用的运行模板；配置对只读挂载，运行时目录独立保存。
- `scripts/config-pair/load-env-file.sh`：唯一的字面值秘密加载实现，挂载到现行容器内入口路径。
- `remediation-isolated-mysql.py`：显式提供连接、测试选择器后运行隔离 MySQL 验证的独立工具，不负责发布。
- 本地使用 Windows 原生 `scripts/windows/` 入口。Node 范围为 `>=22.23.2 <23`，pnpm 为 `>=11.22.0 <12`，默认 Corepack 调用 pnpm 11.22.0。

完整 L2 与隔离数据库验证由项目入口执行，平台健康检查不能代替这些验证。

## 已退役链路

未接入现行平台的 Resolve／离线 Build 链路、专有依赖扫描和封包校验，以及封顶 V35 的生产迁移执行器、恢复快照和启动 Guard 已删除。相关实现可从 Git 历史查询。

Flyway SQL 历史保持不变。生产配置继续关闭自动迁移；正式生产写 SQL 由作者按当前发布流程执行，不再使用旧执行器核验或生成恢复快照。
