# H2 — Plan 1: Direct implementation

实施批准的逐作品开启方案。沿用 H1 证据、原子审阅、任务与分支，新增叙事约定、知情关系、作者分类条目及三种上下文投影。机器建议只有作者接受后可用；缺失标记不推断知情。状态以 `../roadmap.md` 为准。

V18 保存作品开关和分支不可变上下文修订。分支文档承载叙事约定、后补知情和作者条目；每次更新保留历史、请求哈希与幂等回执。生成使用固定版本和配置，写回冲突保留候选。开启作品的新分支缺少约定时拒绝生成，不复制已确认认知。

H2 首轮先完成离线隔离测试，再进行至多六次真实调用，该轮已封存。本轮按作者批准的 [加固计划](h23-quality-iteration.md) 使用独立 200 次上限；调用入口持久记录额度，重试和自动修复均计数。仅本地 Windows；不部署，不修改《明日来信》。H3 必须等 H2 通过，目前未启动。

## 接口和生效边界

在原 `/api/v2/manuscripts/{manuscriptId}/branches/{branchId}/narrative` 下扩展：

| 接口 | 行为 |
|---|---|
| `GET /context` | 读取作品开关、配置修订及正文/账本版本，校对来源失效 |
| `PUT /context` | 使用 `Idempotency-Key` 和四个期望版本原子保存作品开关及分支文档；冲突返回 409 |
| `GET /context/preview?sceneId=…&view=SCENE\|CHARACTER\|READER&characterId=…&budget=3500` | 截止所选场景开始以前的投影，返回采用/排除明细、版本戳和哈希 |
| `GET /context/history` | 作者的追加修订历史，包括待复核记录 |
| `GET /context/candidates` | 本稿本分支最近二十个生成候选，包括因版本冲突未写回的正文 |

`PUT` 的期望字段为 `expectedManuscriptVersion`、`expectedCanonRevision`、`expectedRevision`、`expectedSettingsRevision`；`enabled` 为作品开关。`document` 包含 `policy`、`grants`、`entries`；位置哈希由服务端生成，客户端不能用它恢复已失效结论。历史迁移不变，V18 是新增迁移。

`policy.perspective` 支持 `FIRST_PERSON`、`LIMITED_THIRD`、`OMNISCIENT`，`allowInner` 控制展示内心，`viewpointByScene` 按场景指定人物。第一人称/限知生成缺少视角时停止；开启后未配置本场 PLAN 也停止。全知输入保留每条陈述的类型与知情者，不将叙述者知识授给所有人物。

H1 `Assertion` 新增可空 `knowledge` 数组（旧 JSON 缺省按空数组读取）。每项包含 `characterId`、逐字 `evidence`、`uncertainty`；仍需作者审阅接受。本轮增加独立 `view: {content, kind, certainty, eventActor?, acquisitionBasis?}`，`kind` 必须与关联陈述一致，`certainty` 支持 `OBSERVED/REPORTED/BELIEVED/INFERRED/UNKNOWN`。原陈述、原引文和作者说明仅保留作者侧；人物输入只采用已确认 `view`。旧记录缺少 view 时排除，必须作者补充，不能自动复制原字段。新 H2 抽取使用 `narrative-state-p11-v4`；已启动任务继续使用冻结提示词，关闭作品沿用 v1。内联知情最早在证据场景结束后使用；未知时间与倒叙不自动授予更早位置。

补充 `Grant` 关联 `recordId`、`characterId`、`approvalId`、逐字 `evidence`、`fromSceneId`、`uncertainty`；适用场景必须晚于记录与证据来源场景。无需新模型调用。条目 `Entry` 分为 `BACKGROUND`、`PLAN`、`READER_HYPOTHESIS`，关联明确起点及可选记录依赖；背景限定人物或全知叙述者，计划只供指定本场使用，读者假设仅作者可见。正文事实依赖失效、删除/重排来源时追加待复核修订；恢复旧文字不复活旧决定。

`Grant` 同样支持独立 `view`，可在无付费抽取的配置修订中补充。人物稳定属性模板仅生成作者选择的 BACKGROUND 条目，限定该人物和场景起点，不导入整张人物卡。上述字段保存在 V18 的 JSON 修订中，不需要修改历史迁移或增加空字段迁移。

当前编译版本 `scene-isolation-h2-v4`；预览增加 `promptVersion`，保留稿的 `stampJson` 增加 `promptVersion/promptHash/contextHash/modelKey/attemptCount`，生成历史同步记录。H2 每场初稿加最多一次长度修正；完整上一稿与原结尾进入冻结输入，保守预算检查超限返回 `H2_PROMPT_BUDGET_EXCEEDED`，不会截断再发送。两次仍越界返回 `H2_LENGTH_LIMIT_REACHED`，保留 `LENGTH_REJECTED` 候选及版本元数据，不覆盖正文。v3 增加禁止为 BELIEF/REPORTED/INFERRED 补造亲历和核实过程的指令；这是输入约束，真实复验仍发现关键归因扩写，不能宣称能保证输出语义正确。

权限/位置过滤在相关性排序前执行。沿用场景编译器的词项相关性和 token 估算，预算上限 3,500；知识记录按整条取舍，不截断 JSON 或引文来猜测语义。计划和叙事约定放不下时明确停止。未分类原始资料不参与查询词构造；作者预览可检查排除内容，模型只得到 `content`。

生成沿用现有任务。FAST、CRAFTED、长度重试、文本门禁和剧情修订使用冻结上下文；质量路径不得再次截短该上下文或补入原始人物/世界/梗概。写回校验分支、正文、账本、分支配置、作品开关修订及大纲哈希。冲突留下候选，生成不会自动接受状态。新分支无配置和已确认知情；合并仅合并正文。

## 本地验收预算

作者确认本次验收关闭附带的付费诊断/自动修复，保留本地规则；这些旁路由可控测试覆盖。`local` profile 的 `AI_VALIDATION_RUN_ID` 启用预先创建的持久预算，`AI_VALIDATION_LOCAL_QUALITY_ONLY=true` 启用此次验收选择，默认均关闭。配置不会改变生产默认行为。

每次实际 AINovel 网关请求先以独立事务锁定预算、领取序号、记录模型及完整消息；重试仍领取新序号，缓存回放不再调用模型。预算缺失拒绝调用，不自动新建或重置；第七次拒绝。进程重启不恢复额度。`STARTED` 表示调用结果尚未记录，仍占额度。结果记录 token，用既有积分流水核对实际费用。验收结束仍保留运行记录。

验收状态、费用和证据另记于 `doc/verification/2026-09-22-h2-acceptance.md`，只有约定项均通过才在路线图标记完成。输入隔离不保证模型不会自行编造知情；输出越界必须单独记录，不能把三场案例当作总体质量提升。

人物可用表述的可选 `eventActor`（最多 500 字）、`acquisitionBasis`（最多 1000 字）分别记录作者批准的实施者和获知依据。缺省或空白保存为 null，投影为 NOT_PROVIDED，表示没有作者提供的依据，既不自动推断人物不知，也不授权补造。二者沿用该表述类型，梦境中的实施者不迁移为现实实施者，来源不能将信念变成事实。旧 JSON 不复制引文或说明。界面默认折叠，只需为关键归因补充；普通动作、环境和对白可自由创作。必需约束超过预览预算时返回 H2_REQUIRED_CONTEXT_TOO_LARGE，不能静默裁掉。
