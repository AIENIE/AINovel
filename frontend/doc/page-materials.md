# 素材库页（MaterialPage）

- **路由/文件**：`/materials`
- **对应设计稿文件**：`src/pages/Material/MaterialPage.tsx`
- **子组件目录**：`src/pages/Material/tabs/`
- **权限**：受保护；Tab 显示受 `useCanPerform('workspace:write')` 控制（无写权限仅显示“素材列表”）。

## Tab：创建素材（MaterialCreateForm）
- **对应文件**：`src/pages/Material/tabs/MaterialCreateForm.tsx`
- **布局**：表单（标题、类型、概要、正文、标签）。
- **接口**：`POST /api/v1/materials` Body `{ title, type, summary?, content, tags? }`，返回 `MaterialResponse`。成功后触发 `material:refresh` 全局事件刷新列表。

## Tab：上传文件（MaterialUpload）
- **对应文件**：`src/pages/Material/tabs/MaterialUpload.tsx`
- **布局**：文件选择区上传 TXT/Markdown/PDF/DOC/DOCX、按钮“开始上传”、状态进度条、最近一次上传信息。
- **流程与接口**：
  - 选文件（支持 `.txt`、`.md`、`.pdf`、`.doc`、`.docx`，不立即上传）。
  - 点击上传 → `POST /api/v1/materials/upload`（FormData `file`）。返回 `FileImportJob { id,status,... }`。
  - 轮询 `GET /api/v1/materials/upload/{jobId}` 直到 status ∈ {COMPLETED,FAILED}。
  - 完成后派发 `material:refresh`。
  - 后端解析前会校验扩展名与内容大小；当前素材解析上限默认 10MiB，超过限制会返回 400。

## Tab：素材审核（ReviewDashboard）
- **对应文件**：`src/pages/Material/tabs/ReviewDashboard.tsx`
- **布局**：左侧待审列表，右侧“原始内容”+“解析结果校对”表单，操作按钮“批准/驳回/刷新”。
- **接口**：
  - 拉取待审：`GET /api/v1/materials/review/pending`（前端函数名 `fetchPendingMaterials`）。
  - 批准：`POST /api/v1/materials/{id}/review/approve` Body `{ title, summary, tags, type, entitiesJson, reviewNotes }`。
  - 驳回：`POST /api/v1/materials/{id}/review/reject` 同上。

## Tab：素材列表（MaterialList）
- **对应文件**：`src/pages/Material/tabs/MaterialList.tsx`
- **布局**：筛选/排序工具条 + 表格（标题/类型/标签/状态/操作），抽屉查看或编辑详情；Modal 显示相似素材、引用历史。
- **筛选与排序**：
  - 自由搜索匹配标题、概要、正文和标签。
  - 支持按状态（全部/待审核/已入库/已驳回）、类型（全部/文本/图片/链接）和标签关键词筛选。
  - 支持按创建时间或标题排序，并可切换升序/降序；当前素材类型仅包含 `createdAt`，未使用更新时间排序。
- **接口**：
  - 列表：`GET /api/v1/materials`。
  - 详情：`GET /api/v1/materials/{id}`；更新：`PUT /api/v1/materials/{id}` Body `{ title?, type?, summary?, tags?, content?, status?, entitiesJson? }`；删除：`DELETE /api/v1/materials/{id}`。
  - 查重：`POST /api/v1/materials/find-duplicates`（空 body），返回候选对、分数和原因；合并：`POST /api/v1/materials/merge` Body `{ sourceMaterialId, targetMaterialId, mergeTags?, mergeSummaryWhenEmpty?, note? }`。
  - 引用历史：`GET /api/v1/materials/{id}/citations`，返回被引用的故事、稿件、场景和命中片段。
- **事件**：监听 `material:refresh` 以自动刷新表格。

## 公用：检索与自动提示
- **检索面板**（工作台 Tab 复用）：`POST /api/v1/materials/search` Body `{ query, limit }` → `MaterialSearchResult[]`。
- **自动提示**：写作页 `useAutoSuggestions` → `POST /api/v1/materials/editor/auto-hints` Body `{ text, workspaceId?, limit? }`。

## 待完善
- 合并/删除操作的权限提示与审计；
- 审核批量操作、评论记录。

## 开发对接指南 (Mock vs Real)

### 1. 文件上传与解析
- **当前实现**：`api.materials.upload` 使用 `FormData` 直连后端 `/api/v1/materials/upload`。后端对 `.txt`/`.md` 直接按 UTF-8 读取，对 `.pdf`/`.doc`/`.docx` 使用 Apache Tika 提取正文，生成待审素材并返回 Job ID。
- **异步处理**：当前后端会先创建导入任务与待审素材，前端继续轮询 `GET /api/v1/materials/upload/{jobId}`，直到状态变为 `completed` 或 `failed`。

### 2. 向量检索
- **当前实现**：`api.materials.search` 直连后端混合检索接口，后端结合关键词和向量结果；向量服务不可用时保留关键词 fallback。
- **前端展示**：
  - 使用后端返回的 `score`、`source`、`chunkSeq` 和 `matchReasons` 展示命中质量。
  - 查重和引用历史已直连后端，不再是空数据占位。

### 3. 审核流程
- **当前 Mock**：审核操作仅修改内存中对象的 `status` 字段。
- **真实对接**：
  - 批准操作可能触发“建立索引”的动作（将素材写入向量数据库），这可能也是一个耗时操作，需注意 UI 的 Loading 反馈。
