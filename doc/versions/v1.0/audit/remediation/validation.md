# 整改验证记录

验证日期：2026-09-27。范围为本地 Windows L2 与隔离专项；未部署、未调用付费 AI，真实环境维护窗口及生产验收未执行。所有 MySQL 专项使用 `127.0.0.1:23389` 上临时随机 schema，测试结束自动删除；没有连接业务库。

| 领域 | 验证与结果 | 范围说明 |
|---|---|---|
| 正文和危险输入 | 编辑器真实 hook 的 A/B 稿件、分支、在途保存与草稿切回回归；分支 DTO/状态、合并前备份和坏 JSON 回滚测试通过。 | 只验证构造的本地场景。 |
| PDF | PDFBox 生成 4 页中文/英文/标点及补充平面字样稿；PDFBox 与 pypdf 回读逐字一致，Poppler 首页、中页、末页渲染检查通过；缺字返回错误且不发布字节。 | 使用内置固定字体，未覆盖字形仍会明确失败；证据见 [PDF 专项](evidence/pdf/chinese-pagination.pdf)。 |
| 权限、查重、索引 | 普通用户自审、跨 owner、孤立上传任务拒绝；管理员服务和内容版本回归。索引 chunk/队列/次数/时间边界与查重预算回归通过。 | 恢复会话沿用既有后台认证限制；未进行生产身份提供方端到端验证。 |
| 任务和计费 | AI 两实例领取/取消/旧 token 的隔离 MySQL 并发测试通过；结果不明恢复、重复结算/释放、导出下载名额与断连清理回归通过。 | 远程模型由 stub 代替，不宣称上游恰好执行一次。 |
| 迁移 | 新库 V1–V23、V18→V23、已迁库再运行；正文回填中断续跑、坏 JSON 阻断、校验记录篡改阻断、逆向重建均通过。 | 尚未演练真实环境备份恢复；正式切换按 [维护窗口手册](storage-cutover.md)。 |
| 容量 | 合成 10/100/1,000 场景单行写入分别为 6/3/3 ms，旧 `sections_json` 保持 2 字节档案，EXPLAIN 使用主键；1,000/10,000 资产插入分别为 1,745/14,424 ms，后台 20 项响应 3,084/3,105 字节，使用 `idx_admin_stories_page`；100/1,000/10,000 素材写入分别为 426/3,783/36,946 ms，后台 20 项响应 2,984/2,986/2,988 字节；10,000 素材查重在测试预算 100 对处停止且 `incomplete=true`。连接数前后均为 2，InnoDB 行锁等待增量 0 ms。 | 这些是一次隔离合成运行，不是生产延迟 SLA。内存采样约 22–123 MiB，具体随 GC 波动；实际默认查重预算仍为 100,000 对。 |
| 依赖 | `test_dependency_security.py` 5 项通过；PNPM 锁文件审计 0 命中；最终 JAR 含 shaded 组件扫描阻断项 0。 | 详见 [唯一有期限的条件性例外](security-exception-shaded-netty.md)；最终提交绑定的扫描报告在 `evidence/`。 |

完整 Windows L2：`pwsh -NoProfile -File scripts/windows/Test-Local.ps1 -Level L2`，Node 22.23.2、pnpm 11.22.0。后端 479 项中 457 通过、22 项条件跳过、0 失败；前端 40 个文件、175 项测试通过，lint、TypeScript 和 Vite 构建通过。后端跳过项未计为通过，隔离 MySQL 专项另行启用验证。CI shell 契约脚本在本机 Git Bash 使用 Windows Python 时，扩展名为空的假 PNPM 可执行文件无法由 Win32 直接启动；其 Linux 执行结果未在此处宣称通过。扫描器单元测试及真实 PNPM/JAR 扫描已分别执行。

隔离运行原始日志保留在本地 `artifacts/remediation-isolated-flyway-v23.log`、`artifacts/remediation-isolated-storage-v23.log` 和 `artifacts/remediation-capacity-mysql-v23-final.log`。日志包含随机 schema 名与合成正文，不包含业务数据。PDF 字体许可及输入产物哈希见 `backend/src/main/resources/fonts/`。
