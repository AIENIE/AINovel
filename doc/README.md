# AINovel 文档索引

本目录记录 AINovel 的当前实现、接口、部署和验证说明。代码是事实来源；面向创作者与管理员的操作说明单独维护在仓库根目录 `user-doc/`。

## 架构与模块

- `structure.md`：项目结构与主要代码入口。
- `modules/README.md`：业务模块索引。
- `modules/`：后台、工作台、素材、设定、世界观、分析、远程安全和 v2 模块说明。
- `modules/ai-slop-quality.md`：文本质量诊断、生成门禁和精雕模式说明。

## 接口文档

- `api/`：后端 HTTP/API 文档。
- `api/sso.md`、`api/user.md`、`api/ai.md`、`api/material.md`、`api/story.md`、`api/world.md`：主要业务接口。
- `api/v2-*.md`：v2 工作台、分析、导出、质量、风格和版本相关接口。
- `api/external/`：当前外部服务接入边界和静态地址约束。

## 前端文档

- `../frontend/doc/structure.md`：前端结构说明。
- `../frontend/doc/page-*.md`：页面级说明。
- `../frontend/doc/general.md`：前端通用说明。

## 用户手册

- `../user-doc/README.md`：创作者和管理员的操作入口。
- `../user-doc/01-快速开始.md`：从登录到第一部作品的最短路径。

## 测试与验收

- `test/`：日期化修复、回归和验收记录，属于历史证据，不替代当前模块说明。
- `tutorial/getting-started.md`：开发与部署指南。
- `issues.md`：当前问题跟踪。

## 维护规则

- 本目录以当前仓库代码为准。
- 若文档与代码冲突，应先修正文档并记录差异。
- 涉及公共服务接口时，以 `/home/duwei/aienie-doc/interfaces/` 下对应服务契约为准。
- `doc/audit/`、`doc/test/` 和日期化设计文档保留当时快照；发现现状变化时新增当前说明或验证记录，不回写历史结论。
