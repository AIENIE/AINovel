# 验证与验收

## 标准验证链

```bash
mvn -q -f backend/pom.xml test
cd frontend && npm run test && npm run build
printf '%s\n' "$SUDO_PASSWORD" | sudo -S ./build.sh
curl --noproxy '*' -k https://ainovel.localhut.com/api/actuator/health/liveness
curl --noproxy '*' -k https://ainovel.localhut.com/api/actuator/health/readiness
```

普通用户验收必须通过真实 SSO 会话，管理员验收必须通过 `/admin/login`，不得临时绕过身份验证。

## 最近一次验证

2026-07-15，G1-A P0 交付与文档架构重构收口：

- 后端全量测试通过，Testcontainers 在 MySQL 8 上验证 V1 到 V5 迁移。
- 前端 23 个测试文件、79 项测试通过，生产构建通过。
- `build.sh` 部署成功；后端、前端容器运行正常。
- 运行库 Flyway schema 为 V5。
- 首页、`/novels/quick-create` 和 `/api/actuator/health` 均返回 200。
- SSO 授权码交换返回 200，但 `UserSessionValidator` 调用当前环境的 `userservice.localhut.com:10001` 超时，`GET /api/v1/user/profile` 返回 403。因此本轮未宣称登录后的 G1 浏览器闭环通过。

## G1 引导创作验收

1. 访问 `/novels/quick-create`，分别创建逐步选择和自动模式草稿。
2. 确认前三步恰好 3 个候选；大纲生成三条简要方向，A/B/C 可独立发展和重写，再展开一套满足章节/场景数约束的完整预览。
3. 关闭页面后重新进入，确认草稿和后台进度可恢复。
4. 确认故事、可选世界、角色和大纲写入标准实体。
5. 检查 `async_jobs` 状态、唯一幂等键、`RECOVERY_REQUIRED` 恢复和 `G1_WORKFLOW` 账本引用。
6. 在 1440x900 与 390x844 视口检查布局、候选编辑、错误和完成状态。

## G2 盲测验收

1. 管理员创建活动、邀请评审并推进活动状态。
2. 作者提交自己的场景，确认隔离文本不覆盖正文、版本或剧情质量记录。
3. 不同受邀账号只能看到匿名 A/B；作者不能评审自己的样本。
4. A、B 和中性票统计符合规则。
5. 失败样本的 `G2_EVALUATION` 扣费与退款在本地账本相互抵销。

## 2026-07-23 agent-browser 全书端到端盲测（已结束）

- 环境：`https://ainovel.localhut.com`，普通用户真实 SSO 会话；仅 agent-browser 临时使用 `--ignore-https-errors` 处理 localhut 证书拦截。
- 证据目录：`/home/duwei/tmp/ainovel-e2e-20260723/`（截图与视频，不进入仓库）。
- 当前进度：SSO 登录成功；引导创作被已有失败草稿和 AI 超时阻塞，未创建本轮新小说；已转用已有 3 章/6 场景故事完成下游工作台探测，并停止继续消耗生成积分。

### 本轮结果汇总

| 环节 | 结果 | 说明 |
| --- | --- | --- |
| SSO 登录与受保护页面 | 通过 | 真实会话进入 `/workbench` |
| G1 新建手动/自动流程 | 阻塞 | “新草稿”无法清空失败草稿；章节大纲 AI 两次超时 |
| 已有 3 章 6 场景工作台结构 | 通过 | 可加载故事、大纲、稿件和全部场景 |
| 逐场景正文生成 | 失败 | 首场景请求 200 但正文仍为空、字数 0 |
| 质量门禁 | 失败 | 质量记录查询因数据库缺列返回 500 |
| 导出任务创建/完成 | 通过 | TXT 任务进入 `completed` |
| 导出文件下载 | 失败 | 下载链接返回 403 |
| 移动/桌面布局检查 | 通过 | 未发现新的稳定布局阻塞 |

### 问题计数

| 严重级别 | 数量 |
| --- | ---: |
| Critical | 4 |
| High | 2 |
| Medium | 1 |
| Low | 0 |
| **总计** | **7** |

### ISSUE-001：失败草稿存在时“新草稿”无法回到空白起点

| 字段 | 记录 |
| --- | --- |
| 严重级别 | high |
| 分类 | functional / ux |
| 页面 | `/novels/quick-create?run=e21d185d-3664-4e60-8825-12d1c2d62318` |
| 复现证据 | `screenshots/issue-001-stable-step-1.png`、`issue-001-stable-step-2.png`、`issue-001-stable-result.png`；视频尝试失败（环境缺少 ffmpeg） |
| 状态 | 待修复 |

**现象**：进入引导创作时自动选中历史失败草稿（错误为 AI 服务 `DEADLINE_EXCEEDED`）。点击页面顶部或左侧的“新草稿”后，仍显示同一失败草稿和“重试”按钮，URL 仍携带原 `run`，没有显示“建立创作起点”表单。返回“小说管理”再点击“引导创作”也会重新回到该失败草稿。

**预期**：点击“新草稿”应清除当前运行并显示空白种子表单，允许用户输入新的故事起点。

**复现步骤**：

1. 登录后打开 `/novels/quick-create`，确保最近草稿列表中存在失败草稿。
2. 点击顶部或左侧任一“新草稿”。
3. 观察页面仍为“本步骤未完成”，仍显示原失败草稿和“重试”；刷新或从“小说管理”重新进入，结果相同。

**影响**：只要用户存在失败草稿，就无法从 UI 创建新的引导创作，核心新书流程被阻断。临时绕过只能依赖历史已完成故事或非 UI 的数据操作，本次未采用绕过。

### ISSUE-002：引导创作章节大纲阶段稳定超时

| 字段 | 记录 |
| --- | --- |
| 严重级别 | critical |
| 分类 | functional / performance |
| 页面 | `/novels/quick-create?run=e21d185d-3664-4e60-8825-12d1c2d62318` |
| 复现证据 | `screenshots/issue-002-step-1.png`、`issue-002-mid.png`、`issue-002-result-final.png` |
| 状态 | 待修复/依赖检查 |

**现象**：对处于自动推进状态的引导草稿连续重试两次，均在“确认章节大纲 / STEP 04”停留约 120 秒后失败，页面显示 `DEADLINE_EXCEEDED`，后端错误指向 `aiservice.localhut.com:10011`。

**预期**：章节大纲任务在可接受时间内完成，或在超时前给出可操作的重试/降级提示，不让整条引导创作长期占用页面。

**复现步骤**：

1. 登录后打开包含失败引导草稿的 `/novels/quick-create`。
2. 点击“重试”，观察进入“自动推进中 / 确认章节大纲”。
3. 等待约 120 秒；页面再次显示“本步骤未完成”和相同 `DEADLINE_EXCEEDED`。
4. 再次点击“重试”并重复等待，结果一致。

**影响**：G1 自动流程无法完成章节大纲，普通用户无法进入完整创作准备；本次未继续消耗更多生成积分，也未使用非 UI 绕过。

### ISSUE-003：大纲新增场景触发 React Select 异常并显示空白页

| 字段 | 记录 |
| --- | --- |
| 严重级别 | critical |
| 分类 | functional / console |
| 页面 | `/workbench?id=ec48cf00-4c2a-4ab3-9bde-1664de35d857` |
| 复现证据 | `screenshots/workbench-blank-after-outline-click.png` |
| 状态 | 待修复 |

**现象**：在“大纲编排”点击“添加场景”后，点击新场景条目，工作台变为空白。浏览器控制台报错：`A <Select.Item /> must have a value prop ...`。

**预期**：新场景应进入可编辑状态；缺少反转方案时应显示占位符或校验提示，不应让整个 React 应用崩溃。

**复现步骤**：

1. 登录并进入一个已有故事的 `/workbench`，打开“大纲编排”。
2. 点击“章节”，再点击“添加场景”。
3. 点击左侧新出现的“新场景”条目。
4. **观察**：页面无交互元素、正文为空白，控制台出现 Select.Item `value` 异常。

**影响**：大纲/场景编辑和后续正文生成均被阻断；刷新页面才能尝试恢复工作台。

### ISSUE-004：正文生成返回成功但场景内容为空

| 字段 | 记录 |
| --- | --- |
| 严重级别 | critical |
| 分类 | functional |
| 页面 | `/workbench?id=e843b031-3203-4dcc-8b6d-31ae05ea9fd8` |
| 复现证据 | `screenshots/workbench-before-first-generation.png`、`workbench-after-first-generation.png` |
| 状态 | 待修复/依赖检查 |

**现象**：在已有 3 章 6 场景故事“嘿嘿大陆”的第一场景选择“精雕”并点击“生成本场景”，请求返回 200，但正文编辑器仍为空，字数为 0，未出现可保存的正文结果。

**预期**：生成成功后编辑器显示正文、字数更新，并可保存或刷新后恢复。

**影响**：正文链路无法产出任何可交付内容，本次停止继续生成其余场景以避免无效积分消耗。

### ISSUE-005：质量门禁查询因运行库缺列持续返回 500

| 字段 | 记录 |
| --- | --- |
| 严重级别 | critical |
| 分类 | functional / console |
| 页面 | `/workbench?id=e843b031-3203-4dcc-8b6d-31ae05ea9fd8` |
| 复现证据 | 后端 `ainovel-backend` 日志（2026-07-23 04:33–04:35） |
| 状态 | 待迁移修复 |

**现象**：工作台加载或生成场景后请求质量记录接口返回 500；后端 SQL 报错：`Unknown column 'i1_0.alternative_explanations_json' in 'field list'`，对应 `slop_quality_issues` 运行库结构缺少实体要求的列。

**预期**：质量记录接口应与当前 Flyway/JPA 实体一致，返回空列表或历史记录，不应抛 SQL 语法错误。

**影响**：文本 Slop 诊断和相关质量状态不可用；生成后的质量门禁闭环无法完成。

### ISSUE-006：导出任务完成但下载链接返回 403

| 字段 | 记录 |
| --- | --- |
| 严重级别 | high |
| 分类 | functional / auth |
| 页面 | `/workbench?id=e843b031-3203-4dcc-8b6d-31ae05ea9fd8` |
| 复现证据 | `screenshots/issue-006-download-403.png` |
| 状态 | 待修复 |

**现象**：在“导出”面板创建 TXT 导出任务后，任务状态变为 `completed` 并出现“下载”链接；点击后浏览器跳转到 `chrome-error://chromewebdata/`，显示 `Access to ainovel.localhut.com was denied` / `HTTP ERROR 403`，未下载文件。

**预期**：当前用户可下载自己已完成的导出任务，下载内容应可保存到本地。

**影响**：导出任务虽然完成，但最终交付步骤不可用。

### ISSUE-007：Redis 短暂断连导致健康状态瞬时 DOWN

| 字段 | 记录 |
| --- | --- |
| 严重级别 | medium |
| 分类 | availability / operations |
| 证据 | 2026-07-23 健康探测连续 10 次：第 1 次 `{"status":"DOWN"}`，随后 9 次 `UP`；后端日志记录 Redis `Connection reset` 后自动重连 |
| 状态 | 待基础设施排查 |

**现象**：运行中第一次访问 `/api/actuator/health` 返回 `DOWN`，约 1 秒后恢复 `UP`。后端日志显示 RedisReactiveHealthIndicator 连接重置并随后重连成功。

**影响**：部署/监控可能将短暂依赖抖动误判为服务不可用；本次未因此继续重启或修改基础设施。

## 2026-07-23 七项问题修复后 L5 复验（通过）

- 环境：`https://ainovel.localhut.com`，普通用户真实 SSO、真实 AI 调用；agent-browser 仅临时使用 `--ignore-https-errors`，未保存登录状态。
- 证据目录：`/home/duwei/tmp/ainovel-e2e-fixes-20260723/`；保留 12 张桌面/移动截图和实际下载文件。视频录制因主机缺少 ffmpeg 未生成。
- 手动流程：运行 `2c5cf7dd-f605-4ff3-8a47-c78b86594cd0`，3 章逐步选择模式完成；A 发展、按反馈重写到第 2 版，B 独立发展到第 1 版，切回 A 后状态完整保留；A 展开、编辑预览并确认进入工作台。
- 自动流程：运行 `4b41d345-96b7-49f8-855e-89fd8eb5edb1`，3 章自动模式从故事方向连续推进到完整大纲并成功物化故事 `e7db107b-f333-4b96-972d-0ca1241ebb32`。
- 正文与导出：新增“暴雨前的旧地球回声”场景后生成 3,438 字，刷新并重新选择场景后仍为 3,438 字；TXT 下载接口返回 200，实得 10,846 字节文件并包含该场景全文。

| 问题 | 复验状态 | 修复后证据 |
| --- | --- | --- |
| ISSUE-001 | 已修复 | 失败草稿页点击“新草稿”进入 `?new=1`，刷新后仍保持空白起点；`01-guided-existing.png` |
| ISSUE-002 | 已修复 | 手动与自动两个 3 章流程均完成；大纲先生成 A/B/C 简要方向，再独立发展、重写和展开；`03-outline-abc.png`、`05-manual-completed.png`、`06-automatic-completed.png` |
| ISSUE-003 | 已修复 | 新增并选择空场景正常显示“无”反转方案，无白屏、控制台异常或页面错误；`07-new-scene-no-crash.png` |
| ISSUE-004 | 已修复 | 真实快速生成返回非空正文和 3,438 字计数，刷新后内容不丢失；`08-scene-generated.png` |
| ISSUE-005 | 已修复 | Flyway V6 成功，`slop_quality_issues` 九个缺失列全部存在；质量/剧情记录接口均为 200，质量分析 UI 正常；`09-quality-ui.png` |
| ISSUE-006 | 已修复 | 桌面和 390×844 移动入口均以认证 Blob 下载；任务完成、下载接口 200、文件非空；中文文件名使用 RFC 5987 UTF-8 响应头；`10-export-completed.png`、`12-mobile-export.png` |
| ISSUE-007 | 已修复（探针隔离） | `/health/liveness` 只检查应用存活，`/health/readiness` 检查应用与数据库；高负载后两者持续 200/UP。根健康仍保留 Redis，因此 Redis 抖动时可暂时 DOWN，但不再误伤存活/就绪探针。 |

数据库与运行状态复验：

- Flyway `flyway_schema_history` 中 V6 为成功状态；九列为 `char_start`、`char_end`、`quote`、`module`、`pattern_id`、`issue_type`、`evidence_level`、`alternative_explanations_json`、`repair_hint`。
- 浏览器复验期间质量记录接口、场景生成、导出创建/轮询/下载均返回 200；页面 console 与 page errors 为空。
- 1440×900 与 390×844 均完成布局和下载入口检查。
- G2 路线图票数、成功样本对、评审人数和精雕胜率阈值未修改。
