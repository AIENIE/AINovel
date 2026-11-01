# AINovel 0.3.0-rc.3

本候选修复真实 Servlet 容器中的缺失路由响应。rc.2 已成功部署，但有效管理员会话访问已退役的事件路径时收到 401，未达到约定的 404；该失败已登记原 ReleaseRun。原 `v0.3.0-rc.1`、`v0.3.0-rc.2` 标签保持冻结。

## 修复与验证

`GlobalExceptionHandler` 直接将 Spring 的缺失资源／缺失处理器异常映射为 404 和 `NOT_FOUND`，避免容器再次分发 `/error` 后覆盖原状态。现有认证过滤器、管理员权限、TOTP 和会话校验保持。真实嵌入式 HTTP 回归同时覆盖有效普通／管理员会话、匿名访问、失效会话和校验服务不可用；SSO 传输使用显式测试替身，管理员会话走正常存储。测试配置中无人读取的会话校验关闭标记按 S23 删除。

受影响 12 项回归、标准 Windows 完整 L2 和 `Build-Local.ps1 -Component All` 均通过。完整 L2 后端 970 项中 824 项通过、146 项条件跳过，零失败／错误；前端 88 文件／483 项及 lint、类型检查和构建通过。三份已删除测试的旧 XML 单独识别并排除。配置／部署 Windows 回归为 14 通过、3 项 POSIX 条件跳过，Linux 原生 17 项沿用已通过结果。前后端版本仍为 `0.3.0`，稳定 JAR 文件名为 `ai-novel-backend.jar`。隔离 MySQL 134 项及 V35→V50 回放沿用原证据；本次不改变持久化实现或 SQL。

## 发布与验收

继续通过当前控制面 `1c869f0`／v65 的 ReleaseRun、catalog 构建与项目自定义部署入口发布。冻结候选完整 SHA 后创建唯一 source 发布，随即恢复并读回项目 `deploy_script` 原值（空字符串），跟踪原任务。共享平台、runner、实际 YAML/env 和已执行 SQL 保持。

V50 账本及原 V1–V35、托管配置摘要在发布前再次只读核对。迁移批次继续引用 [rc.1 冻结清单](../0.3.0-rc.1/migrations.json)；135 表兼容性和 65 表已解释的历史物理差异见 [rc.2](../0.3.0-rc.2/README.md)。

实际源码、镜像摘要、配置、容器归属与正常 HTTPS 已核对。13 项普通／管理员接口契约及最终实例的 6 组桌面／390px 流程通过：退役路径 404，空风险均值为 null，正文保存刷新一致，失败保留输入和服务器原正文，历史读取失败保留已加载版本并可恢复。rc.2 的 14 项普通用户流程证据一并保留，前端树未改变。最终实例保持运行。

证据目录为工作区外 `aienie-runtime/evidence/product-artifacts/ainovel/release-0.3.0-rc.1-staging-20261031/`。管理员首次 TOTP 绑定已由作者完成，凭据不进入仓库或证据。

## 发布回执（2026-11-01）

- 运行候选：`v0.3.0-rc.3`，完整 SHA `4107ee6633a3d5e9ac614b03b4a964d4458feead`；活动 release 与该候选保持一致。
- 平台发布：`20261101002`，ID `7c50978b-4609-50f0-8273-fb746bfe5a14`，Jenkins #517；构建、部署及健康均成功。ReleaseRun 为 `6f16ab04-8f6e-58ea-ab84-19ba0f3ef672`，验收步骤已通过。
- 构建身份：`sha256:eafdee8e3b28aac47becaf4010742049fbf66d354b8f1f5d25b4ec6fcfd84a4f`。后端镜像为 `ghcr.io/aienie/ai-novel@sha256:764e08963e580caae363aaf66536558a6cb0e2ff4874628b6229ec713ec2828e`；前端为 `ghcr.io/aienie/ai-novel@sha256:9a5a2459c5a2e533bff3ae6a4b4df9748ad77405bbccdd70c92596d13bd24a1d`。
- 两个容器均归属 `ai-novel_staging`；后端 healthy，JAR 版本 `0.3.0`。构建身份、冻结部署入口、实际镜像引用及配置内容均已核验。OCI 基础镜像标签按基础镜像解释，应用源码以冻结构建身份为准。
- 项目 `deploy_script` 已恢复原空字符串，其他项目字段、部署目标和三个托管配置的内容、大小及权限声明均未改变。当前平台落盘的 `application.yml`、`env.txt` 均为 0600，并只读挂载；托管 YAML 的 0444 声明保持。共享控制面仍为 `1c869f0433c49e9047139be3de3a17c818fb2ddf`／v65。
- V50 的 50 条成功记录及校验和、原 V1–V35 已再次只读核对。AI 操作、计费预占、验证调用、自主写作任务记录均为 0；本次验收未调用付费模型。
- 修复通过普通合并 `c946dbd14eec98eddb0b7337e648cbd47a9e698c` 回合 develop，前后端继续为 `0.4.0-SNAPSHOT`。确认完整祖先关系及远端一致后，已普通删除 `codex/ainovel-code-cleanup`、`codex/ainovel-release-config-guard`、`codex/ainovel-retired-route-status` 的本地和远端引用；保留 master、develop、活动 release、全部标签、原 stash 及既有工作树映射和目录。最终交付记录在 develop 维护，冻结候选不追加文档提交或移动标签。

管理员首次绑定及本轮动态码登录均由作者完成。一次重新登录的挑战在首次输入前已超过两分钟有效期，导致动态码和恢复码请求均在码校验前被拒；重新创建挑战后正常登录成功，未重置凭据。

运营页保留一项现有 `user-service HTTP` 探针证书链 PKIX WARN；实际 SSO、远程会话验证和正文流程已通过。发布切换时写作会话心跳在 02:20:33 UTC 返回一次 502，02:20:54 部署健康成功；最终流程没有新增 HTTP 或控制台错误，两项主动阻断请求属于失败反馈验收，覆盖设置及本任务浏览器标签均已清理。这些观察保存在证据目录，未追加配置或平台改造。

关键回执为 `rc3-runtime-proof.json`、`rc3-final-environment-proof.json`、`rc3-staging-api-contracts.json`、`rc3-admin-api-contracts.json`、`rc3-browser-acceptance.json`、`rc3-branch-cleanup-after.json`；完整日志、截图及 ReleaseRun 最终收尾回执同目录保留。

## 安全事项

无新增或调整。沿用 S1–S23，不新增付费模型调用，不重发历史未知请求，生产另行安排。
