# 2026-07-24 agent-browser 全系统端到端测试问题记录

> 临时测试记录。测试目标：`https://ainovel.localhut.com`。管理员页面按用户要求暂不纳入；其余普通用户可接触功能逐项探索。测试期间允许创建、编辑、删除测试数据。

## 测试信息

- 测试工具：agent-browser，独立 session
- 测试账号：普通用户真实 SSO 会话；不在本文记录凭据或会话状态
- 证据目录：`/home/duwei/tmp/ainovel-full-e2e-20260724/`
- 测试开始：2026-07-24
- 当前状态：已完成（普通用户范围；管理员页面按要求未测）

## 覆盖矩阵

| 区域 | 入口/能力 | 状态 | 备注 |
| --- | --- | --- | --- |
| 公共页面 | 首页、定价 | 已测 | 首页“查看演示”见 ISSUE-001 |
| 鉴权 | 登录、注册、登出/会话恢复 | 已测 | 真实 SSO 路径、注册入口 |
| Dashboard | 小说、世界、最近项目、导航 | 已测 |  |
| 小说管理 | 新建、编辑、简介、删除、进入工作台 | 已测 | 简介编辑见 ISSUE-002；测试故事已删除 |
| 引导创作 | 手动/自动、草稿恢复、候选、大纲展开、物化 | 已测 | 自动生成异常与 ISSUE-005 同族 |
| 世界观 | 新建、编辑、模块、选择、删除 | 已测 | 预检/发布见 ISSUE-003；测试世界已删除 |
| 工作台 | 构思、故事管理、大纲、正文、场景、素材、知识图谱、分析、质量、版本、导出、快捷操作 | 已测 | ISSUE-002、005、006、008-012 |
| 素材库 | 新建、列表、详情、编辑、删除、审核/检索 | 已测 | 操作菜单见 ISSUE-004；批量导入见 ISSUE-007 |
| 设置 | 模型偏好、风格档案、工作区体验、提示词、世界提示词帮助 | 已测 | 有待复核持久化现象 |
| 个人中心 | 资料、积分/账单、兑换/充值入口 | 已测 | 含无效兑换码校验 |
| G2 评审 | 匿名评审入口与投票 | 已测入口 | 当前没有开放活动，提交按钮正确禁用，无法继续投票闭环 |
| 响应式 | 390x844 与桌面关键页面 | 已测 | 移动端设置标签重叠见待复核现象 |
| 管理员页面 | admin 页面及管理员专属能力 | 按要求不测 | 不计入普通用户覆盖范围 |

## 问题汇总

| 编号 | 严重级别 | 页面/能力 | 状态 |
| --- | --- | --- | --- |
| ISSUE-001 | medium | 首页“查看演示”按钮 | 待修复 |
| ISSUE-002 | high | 工作台“故事管理”编辑故事简介 | 待修复 |
| ISSUE-003 | high | 世界编辑“预检/发布” | 待修复 |
| ISSUE-004 | high | 素材列表行操作菜单 | 待修复 |
| ISSUE-005 | critical | 工作台“故事构思”生成结构骨架 | 待修复 |
| ISSUE-006 | high | 工作台“小说创作”生成本场景 | 待修复 |
| ISSUE-007 | high | 素材批量导入任务轮询 | 待修复 |
| ISSUE-008 | high | 工作台“素材检索”接口 500 且错误显示为空结果 | 待修复 |
| ISSUE-009 | high | 小说创作正文保存后刷新为空 | 待修复 |
| ISSUE-010 | high | 小说创作历史版本加载失败 | 待修复 |
| ISSUE-011 | high | 剧情候选采纳返回 Internal server error | 待修复 |
| ISSUE-012 | medium | 小说创作 goals 标签被专注模式按钮遮挡 | 待修复 |

## 详细问题

<!-- 每个问题发现并复现后立即追加，保留截图/视频/控制台或网络证据路径。 -->

### ISSUE-001：首页“查看演示”按钮无任何可见作用

- 严重级别：`medium`
- 分类：`functional / ux`
- 页面：`/`
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/issue-001-before.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/issue-001-after.png`
- 视频：尝试生成 `/home/duwei/tmp/ainovel-full-e2e-20260724/videos/issue-001-home-demo.webm`，但主机缺少 `ffmpeg`，未产出可用视频
- 复现次数：2 次，结果一致

复现步骤：

1. 打开首页，确认“查看演示”按钮可见（`issue-001-before.png`）。
2. 点击“查看演示”。
3. 等待页面完成交互，页面仍停留在首页，未出现演示弹层、视频、跳转或状态反馈（`issue-001-after.png`）。

预期：按钮应打开产品演示或至少给出明确的加载/不可用反馈。

实际：点击无可见效果，URL、页面内容和交互树均未变化；本次点击未产生控制台错误。

### ISSUE-002：“故事管理”中的编辑故事简介按钮完全无效

- 严重级别：`high`
- 分类：`functional`
- 页面：`/workbench?id=fd394ed6-ce91-46e8-9027-20bc9cab9ba6`，工作台 > 故事管理
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/issue-002-before.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/issue-002-after.png`
- 复现次数：2 次，结果一致

复现步骤：

1. 创建并进入测试故事 `E2E全量测试小说-20260724`，打开“故事管理”。
2. 在当前故事标题右侧点击铅笔图标按钮；该按钮没有可访问名称，但 DOM 图标为 `square-pen`（`issue-002-before.png`）。
3. 等待 1 秒，观察页面（`issue-002-after.png`）。
4. 再次点击同一铅笔按钮并重复观察。

预期：打开故事标题/简介编辑控件，允许修改并保存简介，刷新后仍保持修改结果。

实际：两次点击均无任何可见反馈，没有编辑表单、对话框、Toast、URL 变化或可观察的请求；当前简介无法通过该按钮编辑。

### ISSUE-003：世界编辑“预检/发布”稳定返回 Internal server error

- 严重级别：`high`
- 分类：`functional / error-handling`
- 页面：`/world-editor?id=6b21d5e2-0cd0-4add-be07-1abd2d20c658`
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/issue-004-before.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/issue-004-after.png`
- 复现次数：2 次，结果一致

复现步骤：

1. 创建 `E2E全量测试世界-20260724`，填写世界简介、创作意图和主题标签。
2. 分别打开“地理环境”“社会体系”“魔法/科技”，填写各模块字段并点击“保存”，页面提示“保存成功”。
3. 点击右上角“预检/发布”，等待请求结束。
4. 页面提示“预检失败 / Internal server error”；再次点击并等待，结果相同。

预期：预检应返回通过结果，或在确有缺失时指出具体字段和修复方式；不应向普通用户暴露通用 500 错误。

实际：完整填写并保存后仍稳定返回 `Internal server error`，未进入发布或显示具体校验信息。

### ISSUE-004：素材列表三点操作按钮无任何响应

- 严重级别：`high`
- 分类：`functional / crud`
- 页面：`/materials`，素材列表
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/46-material-actions-click.png`、`47-material-actions-css.png`
- 复现次数：多次，结果一致

复现步骤：

1. 在“手动创建”中创建 `E2E全量测试素材-20260724`，页面提示“素材创建成功”，列表显示该行。
2. 点击该行“操作”列中的三点按钮。
3. 等待菜单出现；再重复点击一次，并用元素 CSS 定位再次点击。

预期：出现编辑、删除或其他素材操作菜单，至少能完成素材的后续管理。

实际：按钮无任何可见响应，没有菜单、对话框、Toast、URL 变化或网络请求；素材无法从列表入口编辑或删除。

### ISSUE-005：故事构思生成提交后卡在 0/3，结果区崩溃

- 严重级别：`critical`
- 分类：`functional / console`
- 页面：`/workbench?id=fd394ed6-ce91-46e8-9027-20bc9cab9ba6`，工作台 > 故事构思
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/61-conception-generated.png`、`62-conception-generated-complete.png`、`63-conception-generated-later.png`
- 网络证据：操作 `34315354-f24f-4ddb-980a-07f467b3649d` 的 events/status 请求返回 200，但 UI 长时间保持 `0/3`；控制台出现 `TypeError: Cannot read properties of undefined (reading 'map')`

复现步骤：

1. 打开工作台“故事构思”，填写核心创意、类型“科幻”、基调“暗黑/压抑”，并展开结构引导填写三个可选字段。
2. 点击“生成结构骨架”。页面显示“生成故事构思与初始角色”、`0/3` 和实时 token。
3. 等待约 20 秒，期间进度仍为 `0/3`、剩余 3 步，结果区没有结构骨架（截图 61-63）。
4. 检查浏览器控制台，出现 `TypeError: Cannot read properties of undefined (reading 'map')`。

预期：任务完成后展示结构骨架、双轨反转和伏笔链；失败时应显示可理解的错误和重试入口。

实际：后端任务有事件/状态响应，但前端在处理结果时抛出运行时异常，结果区为空，用户无法继续使用该功能。

### ISSUE-006：正文生成持续停在 0/5，正文不回填且没有失败反馈

- 严重级别：`high`
- 分类：`functional / performance / error-handling`
- 页面：`/workbench?id=fd394ed6-ce91-46e8-9027-20bc9cab9ba6`，工作台 > 小说创作
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/80-manuscript-generation-progress.png`、`81-manuscript-generation-later.png`、`82-manuscript-generation-timeout.png`
- 操作：快速模式，测试章节 `E2E正文生成场景`
- 网络证据：操作 `feda96fa-e319-4a0b-a11a-63a3fb3f9719` 的 events 请求返回 200并持续有 token，但未出现正文完成状态或回填结果

复现步骤：

1. 在测试故事中创建章节和场景 `E2E正文生成场景`，确认“生成本场景”可用。
2. 选择“快速”，点击“生成本场景”。
3. 等待约 80 秒；进度一直是“已完成 0 步 / 剩余 5 步”，token 从 0 增长到约 3,646，但编辑器仍为 0 字、净增 0。
4. 页面没有失败、超时、取消或重试提示，按钮仍显示“生成本场景”。

预期：生成完成后正文回填并触发质量门禁；失败或超时时应明确展示状态、可重试/取消，并避免用户误以为任务仍健康运行。

实际：任务长时间只产出 token 计数，正文没有回填，用户无法继续正文闭环。

### ISSUE-007：素材批量导入轮询接口返回 500，界面永久停留上传处理中

- 严重级别：`high`
- 分类：`functional / error-handling / data-integrity`
- 页面：`/materials`，素材库 > 批量导入
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/51-material-batch-result.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/52-material-batch-complete.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/screenshots/issue-005-retry.png`
- 网络证据：批量导入创建任务后，`/api/v1/materials/upload/{jobId}` 轮询请求返回 HTTP 500；同时生成了状态为 `pending` 的素材记录
- 复现次数：至少 2 次轮询，结果一致

复现步骤：

1. 在素材库进入“批量导入”，上传测试文本文件并提交。
2. 页面显示上传任务已提交，但随后持续显示“上传处理中”。
3. 等待轮询并检查网络请求，上传状态接口返回 `500 Internal Server Error`。
4. 页面没有失败、重试或取消入口，数据库/列表中留下 `pending` 素材记录。

预期：任务完成后显示导入数量和结果；任务失败时应明确提示失败原因并提供重试/清理入口，不能无限轮询或留下不可管理的悬挂记录。

实际：状态轮询稳定返回 500，界面永久停留上传处理中，用户无法知道导入是否成功，也无法从当前界面处理悬挂任务。

### ISSUE-008：工作台“素材检索”搜索接口返回 500，界面误显示“未找到相关素材”

- 严重级别：`high`
- 分类：`functional / error-handling`
- 页面：`/workbench?id=fd394ed6-ce91-46e8-9027-20bc9cab9ba6`，工作台 > 素材检索
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/83-workbench-material-search-no-result.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/84-workbench-material-search-error.png`
- 网络证据：`POST /api/v1/materials/search` 对已有测试素材和不存在素材均返回 HTTP 500
- 复现次数：2 个查询各 1 次，结果一致

复现步骤：

1. 打开工作台“素材检索”，输入已有测试素材 `E2E全量测试素材-20260724` 并提交。
2. 页面没有结果；随后输入不存在的素材 `不存在的素材-xyz` 并按 Enter。
3. 页面显示“未找到相关素材”，但网络层显示两次搜索请求均为 HTTP 500。

预期：已有素材应展示可检索结果；不存在素材应在接口成功返回空集合时显示空状态；服务错误应显示“检索失败”及重试入口。

实际：检索服务报 500，前端把服务故障错误地呈现为“未找到相关素材”，既无法查到已有素材，也无法区分空结果和系统故障。

### ISSUE-009：正文点击“保存”提示成功，但刷新后编辑器为空

- 严重级别：`high`
- 分类：`functional / data-integrity`
- 页面：`/workbench?id=8af794f4-db70-4081-b81b-8cd84165b5409`，工作台 > 小说创作
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/99-manuscript-saved.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/100-manuscript-after-refresh.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/101-manuscript-after-refresh-wait.png`
- 复现次数：首次完整正文保存后复现 1 次；后续短文本另有保存成功记录，但未替代本次失败证据

复现步骤：

1. 选中测试场景 `E2E正文手动编辑场景-20260724`，在编辑器输入三段正文。
2. 点击“保存”，页面显示“已保存”（截图 99）。
3. 重新打开同一工作台 URL，等待网络空闲并继续等待 2 秒（截图 100、101）。

预期：编辑器应回填刚保存的正文，且状态应为已保存。

实际：场景仍在左侧列表，但编辑器保持空白，DOM 内容长度为 1（仅编辑器换行）；页面未给出保存失败提示，用户无法确认正文是否真正持久化。

### ISSUE-010：历史版本打开后稳定提示加载版本失败

- 严重级别：`high`
- 分类：`functional / error-handling`
- 页面：`/workbench?id=8af794f4-db70-4081-b81b-8cd84165b5409`，工作台 > 小说创作 > 历史版本
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/103-manuscript-history.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/104-manuscript-history-refresh.png`
- 复现次数：打开历史版本 1 次、点击版本面板“刷新”1 次

复现步骤：

1. 在测试场景中保存两个不同版本的正文。
2. 点击“历史版本”。
3. 版本面板提示“加载版本失败 / Internal server error”；点击“刷新”后仍没有可用版本列表。

预期：显示可恢复、可对比的版本列表，并允许用户选择版本。

实际：版本加载失败且没有可用版本；同一面板中的分支创建、切换、合并功能仍可操作，但不能弥补历史版本不可用的问题。

### ISSUE-011：剧情候选生成成功，但“采纳候选”返回 Internal server error

- 严重级别：`high`
- 分类：`functional / error-handling`
- 页面：`/workbench?id=8af794f4-db70-4081-b81b-8cd84165b5409`，工作台 > 小说创作 > plot
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/129-manuscript-candidates-later.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/130-manuscript-candidate-adopted.png`

复现步骤：

1. 在 plot 面板运行“文本 Slop 诊断”和剧情诊断。
2. 点击“生成候选”，等待候选修订文本出现（截图 129）。
3. 点击“采纳候选”。

预期：候选文本被应用到编辑器，或明确提示需要先保存/选择目标。

实际：页面提示“采纳候选失败 / Internal server error”，正文保持原文，用户无法使用生成结果。

### ISSUE-012：小说创作右侧 goals 标签被“专注模式”按钮遮挡

- 严重级别：`medium`
- 分类：`functional / ux / accessibility`
- 页面：`/workbench?id=8af794f4-db70-4081-b81b-8cd84165b5409`，工作台 > 小说创作右侧栏
- 证据：`/home/duwei/tmp/ainovel-full-e2e-20260724/132-manuscript-goals-panel.png`、`/home/duwei/tmp/ainovel-full-e2e-20260724/135-manuscript-goal-created.png`
- 复现次数：鼠标点击及语义定位点击均复现被遮挡；键盘聚焦后按 Enter 可绕过

复现步骤：

1. 打开小说创作右侧栏并切换到“统计”面板。
2. 点击右侧标签栏最末的 `goals`。
3. 浏览器报告点击点被 `专注模式` 按钮覆盖，页面仍停留在“统计”面板。

预期：每个右侧标签都能通过鼠标点击打开。

实际：在 1440px 桌面视口下，`goals` 标签位于专注模式按钮的点击区域下方，鼠标无法打开；将焦点移到该标签后按 Enter 可以打开并完成目标 CRUD，因此属于鼠标交互/布局缺陷。

## 待进一步复核的异常现象

以下现象已在测试过程中观察到，但本轮没有再创建额外数据进行第二次独立复现，暂不单列为 ISSUE 编号：

- 删除世界后短暂跳转到已删除世界的编辑页，刷新世界列表后才消失：`93-world-delete-result.png`。
- 模型偏好保存为 `901/1301` 后离开页面，再次选择同一任务恢复为 `800/1200`，疑似保存未持久化：`99-model-preference-after-reload.png`。
- 390px 移动端设置页顶部标签出现互相重叠：`110-mobile-settings.png`。
- 创建小说时勾选自动生成，任务保持 `0/3` 且最终工作台为空；与 ISSUE-005 的生成链路异常相关：`112-new-story-after-create.png`。
- 世界观提示词的“最终整合模板”标签页为空。
- 首次场景结构保存并刷新后，重新进入大纲编排未显示已保存的场景节点；随后重新创建并保存场景后正文编辑链路可见，需进一步区分状态恢复问题与首次保存时序问题：`90-outline-scene-before-save.png`、`93-outline-scene-save-result.png`、`94-outline-scene-after-refresh.png`。

## 清理记录

- 测试故事 `E2E全量测试小说-20260724`、`E2E手动正文小说-20260724` 已从小说管理删除；测试世界 `E2E全量测试世界-20260724` 已删除。
- 临时风格画像、角色声音、布局、写作目标、导出模板和分支测试数据已清理；测试期间创建的正文导出任务均已完成。
- 素材库仍残留 `E2E全量测试素材-20260724` 1 条已入库记录，以及 `e2e-material-import-20260724.txt` 2 条 `pending` 记录。素材列表三点操作菜单仍无响应（ISSUE-004），当前无法通过普通用户 UI 删除，故保留并在此标记原因。
- 浏览器证据：保留在 `/home/duwei/tmp/ainovel-full-e2e-20260724/`，不纳入仓库。
- 未修改业务代码；本次仓库变更仅为本临时测试文档。
