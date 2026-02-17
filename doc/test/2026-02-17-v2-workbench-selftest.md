# v2 工作台自测记录（2026-02-17）

## 执行信息
- 执行日期：2026-02-17
- 执行环境：Win11 + PowerShell（管理员）
- 前端入口端口：`10010`
- 后端入口端口：`10011`
- 自测方式：Playwright MCP 端到端页面操作

## 入口与降级策略
- 首选入口：`http://ainovel.seekerhut.com/workbench`
- 结果：域名在浏览器环境中报 `ERR_NAME_NOT_RESOLVED`
- 降级入口：`http://127.0.0.1:10010/workbench`
- 连通性确认：
  - `http://127.0.0.1:10010/workbench` -> 200
  - `http://127.0.0.1:10011/api/v3/api-docs` -> 200

## 测试数据
- `storyId`: `17f9df8a-2f88-4a8c-9b75-80f383129f5e`
- `manuscriptId`: `dde00ee8-b418-4411-b9cd-c3bc95f4dbe0`

## 文本输入（按功能语义填写）
- Lorebook 标题：`主角记忆缺口`
- Lorebook 正文：`第1章里，林渡在雨夜醒来时只记得“银鸢车站”四个字；第3章前不得直接恢复全部记忆。`

## 覆盖场景与结果
- 上下文记忆：创建 Lorebook、上下文预览 -> 通过
- 风格画像：新建画像、风格分析 -> 通过
- 分析能力：Beta Reader、连续性检查 -> 通过
- 版本管理：创建快照、查询版本、自动保存设置 -> 通过
- 导出系统：发起导出、任务列表、模板列表 -> 通过
- 多模型协作：模型列表、模型对比 -> 通过
- 工作台体验：布局创建、会话开始/结束 -> 通过

## 控制台与网络摘要
- 控制台：仅发现 React Router future flag warnings；未发现运行时 error。
- 网络：关键 v2 接口均有成功响应；未出现阻塞性 4xx/5xx 失败。

## 证据
- 截图：`tmp/playwright-v2-workbench-2026-02-17.png`

