# AINovel v2 前端功能对齐验收报告（2026-02-18）

## 1. 验收范围

对照 `design-doc/v2` 中 01~07 的前端能力，核对当前实现是否可用且符合文档预期；若发现不一致则修复并回归。

- 文档基准：`design-doc/v2/01-上下文记忆系统.md` ~ `design-doc/v2/07-工作台与体验优化.md`
- 验证环境：本机通过 `http://ainovel.seekerhut.com`（nginx + hosts）访问
- 证据目录：`artifacts/test/2026-02-18-v2-alignment/`

## 2. 结论

01~07 功能均已完成并可正常使用，当前前端实现与 `design-doc/v2` 预期一致。

本轮新增修复 2 项：

1. 设置页兼容 `?tab=model`（历史链接）正确跳转到“模型偏好”。
2. 工作台顶层 9 标签在移动端改为横向滚动，避免小屏挤压重叠。

## 3. 逐项对齐结果（01~07）

| 模块 | 结果 | 证据 |
|---|---|---|
| 00-总体入口与导航 | PASS | `artifacts/test/2026-02-18-v2-alignment/00-home.png` |
| 01-上下文记忆系统（Lorebook + 关系） | PASS | `artifacts/test/2026-02-18-v2-alignment/01-lorebook-preview.png`, `artifacts/test/2026-02-18-v2-alignment/01-graph-relationships.png` |
| 02-风格画像与角色声音 | PASS | `artifacts/test/2026-02-18-v2-alignment/02-style-profiles-voices.png` |
| 03-AI Beta Reader 与连续性检查 | PASS | `artifacts/test/2026-02-18-v2-alignment/03-analysis-dashboard.png` |
| 04-版本控制系统 | PASS | `artifacts/test/2026-02-18-v2-alignment/04-version-control.png` |
| 05-稿件导出系统 | PASS | `artifacts/test/2026-02-18-v2-alignment/05-export-system.png` |
| 06-多模型协作（偏好/成本/A-B） | PASS | `artifacts/test/2026-02-18-v2-alignment/06-model-preferences.png` |
| 07-工作台与体验优化（多面板/快捷键/状态栏/目标）桌面 | PASS | `artifacts/test/2026-02-18-v2-alignment/07-workbench-experience.png` |
| 07-工作台与体验优化（移动端） | PASS | `artifacts/test/2026-02-18-v2-alignment/07-workbench-mobile.png` |

## 4. 本轮修复与代码位置

### 4.1 设置页 tab 参数兼容修复

- 文件：`frontend/src/pages/Settings/Settings.tsx`
- 变更：新增 tab 归一化逻辑，支持 `model -> models`，并对非法值回退到 `workspace`。

回归验证：

- `http://ainovel.seekerhut.com/settings?tab=model` 激活“模型偏好”
- `http://ainovel.seekerhut.com/settings?tab=models` 激活“模型偏好”

### 4.2 工作台移动端标签栏可用性修复

- 文件：`frontend/src/pages/Workbench/Workbench.tsx`
- 变更：将顶层 `TabsList` 调整为横向滚动 (`overflow-x-auto + shrink-0`)；避免小屏挤压重叠。

回归验证：

- 移动端视口（390x844）下顶部标签可读且可操作。
- 小说创作页内 `大纲/编辑/参考` 三分栏移动端切换正常。

### 4.3 单元测试补充

- 新增：`frontend/src/pages/Settings/__tests__/settings-tabs.test.ts`
- 覆盖：`model -> models`、合法 tab 透传、非法/空值回退。

## 5. 构建与测试结果

在 `frontend/` 执行：

- `npm run test -- --run`：通过（3 files, 7 tests）
- `npm run build`：通过

备注：Vite 输出仍有既有警告（`baseline-browser-mapping` 数据过旧、chunk > 500KB），不影响本次功能一致性结论。

## 6. 域名 / hosts / nginx 复核

### 6.1 hosts

`C:\Windows\System32\drivers\etc\hosts` 已包含：

- `127.0.0.1 ainovel.seekerhut.com`
- `127.0.0.1 ainovel.aienie.com`

### 6.2 nginx

`nginx -T` 输出显示 `server_name ainovel.seekerhut.com ainovel.aienie.com`，并配置：

- `/` -> `http://127.0.0.1:10010/`
- `/api/` -> `http://127.0.0.1:10011/api/`

且 `nginx -t` 语法检查通过。

### 6.3 可达性

- `http://ainovel.seekerhut.com` 返回 `200`
- `http://127.0.0.1:10010` 返回 `200`
- `http://ainovel.seekerhut.com/api/` 返回 `403`
- `http://127.0.0.1:10011/api/` 返回 `403`

说明域名反向代理链路与本地直连行为一致，可通过域名正常访问前后端。
