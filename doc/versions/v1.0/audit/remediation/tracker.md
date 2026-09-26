# 整改台账

更新时间：2026-09-27。`本地关闭`指整改代码、L2 与约定隔离专项已交付，**不代表部署或真实环境验收**。原始审计结论保留在上级目录。统一结果见 [验证记录](validation.md)，逐批技术说明见同目录 `batch-01` 至 `batch-06`。

| 编号 | 状态 | 修复提交 | 主要关闭证据 |
|---|---|---|---|
| PERF-01 | 本地关闭 | `ca13d4c` | `useManuscriptEditorState.test.tsx`：A/B 稿件、分支、在途保存与草稿切回 |
| MAINT-01 | 本地关闭 | `0086999`、`ecb0a69` | `PdfExportRendererTest`；4 页 PDF 双解析器回读及三页 Poppler 渲染 |
| MAINT-02 | 本地关闭 | `0086999` | `V2BranchRequestContractTest`、`V2BranchIntegrityTest`：非法策略/状态、合并前快照、冲突回滚 |
| MAINT-03 | 本地关闭 | `ca13d4c`、`0086999` | `JsonColumnCodecTest`、`V2BranchIntegrityTest`：坏 JSON/序列化失败不写回正文 |
| SEC-01 | 本地关闭 | `ecb0a69` | 锁文件 PNPM 扫描 0 命中；Windows L2 工具链与前端测试通过 |
| SEC-02 | 本地关闭 | `ecb0a69` | `test_dependency_security.py` 5 项；真实 PNPM/JAR 扫描与离线绑定校验，缺失/不匹配失败关闭 |
| SEC-06 | 本地关闭 | `ecb0a69` | 最终 JAR 组件库存及 OSV 复扫：Protobuf 漏洞版本已替换，Critical/High 阻断项 0 |
| MAINT-06 | 本地关闭 | `ecb0a69` | 最新 Flyway 清单计数及固定 target 测试；隔离新库/V18→V23 |
| MAINT-08 | 本地关闭 | `ecb0a69` | 精确 Node/pnpm 入口拒绝测试；`AGENTS.md` 与 CI 说明引用路线图 |
| SEC-03 | 本地关闭 | `ca13d4c`、`13a26a8` | `AUTH_LOCAL_ADMIN` 独立治理服务及控制器；普通服务 owner 检查，专项权限回归 |
| SEC-04 | 本地关闭 | `ca13d4c`、`13a26a8` | `MaterialGovernanceServiceTest`：普通 DTO 不接收审核状态，上传内容变更重审 |
| SEC-05 | 本地关闭 | `ca13d4c`、`13a26a8` | 上传任务不可变 owner、孤立任务拒绝、GET 无状态推进回归 |
| PERF-09 | 本地关闭 | `ca13d4c`、`13a26a8` | 指纹倒排、50 候选/素材和 100,000 对/任务预算；10,000 条隔离测试以 100 对预算标记不完整 |
| PERF-02 | 本地关闭 | `ca13d4c`、`fddeaca` | `AiOperationFencingTest`、双实例隔离 MySQL 取消/旧 token 竞争 |
| PERF-03 | 本地关闭 | `ca13d4c`、`fddeaca` | `AiOperationClaimConcurrencyTest`、隔离 MySQL 双实例领取；导出和索引领取的数据库仲裁 |
| PERF-04 | 本地关闭 | `fddeaca`、`172a3ad` | `QualityPersistenceBoundaryTest`、`EconomyServiceTests`：远程调用不持业务行锁、结果/额度重放幂等 |
| PERF-05 | 本地关闭 | `fddeaca` | `MaterialIndexBudgetTest`、`MaterialIndexCapacityConfigTest`：2/16 worker、512 chunk/10 分钟/5 尝试和版本查询校验 |
| PERF-08 | 本地关闭 | `fddeaca` | `V2ExportDownloadBudgetTest`：数据库复制后流式发送、4/2/100 MiB 限额及清理 |
| PERF-06 | 本地关闭，环境切换待执行 | `ca13d4c` | `ManuscriptStorageExternalMysqlTest`；10/100/1,000 场景单行写入，不改旧正文档案列 |
| PERF-07 | 本地关闭 | `ca13d4c`、`13a26a8` | SQL 分页 DTO；1,000/10,000 资产与 100/1,000/10,000 素材隔离容量测试 |
| MAINT-04 | 本地关闭（关键路径） | `ca13d4c` | `api-client.test.ts`、Zod 契约：稿件、分支、导出、任务和分页边界拒绝畸形响应 |
| MAINT-05 | 本地关闭 | `ca13d4c`、`172a3ad` | `RichTextProjectorTest`、V23 转换版本字段及质量/叙事回归；历史 H1/H2 不重算 |
| MAINT-07 | 本地关闭 | `172a3ad` | `GeneratedOpenApiContractTest`：生成 `/v3/api-docs` 中关键请求与错误状态 |

兼容变化：分支显式非法输入稳定 4xx，正文新表需维护窗口回填和开关，新增场景/摘要 API 与后台分页响应，上传内容修改后重审。旧全稿接口保留兼容；旧正文列是切换前档案，恢复写入后不能直接回到旧应用。详情见 [维护窗口手册](storage-cutover.md)。

保留条件：gRPC 客户端内 shaded Netty 有一项有期限的适用性例外及两项 Moderate 命中，须按 [例外说明](security-exception-shaded-netty.md)到期复核。旧通用 `NetworkObject` 在非关键页面仍使用 `any`，本次约定的关键 API 路径已使用具体契约。Linux CI shell 契约测试和真实环境维护窗口、备份恢复、部署与生产验收尚未执行；这些不计为本地测试通过。

提交顺序按共享契约与迁移依赖排列为第二、第五、第一、第三、第四、第六批；V19–V22 表结构在第五批公共基础提交中预先扩展，业务逻辑在第三/第四批接入。每个代码提交均单独运行 Windows L2，结果见[验收记录](validation.md)。
