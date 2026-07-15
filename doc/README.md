# AINovel 文档索引

本目录记录 AINovel 的当前实现、接口、部署和验证说明。代码是事实来源；面向创作者与管理员的操作说明单独维护在仓库根目录 `user-doc/`。

## 架构与模块

- `structure.md`：项目结构与主要代码入口。
- `modules/README.md`：业务模块索引。
- `modules/`：后台、工作台、素材、设定、世界观、分析、远程安全和 v2 模块说明。
- `modules/ai-slop-quality.md`：文本质量诊断、生成门禁和精雕模式说明。
- `modules/guided-creation.md`：G1 引导创作、后台任务和标准实体落库说明。

## 接口文档

- `api/`：后端 HTTP/API 文档。
- `api/sso.md`、`api/user.md`、`api/ai.md`、`api/material.md`、`api/story.md`、`api/world.md`：主要业务接口。
- `api/v2-*.md`：v2 工作台、分析、导出、质量、风格和版本相关接口。
- `api/guided-creation.md`：引导创作运行、候选生成、确认、自动推进与重试接口。
- `api/external/`：当前外部服务接入边界和静态地址约束。

## 前端文档

- `../frontend/doc/structure.md`：前端结构说明。
- `../frontend/doc/page-*.md`：页面级说明。
- `../frontend/doc/general.md`：前端通用说明。

## 用户手册

- `../user-doc/README.md`：创作者和管理员的操作入口。
- `../user-doc/01-快速开始.md`：从登录到第一部作品的最短路径。

## 部署与验证

- `tutorial/getting-started.md`：开发与部署指南。
- `verification.md`：当前可复现的测试、部署和验收步骤。

## 维护规则

- 本目录以当前仓库代码为准。
- 若文档与代码冲突，应先修正文档并记录差异。
- 涉及公共服务接口时，以 `/home/duwei/aienie-doc/interfaces/` 下对应服务契约为准。
- 已删除的历史审计、日期化测试和过期设计快照不再作为维护入口；需要记录新的验证结论时更新 `verification.md` 或对应模块说明。
