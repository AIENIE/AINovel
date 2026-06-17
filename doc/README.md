# AINovel 文档索引

本目录记录 AINovel 的代码结构、业务模块、接口说明、测试记录和使用说明。后续开发应优先从本文件进入，再按任务读取对应专题文档。

## 架构与模块

- `structure.md`：项目结构与主要代码入口。
- `modules/README.md`：业务模块索引。
- `modules/`：后台、工作台、素材、设定、世界观、分析、远程安全等模块说明。
- `modules/ai-slop-quality.md`：AI Slop 文本质量诊断、证据等级、安全边界与后续分期 Backlog。

## 接口文档

- `api/`：后端 HTTP/API 文档。
- `api/sso.md`、`api/user.md`、`api/ai.md`、`api/material.md`、`api/story.md`、`api/world.md`：主要业务接口。
- `api/v2-*.md`：v2 工作台、分析、导出、质量、风格和版本相关接口。

## 前端文档

- `../frontend/doc/structure.md`：前端结构说明。
- `../frontend/doc/page-*.md`：页面级说明。
- `../frontend/doc/general.md`：前端通用说明。

## 测试与验收

- `test/`：修复记录、回归记录和验收记录。
- `tutorial/getting-started.md`：入门使用说明。

## 维护规则

- 本目录以当前仓库代码为准。
- 若文档与代码冲突，应先修正文档并记录差异。
- 涉及公共服务接口时，以 `/home/duwei/aienie-services/aienie-doc/interfaces/` 下对应服务契约为准。
