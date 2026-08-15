# AINovel 文档中心

`doc/` 是研发文档的唯一入口；面向创作者和管理员的操作说明独立维护在 [`../user-doc/`](../user-doc/README.md)。代码是当前行为的事实来源。

## 当前实现

- [`architecture/overview.md`](architecture/overview.md)：系统边界、模块、路由和数据。
- [`architecture/frontend.md`](architecture/frontend.md)：前端结构和页面地图。
- [`architecture/page-function-tree.md`](architecture/page-function-tree.md)：页面功能树、职责边界和已知限制。
- [`architecture/guided-creation.md`](architecture/guided-creation.md)：G1 引导创作。
- [`architecture/workbench.md`](architecture/workbench.md)：创作工作台与 v2 能力。
- [`architecture/quality.md`](architecture/quality.md)：文本质量、精雕和 G2 Step 1。
- [`architecture/admin.md`](architecture/admin.md)：管理后台。
- [`architecture/integrations.md`](architecture/integrations.md)：外部服务、鉴权和基础设施。

## 接口与运维

- [`api/README.md`](api/README.md)：HTTP API 索引。
- [`operations/development.md`](operations/development.md)：开发环境和本地检查。
- [`operations/deployment.md`](operations/deployment.md)：配置、部署、迁移与故障处理。
- [`operations/windows-native.md`](operations/windows-native.md)：Windows 原生按需 Build/Start/Status/Stop/Test 入口。
- [`operations/verification.md`](operations/verification.md)：标准验证链和可重复验收步骤。

## 计划与研究

- [`roadmap.md`](roadmap.md)：唯一后续工作账本。
- [`planning/v3/README.md`](planning/v3/README.md)：G1-G4 v3 提案与设计依据。
- [`research/ai-writing-quality/README.md`](research/ai-writing-quality/README.md)：AI 写作质量研究与采用状态。

## 维护规则

1. 当前行为与文档冲突时，以代码为准并同步修正文档。
2. 后续状态、优先级和门槛只写入 `roadmap.md`，专题文档只保留设计依据。
3. 不新增日期化执行结论、测试快照、截图证据、临时备份或第二份路线图；`operations/verification.md` 只维护可重复步骤，具体结果由 CI、提交记录或外部交付记录保存。
4. 跨服务正式契约维护在工作区相对目录 `../../aienie-doc/interfaces/<service>/`，本仓库只记录消费方式；不要写入用户主目录绝对路径。
5. 删除过时内容时依赖 Git 历史追溯，不在仓库内建立归档副本。
