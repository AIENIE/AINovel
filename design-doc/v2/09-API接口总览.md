# API 接口总览

## 文档信息

| 字段 | 值 |
|------|-----|
| 版本 | v2.0 |
| 日期 | 2026-02-17 |
| 说明 | 汇总 v2 全部新增 API 端点速查 |

---

## 1. 接口概览

### 1.1 统计

| 维度 | 数量 |
|------|------|
| 新增端点总数 | ~80 |
| 涉及模块 | 7 |
| GET 端点 | ~35 |
| POST 端点 | ~25 |
| PUT 端点 | ~15 |
| DELETE 端点 | ~8 |

### 1.2 全局约定

- Base URL: `/api/v2/`（沿用现有 `spring.mvc.servlet.path=/api`）
- 认证: Bearer Token (JWT)，Header `Authorization: Bearer <token>`
- Content-Type: `application/json`
- 分页: `?page=0&size=20&sort=createdAt,desc`
- 错误响应格式:

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "故事不存在",
  "details": { "storyId": "uuid-xxx" }
}
```

---

## 2. 端点速查表

| 方法 | 路径 | 所属模块 | 说明 | 认证 | 来源 |
|------|------|----------|------|------|------|
| GET | `/v2/stories/{storyId}/lorebook` | 上下文记忆 | 获取 Lorebook 条目列表 | 是 | 01 § 3.3 |
| POST | `/v2/stories/{storyId}/lorebook` | 上下文记忆 | 创建 Lorebook 条目 | 是 | 01 § 3.3 |
| PUT | `/v2/stories/{storyId}/lorebook/{entryId}` | 上下文记忆 | 更新 Lorebook 条目 | 是 | 01 § 3.3 |
| DELETE | `/v2/stories/{storyId}/lorebook/{entryId}` | 上下文记忆 | 删除 Lorebook 条目 | 是 | 01 § 3.3 |
| POST | `/v2/stories/{storyId}/lorebook/import` | 上下文记忆 | 批量导入 Lorebook 条目 | 是 | 01 § 3.3 |
| GET | `/v2/stories/{storyId}/graph` | 上下文记忆 | 获取完整图谱数据 | 是 | 01 § 3.3 |
| GET | `/v2/stories/{storyId}/graph/query` | 上下文记忆 | 查询子图 | 是 | 01 § 3.3 |
| POST | `/v2/stories/{storyId}/graph/sync` | 上下文记忆 | 手动触发图谱同步 | 是 | 01 § 3.3 |
| POST | `/v2/stories/{storyId}/extract-entities` | 上下文记忆 | 从文本提取实体 | 是 | 01 § 3.3 |
| GET | `/v2/stories/{storyId}/extractions` | 上下文记忆 | 获取待审核提取列表 | 是 | 01 § 3.3 |
| PUT | `/v2/stories/{storyId}/extractions/{id}/review` | 上下文记忆 | 审核实体提取 | 是 | 01 § 3.3 |
| GET | `/v2/stories/{storyId}/context/preview` | 上下文记忆 | 预览组装上下文 | 是 | 01 § 3.3 |
| GET | `/v2/stories/{storyId}/style-profiles` | 风格画像 | 获取风格画像列表 | 是 | 02 § 3.3 |
| POST | `/v2/stories/{storyId}/style-profiles` | 风格画像 | 创建风格画像 | 是 | 02 § 3.3 |
| PUT | `/v2/stories/{storyId}/style-profiles/{id}` | 风格画像 | 更新风格画像 | 是 | 02 § 3.3 |
| DELETE | `/v2/stories/{storyId}/style-profiles/{id}` | 风格画像 | 删除风格画像 | 是 | 02 § 3.3 |
| POST | `/v2/stories/{storyId}/style-profiles/{id}/activate` | 风格画像 | 激活风格画像 | 是 | 02 § 3.3 |
| POST | `/v2/style-analysis` | 风格画像 | 分析文本风格 | 是 | 02 § 3.3 |
| GET | `/v2/stories/{storyId}/character-voices` | 角色声音 | 获取角色声音列表 | 是 | 02 § 3.3 |
| POST | `/v2/stories/{storyId}/character-voices` | 角色声音 | 创建角色声音 | 是 | 02 § 3.3 |
| PUT | `/v2/stories/{storyId}/character-voices/{id}` | 角色声音 | 更新角色声音 | 是 | 02 § 3.3 |
| DELETE | `/v2/stories/{storyId}/character-voices/{id}` | 角色声音 | 删除角色声音 | 是 | 02 § 3.3 |
| POST | `/v2/stories/{storyId}/character-voices/{id}/generate` | 角色声音 | AI 生成角色声音 | 是 | 02 § 3.3 |
| POST | `/v2/stories/{storyId}/analysis/beta-reader` | Beta Reader | 触发 Beta Reader 分析 | 是 | 03 § 3.3 |
| POST | `/v2/stories/{storyId}/analysis/continuity-check` | 连续性检查 | 触发连续性检查 | 是 | 03 § 3.3 |
| GET | `/v2/stories/{storyId}/analysis/jobs` | Beta Reader | 查询分析任务列表 | 是 | 03 § 3.3 |
| GET | `/v2/stories/{storyId}/analysis/jobs/{jobId}` | Beta Reader | 查询单个任务状态 | 是 | 03 § 3.3 |
| GET | `/v2/stories/{storyId}/analysis/reports` | Beta Reader | 查询报告列表 | 是 | 03 § 3.3 |
| GET | `/v2/stories/{storyId}/analysis/reports/{reportId}` | Beta Reader | 获取完整报告 | 是 | 03 § 3.3 |
| GET | `/v2/stories/{storyId}/analysis/continuity-issues` | 连续性检查 | 查询问题列表 | 是 | 03 § 3.3 |
| PUT | `/v2/stories/{storyId}/analysis/continuity-issues/{issueId}` | 连续性检查 | 更新问题状态 | 是 | 03 § 3.3 |
| GET | `/v2/manuscripts/{manuscriptId}/versions` | 版本控制 | 获取版本列表 | 是 | 04 § 3.3 |
| POST | `/v2/manuscripts/{manuscriptId}/versions` | 版本控制 | 创建手动快照 | 是 | 04 § 3.3 |
| GET | `/v2/manuscripts/{manuscriptId}/versions/{versionId}` | 版本控制 | 获取版本详情 | 是 | 04 § 3.3 |
| POST | `/v2/manuscripts/{manuscriptId}/versions/{versionId}/rollback` | 版本控制 | 回滚到指定版本 | 是 | 04 § 3.3 |
| GET | `/v2/manuscripts/{manuscriptId}/versions/diff` | 版本控制 | 计算版本差异 | 是 | 04 § 3.3 |
| GET | `/v2/manuscripts/{manuscriptId}/branches` | 版本控制 | 获取分支列表 | 是 | 04 § 3.3 |
| POST | `/v2/manuscripts/{manuscriptId}/branches` | 版本控制 | 创建分支 | 是 | 04 § 3.3 |
| PUT | `/v2/manuscripts/{manuscriptId}/branches/{branchId}` | 版本控制 | 更新分支信息 | 是 | 04 § 3.3 |
| POST | `/v2/manuscripts/{manuscriptId}/branches/{branchId}/merge` | 版本控制 | 合并分支 | 是 | 04 § 3.3 |
| DELETE | `/v2/manuscripts/{manuscriptId}/branches/{branchId}` | 版本控制 | 废弃分支 | 是 | 04 § 3.3 |
| GET | `/v2/users/me/auto-save-config` | 版本控制 | 获取自动保存配置 | 是 | 04 § 3.3 |
| PUT | `/v2/users/me/auto-save-config` | 版本控制 | 更新自动保存配置 | 是 | 04 § 3.3 |
| POST | `/v2/manuscripts/{manuscriptId}/export` | 稿件导出 | 发起导出任务 | 是 | 05 § 3.3 |
| GET | `/v2/manuscripts/{manuscriptId}/export/jobs` | 稿件导出 | 查询导出任务列表 | 是 | 05 § 3.3 |
| GET | `/v2/manuscripts/{manuscriptId}/export/jobs/{jobId}` | 稿件导出 | 查询单个导出任务状态 | 是 | 05 § 3.3 |
| GET | `/v2/manuscripts/{manuscriptId}/export/jobs/{jobId}/download` | 稿件导出 | 下载导出文件 | 是 | 05 § 3.3 |
| GET | `/v2/export-templates` | 稿件导出 | 查询导出模板列表 | 是 | 05 § 3.3 |
| POST | `/v2/export-templates` | 稿件导出 | 创建导出模板 | 是 | 05 § 3.3 |
| PUT | `/v2/export-templates/{id}` | 稿件导出 | 更新导出模板 | 是 | 05 § 3.3 |

| DELETE | `/v2/export-templates/{id}` | 稿件导出 | 删除导出模板 | 是 | 05 § 3.3 |
| GET | `/v2/models` | 多模型协作 | 获取可用模型列表 | 是 | 06 § 3.3 |
| GET | `/v2/models/{modelKey}` | 多模型协作 | 获取单个模型详情 | 是 | 06 § 3.3 |
| GET | `/v2/admin/model-routing` | 多模型协作 | 获取所有路由规则 | 管理员 | 06 § 3.3 |
| PUT | `/v2/admin/model-routing/{taskType}` | 多模型协作 | 更新路由规则 | 管理员 | 06 § 3.3 |
| GET | `/v2/users/me/model-preferences` | 多模型协作 | 获取用户模型偏好 | 是 | 06 § 3.3 |
| PUT | `/v2/users/me/model-preferences/{taskType}` | 多模型协作 | 设置模型偏好 | 是 | 06 § 3.3 |
| DELETE | `/v2/users/me/model-preferences/{taskType}` | 多模型协作 | 重置为系统默认 | 是 | 06 § 3.3 |
| GET | `/v2/users/me/model-usage` | 多模型协作 | 用量汇总 | 是 | 06 § 3.3 |
| GET | `/v2/users/me/model-usage/details` | 多模型协作 | 详细调用日志 | 是 | 06 § 3.3 |
| POST | `/v2/stories/{storyId}/compare-models` | 多模型协作 | 双模型对比生成 | 是 | 06 § 3.3 |
| GET | `/v2/users/me/workspace-layouts` | 工作台 | 获取布局列表 | 是 | 07 § 3.3 |
| POST | `/v2/users/me/workspace-layouts` | 工作台 | 创建布局 | 是 | 07 § 3.3 |
| PUT | `/v2/users/me/workspace-layouts/{id}` | 工作台 | 更新布局 | 是 | 07 § 3.3 |
| DELETE | `/v2/users/me/workspace-layouts/{id}` | 工作台 | 删除布局 | 是 | 07 § 3.3 |
| POST | `/v2/users/me/workspace-layouts/{id}/activate` | 工作台 | 激活布局 | 是 | 07 § 3.3 |
| POST | `/v2/writing-sessions/start` | 工作台 | 开始写作会话 | 是 | 07 § 3.3 |
| PUT | `/v2/writing-sessions/{id}/heartbeat` | 工作台 | 会话心跳更新 | 是 | 07 § 3.3 |
| POST | `/v2/writing-sessions/{id}/end` | 工作台 | 结束写作会话 | 是 | 07 § 3.3 |
| GET | `/v2/writing-sessions/stats` | 工作台 | 写作统计数据 | 是 | 07 § 3.3 |
| GET | `/v2/users/me/writing-goals` | 工作台 | 获取写作目标列表 | 是 | 07 § 3.3 |
| POST | `/v2/users/me/writing-goals` | 工作台 | 创建写作目标 | 是 | 07 § 3.3 |
| PUT | `/v2/users/me/writing-goals/{id}` | 工作台 | 更新写作目标 | 是 | 07 § 3.3 |
| DELETE | `/v2/users/me/writing-goals/{id}` | 工作台 | 删除写作目标 | 是 | 07 § 3.3 |
| GET | `/v2/users/me/shortcuts` | 工作台 | 获取快捷键配置 | 是 | 07 § 3.3 |
| PUT | `/v2/users/me/shortcuts` | 工作台 | 批量更新快捷键 | 是 | 07 § 3.3 |

---

## 3. 按模块分组详情

### 3.1 上下文记忆系统（→ 见 `01-上下文记忆系统.md` § 3.3）

12 个端点，覆盖 Lorebook CRUD、知识图谱查询/同步、实体提取与审核、上下文预览。

核心流程：
- Lorebook 管理：标准 CRUD + 批量导入
- 图谱操作：全图获取（可视化用）、子图查询、手动同步
- 实体提取：AI 提取 → 人工审核 → 合并到 Lorebook
- 上下文预览：预览指定章节/场景的组装上下文

### 3.2 风格画像与角色声音（→ 见 `02-风格画像与角色声音.md` § 3.3）

11 个端点，覆盖风格画像 CRUD + 激活、风格分析、角色声音 CRUD + AI 生成。

核心流程：
- 风格画像：创建/编辑/激活，每个故事同时只有一个激活画像
- 风格分析：提交文本 → 异步分析 → 返回维度评分
- 角色声音：基于角色卡创建声音配置，支持 AI 自动生成

### 3.3 AI Beta Reader 与连续性检查（→ 见 `03-AI-Beta-Reader与连续性检查.md` § 3.3）

8 个端点，覆盖分析任务触发/查询、报告查看、连续性问题管理。

核心流程：
- 触发分析 → 异步任务 → 轮询进度 → 查看报告
- 连续性问题独立管理：筛选/确认/解决/标记误报

### 3.4 版本控制系统（→ 见 `04-版本控制系统.md` § 3.3）

12 个端点，覆盖版本快照 CRUD、分支管理、差异对比、自动保存配置。

核心流程：
- 版本：列表/创建快照/查看/回滚/差异对比
- 分支：创建/更新/合并/废弃
- 配置：自动保存间隔和上限

### 3.5 稿件导出系统（→ 见 `05-稿件导出系统.md` § 3.3）

8 个端点，覆盖导出任务管理和模板管理。

核心流程：
- 发起导出 → 异步处理 → 轮询状态 → 下载文件
- 模板：系统预设 + 用户自定义

### 3.6 多模型协作（→ 见 `06-多模型协作.md` § 3.3）

11 个端点，覆盖模型查询、路由管理（管理员）、用户偏好、用量统计、对比生成。

核心流程：
- 模型发现：从 ai-service 同步可用模型
- 路由管理：管理员配置任务-模型映射
- 用户偏好：覆盖系统默认
- 对比生成：同一输入用两个模型生成，用户选择更好的结果

### 3.7 工作台与体验优化（→ 见 `07-工作台与体验优化.md` § 3.3）

16 个端点，覆盖布局管理、写作会话、写作目标、快捷键配置。

核心流程：
- 布局：多套布局方案切换
- 会话：开始 → 心跳 → 结束，自动统计字数和时长
- 目标：每日/会话/总字数/截稿日期
- 快捷键：用户自定义覆盖系统默认

---

## 4. 错误码汇总

| 错误码 | HTTP 状态 | 说明 | 涉及模块 |
|--------|-----------|------|----------|
| `RESOURCE_NOT_FOUND` | 404 | 资源不存在（故事/稿件/条目等） | 全部 |
| `ACCESS_DENIED` | 403 | 无权访问该资源 | 全部 |
| `VALIDATION_ERROR` | 400 | 请求参数校验失败 | 全部 |
| `DUPLICATE_ENTRY` | 409 | 重复创建（如同名分支） | 版本控制 |
| `JOB_IN_PROGRESS` | 409 | 已有同类任务在执行中 | Beta Reader, 导出 |
| `MODEL_UNAVAILABLE` | 503 | 指定模型当前不可用 | 多模型协作 |
| `TOKEN_BUDGET_EXCEEDED` | 400 | 上下文 Token 超出预算 | 上下文记忆 |
| `MERGE_CONFLICT` | 409 | 分支合并存在冲突 | 版本控制 |
| `EXPORT_EXPIRED` | 410 | 导出文件已过期 | 稿件导出 |
| `ANALYSIS_FAILED` | 500 | AI 分析过程失败 | Beta Reader |
| `GRAPH_SYNC_ERROR` | 500 | Neo4j 同步失败 | 上下文记忆 |

---

## 5. 认证与权限矩阵

| 端点模式 | 认证要求 | 角色要求 |
|----------|----------|----------|
| `/v2/stories/{storyId}/**` | Bearer Token | 故事所有者 |
| `/v2/manuscripts/{manuscriptId}/**` | Bearer Token | 稿件所有者（通过 outline → story 链路校验） |
| `/v2/users/me/**` | Bearer Token | 当前用户 |
| `/v2/writing-sessions/**` | Bearer Token | 当前用户 |
| `/v2/export-templates` (GET) | Bearer Token | 任意用户（可见系统模板 + 自己的模板） |
| `/v2/export-templates` (POST/PUT/DELETE) | Bearer Token | 模板所有者 |
| `/v2/models/**` | Bearer Token | 任意用户（只读） |
| `/v2/admin/model-routing/**` | Bearer Token | ADMIN 角色 |
| `/v2/style-analysis` | Bearer Token | 任意用户 |

---

## 6. 与 v1 接口的兼容性说明

1. v1 接口（`/api/v1/*`）保持不变，v2 接口为纯增量新增。
2. v2 接口统一使用 `/api/v2/` 前缀，与 v1 路径不冲突。
3. 认证机制复用现有 JWT + SSO 体系，无需额外登录。
4. 前端可渐进式迁移：先接入 v2 新功能，v1 页面保持原有调用。
5. 数据层共享同一 MySQL 数据库，v2 新表通过 FK 关联现有 v1 表。
