# 上游与接入链路审查

基线 `3678b7a772d9916ac26bd74129098cb01c90d17f`，本轮只读业务代码。

| 位置 | 观察到的实现事实 | 后续假设及验证 |
|---|---|---|
| `backend/src/main/java/com/ainovel/app/style/StyleService.java`：`analyzeDimensionScores`、`generateCharacterVoice` | `sentence_length` 由全文 length/120 推导，词汇丰富度用不同字符数；引号计数推对白密度。角色声音固定偏好短句/反问/口语化，固定“这事不对劲”“让我想想”和示例。 | 这不是可靠作者画像；延长同一文本即可改变所谓句长。应先把占位维度标明来源，再研究按句分布、对白归属、场景条件和作者确认。固定声音可能制造同质化，尚未因果实测。 |
| `style/StyleContextProvider.java` | slop 上下文总长 1800；analysis 300、sample 180、catchphrase 180、sampleDialogues 240；诊断处风格上下文又限 1400。 | 样例被截断可能只剩开头句法；按完整示例与来源预算选择，记录被排除项，不拼接半句话。 |
| `manuscript/context/SceneDraftContextCompiler.java` | 非 H2 路径构造风格/场景覆盖项与至多两个相关角色声音。H2 开启时先返回 `NarrativeContextService.preview`，不再执行旧 `addStyleCandidates`。 | 不能以旧路径可见风格证明 H2 已注入。 |
| `narrative/NarrativeContextService.java` | H2 preview 消费批准记录/知识视图/Entries，预算上限 3500；没有读取 StyleProfile/CharacterVoice repository。约束明确普通动作可自由写，关键往事/身份/设定不可补造。 | 后续风格注入必须作为独立、无剧情秘密的投影，沿用 H2 过滤，不把旧全量 style 字符串塞回去。Entry 的手工文本可能含风格指示，但不等于原有画像自动注入。 |
| `manuscript/SceneGenerationPromptBuilder.java`、`PromptAssemblyService` | 精雕有 15 条采样禁用项，同时已有正向质量目标与轮换 human-trace 条目。 | 本次 positive 只移除禁用块，系统仍有避免泛化的表述；不能称纯正向提示。要求代价/关系变化等条目可能诱发额外经历，需要下轮单因素消融，不能由本轮归因。 |
| `ai/AiService.java`：`invokeGatewayTransport` | 实际使用 `modelPolicy.modelKey()`；请求 `modelId` 不是自由路由控制。预算账本也记录模型策略。 | 换模型需要网关可用模型、策略与计费契约一起验证，不能只改 JSON 字段。 |
| `ai/dto/AiChatRequest` / 当前 gateway proto | 请求只支持 messages/modelId/context 等现有字段，未暴露 temperature/top_p/seed/重复惩罚。 | 本实验固定模型名和提示输入，无法保证底层版本/随机种子或采样参数不变；必须如实记录 unknown。 |

第一轮的全文截断、UTF-16 坐标、重复引文定位、来源失效和风险下降误接受仍成立。第二轮不把这些基础设施问题与“怎样写得好”混为一项模型评分问题。
