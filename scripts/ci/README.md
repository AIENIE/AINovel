# AINovel 发布与构建入口

Linux 发布通过发版中心执行。当前平台按 catalog 检出固定源码提交，构建并推送 backend/frontend 镜像；控制面消费运行模板、挂载配置对并执行部署与健康检查。实际目标环境以发布记录为准。

## 当前保留入口

- `backend/Dockerfile` 与 `backend/start-backend.sh`：后端镜像与启动入口。
- `ainovel-runtime-compose.yml`：catalog 使用的运行模板；配置对只读挂载，运行时目录独立保存。
- `deploy_staging.py`：发版中心项目自定义 `deploy_script` 调用的 staging 部署入口。消费 catalog 的部署声明及冻结镜像，先校验 S3 配置对与只读挂载，再同步运行目录并启动 Compose；现有日志、备份及发布记录目录保留。不执行数据库迁移。
- `scripts/config-pair/load-env-file.sh`：唯一的字面值秘密加载实现，挂载到现行容器内入口路径。
- `remediation-isolated-mysql.py`：显式提供连接、测试选择器后运行隔离 MySQL 验证的独立工具，不负责发布。
- 本地使用 Windows 原生 `scripts/windows/` 入口。Node 范围为 `>=22.23.2 <23`，pnpm 为 `>=11.22.0 <12`，默认 Corepack 调用 pnpm 11.22.0。

完整 L2 与隔离数据库验证由项目入口执行，平台健康检查不能代替这些验证。

## staging 自定义流程

本次 0.3.0 使用控制面 `1c869f0`／v65 的 ReleaseRun API，声明 source、prepare、database、deployment、acceptance、closeout，关联原 SQL 执行及平台发布 ID。协调器只为本次 staging 快照临时设置 AINovel 项目 `deploy_script`，创建发布后恢复原值并读回核对。实际部署、操作回执和健康检查仍由发版中心执行；共享控制面及 runner 保持现有版本。

平台传入的六个参数依次是候选目录、运行目录、平台版本号、组件、环境和发布 ID。自定义入口由平台以 `sudo -n python3 "$1/scripts/ci/deploy_staging.py" "$@"` 调用。该入口只处理 AINovel staging，其他环境继续使用原项目设置。Compose 只需要 `images.env` 的不可变镜像和配置文件属主 UID/GID，应用参数及秘密仍从原只读配置对读取，已退役的 `compose_parameters.py` 不恢复。

配置及部署顺序回归由标准 Windows 测试入口执行；涉及 POSIX 权限的三项在 Windows 条件跳过，在现有 Linux 目标原生运行。测试使用临时夹具和命令替身，不启动容器或调用模型。

## 已退役链路

未接入现行平台的 Resolve／离线 Build 链路、专有依赖扫描和封包校验，以及封顶 V35 的生产迁移执行器、恢复快照和启动 Guard 已删除。相关实现可从 Git 历史查询。

Flyway SQL 历史保持不变。生产配置继续关闭自动迁移；正式生产写 SQL 由作者按当前发布流程执行，不再使用旧执行器核验或生成恢复快照。
