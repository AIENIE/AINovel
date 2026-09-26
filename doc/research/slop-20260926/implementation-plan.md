# Slop 句式检测与局部修订实施方案（待实施）

本文件是 2026-09-26 专项研究的后续工程方案，不代表业务功能已经实现。状态与优先级以 [roadmap](../../roadmap.md) 为准。目标是让作者发现可核对的句式问题，并安全地选择最小修改；不是判断作品是否由 AI 写成。自动路径最多修订一次，手动路径逐项接受、拒绝及撤销，共用定位、补丁和内容保留机制。

## 1. 当前实现与改动边界

- `LocalSlopHeuristics`、版本化 `SlopPatternRegistry`、500 字窗口和现有 12 条问题上限保留。SHADOW 仍只观测，不因本研究的模型/代理标签转 ACTIVE。
- `SlopQualityGate` 当前以本地风险改善接受整稿修订；替换为有来源校验的局部补丁候选，先保留原稿再评估。
- `AiSlopJudgeClient`、`SlopDiagnosticService` 和生成修订提示分别截取 5000/7000/6000 字；改为统一抽取与显式覆盖范围，删除静默 substring 上限。
- 扩展 `SlopQualityRunDto`、质量问题和现有 `rewriteTasksJson` 类型。旧报告可读，不能自动变成可应用的建议。复用 `AiOperation` 异步生命周期、费用预占/结算、正文版本保存、生成历史及 H2 上下文构建器，不另建任务或账本框架。
- 不新增全知审稿、剧情修补、多候选竞技场、题材分库或跨章自动修改；不解锁 H2/H3/H4。

## 2. 唯一来源与位置契约

新增服务 `SlopTextSnapshotService`，输入不可变正文版本（手动）或不可变生成候选（自动），输出 `schemaVersion=1`：

```ts
type SourceRef = {
  manuscriptId: string; sceneId: string; branchId: string;
  sourceVersionId: string; sourceKind: 'SAVED_SECTION' | 'GENERATION_CANDIDATE';
  sourceTextHash: string; sourceDocumentHash: string;
  extractorVersion: 'slop-text-v1'; contextStamp: string;
};
type Span = { startUtf16: number; endUtf16: number }; // [start,end)
type SlopPatch = {
  id: string; issueIds: string[]; source: SourceRef; span: Span;
  quote: string; replacement: string; reason: string;
  state: 'PROPOSED' | 'REJECTED' | 'APPLIED' | 'UNDONE' | 'STALE' | 'INVALID';
};
```

抽取规则冻结为版本化契约：解析受支持的 Tiptap HTML，实体解码；块节点间一个 LF，块内 hard break 为 LF；CRLF 规范为 LF；保留前后空白、内部空格和标点，不 trim、不 NFC、不合并段落。以 UTF-8 编码计算 SHA-256，位置以 Java/JS 原生 UTF-16 code unit 计数，禁止切开代理项对。保留 `{nodePath, textNodeOffset, plainStart, plainEnd}` 映射，映射不完整的富文本返回不可应用状态。HTML 标签、实体长度和 Tiptap transaction 位置不得直接当作正文位置。

收到模型建议后必须验证：来源版本/分支/哈希完全一致；范围有效；`text.slice(start,end) === quote`；引用发生位置唯一或由明确位置加前后上下文唯一验证。重复引文不能回退到 `indexOf` 第一处。模型提供位置失败时，只允许唯一逐字匹配重新定位，并标记定位来源；多解、不存在、越界均为 INVALID。持久化验证后的坐标，模型原始证据另存，禁止用臆造的位置高亮。

同一批补丁最多 4 项；按原稿位置排序，不相交、不含嵌套；同一问题只允许一个候选。验证关联 issue 存在且同源。结构上跨段落或跨富文本标记的补丁，v1 仅展示原因并要求作者手动编辑，不自动应用；普通文本替换保持节点标记。空 replacement 是显式删除，走更严格内容审查，不能因为删除变短自动通过。

## 3. 全文覆盖与预算

先全量抽取，再按完整段落组合至目标 3000 UTF-16 单位的主体块。单段过长时按句末切分；无句末则在安全的 Unicode 边界切分。每个块额外提供前后各最多 500 UTF-16 上下文，主范围不重叠；问题归属按起点落入主范围，合并时按位置及规则去重。500 字检测窗口继续沿用当前 UTF-16 口径，窗口须跨块边界运行，不能按块重新清零。

记录每块主范围/上下文范围、请求哈希、完成/失败/跳过状态、模型及规则版本。覆盖率按主范围并集计算，不以模型回答字数计算。缺块报告为 PARTIAL，列出未覆盖段落；自动修改必须覆盖率 100%，预算不够则保留原稿。模型输入容量应同时校验 token 预算；超限进一步切分或明确失败，不静默去尾。报告 12 条上限只限制展示和候选数，不得提前停止正文扫描；额外计数单独汇总。

整场一次自动修订指一次 proposal 批次，不是每个块各修一次；每块至多一次诊断/提案/复核，补丁整场最多 4 条。启动前估算整批最大费用并预留预算；续跑检查持久化计数。调用超时标 UNKNOWN，按幂等键查询已有结果后恢复，不盲目再扣费。取消、预算耗尽和失败均保留覆盖状态、原稿和已有结果。

## 4. 检测策略

将“形式重复”与“是否值得修改”分开记录。输出六类信号、500 字密度、分散位置、叙事作用与合理替代解释。单个否定转折、人物口头禅、敲击信号和意象回扣，不充分构成可修改问题。`不是查无此件……编码段被冻结` 的信息增量必须保留。

先离线观察三个结构特征：对白与情绪尾注配对率；替换主语/宾语后相似的动作骨架；短句长度与标点停顿序列。保留原句、同构片段和上下文，不能仅输出“AI 味重”。观察项不增加风险分、不触发付费生成或自动改写。固定 16 个开发样本与 8 个留出样本，只在开发集调规则；本轮留出已打开，下一轮参数选择须另冻真人标注留出集，不能继续称其未知测试集。

局部上下文例外应绑定证据范围，避免一个“侦探分析腔”标签降级整场所有机械套语。修复 10-gram 固定步长的起点敏感性，但保留密度阈值与替代解释，避免把信号复现当无效重复。新阈值只能通过标注验收启用，不能由本轮无误报小样本推导生产保证。

## 5. 内容保留与两条产品路径

**共同评估**：位置和版本确定性校验优先；合成候选后，原文未被补丁覆盖的区间必须逐字一致。分别报告句式改善、内容保留、流畅度，不合成“小说质量总分”。提取并逐项比较人物/物件、数值/时间、所属/位置、否定范围、确定性、知情依据、对白意图及线索。确定性检查只能查显式变化，不能证明语义完全等价；模型复核 PASS 也不是可靠保证。本研究未提供可靠的全自动语义保真判定器。

**自动生成**：用生成时的 H2 合法投影与不可变 contextStamp，不重新从全知账本拼上下文；候选已含的 H2 错误不能借句式修订洗白。保存原稿、补丁、候选稿及 diff，至多一次；保留检查 fail/uncertain、模型间分歧、来源变化、缺块、定位无效、涉及关键事实或新增叙述均回退原稿。原稿、修订稿的独立质量结果均落库，不能覆盖原问题。默认关闭自动采纳；先开放 opt-in 的“生成后建议”，达到下述条件后才允许作品级自动开关。用户已设关闭时不发付费修订。

**编辑审阅**：先保存当前正文版本，再异步分析；按来源版本显示原句、建议、理由、合理解释和内容影响。作者可逐项接受/拒绝，接受生成新的正文版本和审计记录，撤销生成反向新版本，不删除历史。接受一项后，其余项进入 STALE，必须服务端针对新版本逐字/哈希/区间重新验证生成新派生建议；旧 ID 不可直接应用，显式标注“已重新校验”，不自动付费重诊断。正文任意编辑、回滚、分支合并、场景切换均冻结旧建议；页面以 manuscript+branch+scene+version 关联异步响应，迟到响应不改另一场景。撤销只允许当前版本等于对应应用的结果版本，否则 409 要求重新审阅。

## 6. 接口与迁移

沿用 `/api` 网关前缀。现有 `GET /v2/manuscripts/{id}/quality-runs` 和同步诊断 POST 保持兼容；新客户端通过现有异步任务机制提交，避免悄悄改变旧 POST 的响应类型。增量接口：

| 接口 | 请求/响应契约 |
|---|---|
| `POST /v2/manuscripts/{m}/scenes/{s}/slop-proposals` | `{source: SourceRef, mode: 'MANUAL', idempotencyKey}`；202 返回 operationId，任务完成关联 qualityRunId；不接受任意隐藏上下文 |
| `GET /v2/manuscripts/{m}/quality-runs/{r}/patches` | 验所有权及场景/分支关联；返回 source、coverage、evaluations、patches |
| `POST /v2/manuscripts/{m}/quality-runs/{r}/patches/{p}/decision` | `{decision: 'ACCEPT'|'REJECT', expectedVersionId, idempotencyKey}`；200 返回新版本和建议状态；接受与正文保存同事务 |
| `POST /v2/manuscripts/{m}/quality-runs/{r}/patches/{p}/undo` | `{expectedVersionId, idempotencyKey}`；200 返回反向新版本；只能撤销本项对应的当前版本 |

同用户同键不同请求体 409；同键同请求返回原结果。旧版本/分支 409 `SOURCE_STALE`，非法或重叠补丁 422 `PATCH_INVALID`，预算不足沿用现有错误码，权限沿用 ResourceAccessGuard，不能在后台任务恢复时跳过所有权检查。结果中的 source 和 appliedVersionId 必须由服务端赋值。

迁移用实施时下一个可用 Flyway 编号（当前最新 V18；不抢占 H3 的 V19）：`slop_quality_runs` 增加可空来源版本/分支、抽取器版本、documentHash、contextStamp、coverage/evaluations JSON。正文哈希复用已有列并明确抽取版本。新增 `slop_revision_patches`，保存 runId/issueIds/sourceSpan/quote/replacement/reason/state、来源及应用/撤销版本、幂等键、审阅人/时间。对 runId、sourceVersionId 建索引，唯一键约束操作幂等。`rewriteTasksJson` 增加 `schemaVersion` 和 patchId 引用，旧结构转换为只读建议，不伪造 hash/version，不回填 APPLICABLE。

迁移先加可空字段与表，服务端双读旧/新报告，客户端遇未知枚举只读；功能开关默认关。回滚代码仍能读旧列，不撤销已保存作者正文；禁用开关即可停新任务。不要 drop 历史质量记录或修改 V1–V18。真实 MySQL 验证空库、当前 V18 升级、旧报告只读和幂等恢复。

## 7. 实施顺序、验收及启用条件

1. 统一正文快照、Unicode/富文本位置映射、来源版本校验；先修无效定位、静默去尾和覆盖记录。L2 确定性用例通过后接入报告。
2. 补丁引擎与迁移、旧报告兼容、异步生命周期；先上线手动预览，再做逐项采纳与撤销。
3. 内容保留评估与生成一次修订实验；自动采纳继续默认关。新增结构信号仅离线观测。

| L2 必须通过 | 本地 L4 必须观察 |
|---|---|
| 同一句多处出现/假引文/越界拒绝；emoji、扩展汉字、组合字符、LF/CRLF、首尾空格 | 高亮原句准确，重复引文不跳到第一处 |
| HTML 实体、跨标签、空段、hard break、富文本节点映射；不支持的结构显式拒绝 | 桌面与窄屏原句/建议/理由可读，替换不破坏格式 |
| 超 7000 字尾哨兵、跨块窗口、缺块、12 条封顶不漏尾扫描 | 长正文尾问题可见，缺块报告标明范围且不能自动采用 |
| 重叠补丁、无效 issueId、局部删除、新增数字/否定/事实、模型误判 | 原稿/候选/diff 独立可见，事实变化或 uncertain 保留原稿 |
| 同时保存、跨用户、跨分支、版本冲突、幂等重复、撤销后版本保护 | 接受/拒绝/撤销/刷新/回滚；切场景后迟到响应不串文 |
| 预算持久化、耗尽、超时未知、取消和重启恢复、不重复付费 | 失败可恢复且费用可对账；预算不足不丢原稿 |
| H2 隐藏秘密不进入任一诊断/提案/复核请求；上下文戳不变 | 隔离项目实际请求检查，完整原稿与前后版本一致 |

手动试用启用条件：上述 L2 和本地 L4 关键流程全过，旧报告兼容及回滚验证完成，确认计费提示和无效建议不能写入正文。自动采纳另外要求独立真人评审冻结集、包含六类正反例及至少 30 个真实场景的保留对照；零关键事实/知情/线索/否定损失，争议和证据不足全部回退，句式改善与流畅度无系统性退化。30 例不是可靠性证明，仍只按作品 opt-in 灰度，发现一次关键内容损失即停自动采纳并保留失败例。具体真人招募和质量阈值须在该阶段验收协议冻结，不将本轮代理标签算作真人通过。

评估维度参考 [Text Style Transfer Evaluation Using Large Language Models](https://aclanthology.org/2024.lrec-main.1373/) 的风格、内容保留、流畅度拆分；该论文不直接证明中文小说自动修订有效。模型评审与作者判断分开保存，留意 [G-Eval](https://aclanthology.org/2023.emnlp-main.153/) 讨论的评审偏差。
