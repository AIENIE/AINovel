# 正文存储维护窗口与回退

本手册描述**尚未执行的环境切换**。当前代码默认 `app.manuscript.scene-storage.enabled=false`；仓库交付不等于部署或生产数据迁移。运行时配置继续由 config-center 管理，经发版中心挂载；不把连接信息写入仓库或构建产物。

1. 停止新写入和 AI、导出、素材索引领取。等待或明确终止在途任务；确认所有旧进程不再连接目标库。记录当前应用版本、Flyway 版本与稿件数。
2. 制作数据库一致性备份并验证可恢复。执行 V19–V23 扩展迁移；V1–V18 不修改。`AINOVEL_MIGRATION_JDBC_URL`、`AINOVEL_MIGRATION_USER`、`AINOVEL_MIGRATION_PASSWORD` 只由维护环境注入。
3. 使用独立 `com.ainovel.app.manuscript.ManuscriptStorageMaintenance` 入口按序执行 `backfill --maintenance-confirmed` 与 `verify --maintenance-confirmed`。它不启动 Spring HTTP、调度器或 AI 客户端。可中断后重跑：每稿在独立事务内核对 JSON 形状、UUID 键、场景数、正文、字数和 SHA-256，然后写入 `content_storage_version=2` 与校验记录。`verify` 不改写业务数据，校验记录被篡改或任何稿件不一致均阻断。
4. 确认所有稿件 `content_storage_version=2`，校验记录数量等于稿件数、场景行数量等于汇总数量；核对历史版本与 H1/H2 证据表的备份哈希。隔离回归通过后，在 config-center 对新版本启用 `app.manuscript.scene-storage.enabled=true`，再按发版中心规定发布并恢复写入与任务领取。新代码遇到未迁稿件返回 `MANUSCRIPT_MIGRATION_REQUIRED`，旧模式遇到已切换稿件返回 `MANUSCRIPT_STORAGE_DOWNGRADE_BLOCKED`。

恢复写入前可以恢复备份并回到旧版本。恢复写入后**不能仅切换旧镜像**：再次停写、备份，以新场景表为事实来源执行 `reverse --maintenance-confirmed`；它逐稿重建旧 `sections_json`、回读核对并递增稿件版本。全部逆向核对后才能启动旧代码。历史快照、原文位置和哈希不参与逆向重算。迁移失败保持停写，先调查对应稿件，不能跳过坏 JSON 或猜测正文。

隔离 MySQL 演练已覆盖空库、V18 升级、回填中断续跑、坏 JSON、校验记录篡改、单场景新表写入及逆向重建；命令、结果和范围见 [统一验收记录](validation.md)。正式维护窗口、备份恢复与真实环境切换尚未执行。
