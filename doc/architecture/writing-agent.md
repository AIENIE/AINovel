# 自主创作与按需取材

阶段、验收缺口及长期顺序以[路线图](../roadmap.md)为准。当前接口见[自主创作 API](../api/writing-agent.md)。

## 执行与上下文

单协调 Agent 通过原生工具决定搜索、局部读取、起草、检查、修改、询问或结束；工具没有隐含的二次推理、自动格式修复或正式正文写入。原手动生成和两步助手保留原协议。

受理冻结正文/选区、分支与版本、规划摘要、H2 版本戳、世界版本、取材 revision、工具 JSON、控制首步消息、生成规则、模板指纹和模型/价格方案。首步只发送当前任务、可编辑原文、必要资料及世界绑定身份；世界源池只在服务端保存，不是初始模型上下文。上下文不足时暂停，不隐藏截断或自动分段调用。

`world_search/world_read/world_related` 复用本地匹配，支持中文别名、规则优先、稳定 ID 排序和一跳关联。段落分块保留 UTF-16 范围及摘要；搜索片段同样计为实际读取，不能标成完整读过。AUTO 不预载概览，INCLUDE 必须完整工具读取，EXCLUDE 作用于具体来源及有端点依赖的关系，不宣称抹去其他资料中的相同事实。3500 估算 token 同时约束已返回内容，并为尚未完成的必读块保留容量。

H2 编辑不注册或返回未分类世界源；作者问答不能调用候选写工具，历史不混入编辑。模型越界请求这两类工具时停止续接。章内暂定状态隔离属于后续 A4，场景运行器完成不表示 H2 语义验收通过。

候选工具复用段落组投影与原格式保护。起草沿用 fast/crafted 规则，修订使用独立配置；语言检查只读取当前完整候选，剧情检查另读取本次获准资料。报告与来源绑定候选 hash；相同输入检查重放，过期报告不当成新稿检查。作者最终采纳检查当前分支、正文/规划、世界/取材及 H2 基线，保存独立版本且不再触发付费检查。

当前协议允许只读工具同批返回；包含候选写作、检查或结束等工具的多项请求会逐项返回需分开执行的回执，不隐含外发。协调轮次仍计数，这一约束的提示与轮次效率待A2后续完善。单次起草不沿用旧入口的隐藏篇幅修订；实际汉字数、检查对应的候选及停止原因分别判断，达到上限保留候选不等于质量通过。

## 数据结构与恢复

V50 追加 `ai_credit_reservations.result_native_response_json`（可空 LONGTEXT）。私有原生回执与安全 metadata、正文及用量同事务保存；历史行不回填。原生字段不返回普通聊天 HTTP，也不进入公开任务来源。

`writing_agent_runs`：id、user_id、manuscript_id、branch_id、scene_id、request_key、request_hash、status、revision、operation_id、frozen_json、state_json、max_cost_cny、max_project_credits、decision_json、created_at、updated_at。请求按用户/稿件/场景/幂等键唯一；正文删除按稿件外键清理。状态及作者决定并发写使用当前加锁读取，历史只读使用无锁查询。

`writing_agent_steps`：id、run_id、sequence_number、call_key、kind、feature_key、request_hash、request_json、result_json、gateway_request_base64、budget_json、gateway_result_base64、settlement_state、reserved_cny、settled_cny、reserved_project_credits、settled_project_credits、created_at。运行内序号和调用键分别唯一；完整结构、外键与索引以 V50 为准。

每次模型请求先存 journal，再单独准备人民币预算。run 的累计已结算/未确定预留及项目积分上界必须在批准额度内。短期预算凭据仅首发内存使用，不写入 run 的 grant。专项验收同时使用原验证账本及 run 预算，并共享同一个 prepare grant，不能各准备一份后查询错身份。

MODEL 保存精确请求、私有原响应及安全用量；TOOL checkpoint 保存原回执、读取状态、候选引用和包括嵌套模型在内的序号/计数。恢复重放到第一个缺失步骤，已有模型请求使用原请求而不重建；模板不可恢复则停止。供应商结果未知只查询原 budget ID，不能换键或再推理；权威原回执恢复后才完成项目积分结算。仅在原预算与请求摘要吻合且明确 FAILED_NOT_SENT 时，按原 WRITING_AGENT/run/step 身份幂等释放新任务预留；已有结果、已结算和旧普通调用的未知预留不适用。

候选复用 `narrative_generation_candidates`，source_kind=AGENT，operation_id 为自主运行 ID，generation_sequence 为候选序号；metadata_json 保存父候选、段落变更及实际来源。未采纳不写稿件。作者答复单独保留在运行状态中，进度刷新不清空，且不自动成为人物知情或正式事实。

## 原生预算契约

原生控制及 run 内 worker 均使用预算一元请求；模型支持流式是一项能力，不代表本任务会流式执行。每步最多一次外发，工具本地读取不消耗模型调用。组合能力须由 AIService 显式声明，普通预算和普通工具分别可用不足以放行。未配置新步骤或组合能力不可用时不调用模型。

local 验收可选 `app.ai.validation.phase-start-calls`、`phase-max-calls`、`phase-start-cost-cny`、`phase-max-cost-cny`。四项全部缺省保留既有规则；配置时必须完整，基线与原账本一致。次数增量及已结算增量加原预留/本次上界在原账本同一锁内检查。历史未知费用、旧预留和原累计上限不变。
