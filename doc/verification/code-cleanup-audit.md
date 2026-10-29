# 全仓清理与防御逻辑去留台账

基线：`aeccea278d53d48d1c80efad076d6165d135c363`。实施分支：`codex/ainovel-code-cleanup`。本台账记录作者在当前会话逐组确认的决定及实际进度。

## 进度与覆盖口径

状态：实施中。尚未完成全仓语义复查、完整 L2 和正常 local L4，不将当前已检查部分记为全仓完成。

覆盖当前 Git 跟踪的自有源代码、测试、脚本、配置、依赖声明和文档。机械检索、依赖图检查与源码语义核对分别记录；第三方依赖源码、缓存、构建产物、秘密文件和用户数据不属于删除对象。CodeGraph 已同步当前基线；动态入口仍以真实源码、配置和消费者为准。

## 作者确认的防御用途

| 编号 | 明确决定 | 判定边界 |
|---|---|---|
| S1 | 保留登录、资源权限、管理员二次验证、格式校验、版本/并发、计费幂等和预算、未知结果不重发、H2 隔离、作者采纳及日志脱敏 | 实际使用的最小实现；无调用或重复代码仍删除 |
| S2 | 保留浏览器偏好降级、明确标记失败的检查、现存历史/冻结任务/旧世界绑定兼容、已执行 SQL 和作者待审阅材料 | 不将损坏业务数据解释为空值或正常结果 |
| S3 | 保留秘密文件权限、普通文件、字面值读取、重复键、配置分工、当前必需配置、ENV/AUTH_MODE、TLS 和环境证书边界 | 不增加新规则；重复实现合并 |
| S4 | 删除固定部署主机/端口、强制预生产明文无凭据、按 root/ainovel 名称拒绝启动 | 配置中心继续提供实际连接值；保留通用格式、加密和鉴权 |
| S5 | 删除进程变量名称黑名单及继承环境清洗 | YAML 放非秘密配置，env 只放秘密的格式分工保留 |
| S6 | 退役未接入的两阶段构建、专有扫描和封包校验、旧 V35 生产迁移及 Guard | 保留现行 catalog 镜像/Compose/启动；生产不自动迁移；旧机制能力不再提供 |
| S7 | Node >=22.23.2 <23；pnpm >=11.22.0 <12；默认 pnpm 11.22.0 | 保留 Windows/PowerShell、本地环境、端口、进程归属、超时和严格就绪检查 |
| S8 | 删除静默假成功、猜测性内容补全、固定失败评分、测试构造器关闭保护、必需依赖缺失旁路 | 保留作者业务默认值和不改变内容的可验证格式适配；不追加付费修复 |

以上均已逐项获准。本次不新增防护、专项漏洞扫描或发布门槛。发现独立用途时先讨论，不能自动并入已有批准。

## 已处置项

| 项目 | 源码及消费者证据 | 处置 | 验证 |
|---|---|---|---|
| C01 旧润色弹窗 | `AiRefineDialog` 无产品导入；仅残留编辑器测试 mock | 删除组件及 mock | 待定向验证 |
| C02 历史研究命令 | 十五个 Python CLI 均在执行前退出退役；三份 Java 辅助程序仅由旧批次消费，探针还引用已删除类/方法 | 删除脚本与旧测试，更新研究说明；保留现行语料和可独立工作的 `SlopStudy` | 待引用复查 |
| C03 一次性恢复工具 | `SceneCandidateRecovery` 和 Windows Python 包装器固定绑定同一历史事故；标准候选接口已能读取结果 | 删除事故执行入口及文档命令 | 待后端编译 |
| C04 无调用私有方法 | `parseUuidOrRandom`、`jaccard`、`readStringMap`、`trimToBudget`、`writeSections`、`remoteUidOrThrow` 在当前 main/test/scripts 无调用；前三项同时核对实时 CodeGraph | 删除六个方法 | 待定向验证 |
| C05 测试记录器 | `InMemorySlopQualityRecorder` 只由同包 `SlopQualityGateTest` 消费 | 移入 test 目录，不进入生产 JAR | 待后端编译 |
| C06 前端残留包装 | `AdminEntry` 未使用，真正动态入口为 bootstrap 的 `mountAdmin`；本地分页工具只剩自身测试；Tooltip 只挂空 Provider，没有 Tooltip 消费者 | 删除重复入口、旧分页及对应测试、空 Provider 和直接依赖 | 待前端验证 |
| C07 UI 组件未用变体 | TypeScript 符号引用覆盖全部前端源码及测试，排除导出列表与 displayName 元数据后，sheet/dialog/table/select/dropdown-menu/context-menu 中 28 个组件无消费者 | 删除未用变体、导出和孤立图标导入；保留内部使用的滚动按钮、Portal 等 | 待类型检查和构建 |
| C08 部署预检 S4 | RuntimeEnvironmentPreflight 的预生产固定域名/端口、Redis/Qdrant 明文及空凭据要求和 root/ainovel 名称限制；旧迁移器另有固定地址 | 删除固定值限制；保留 MySQL TLS 参数、生产 TLS/凭据、必填项及认证规则 | 启动预检及证书/认证定向测试通过 |
| C09 环境变量 S5 | PowerShell 函数及读文件黑名单、Python parse_env 黑名单、Shell 名称拒绝和 unset、固定覆盖继承 PATH | 删除黑名单及清洗；配置格式和旧认证凭据拒绝仍按 S1/S3 沿用 | Windows 契约、Python 字面值/重复键测试及唯一 Shell 加载路径核对通过 |
| C10 旧发布/迁移 S6 | 当前平台 catalog 指向镜像及 ainovel-runtime-compose；两阶段脚本无平台调用；旧迁移器固定 V35，当前 SQL 至 V50 | 删除独有执行器、封包/扫描/恢复契约及专用测试；保留现行运行模板与 SQL | 引用复查、后端编译通过；未执行 Linux 发布 |
| C11 工具链 S7 | Windows Build/Test/Start 的精确版本与 package engines 重复限制 | 放宽为已批准范围，默认 Corepack pnpm 11.22.0 不变 | Windows 契约覆盖上下界及范围内版本通过 |
| C12 退役接口 | ES 查询实现恒定返回不可用空页；Beta Reader/连续性触发器恒定 501，当前无产品调用 | 删除四个 HTTP 入口、旧 DTO、禁用按钮及 ES 配置；中央日志、独立审计和历史报告读取保留 | MockMvc 四个旧路径 404、历史报告响应和中央日志入口通过 |
| C13 未用客户端封装 | 当前全部前端源码的 TypeScript 属性访问图，未发现动态 api 下标或 namespace 别名消费；41 个内联封装只有测试或无消费者 | 删除封装和三个孤立转换函数；移除十一项仅测试旧封装的场景；报价/鉴权/历史响应覆盖迁移到现用入口 | 前端类型检查通过，5 文件 55 项定向测试最终通过 |

| C14 质量失败评分 | Plot/Slop/Drift 的失败、短稿或不完整 coverage 曾暴露数据库占位分；趋势把失败纳入均值 | DTO 返回 null；趋势先选最新再排除失败，空均值 null；保留实体非空列及历史值；前端和 API 文档同步 | 覆盖历史45分不改库、失败最新记录不复活旧分、损坏快照明确 FAILED 与空评分 |
| C15 JSON 静默降级 | 共用 JsonColumnCodec、前端报告 JSON 解析和工作流写入消费者 | 可选字段 null/blank 保留业务空值；非空损坏/类型错误抛错，写入失败不写空对象；移除写入 fallback 参数 | 前端解析拒绝损坏/错误结构，后端必填/可选空值及失败写入定向测试通过 |

| C16 必需依赖与计费 | AiService 的双构造器使旧测试关闭准入/预占；一批默认注入字段还允许缺失后走旧实现 | 删除关闭保护的构造器、执行分支及重复校验；必需 Bean 改为必需注入，测试显式提供替身 | 现行准入→预占→调用→记录→结算、未知结果恢复及不重发定向通过 |
| C17 旧检索旁路 | MaterialEvidence 为无条件 Bean；旧检索分支仅在该 Bean 缺失或四参测试构造器时进入 | 删除无依赖的旧搜索/入队路径和过时测试；历史索引处理仍按显式配置开关运行；默认 false 与 YAML 一致 | 索引预算及当前检索专项继续覆盖；无自动真实嵌入调用 |
| C18 质量和上下文旁路 | QualityPersistenceBoundary、隔离上下文、W4 选择器、快照与版本服务均为实际 Bean | 删除缺失时跳过事务、隔离、来源快照和世界选择的分支；删除外层吞掉质量持久化失败的 catch | 冻结输入、单次修订、恢复不重发及正文保持测试通过；显式测试依赖代替生产旁路 |
| C19 素材向量失败 | 生产 Evidence 构造器已用严格 Qdrant 模式；另一构造器查询失败返回空匹配 | 统一严格实现并删 strict 旁路，错误结果不当作无匹配；删除无条件真实实现旁的 Noop embedding Bean | 本地 HTTP 替身验证 owner filter、响应映射及维度契约；未访问实际 Qdrant |
| C20 初始化补造 | StoryConceptionDraftNormalizer、ProjectionBuilder 和 UI fallback 自行生成人物、章节、伏笔及固定置信度 | 删除补造器；对必需 AI 结构校验，投影只映射真实内容；允许作者元数据默认值和明确可选空集合 | 无效结果仅调用一次 AI、无故事/角色写入；有效投影不增添人物、置信度或情节 |
| C21 假模型比较 | compare-models 无前端消费者，拼接候选、固定分数及虚构 usage | 删除演示 HTTP/实现及假 usage 写入、旧模型配置缺失旁路；当前配置和历史偏好读取保留 | MockMvc 404、现行管理员主体审计及模型目录测试通过 |
| C22 其他静默数据 | 管理员余额 catch→0、设置 JSON→空、角色日志跳过损坏行、结果元数据损坏→无记录 | 读取/写入失败明确报错，保留合法 null 及无旧元数据记录；不回写历史 | JSON、世界历史 null 和余额/设置现有测试通过 |
| C23 引用复查新增死代码 | 本类 token 引用、全仓文本及当前 CodeGraph 的 callers 核对 | 删除第二轮六个无用私有方法、孤立依赖字段及未用 RuntimeConfiguration.fromEnvironment | 编译及对应控制器/质量定向测试通过 |
| C24 测试继承环境 | Maven Surefire 仍有独立 excludedEnvironmentVariables 名单 | 按 S5 删除；保留显式 test ENV/profile/H2 契约 | 干净 Maven 测试运行未触发外部数据库；最终 Windows 契约仍待完整执行 |

保留证据：React Query 被提案卡、讨论面板及治理界面的查询使用；Sonner 被 SSO 回调使用；Tiptap BubbleMenu 通过 `@tiptap/react/menus` 使用其 optional dependency。这些依赖不按缺少直接导入删除。

## 批次验证

第一批：在既有 Node 22.23.2 / pnpm 11.22.0 下，前端类型检查、lint、生产构建及编辑器/管理员搜索定向 2 文件 3 测试通过。后端全源码编译及 PromptAssemblyServiceTest、SlopQualityGateTest、ManuscriptServiceTest 合计 12 项通过。此次移除均为无调用实现或测试位置调整；完整 L2 和运行验收继续待办。

第二批：Windows 本地启动契约通过；Python 配置字面值、重复键、普通文件及唯一加载路径检查通过。后端 AiNovelApplicationPreflightTest、RuntimeConfigurationTest、AdminAuthPolicyValidatorTest、StagingTrustPolicyTest、StrictHttpHealthProbeTest 合计 21 项通过。生产仍为 flyway.enabled=false / ddl-auto=none，V1–V50 SQL 未修改。Shell 的 Linux 运行和服务器发布本批未执行。

第三批：四个退役接口已由 MockMvc 验证 404；历史分析报告、中央日志与独立审计写入测试通过。前端 5 文件 55 项最终通过，类型检查通过。其间已修正过时 ES 测试的编译引用与历史按钮选择器；测试失败记录未计为通过。

作者补充决定 S9：删除 Slop/drift 固定的作者 AI 身份免责声明及对应提示词约束；保留历史报告原文，不改写冻结请求。现行代码、测试示例及 API/机制说明已同步。
作者补充决定 S10：删除旧角色变化占位触发入口，返回 404；保留角色日志读取和 H1 真实抽取。已删除控制器/服务/请求 DTO，并收窄既有 UUID 保存路由，避免该退役字面路径匹配成 405。

作者补充决定 S11：退役直接调用旧固定模型的付费研究测试、专有 Maven profile 和命令说明；保留离线语料与历史研究材料。本次未执行真实模型调用。

## 验证与交付

待填写实际定向检查、完整 Windows L2、隔离 MySQL、正常 local 桌面/390px 验收、真实提交与推送结果。条件跳过单列，不计为通过。

第四批：前端真实项目 typecheck 及 API/Slop 面板/移动面板共 3 文件 34 测试通过。后端 JSON、剧情诊断/趋势、drift、质量响应、大纲和风格六组共 22 项定向测试通过；完整 L2 继续待办。修复了删除旧写入方法后测试辅助代码的编译引用，未保留生产兼容旁路。

第五批后端：干净 Maven 构建执行 958 项，146 项按原条件跳过；初次唯一失败为退役角色路径碰到 UUID 保存路径返回 405。已修正实际路由匹配并定向验证 404，随后受影响的控制器、质量和初始化测试通过。所有当前 surefire 报告失败/错误为零；最终完整 Windows L2 仍待执行。测试替身全部位于 test；无真实付费调用或服务器发布。
