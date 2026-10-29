# 全仓清理与防御逻辑去留台账

基线：`aeccea278d53d48d1c80efad076d6165d135c363`；分支：`codex/ainovel-code-cleanup`。本台账记录本次获准的清理、实际使用证据和验证；不改变 H/G 阶段状态。

## 覆盖与判定方法

状态：验收中。代码清理、最终 Windows L2 与构建已完成；隔离 MySQL 和正常 local L4 正在收尾。

[逐文件覆盖表](code-cleanup-files.csv)登记基线和最终源码并集的1,525个文件：当前1,440个，85个路径删除或迁移。两份索引CSV自身在此登记，不递归记录自己的摘要。每行含路径、类别、变更、SHA-256、符号、消费者/运行注册、处置及检查。UTF-8文本摘要统一LF，二进制按原字节；变更状态按Git diff判定。

覆盖868个源码、317个测试/支持文件、85个入口/配置工具、30个配置、13个依赖/构建文件、74份文档、50份执行迁移、84份冻结数据和4份资产。第三方源码、依赖缓存、构建产物、秘密配置和用户数据不在删除范围。

文件表中的内容读取、符号和引用核对包含机械检查，不代表每行都经人工语义证明。实际删除同时核对Spring/JPA注册、定时/生命周期回调、动态/静态导入、脚本入口、配置消费者、依赖和历史用途；不凭关键词或零入边删除。

[条件与防御候选索引](code-cleanup-defenses.csv)登记当前7,675个位置、符号、源码、消费者及批准用途。其包含普通业务条件和默认值，数量不是独立安全机制数量；实际去留依据S1–S23。删除项对应下列C项及Git diff，保留用途不豁免内部重复或失效实现。

## 清理处置

| 项目 | 源码及消费者证据 | 最终处置 | 验证 |
|---|---|---|---|
| C01 旧润色弹窗 | `AiRefineDialog` 无产品导入；仅残留编辑器测试 mock | 删除组件及 mock | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C02 历史研究命令 | 十五个 Python CLI 均在执行前退出退役；三份 Java 辅助程序仅由旧批次消费，探针还引用已删除类/方法 | 删除脚本与旧测试，更新研究说明；保留现行语料和可独立工作的 `SlopStudy` | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C03 一次性恢复工具 | `SceneCandidateRecovery` 和 Windows Python 包装器固定绑定同一历史事故；标准候选接口已能读取结果 | 删除事故执行入口及文档命令 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C04 无调用私有方法 | `parseUuidOrRandom`、`jaccard`、`readStringMap`、`trimToBudget`、`writeSections`、`remoteUidOrThrow` 在当前 main/test/scripts 无调用；前三项同时核对实时 CodeGraph | 删除六个方法 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C05 测试记录器 | `InMemorySlopQualityRecorder` 只由同包 `SlopQualityGateTest` 消费 | 移入 test 目录，不进入生产 JAR | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C06 前端残留包装 | `AdminEntry` 未使用，真正动态入口为 bootstrap 的 `mountAdmin`；本地分页工具只剩自身测试；Tooltip 只挂空 Provider，没有 Tooltip 消费者 | 删除重复入口、旧分页及对应测试、空 Provider 和直接依赖 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C07 UI 组件未用变体 | TypeScript 符号引用覆盖全部前端源码及测试，排除导出列表与 displayName 元数据后，sheet/dialog/table/select/dropdown-menu/context-menu 中 28 个组件无消费者 | 删除未用变体、导出和孤立图标导入；保留内部使用的滚动按钮、Portal 等 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C08 部署预检 S4 | RuntimeEnvironmentPreflight 的预生产固定域名/端口、Redis/Qdrant 明文及空凭据要求和 root/ainovel 名称限制；旧迁移器另有固定地址 | 删除固定值限制；保留 MySQL TLS 参数、生产 TLS/凭据、必填项及认证规则 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C09 环境变量 S5 | PowerShell 函数及读文件黑名单、Python parse_env 黑名单、Shell 名称拒绝和 unset、固定覆盖继承 PATH | 删除黑名单及清洗；配置格式仍按 S3 沿用；旧认证变量名称拒绝随后按 S13 删除 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C10 旧发布/迁移 S6 | 当前平台 catalog 指向镜像及 ainovel-runtime-compose；两阶段脚本无平台调用；旧迁移器固定 V35，当前 SQL 至 V50 | 删除独有执行器、封包/扫描/恢复契约及专用测试；保留现行运行模板与 SQL | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C11 工具链 S7 | Windows Build/Test/Start 的精确版本与 package engines 重复限制 | 放宽为已批准范围，默认 Corepack pnpm 11.22.0 不变 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C12 退役接口 | ES 查询实现恒定返回不可用空页；Beta Reader/连续性触发器恒定 501，当前无产品调用 | 删除四个 HTTP 入口、旧 DTO、禁用按钮及 ES 配置；中央日志、独立审计和历史报告读取保留 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C13 未用客户端封装 | 当前全部前端源码的 TypeScript 属性访问图，未发现动态 api 下标或 namespace 别名消费；41 个内联封装只有测试或无消费者 | 删除封装和三个孤立转换函数；移除十一项仅测试旧封装的场景；报价/鉴权/历史响应覆盖迁移到现用入口 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C14 质量失败评分 | Plot/Slop/Drift 的失败、短稿或不完整 coverage 曾暴露数据库占位分；趋势把失败纳入均值 | DTO 返回 null；趋势先选最新再排除失败，空均值 null；保留实体非空列及历史值；前端和 API 文档同步 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C15 JSON 静默降级 | 共用 JsonColumnCodec、前端报告 JSON 解析和工作流写入消费者 | 可选字段 null/blank 保留业务空值；非空损坏/类型错误抛错，写入失败不写空对象；移除写入 fallback 参数 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C16 必需依赖与计费 | AiService 的双构造器使旧测试关闭准入/预占；一批默认注入字段还允许缺失后走旧实现 | 删除关闭保护的构造器、执行分支及重复校验；必需 Bean 改为必需注入，测试显式提供替身 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C17 旧检索旁路 | MaterialEvidence 为无条件 Bean；旧检索分支仅在该 Bean 缺失或四参测试构造器时进入 | 删除无依赖的旧搜索/入队路径和过时测试；旧索引写入器随后按 S18 全部退役；历史表和数据不动 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C18 质量和上下文旁路 | QualityPersistenceBoundary、隔离上下文、W4 选择器、快照与版本服务均为实际 Bean | 删除缺失时跳过事务、隔离、来源快照和世界选择的分支；删除外层吞掉质量持久化失败的 catch | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C19 素材向量失败 | 生产 Evidence 构造器已用严格 Qdrant 模式；另一构造器查询失败返回空匹配 | 统一严格实现并删 strict 旁路，错误结果不当作无匹配；删除无条件真实实现旁的 Noop embedding Bean | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C20 初始化补造 | StoryConceptionDraftNormalizer、ProjectionBuilder 和 UI fallback 自行生成人物、章节、伏笔及固定置信度 | 删除补造器；对必需 AI 结构校验，投影只映射真实内容；允许作者元数据默认值和明确可选空集合 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C21 假模型比较 | compare-models 是拼接候选、固定分数及虚构 usage 的演示；第二轮纠正首次“无前端消费者”的误判，实际有 Settings 和 V2Studio 两处消费者 | 删除演示 HTTP/实现、两个 UI 入口、客户端包装、专属文案及假 usage 写入、旧模型配置缺失旁路；当前配置和历史偏好读取保留 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C22 其他静默数据 | 管理员余额 catch→0、设置 JSON→空、角色日志跳过损坏行、结果元数据损坏→无记录 | 读取/写入失败明确报错，保留合法 null 及无旧元数据记录；不回写历史 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C23 引用复查新增死代码 | 本类 token 引用、全仓文本及当前 CodeGraph 的 callers 核对 | 删除第二轮六个无用私有方法、孤立依赖字段及未用 RuntimeConfiguration.fromEnvironment | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C24 测试继承环境 | Maven Surefire 仍有独立 excludedEnvironmentVariables 名单 | 按 S5 删除；保留显式 test ENV/profile/H2 契约 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C25 调用图零入边复查 | 681 个候选查询入边，60 个零入边继续核对源码；排除 Spring/JPA 回调、静态导入、嵌套类型和链式调用的假阳性 | 删除无产品调用的候选旧写入器、模型路由/假用量实体和仓库、计费旧包装、旧评测整对包装、未用请求类型/查询/辅助方法；历史表和 SQL 不变 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C26 旧配置工具复制 | catalog 只取仓库格式标记、mapping 和实际 Docker/Compose；控制面使用自身 Python 工具；旧四个脚本在产品中无调用 | 删除旧 shell_configuration、grpc_shared、compose_parameters、load-configuration-pair；Python config_pair 缩减为当前验证读取器实际调用的五个助手 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |
| C27 读取与结果真实性 | 前端空统计、构思补造、损坏任务回执；归因 JSON/实际模型、模型能力与任务操作枚举的静默兜底 | 区分合法空值和失败；失败保留正文、已加载数据和输入，并呈现错误；删除未用 TS 类型 | 最终 L2、所属定向及引用检查通过；专项结果见验证节 |

C28：删除 V2Studio 手工演示会话入口，其结束请求固定写入 320/35 字虚构统计；真实编辑器会话及统计继续使用现行 API。静态翻译引用同时核对动态命名空间及 i18next 复数形式，删除失效标签，保留动态消费者。

C29：配置消费者复查删除旧 HTTP AI 的 model/base-url/api-key 声明、映射/别名，以及无人读取的通用 external.timeout-ms/getter/setter；现行文本模型配置、独立 AI/计费/会话超时与 gRPC 认证保持。私有运行配置未修改。

C30：前端类型引用排除 default export 自身后，确认 AppearanceSettings 仅提供类型、组件未挂载；类型收回 TiptapEditor，删除旧组件及唯一服务它的 Popover 和直接依赖。删除旧 Accordion 动画和不存在的 Tailwind 搜索目录；正文样式与实际编辑器设置保留。

C31：S17–S19 对应的旧研究入口、旧素材写入器及认证头残留已删除，旧数据库表/数据和现行产品读取保持；标准配置读取器仍由 Windows 入口使用，不能随旧采集器删除。

C32：S8 最终复查删除余额错误→旧值/零、角色/目标错误→空列表、候选用量损坏→空对象；浏览器草稿及创建请求身份损坏明确反馈并保留原记录，不生成替代幂等键。历史版本缺失字数与损坏内容可区分。

C33：删除伪图谱同步、固定置信度的旧实体抽取、固定对白的“AI 声音生成”及专属按钮/封装/文案；保留历史读取、真实关系编辑、作者声音编辑。声音预览只格式化已填写内容。轻量风格指标拒绝空样本，移除固定语气结论和通用建议。

C34：删除分析页遗漏的退役禁用控件、无效固定章节选择和人工张力曲线；历史报告的缺失评分保持未知，真实零值不改写。历史 issue 改为只读状态显示。刷新失败保留已读结果并反馈。

C35：风格设置按实际 Map/List 类型读取；错误结构、分数或对白行不再变成空数据或默认分。合法可选 null 保持正确的空对象/空数组，刷新失败保留作者未保存输入；分析必须提供全部真实维度。删除仅对已验证非空样本重复执行的空值检查，以及对本函数必定生成指标的固定 6 分兜底。后端 4 项、前端 2 项定向测试通过。

C36：管理员质量聚合曾绕过质量 DTO，直接读取数据库占位风险分。现按状态和语义覆盖标记先投影为 nullable 分数/严重程度，再统一筛选、计数、分页；高风险数量复用同一读模型。删除原始风险计数仓库方法及无用注入。前端使用实际 nullable 契约解析并以“—”显示未知，保留真实0。沿用现有 LanguageQualityMysqlTest 增加历史、损坏/未完成、空值、零分和分页验证；不改历史列或记录。

## 调用、依赖与文档复查

TypeScript最终零引用候选仅`mountAdmin`和`mountUser`，由bootstrap动态调用；未用API包装扫描为空。初始681个Java main方法候选中，60个零入边已继续核实并分类处置；不能把框架回调当死代码：保留实际Scheduled对账/G2租约、PreDestroy客户端/worker、JPA生命周期及Application/HealthProbe/ManuscriptStorageMaintenance CLI。

静态或链式调用已核对：WritingAgentDtos.checked/defaults/withSelection、WorldContextPreferences.defaults、WorldAgentTools.invoke/requiredComplete、CandidateAgentTools.parse、LanguageStandardSelection.replacesLegacy、LanguageSettings.configure、Story/WorldAssistantErrors、MaterialFingerprint.uuid、XmlZipSupport.xml、PayServiceJwt.currentToken、ConsulEndpoint地址转换、ProgressUpdater.step。Servlet.getContentLength、UserDetails.getPassword、HTTPSettings.getPrompts、V2Export.getExportJob有真实接口/字段用途；SafeLogThrowable.serialVersionUID为序列化标识。

依赖按直接调用、动态加密/导出、peer及测试注册核对：旧grpc starter和旧UI专属依赖删除；实际RPC、Argon2的bcprov、PDFBox、React Query/Sonner/Tiptap所需peer和testing-library/dom保留。Java无用/重复import检查为空，PowerShell入口、配置/映射消费者已交叉核对；50份Flyway SQL与基线无差异，全仓Markdown本地链接无缺失。

## 验证与交付

- 此前切片已推送：`12ac880`、`9e78dc9`、`481ac26`、`eae392c`、`640ead0`、`30136a3`、`b1620d9`、`41941be`、`b629e17`；后续管理员补齐及最终交付记录继续追加。
- 定向后执行标准Windows `Test-Local.ps1 -Level L2 -Component All`、`Build-Local.ps1 -Component All`，均exit 0。当前源码208份后端报告共967项，822通过、145条件跳过，零失败/错误；前端88文件/483项通过，lint、类型检查、生产构建通过。已删除类的旧XML不计入本次结果。
- 默认145项跳过中134项为现有隔离MySQL专项，执行结果单独汇总；8项Testcontainers测试因Windows不运行Docker跳过，使用现有外部MySQL迁移专项；3项历史冻结请求导出保持opt-in，不重新生成作者材料。
- MySQL：验收中；每类使用任务专用临时schema，结束即删除，独立源码/target防止影响正常服务。不向正常ainovel库写测试夹具。
- 正常local：`b629e17`已通过旧工作树标准Stop、新工作树标准Start交接；11040/11041进程、来源目录、就绪及正常TLS HTTPS入口均核实。桌面/390px验收进行中，不发起真实付费AI请求、不重发历史未知请求。

## 安全事项与作者明确决定

以下均获明确许可；没有新增防护、专项漏洞扫描或发布门槛。独立用途不自动归入既有批准。

| 编号 | 明确决定 | 边界 |
|---|---|---|
| S1 | 保留登录、资源权限、管理员二次验证、格式校验、版本/并发、计费幂等和预算、未知结果不重发、H2 隔离、作者采纳及日志脱敏 | 实际使用的最小实现；无调用或重复代码仍删除 |
| S2 | 保留浏览器偏好降级、明确标记失败的检查、现存历史/冻结任务/旧世界绑定兼容、已执行 SQL 和作者待审阅材料 | 不将损坏业务数据解释为空值或正常结果 |
| S3 | 保留秘密文件权限、普通文件、字面值读取、重复键、配置分工、当前必需配置、ENV/AUTH_MODE、TLS 和环境证书边界 | 不增加新规则；重复实现合并 |
| S4 | 删除固定部署主机/端口、强制预生产明文无凭据、按 root/ainovel 名称拒绝启动 | 配置中心继续提供实际连接值；保留通用格式、加密和鉴权 |
| S5 | 删除进程变量名称黑名单及继承环境清洗 | YAML 放非秘密配置，env 只放秘密的格式分工保留 |
| S6 | 退役未接入的两阶段构建、专有扫描和封包校验、旧 V35 生产迁移及 Guard | 保留现行 catalog 镜像/Compose/启动；生产不自动迁移；旧机制能力不再提供 |
| S7 | Node >=22.23.2 <23；pnpm >=11.22.0 <12；默认 pnpm 11.22.0 | 保留 Windows/PowerShell、本地环境、端口、进程归属、超时和严格就绪检查 |
| S8 | 删除静默假成功、猜测性内容补全、固定失败评分、测试构造器关闭保护、必需依赖缺失旁路 | 保留作者业务默认值和不改变内容的可验证格式适配；不追加付费修复 |

作者补充决定 S9：删除 Slop/drift 固定的作者 AI 身份免责声明及对应提示词约束；保留历史报告原文，不改写冻结请求。现行代码、测试示例及 API/机制说明已同步。

作者补充决定 S10：删除旧角色变化占位触发入口，返回 404；保留角色日志读取和 H1 真实抽取。已删除控制器/服务/请求 DTO，并收窄既有 UUID 保存路由，避免该退役字面路径匹配成 405。

作者补充决定 S11：退役直接调用旧固定模型的付费研究测试、专有 Maven profile 和命令说明；保留离线语料与历史研究材料。本次未执行真实模型调用。

作者补充决定 S12：删除旧管理员无密码登录提示 `/api/v1/admin-auth/login/challenge`，返回 404；现行登录与二次验证保留。

作者补充决定 S13：删除 Shell/Java 对旧认证变量名称的拒绝；旧协议不恢复，现行 HMAC/JWT 必填、格式和 YAML/env 分工继续校验。

作者补充决定 S14：删除普通用户旧改密提示 `/api/v1/user/password` 及专属响应 DTO、文档，返回 404；统一账号服务继续负责密码。

额外获准事项 S15：标准 mTLS 续签和原 Nginx 重载。过期产品叶被 CA 拒绝，无运行变更。

额外获准事项 S16：受限过期重签恢复。基础设施提交 fd62b59 已推送；产品证书保持原 CA/CN/SAN/路径，新有效期至 2026-11-28 07:01:45 UTC。服务证书至 2026-11-02 14:38:12 UTC 仍有效，恢复入口拒绝替换。原 SYSTEM Nginx 验证并重载，实际 HTTPS 已在正常信任验证下返回 200。一次性 SYSTEM 重载任务已清除。其他基础设施既有未提交改动未纳入提交。

作者补充决定 S17：退役 MaterialSelectionMysqlTest、采集器及专用助手。离线评分器同样无法校验 Git 当前冻结文件，按同一旧研究入口决定删除；语料、排名、原 manifest 与历史报告保持。不将该差异解释为 Windows 换行问题，也不重新认证旧结果。

作者补充决定 S18：退役旧素材后台写入器及其嵌入客户端、重试、租约、预算/容量配置与专有测试。现行 MaterialEvidence 捕获、索引及检索保留；历史表和 SQL 不变。旧 job/chunk 实体与仓库在删除写入器后没有独立产品读取消费者，因此一并删除源代码；已有素材、证据任务与报告读取保留，没有删除现有 HTTP 历史读取接口。

作者补充决定 S19：删除 PayService 对 x-internal-token 的旧头清洗及专用断言；每次 RPC 替换现行 Bearer JWT 仍在执行。

作者补充决定 S20：保留语义检索/重排失败时明确标记的真实基础检索降级，不追加模型调用。

作者补充决定 S21：保留正文提交后归因重算失败时的已保存正文和待重算状态。

作者补充决定 S22：删除旧向量清理失败仅记日志的容错；提交后清理失败返回明确 502，界面说明素材变更已保存并提示刷新。不会把已提交事务描述为已回滚。

作者补充决定 S23：删除仅验签登录兼容旁路及远程会话校验关闭开关。UserSessionValidator 是 JwtAuthFilter 的必需依赖；实际会话无效/不可用继续分别返回 401/503。test profile 不建立真实传输，完整应用测试显式提供 Mockito 替身；删除未使用的 UserDetailsService 构造器参数及其无效测试夹具。旧私有配置不改写，旧开关不再作为输入。
