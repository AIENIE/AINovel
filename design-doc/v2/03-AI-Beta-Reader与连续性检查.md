# AI Beta Reader 与连续性检查

## 文档信息

| 字段 | 值 |
|------|-----|
| 版本 | v2.0 |
| 日期 | 2026-02-17 |
| 优先级 | P1 |
| 关联模块 | 01-上下文记忆, 02-风格画像, 04-版本控制 |

---

## 1. 背景与动机

### 1.1 问题陈述

当前 v1 版本的 AINovel 系统缺乏任何自动化的写作质量反馈机制：

1. **无自动质量反馈**：作者完成章节后，只能依赖人类 Beta Reader 进行审读。人类审读周期长（通常数天到数周）、成本高、且反馈质量参差不齐。在日更连载场景下，作者几乎不可能在发布前获得有效的质量反馈。

2. **连续性错误累积**：长篇小说（10万字以上）在创作过程中极易累积连续性错误——角色眼睛颜色在第3章是蓝色、第8章变成棕色；已在第5章死亡的角色在第12章突然重新出现；时间线矛盾（角色在同一天出现在两个相距千里的地点）。当前系统中 `Manuscript.sectionsJson` 仅存储各场景正文，`CharacterCard` 仅存储静态角色信息，没有任何机制追踪和校验这些动态事实。

3. **叙事结构盲区**：作者在沉浸式写作中难以客观评估自己的叙事节奏——是否存在连续多章的平淡叙述、对话是否过于冗长、角色弧线是否完整、"展示而非讲述"（show vs tell）的比例是否合理。这些结构性问题通常需要专业编辑才能发现。

4. **问题追踪缺失**：即使作者自己发现了问题，也没有系统化的方式记录、追踪和管理这些问题。问题散落在笔记、便签、脑海中，容易遗漏。

5. **与上下文记忆系统脱节**：01-上下文记忆系统（Lorebook + 知识图谱）积累了丰富的故事事实数据，但这些数据目前仅用于 AI 续写时的上下文注入，未被用于反向校验已写内容的一致性。

### 1.2 业界参考

| 产品/方案 | 核心机制 | 可借鉴点 |
|-----------|----------|----------|
| **Sudowrite Story Bible** | 自动从文本提取角色/地点/物品信息，构建故事圣经，支持一致性校验 | 事实提取 + 交叉校验的流程设计 |
| **ProWritingAid** | 对文本进行多维度分析：可读性评分、句长分布、重复词检测、节奏分析、对话标签检查 | 多维度评分体系、可视化报告设计 |
| **Grammarly** | 实时语法/风格检查，提供上下文相关的修改建议，支持语气/正式度分析 | 实时反馈机制、建议的可操作性 |
| **Fictionary** | 基于38个故事元素（Story Elements）的结构化分析，包括场景目标、冲突、结局、POV等 | 结构化叙事分析框架、场景级别的评估维度 |
| **Marlowe (by Authors A.I.)** | AI 驱动的稿件分析，输出节奏曲线、角色弧线、情感热力图 | 张力曲线可视化、角色发展追踪 |
| **Atticus** | 书籍格式化工具，内置基础一致性检查（拼写、格式） | 轻量级检查与重量级分析的分层策略 |

---

## 2. 设计目标

| 编号 | 目标 | 说明 |
|------|------|------|
| G1 | AI Beta Reader | 提供全面的稿件分析能力，覆盖叙事节奏、张力曲线、角色弧线、对话质量、展示/讲述比例等维度，输出量化评分和文字建议 |
| G2 | 连续性检查 | 自动检测小说内部的事实不一致，包括角色属性矛盾、时间线错误、地点错误、物品状态矛盾、已死角色复活、名称不一致等 |
| G3 | 问题追踪 | 发现的问题以结构化方式存储，支持状态管理（打开/已确认/已解决/误报），可关联到具体的章节和文本位置 |
| G4 | 章节级与全稿分析 | 支持单章节快速分析（写完一章立即检查）和全稿综合分析（完成初稿后全面审读） |
| G5 | 知识图谱集成 | 连续性检查与 01-上下文记忆系统的 Lorebook 和 Neo4j 知识图谱深度集成，利用已有的结构化事实进行校验 |
| G6 | 异步执行 | 分析任务通过异步作业执行（复用现有 Spring @Async + 状态轮询模式），不阻塞用户操作 |
| G7 | 可视化报告 | 分析结果以直观的可视化形式呈现——雷达图、张力曲线、时间线等 |

---

## 3. 详细设计

### 3.1 数据模型

#### 3.1.1 ER 关系概览

```
stories 1──N beta_reader_reports
stories 1──N continuity_issues
stories 1──N analysis_jobs
users   1──N analysis_jobs
users   1──N beta_reader_reports
beta_reader_reports 1──N continuity_issues (可选关联)
analysis_jobs 1──0..1 beta_reader_reports (通过 result_reference)
```

#### 3.1.2 表：`beta_reader_reports`（Beta Reader 分析报告）

存储每次 Beta Reader 分析的完整结果，包含多维度评分和详细分析文本。

```sql
CREATE TABLE beta_reader_reports (
    id                  CHAR(36)        NOT NULL    COMMENT '主键 UUID',
    story_id            CHAR(36)        NOT NULL    COMMENT '所属故事 FK -> stories.id',
    user_id             CHAR(36)        NOT NULL    COMMENT '发起用户 FK -> users.id',
    scope               ENUM('chapter','full_manuscript','scene')
                                        NOT NULL    COMMENT '分析范围',
    scope_reference     VARCHAR(200)    NULL        COMMENT '范围引用：章节索引 / 场景ID / NULL(全稿)',
    status              ENUM('pending','analyzing','completed','failed')
                                        NOT NULL DEFAULT 'pending'
                                                    COMMENT '报告状态',
    analysis_json       LONGTEXT        NULL        COMMENT '完整分析结果 JSON（见下方结构）',
    summary             TEXT            NULL        COMMENT '执行摘要：一段话概括分析结论',
    score_overall       INT             NULL        COMMENT '综合评分 1-100',
    score_pacing        INT             NULL        COMMENT '叙事节奏评分 1-100',
    score_characters    INT             NULL        COMMENT '角色塑造评分 1-100',
    score_dialogue      INT             NULL        COMMENT '对话质量评分 1-100',
    score_consistency   INT             NULL        COMMENT '情节连贯性评分 1-100',
    score_engagement    INT             NULL        COMMENT '读者吸引力评分 1-100',
    token_cost          INT             NOT NULL DEFAULT 0
                                                    COMMENT '本次分析消耗的 Token 数',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_brr_story      (story_id),
    INDEX idx_brr_user       (user_id),
    INDEX idx_brr_status     (story_id, status),
    INDEX idx_brr_scope      (story_id, scope),
    INDEX idx_brr_created    (story_id, created_at DESC),
    CONSTRAINT fk_brr_story  FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_brr_user   FOREIGN KEY (user_id)  REFERENCES users(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI Beta Reader 分析报告';
```

`analysis_json` 结构示例：

```json
{
  "pacing": {
    "score": 72,
    "tensionCurve": [
      { "position": 0.0, "tension": 3, "label": "开场" },
      { "position": 0.15, "tension": 5, "label": "冲突引入" },
      { "position": 0.4, "tension": 8, "label": "高潮前奏" },
      { "position": 0.6, "tension": 9, "label": "高潮" },
      { "position": 0.8, "tension": 6, "label": "回落" },
      { "position": 1.0, "tension": 4, "label": "结尾" }
    ],
    "issues": [
      "第3-5段叙述节奏偏慢，连续三段环境描写缺乏推进力",
      "高潮部分转折过于突然，缺少铺垫"
    ],
    "suggestions": [
      "建议在第4段插入一个小冲突或悬念，打破平淡",
      "在高潮前增加一段角色内心挣扎，增强张力递进感"
    ]
  },
  "characters": {
    "score": 85,
    "arcs": [
      {
        "name": "林逸",
        "role": "protagonist",
        "developmentNotes": "本章展现了从犹豫到坚定的转变，弧线完整",
        "consistencyIssues": []
      },
      {
        "name": "苏瑶",
        "role": "supporting",
        "developmentNotes": "出场较少，性格特征不够鲜明",
        "consistencyIssues": ["第7段的反应与前文建立的冷静性格不符"]
      }
    ],
    "suggestions": [
      "苏瑶的角色需要更多独特的行为细节来区分",
      "建议为配角增加至少一个标志性动作或口头禅"
    ]
  },
  "dialogue": {
    "score": 68,
    "totalLines": 42,
    "showVsTellRatio": 0.6,
    "tagVariety": 0.45,
    "issues": [
      "对话标签过于单一，80%使用'说'",
      "林逸和苏瑶的对话语气过于相似，缺乏区分度"
    ],
    "suggestions": [
      "减少对话标签使用，通过动作描写暗示说话者",
      "参考角色声音画像（→ 02-风格画像），为每个角色赋予独特的语言习惯"
    ]
  },
  "consistency": {
    "score": 78,
    "issues": [
      {
        "type": "timeline_error",
        "description": "第3段提到'三天后'，但第5段的时间描述暗示仅过了一天",
        "severity": "warning"
      }
    ]
  },
  "engagement": {
    "score": 75,
    "hookStrength": 7,
    "cliffhangerScore": 6,
    "emotionalResonance": 8,
    "suggestions": [
      "开头可以用一个更强的钩子——直接从冲突场景切入",
      "结尾缺少悬念，建议留一个未解决的问题引导读者继续"
    ]
  },
  "overallSuggestions": [
    "本章整体质量良好，角色发展是最大亮点",
    "主要改进方向：节奏控制和对话多样性",
    "建议重点修改第3-5段的节奏问题和对话标签"
  ]
}
```

#### 3.1.3 表：`continuity_issues`（连续性问题）

存储检测到的所有连续性问题，支持独立于报告存在（手动创建）或关联到某次分析报告。

```sql
CREATE TABLE continuity_issues (
    id                  CHAR(36)        NOT NULL    COMMENT '主键 UUID',
    story_id            CHAR(36)        NOT NULL    COMMENT '所属故事 FK -> stories.id',
    report_id           CHAR(36)        NULL        COMMENT '来源报告 FK -> beta_reader_reports.id（可为空）',
    issue_type          ENUM(
                            'character_inconsistency',
                            'timeline_error',
                            'location_error',
                            'object_state',
                            'plot_hole',
                            'dead_character',
                            'name_inconsistency'
                        )               NOT NULL    COMMENT '问题类型',
    severity            ENUM('critical','warning','info')
                                        NOT NULL DEFAULT 'warning'
                                                    COMMENT '严重程度',
    description         TEXT            NOT NULL    COMMENT '问题描述',
    evidence_json       TEXT            NULL        COMMENT '证据 JSON 数组（见下方结构）',
    suggestion          TEXT            NULL        COMMENT '修复建议',
    status              ENUM('open','acknowledged','resolved','false_positive')
                                        NOT NULL DEFAULT 'open'
                                                    COMMENT '问题状态',
    resolved_at         TIMESTAMP       NULL        COMMENT '解决时间',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_ci_story       (story_id),
    INDEX idx_ci_report      (report_id),
    INDEX idx_ci_type        (story_id, issue_type),
    INDEX idx_ci_severity    (story_id, severity),
    INDEX idx_ci_status      (story_id, status),
    INDEX idx_ci_open        (story_id, status, severity),
    CONSTRAINT fk_ci_story   FOREIGN KEY (story_id)  REFERENCES stories(id)              ON DELETE CASCADE,
    CONSTRAINT fk_ci_report  FOREIGN KEY (report_id) REFERENCES beta_reader_reports(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='连续性问题追踪表';
```

`evidence_json` 结构示例：

```json
[
  {
    "chapterIndex": 2,
    "sceneIndex": 1,
    "textExcerpt": "林逸的蓝色眼睛在月光下闪烁",
    "lineHint": "第3段第2行"
  },
  {
    "chapterIndex": 7,
    "sceneIndex": 0,
    "textExcerpt": "他棕色的眼眸中满是坚定",
    "lineHint": "第1段第5行"
  }
]
```

#### 3.1.4 表：`analysis_jobs`（分析任务）

统一管理所有类型的分析异步任务，复用现有 Spring @Async + 状态轮询模式。

```sql
CREATE TABLE analysis_jobs (
    id                  CHAR(36)        NOT NULL    COMMENT '主键 UUID',
    story_id            CHAR(36)        NOT NULL    COMMENT '所属故事 FK -> stories.id',
    user_id             CHAR(36)        NOT NULL    COMMENT '发起用户 FK -> users.id',
    job_type            ENUM('beta_reader','continuity_check','style_check')
                                        NOT NULL    COMMENT '任务类型',
    scope               ENUM('chapter','full','scene')
                                        NOT NULL    COMMENT '分析范围',
    scope_reference     VARCHAR(200)    NULL        COMMENT '范围引用：章节索引 / 场景ID',
    status              ENUM('queued','processing','completed','failed')
                                        NOT NULL DEFAULT 'queued'
                                                    COMMENT '任务状态',
    progress            INT             NOT NULL DEFAULT 0
                                                    COMMENT '进度百分比 0-100',
    progress_message    VARCHAR(500)    NULL        COMMENT '当前进度描述，如"正在分析第3章..."',
    result_reference    CHAR(36)        NULL        COMMENT '结果引用：报告ID',
    error_message       TEXT            NULL        COMMENT '失败原因',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_aj_story       (story_id),
    INDEX idx_aj_user        (user_id),
    INDEX idx_aj_status      (story_id, status),
    INDEX idx_aj_type        (story_id, job_type),
    INDEX idx_aj_created     (story_id, created_at DESC),
    CONSTRAINT fk_aj_story   FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_aj_user    FOREIGN KEY (user_id)  REFERENCES users(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='分析任务队列表';
```

#### 3.1.5 JPA 实体与现有模型的关系

```
Story (现有, com.ainovel.app.story.model.Story)
  ├── @OneToMany → BetaReaderReport (新增)
  ├── @OneToMany → ContinuityIssue  (新增)
  └── @OneToMany → AnalysisJob      (新增)

User (现有, com.ainovel.app.user.User)
  ├── @OneToMany → BetaReaderReport (新增)
  └── @OneToMany → AnalysisJob      (新增)

BetaReaderReport (新增)
  └── @OneToMany → ContinuityIssue  (新增, 可选关联)

AnalysisJob (新增)
  └── result_reference → BetaReaderReport.id (逻辑外键，非物理约束)
```

### 3.2 后端设计

#### 3.2.1 包结构

```
backend/src/main/java/com/ainovel/app/analysis/
├── AnalysisController.java          // REST 控制器，处理所有分析相关 API
├── model/
│   ├── BetaReaderReport.java        // JPA 实体
│   ├── ContinuityIssue.java         // JPA 实体
│   └── AnalysisJob.java             // JPA 实体
├── dto/
│   ├── BetaReaderReportDto.java     // 报告响应 DTO
│   ├── ContinuityIssueDto.java      // 问题响应 DTO
│   ├── AnalysisJobDto.java          // 任务响应 DTO
│   ├── TriggerAnalysisRequest.java  // 触发分析请求
│   ├── UpdateIssueStatusRequest.java // 更新问题状态请求
│   └── AnalysisScoreDto.java        // 评分摘要 DTO
├── repo/
│   ├── BetaReaderReportRepository.java
│   ├── ContinuityIssueRepository.java
│   └── AnalysisJobRepository.java
├── service/
│   ├── BetaReaderService.java       // Beta Reader 分析编排
│   ├── ContinuityCheckService.java  // 连续性检查核心逻辑
│   ├── PacingAnalysisService.java   // 叙事节奏分析
│   ├── CharacterArcAnalysisService.java // 角色弧线分析
│   ├── DialogueAnalysisService.java // 对话质量分析
│   └── AnalysisJobService.java      // 异步任务生命周期管理
└── prompt/
    └── AnalysisPromptBuilder.java   // 分析用 Prompt 模板构建器
```

#### 3.2.2 核心类设计

##### AnalysisController

```java
package com.ainovel.app.analysis;

@RestController
@RequestMapping("/v2/stories/{storyId}/analysis")
@Tag(name = "Analysis", description = "AI Beta Reader 与连续性检查接口")
@SecurityRequirement(name = "bearerAuth")
public class AnalysisController {

    @Autowired private BetaReaderService betaReaderService;
    @Autowired private ContinuityCheckService continuityCheckService;
    @Autowired private AnalysisJobService analysisJobService;
    @Autowired private UserRepository userRepository;

    // ===== 分析任务 =====

    @PostMapping("/beta-reader")
    @Operation(summary = "触发 Beta Reader 分析")
    public ResponseEntity<AnalysisJobDto> triggerBetaReader(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID storyId,
            @Valid @RequestBody TriggerAnalysisRequest request) {
        User user = currentUser(principal);
        AnalysisJob job = betaReaderService.trigger(user, storyId, request);
        return ResponseEntity.accepted().body(AnalysisJobDto.from(job));
    }

    @PostMapping("/continuity-check")
    @Operation(summary = "触发连续性检查")
    public ResponseEntity<AnalysisJobDto> triggerContinuityCheck(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID storyId,
            @Valid @RequestBody TriggerAnalysisRequest request) {
        User user = currentUser(principal);
        AnalysisJob job = continuityCheckService.trigger(user, storyId, request);
        return ResponseEntity.accepted().body(AnalysisJobDto.from(job));
    }

    @GetMapping("/jobs")
    @Operation(summary = "查询分析任务列表")
    public List<AnalysisJobDto> listJobs(@PathVariable UUID storyId) { ... }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询任务状态与进度")
    public AnalysisJobDto getJob(@PathVariable UUID storyId, @PathVariable UUID jobId) { ... }

    // ===== 报告 =====

    @GetMapping("/reports")
    @Operation(summary = "查询分析报告列表")
    public List<BetaReaderReportDto> listReports(@PathVariable UUID storyId) { ... }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "获取完整分析报告")
    public BetaReaderReportDto getReport(@PathVariable UUID storyId, @PathVariable UUID reportId) { ... }

    // ===== 连续性问题 =====

    @GetMapping("/continuity-issues")
    @Operation(summary = "查询连续性问题列表")
    public List<ContinuityIssueDto> listIssues(
            @PathVariable UUID storyId,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status) { ... }

    @PutMapping("/continuity-issues/{issueId}")
    @Operation(summary = "更新问题状态")
    public ContinuityIssueDto updateIssueStatus(
            @PathVariable UUID storyId,
            @PathVariable UUID issueId,
            @Valid @RequestBody UpdateIssueStatusRequest request) { ... }
}
```

##### BetaReaderService

```java
package com.ainovel.app.analysis.service;

@Service
public class BetaReaderService {

    @Autowired private AnalysisJobService jobService;
    @Autowired private PacingAnalysisService pacingService;
    @Autowired private CharacterArcAnalysisService characterArcService;
    @Autowired private DialogueAnalysisService dialogueService;
    @Autowired private ContinuityCheckService continuityService;
    @Autowired private AiService aiService;
    @Autowired private ManuscriptService manuscriptService;
    @Autowired private BetaReaderReportRepository reportRepository;

    /**
     * 触发 Beta Reader 分析，创建异步任务。
     */
    public AnalysisJob trigger(User user, UUID storyId, TriggerAnalysisRequest request) {
        AnalysisJob job = jobService.createJob(user, storyId, "beta_reader",
                request.scope(), request.scopeReference());
        executeAsync(job, user, storyId, request);
        return job;
    }

    /**
     * 分析单个章节：节奏 + 角色 + 对话 + 一致性。
     */
    public BetaReaderReport analyzeChapter(UUID storyId, int chapterIndex, User user) {
        // 1. 从 Manuscript 提取该章节所有场景文本
        // 2. 并行调用各子分析服务
        // 3. 汇总评分，生成报告
        // 4. 保存并返回
    }

    /**
     * 全稿分析：逐章分析 + 跨章汇总。
     */
    public BetaReaderReport analyzeFullManuscript(UUID storyId, User user) {
        // 1. 获取所有章节列表
        // 2. 逐章分析（更新进度）
        // 3. 跨章节综合分析（整体节奏曲线、角色弧线完整性）
        // 4. 运行全稿连续性检查
        // 5. 汇总生成最终报告
    }

    @Async
    private void executeAsync(AnalysisJob job, User user, UUID storyId,
                              TriggerAnalysisRequest request) {
        try {
            jobService.updateStatus(job.getId(), "processing", 0, "开始分析...");
            BetaReaderReport report;
            if ("chapter".equals(request.scope())) {
                int chapterIndex = Integer.parseInt(request.scopeReference());
                report = analyzeChapter(storyId, chapterIndex, user);
            } else {
                report = analyzeFullManuscript(storyId, user);
            }
            jobService.complete(job.getId(), report.getId());
        } catch (Exception e) {
            jobService.fail(job.getId(), e.getMessage());
        }
    }
}
```

##### ContinuityCheckService

```java
package com.ainovel.app.analysis.service;

@Service
public class ContinuityCheckService {

    @Autowired private AiService aiService;
    @Autowired private AnalysisJobService jobService;
    @Autowired private ContinuityIssueRepository issueRepository;
    @Autowired private AnalysisPromptBuilder promptBuilder;
    // 以下为 01-上下文记忆系统提供的服务
    // @Autowired private LorebookService lorebookService;
    // @Autowired private KnowledgeGraphService knowledgeGraphService;

    /**
     * 触发连续性检查，创建异步任务。
     */
    public AnalysisJob trigger(User user, UUID storyId, TriggerAnalysisRequest request) {
        AnalysisJob job = jobService.createJob(user, storyId, "continuity_check",
                request.scope(), request.scopeReference());
        executeAsync(job, user, storyId, request);
        return job;
    }

    /**
     * 检查全稿连续性：提取事实 → 对比知识库 → 生成问题列表。
     */
    public List<ContinuityIssue> checkConsistency(UUID storyId, User user) {
        // 1. 从 Lorebook 和知识图谱获取已知事实
        // 2. 逐章提取当前文本中的事实断言
        // 3. 交叉比对，识别矛盾
        // 4. 调用 AI 进行深度分析（处理模糊矛盾）
        // 5. 生成 ContinuityIssue 列表
    }

    /**
     * 检查单章连续性：仅对比该章与已知事实。
     */
    public List<ContinuityIssue> checkChapterConsistency(
            UUID storyId, int chapterIndex, User user) {
        // 1. 获取该章节文本
        // 2. 获取相关 Lorebook 条目和知识图谱子图
        // 3. 构建连续性检查 Prompt
        // 4. 调用 AI 分析
        // 5. 解析结果，创建 ContinuityIssue 记录
    }

    @Async
    private void executeAsync(AnalysisJob job, User user, UUID storyId,
                              TriggerAnalysisRequest request) {
        try {
            jobService.updateStatus(job.getId(), "processing", 0, "正在收集事实数据...");
            List<ContinuityIssue> issues;
            if ("chapter".equals(request.scope())) {
                int chapterIndex = Integer.parseInt(request.scopeReference());
                issues = checkChapterConsistency(storyId, chapterIndex, user);
            } else {
                issues = checkConsistency(storyId, user);
            }
            jobService.complete(job.getId(), null); // 连续性检查无单一报告
        } catch (Exception e) {
            jobService.fail(job.getId(), e.getMessage());
        }
    }
}
```

##### AnalysisJobService

```java
package com.ainovel.app.analysis.service;

@Service
public class AnalysisJobService {

    @Autowired private AnalysisJobRepository jobRepository;

    public AnalysisJob createJob(User user, UUID storyId, String jobType,
                                  String scope, String scopeReference) {
        AnalysisJob job = new AnalysisJob();
        job.setStoryId(storyId);
        job.setUserId(user.getId());
        job.setJobType(jobType);
        job.setScope(scope);
        job.setScopeReference(scopeReference);
        job.setStatus("queued");
        job.setProgress(0);
        return jobRepository.save(job);
    }

    @Transactional
    public void updateStatus(UUID jobId, String status, int progress, String message) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(status);
        job.setProgress(progress);
        job.setProgressMessage(message);
        jobRepository.save(job);
    }

    @Transactional
    public void complete(UUID jobId, UUID resultReference) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus("completed");
        job.setProgress(100);
        job.setResultReference(resultReference);
        job.setProgressMessage("分析完成");
        jobRepository.save(job);
    }

    @Transactional
    public void fail(UUID jobId, String errorMessage) {
        AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus("failed");
        job.setErrorMessage(errorMessage);
        job.setProgressMessage("分析失败");
        jobRepository.save(job);
    }

    public List<AnalysisJob> listByStory(UUID storyId) {
        return jobRepository.findByStoryIdOrderByCreatedAtDesc(storyId);
    }
}
```

### 3.3 API 设计

#### 3.3.1 分析任务接口

##### POST `/v2/stories/{storyId}/analysis/beta-reader` — 触发 Beta Reader 分析

请求体：

```json
{
  "scope": "chapter",
  "scopeReference": "3"
}
```

响应（202 Accepted）：

```json
{
  "id": "job-uuid-001",
  "storyId": "story-uuid-001",
  "jobType": "beta_reader",
  "scope": "chapter",
  "scopeReference": "3",
  "status": "queued",
  "progress": 0,
  "progressMessage": null,
  "createdAt": "2026-02-17T10:30:00Z"
}
```

##### POST `/v2/stories/{storyId}/analysis/continuity-check` — 触发连续性检查

请求体：

```json
{
  "scope": "full",
  "scopeReference": null
}
```

响应（202 Accepted）：

```json
{
  "id": "job-uuid-002",
  "storyId": "story-uuid-001",
  "jobType": "continuity_check",
  "scope": "full",
  "scopeReference": null,
  "status": "queued",
  "progress": 0,
  "progressMessage": null,
  "createdAt": "2026-02-17T10:31:00Z"
}
```

##### GET `/v2/stories/{storyId}/analysis/jobs` — 查询任务列表

响应（200）：

```json
[
  {
    "id": "job-uuid-001",
    "storyId": "story-uuid-001",
    "jobType": "beta_reader",
    "scope": "chapter",
    "scopeReference": "3",
    "status": "processing",
    "progress": 45,
    "progressMessage": "正在分析对话质量...",
    "resultReference": null,
    "createdAt": "2026-02-17T10:30:00Z",
    "updatedAt": "2026-02-17T10:31:15Z"
  },
  {
    "id": "job-uuid-002",
    "storyId": "story-uuid-001",
    "jobType": "continuity_check",
    "scope": "full",
    "scopeReference": null,
    "status": "completed",
    "progress": 100,
    "progressMessage": "分析完成，发现 5 个问题",
    "resultReference": null,
    "createdAt": "2026-02-17T10:31:00Z",
    "updatedAt": "2026-02-17T10:35:00Z"
  }
]
```

##### GET `/v2/stories/{storyId}/analysis/jobs/{jobId}` — 查询单个任务状态

响应（200）：

```json
{
  "id": "job-uuid-001",
  "storyId": "story-uuid-001",
  "jobType": "beta_reader",
  "scope": "chapter",
  "scopeReference": "3",
  "status": "completed",
  "progress": 100,
  "progressMessage": "分析完成",
  "resultReference": "report-uuid-001",
  "createdAt": "2026-02-17T10:30:00Z",
  "updatedAt": "2026-02-17T10:33:00Z"
}
```

#### 3.3.2 报告接口

##### GET `/v2/stories/{storyId}/analysis/reports` — 查询报告列表

响应（200）：

```json
[
  {
    "id": "report-uuid-001",
    "storyId": "story-uuid-001",
    "scope": "chapter",
    "scopeReference": "3",
    "status": "completed",
    "summary": "第3章整体质量良好（综合72分），角色塑造突出，但叙事节奏和对话多样性有提升空间。",
    "scoreOverall": 72,
    "scorePacing": 65,
    "scoreCharacters": 85,
    "scoreDialogue": 68,
    "scoreConsistency": 78,
    "scoreEngagement": 75,
    "createdAt": "2026-02-17T10:33:00Z"
  }
]
```

##### GET `/v2/stories/{storyId}/analysis/reports/{reportId}` — 获取完整报告

响应（200）：

```json
{
  "id": "report-uuid-001",
  "storyId": "story-uuid-001",
  "scope": "chapter",
  "scopeReference": "3",
  "status": "completed",
  "summary": "第3章整体质量良好（综合72分），角色塑造突出，但叙事节奏和对话多样性有提升空间。",
  "scoreOverall": 72,
  "scorePacing": 65,
  "scoreCharacters": 85,
  "scoreDialogue": 68,
  "scoreConsistency": 78,
  "scoreEngagement": 75,
  "analysis": {
    "pacing": { "...": "完整 analysis_json 中的 pacing 部分" },
    "characters": { "...": "完整 analysis_json 中的 characters 部分" },
    "dialogue": { "...": "完整 analysis_json 中的 dialogue 部分" },
    "consistency": { "...": "完整 analysis_json 中的 consistency 部分" },
    "engagement": { "...": "完整 analysis_json 中的 engagement 部分" },
    "overallSuggestions": ["..."]
  },
  "tokenCost": 4520,
  "createdAt": "2026-02-17T10:33:00Z"
}
```

#### 3.3.3 连续性问题接口

##### GET `/v2/stories/{storyId}/analysis/continuity-issues` — 查询问题列表

支持查询参数过滤：`?issueType=character_inconsistency&severity=critical&status=open`

响应（200）：

```json
[
  {
    "id": "issue-uuid-001",
    "storyId": "story-uuid-001",
    "reportId": "report-uuid-001",
    "issueType": "character_inconsistency",
    "severity": "critical",
    "description": "林逸的眼睛颜色不一致：第2章描述为蓝色，第7章描述为棕色",
    "evidence": [
      {
        "chapterIndex": 2,
        "sceneIndex": 1,
        "textExcerpt": "林逸的蓝色眼睛在月光下闪烁",
        "lineHint": "第3段第2行"
      },
      {
        "chapterIndex": 7,
        "sceneIndex": 0,
        "textExcerpt": "他棕色的眼眸中满是坚定",
        "lineHint": "第1段第5行"
      }
    ],
    "suggestion": "建议统一为蓝色（首次出现的描述），修改第7章第1段的描述",
    "status": "open",
    "resolvedAt": null,
    "createdAt": "2026-02-17T10:33:00Z"
  },
  {
    "id": "issue-uuid-002",
    "storyId": "story-uuid-001",
    "reportId": null,
    "issueType": "dead_character",
    "severity": "critical",
    "description": "张老三在第5章战斗中死亡，但在第12章再次出现并参与对话",
    "evidence": [
      {
        "chapterIndex": 5,
        "sceneIndex": 2,
        "textExcerpt": "张老三倒在血泊中，再也没有站起来",
        "lineHint": "第8段"
      },
      {
        "chapterIndex": 12,
        "sceneIndex": 1,
        "textExcerpt": "张老三拍了拍林逸的肩膀，笑道：'小子，不错嘛'",
        "lineHint": "第4段"
      }
    ],
    "suggestion": "如果张老三确实死亡，需要删除第12章的出场；如果是假死，需要在第5-12章之间补充复活/假死的情节",
    "status": "open",
    "resolvedAt": null,
    "createdAt": "2026-02-17T10:35:00Z"
  }
]
```

##### PUT `/v2/stories/{storyId}/analysis/continuity-issues/{issueId}` — 更新问题状态

请求体：

```json
{
  "status": "resolved"
}
```

可选状态值：`acknowledged`（已确认，待修复）、`resolved`（已解决）、`false_positive`（误报）

响应（200）：

```json
{
  "id": "issue-uuid-001",
  "status": "resolved",
  "resolvedAt": "2026-02-17T11:00:00Z"
}
```

### 3.4 前端设计

#### 3.4.1 组件结构

```
frontend/src/pages/Workbench/tabs/AnalysisDashboard.tsx    // 分析总览页（新增 Tab）
frontend/src/components/analysis/
├── ReportCard.tsx                  // 报告摘要卡片（评分 + 状态）
├── ScoreRadarChart.tsx             // 五维雷达图（recharts RadarChart）
├── BetaReaderReportView.tsx        // 完整报告详情页
│   ├── PacingChart.tsx             // 张力曲线折线图
│   ├── CharacterArcTimeline.tsx    // 角色发展时间线
│   ├── DialogueQualityBreakdown.tsx // 对话质量分解
│   └── ChapterByChapterAnalysis.tsx // 逐章分析列表
├── ContinuityIssueList.tsx         // 连续性问题列表（可过滤）
│   ├── IssueCard.tsx               // 单个问题卡片（证据 + 建议 + 操作）
│   └── IssueStatusBadge.tsx        // 问题状态徽章
├── AnalysisJobProgress.tsx         // 分析任务进度条
└── TriggerAnalysisDialog.tsx       // 触发分析的对话框
```

#### 3.4.2 Workbench 集成

在现有 `Workbench.tsx` 的 TabsList 中新增第6个 Tab：

```tsx
// 新增 import
import { ClipboardCheck } from "lucide-react";
import AnalysisDashboard from "./tabs/AnalysisDashboard";

// TabsList 改为 grid-cols-6
<TabsTrigger value="analysis" className="gap-2">
  <ClipboardCheck className="h-4 w-4" /> 质量分析
</TabsTrigger>

// TabsContent
<TabsContent value="analysis" className="h-full m-0 border-0 p-0">
  <AnalysisDashboard initialStoryId={storyId} />
</TabsContent>
```

#### 3.4.3 核心组件说明

##### AnalysisDashboard

分析总览页面，分为三个区域：

1. **顶部操作栏**：故事选择器 + "运行 Beta Reader" 按钮 + "运行连续性检查" 按钮
2. **进行中的任务**：显示当前正在运行的分析任务及其进度（轮询 `/analysis/jobs`，间隔 3 秒）
3. **历史报告列表**：按时间倒序展示所有已完成的报告卡片
4. **未解决问题摘要**：显示当前 open 状态的连续性问题数量，按严重程度分组

##### ScoreRadarChart

使用 `recharts` 的 `RadarChart` 组件，展示五个维度的评分：

```tsx
import { RadarChart, PolarGrid, PolarAngleAxis, Radar, ResponsiveContainer } from 'recharts';

const dimensions = [
  { dimension: '叙事节奏', score: report.scorePacing },
  { dimension: '角色塑造', score: report.scoreCharacters },
  { dimension: '对话质量', score: report.scoreDialogue },
  { dimension: '情节连贯', score: report.scoreConsistency },
  { dimension: '读者吸引', score: report.scoreEngagement },
];
```

##### PacingChart

使用 `recharts` 的 `AreaChart` 展示张力曲线，X 轴为章节/段落位置（0-100%），Y 轴为张力值（1-10）。支持鼠标悬停显示对应位置的标签说明。

##### ContinuityIssueList

可过滤的问题列表，支持：
- 按类型过滤（下拉多选）
- 按严重程度过滤（critical / warning / info）
- 按状态过滤（open / acknowledged / resolved / false_positive）
- 点击问题卡片可展开查看完整证据和建议
- 每个问题卡片提供快捷操作按钮：确认 / 标记已解决 / 标记误报

##### IssueCard

```tsx
<Card className={cn(
  "border-l-4",
  severity === 'critical' && "border-l-red-500",
  severity === 'warning' && "border-l-yellow-500",
  severity === 'info' && "border-l-blue-500"
)}>
  <CardHeader>
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <IssueStatusBadge status={issue.status} />
        <Badge variant="outline">{issueTypeLabel(issue.issueType)}</Badge>
      </div>
      <DropdownMenu>
        {/* 状态变更操作 */}
      </DropdownMenu>
    </div>
    <CardTitle className="text-sm">{issue.description}</CardTitle>
  </CardHeader>
  <CardContent>
    {/* 证据列表：每条证据显示章节位置 + 文本摘录 */}
    {/* 修复建议 */}
    {/* "跳转到原文" 按钮 */}
    <Button
      variant="ghost" size="sm"
      onClick={() => {
        // 1. 切换 Workbench 到 ManuscriptWriter Tab
        // 2. 定位到对应章节 + 场景
        // 3. 在编辑器中搜索 evidence.textExcerpt 并滚动到该位置
        // 4. 对匹配文本施加 highlight-flash 动画（黄色背景 → 渐隐）
        workbenchStore.switchTab('manuscript');
        editorBridge.scrollToAndHighlight({
          chapterIndex: evidence.chapterIndex,
          sceneIndex: evidence.sceneIndex,
          searchText: evidence.textExcerpt,
        });
      }}
    >
      <ExternalLink className="h-3 w-3 mr-1" />
      跳转到原文
    </Button>
  </CardContent>
</Card>
```

#### 3.4.4 编辑器联动设计

Beta Reader 报告采用卡片式展示，并与 TiptapEditor 深度联动，让作者可以在阅读问题的同时快速定位到原文对应位置。

##### 整体交互流程

```
IssueCard 点击"跳转到原文"
  → workbenchStore.switchTab('manuscript')
  → editorBridge.scrollToAndHighlight({ chapterIndex, sceneIndex, searchText })
      → ManuscriptWriter 切换到目标章节/场景
      → TiptapEditor 执行文本搜索，滚动到匹配位置
      → 对匹配文本施加 highlight-flash 动画（黄色背景 2s 渐隐）
```

##### 编辑器 Gutter 标记

在 TiptapEditor 左侧 gutter（行号区域）显示彩色圆点，标识当前场景中存在问题的文本行。圆点颜色与问题严重程度对应：

| 颜色 | 严重程度 | CSS 变量 |
|------|----------|----------|
| 红色 `#ef4444` | critical | `--gutter-dot-critical` |
| 黄色 `#eab308` | warning | `--gutter-dot-warning` |
| 蓝色 `#3b82f6` | info | `--gutter-dot-info` |

交互行为：

- 鼠标悬停 gutter 圆点 → 显示 Tooltip，内容为问题摘要（`issue.description` 截取前 80 字符）
- 单击 gutter 圆点 → 在编辑器内联展开问题详情面板（包含完整描述、证据摘录、修复建议、状态操作按钮）
- 同一行存在多个问题时，显示最高严重程度的颜色，Tooltip 中列出所有问题

##### TiptapEditor Gutter 扩展

通过自定义 Tiptap Extension 实现 gutter 标记功能：

```typescript
// frontend/src/components/editor/extensions/IssueGutterExtension.ts

import { Extension } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';

/** 单个 gutter 标记的数据结构 */
export interface GutterMarker {
  /** 问题 ID，用于点击时定位到对应 IssueCard */
  issueId: string;
  /** 问题严重程度，决定圆点颜色 */
  severity: 'critical' | 'warning' | 'info';
  /** 问题摘要，用于 Tooltip 显示 */
  summary: string;
  /** 标记起始位置（ProseMirror 文档偏移量） */
  from: number;
  /** 标记结束位置 */
  to: number;
}

/** 扩展配置项 */
export interface IssueGutterOptions {
  /** 当前场景关联的 gutter 标记列表 */
  markers: GutterMarker[];
  /** 点击 gutter 圆点的回调 */
  onMarkerClick?: (marker: GutterMarker) => void;
  /** 悬停 gutter 圆点的回调（用于显示 Tooltip） */
  onMarkerHover?: (marker: GutterMarker | null, coords: { x: number; y: number }) => void;
}

const issueGutterPluginKey = new PluginKey('issueGutter');

export const IssueGutterExtension = Extension.create<IssueGutterOptions>({
  name: 'issueGutter',

  addOptions() {
    return {
      markers: [],
      onMarkerClick: undefined,
      onMarkerHover: undefined,
    };
  },

  addProseMirrorPlugins() {
    const { markers, onMarkerClick, onMarkerHover } = this.options;

    return [
      new Plugin({
        key: issueGutterPluginKey,
        props: {
          decorations(state) {
            const decorations: Decoration[] = [];
            for (const marker of markers) {
              // 文本范围高亮（半透明背景）
              decorations.push(
                Decoration.inline(marker.from, marker.to, {
                  class: `issue-highlight issue-highlight--${marker.severity}`,
                })
              );
              // Gutter 圆点（通过 widget decoration 挂载到行首）
              const lineStart = state.doc.resolve(marker.from).start();
              decorations.push(
                Decoration.widget(lineStart, () => {
                  const dot = document.createElement('span');
                  dot.className = `gutter-dot gutter-dot--${marker.severity}`;
                  dot.dataset.issueId = marker.issueId;
                  dot.title = marker.summary;
                  return dot;
                }, { side: -1, key: `gutter-${marker.issueId}` })
              );
            }
            return DecorationSet.create(state.doc, decorations);
          },
        },
      }),
    ];
  },
});
```

对应的 CSS 样式：

```css
/* frontend/src/components/editor/styles/issue-gutter.css */

.gutter-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  cursor: pointer;
  vertical-align: middle;
  transition: transform 0.15s ease;
}
.gutter-dot:hover {
  transform: scale(1.5);
}
.gutter-dot--critical { background-color: #ef4444; }
.gutter-dot--warning  { background-color: #eab308; }
.gutter-dot--info     { background-color: #3b82f6; }

.issue-highlight {
  border-radius: 2px;
  transition: background-color 0.3s ease;
}
.issue-highlight--critical { background-color: rgba(239, 68, 68, 0.12); }
.issue-highlight--warning  { background-color: rgba(234, 179, 8, 0.12); }
.issue-highlight--info     { background-color: rgba(59, 130, 246, 0.10); }

/* 跳转定位时的闪烁高亮动画 */
@keyframes highlight-flash {
  0%   { background-color: rgba(250, 204, 21, 0.5); }
  100% { background-color: transparent; }
}
.highlight-flash {
  animation: highlight-flash 2s ease-out forwards;
}
```

##### editorBridge 跳转定位逻辑

`editorBridge` 是连接 AnalysisDashboard 与 ManuscriptWriter 的跨 Tab 通信桥梁，核心方法如下：

```typescript
// frontend/src/lib/editorBridge.ts

export interface ScrollToTarget {
  chapterIndex: number;
  sceneIndex: number;
  searchText: string;
}

/**
 * 跳转到编辑器中的指定文本位置并施加高亮动画。
 *
 * 执行步骤：
 * 1. 通知 ManuscriptWriter 切换到目标章节和场景
 * 2. 等待编辑器内容加载完成（通过 Promise 回调）
 * 3. 在 ProseMirror 文档中搜索 searchText
 * 4. 将匹配位置滚动到视口中央
 * 5. 对匹配文本施加 highlight-flash CSS 动画（黄色背景 2s 渐隐）
 * 6. 动画结束后自动移除 highlight-flash class
 */
export function scrollToAndHighlight(target: ScrollToTarget): void {
  // 通过 zustand store 或 EventEmitter 通知 ManuscriptWriter
  workbenchStore.getState().navigateToScene(
    target.chapterIndex,
    target.sceneIndex
  );

  // 监听编辑器就绪事件后执行搜索和高亮
  editorEvents.once('scene-loaded', (editor) => {
    const { doc } = editor.state;
    const text = doc.textContent;
    const index = text.indexOf(target.searchText);
    if (index === -1) return;

    // 将文档文本偏移量转换为 ProseMirror position
    const from = mapTextOffsetToPos(doc, index);
    const to = from + target.searchText.length;

    // 滚动到视口中央
    editor.commands.scrollIntoView();
    editor.commands.setTextSelection({ from, to });

    // 施加闪烁高亮
    const dom = editor.view.domAtPos(from);
    applyHighlightFlash(dom, to - from);
  });
}
```

##### Gutter 标记数据流

```
AnalysisDashboard 加载报告
  → 从 continuity_issues API 获取当前故事的所有 open 问题
  → 按 chapterIndex + sceneIndex 分组
  → 存入 zustand analysisStore.issuesByScene

ManuscriptWriter 切换场景时
  → 从 analysisStore.issuesByScene 读取当前场景的问题列表
  → 将 evidence.textExcerpt 在编辑器文档中搜索，计算 from/to 位置
  → 转换为 GutterMarker[] 传入 IssueGutterExtension
  → 编辑器渲染 gutter 圆点和文本高亮
```

### 3.5 Prompt 工程

#### 3.5.1 Beta Reader 章节分析 Prompt

```
[System]
你是一位专业的小说 Beta Reader，拥有丰富的文学评论和编辑经验。你的任务是对给定的章节进行全面、客观、建设性的分析。

你需要从以下五个维度进行评估，每个维度给出 1-100 的评分和详细分析：

1. 叙事节奏 (pacing)：
   - 场景转换是否流畅
   - 是否存在过长的静态描写或过快的情节推进
   - 张力曲线是否合理（有起伏，而非一马平川或持续高压）
   - 输出张力曲线数据点（position: 0.0-1.0, tension: 1-10, label）

2. 角色塑造 (characterization)：
   - 角色行为是否符合已建立的性格特征
   - 角色是否有成长或变化
   - 角色之间的互动是否自然
   - 列出每个出场角色的发展评估

3. 对话质量 (dialogue)：
   - 对话是否推动情节或揭示角色
   - 不同角色的对话是否有区分度
   - 对话标签是否多样（避免过度使用"说"）
   - "展示而非讲述"的比例
   - 统计对话总行数和标签多样性

4. 情节连贯性 (consistency)：
   - 本章内部是否存在逻辑矛盾
   - 与前文是否存在不一致（如有前文摘要的话）
   - 因果关系是否合理

5. 读者吸引力 (engagement)：
   - 开头是否有足够的钩子
   - 结尾是否留有悬念或满足感
   - 情感共鸣度
   - 是否让读者想继续阅读

请严格按照以下 JSON 格式输出分析结果（不要输出任何 JSON 之外的内容）：

{
  "pacing": {
    "score": <1-100>,
    "tensionCurve": [{"position": <0.0-1.0>, "tension": <1-10>, "label": "<描述>"}],
    "issues": ["<问题1>", "<问题2>"],
    "suggestions": ["<建议1>", "<建议2>"]
  },
  "characters": {
    "score": <1-100>,
    "arcs": [
      {
        "name": "<角色名>",
        "role": "<protagonist/antagonist/supporting/minor>",
        "developmentNotes": "<本章发展评估>",
        "consistencyIssues": ["<不一致问题>"]
      }
    ],
    "suggestions": ["<建议>"]
  },
  "dialogue": {
    "score": <1-100>,
    "totalLines": <对话总行数>,
    "showVsTellRatio": <0.0-1.0>,
    "tagVariety": <0.0-1.0>,
    "issues": ["<问题>"],
    "suggestions": ["<建议>"]
  },
  "consistency": {
    "score": <1-100>,
    "issues": [
      {
        "type": "<timeline_error/character_inconsistency/location_error/plot_hole>",
        "description": "<描述>",
        "severity": "<critical/warning/info>"
      }
    ]
  },
  "engagement": {
    "score": <1-100>,
    "hookStrength": <1-10>,
    "cliffhangerScore": <1-10>,
    "emotionalResonance": <1-10>,
    "suggestions": ["<建议>"]
  },
  "overallSuggestions": ["<总体建议1>", "<总体建议2>"]
}

[User]
## 故事信息
- 标题：{story_title}
- 类型：{story_genre}
- 基调：{story_tone}

## 前情摘要（前几章的关键信息）
{previous_chapters_summary}

## 角色信息
{character_cards_summary}

## 当前章节
### 第{chapter_index}章：{chapter_title}

{chapter_content}
```

#### 3.5.2 连续性检查 Prompt

```
[System]
你是一位小说连续性检查专家。你的任务是仔细比对当前章节内容与已知事实库，找出所有不一致之处。

你需要检查以下类型的不一致：

1. character_inconsistency（角色属性不一致）：
   - 外貌描述变化（眼睛颜色、发色、身高等）
   - 性格突变（无合理铺垫的性格转变）
   - 能力/技能矛盾（使用了不应该拥有的能力）
   - 背景信息矛盾

2. timeline_error（时间线错误）：
   - 事件发生顺序矛盾
   - 时间跨度不合理（一天内完成了需要数天的事）
   - 日期/季节/时间描述矛盾

3. location_error（地点错误）：
   - 角色不可能在该时间出现在该地点
   - 地点描述与之前不一致（建筑布局、地理特征）
   - 距离/旅行时间不合理

4. object_state（物品状态矛盾）：
   - 已损毁/丢失的物品再次出现
   - 物品属性描述变化
   - 物品所有权矛盾

5. dead_character（已死角色复活）：
   - 已明确死亡的角色再次出场
   - 已离开的角色在不合理的情况下出现

6. plot_hole（情节漏洞）：
   - 未解释的情节转折
   - 被遗忘的伏笔或承诺
   - 因果关系断裂

7. name_inconsistency（名称不一致）：
   - 角色名字拼写变化
   - 地名/组织名不一致
   - 称呼方式矛盾

对于每个发现的问题，请提供：
- 问题类型
- 严重程度（critical: 明显矛盾，读者一定会注意到；warning: 可能的矛盾，需要作者确认；info: 轻微不一致，可能是有意为之）
- 详细描述
- 证据（引用原文，标注章节和大致位置）
- 修复建议

请严格按照以下 JSON 格式输出（不要输出任何 JSON 之外的内容）：

{
  "issues": [
    {
      "issueType": "<类型>",
      "severity": "<critical/warning/info>",
      "description": "<详细描述>",
      "evidence": [
        {
          "chapterIndex": <章节索引>,
          "sceneIndex": <场景索引>,
          "textExcerpt": "<引用原文>",
          "lineHint": "<大致位置>"
        }
      ],
      "suggestion": "<修复建议>"
    }
  ],
  "summary": "<总结：共发现X个问题，其中critical X个，warning X个，info X个>"
}

如果没有发现任何问题，请返回：
{
  "issues": [],
  "summary": "未发现连续性问题，当前章节与已知事实一致。"
}

[User]
## 已知事实库

### Lorebook 条目
{lorebook_entries}

### 知识图谱事实
角色属性：
{character_attributes_from_graph}

角色关系：
{character_relationships_from_graph}

事件时间线：
{event_timeline_from_graph}

地点信息：
{location_details_from_graph}

物品状态：
{item_states_from_graph}

### 前文关键事实摘要
{previous_chapters_facts}

## 当前章节
### 第{chapter_index}章：{chapter_title}

{chapter_content}
```

#### 3.5.3 全稿综合分析 Prompt

```
[System]
你是一位资深的小说编辑，正在对一部完整的小说进行全面审读。你已经收到了每个章节的独立分析结果，现在需要进行跨章节的综合评估。

请从以下角度进行全稿综合分析：

1. 整体叙事弧线：故事的起承转合是否完整，高潮是否在合适的位置
2. 角色成长弧线：主要角色从开始到结束是否有清晰的成长/变化轨迹
3. 节奏一致性：是否存在连续多章的节奏问题（如连续平淡或连续高压）
4. 主题一致性：全书的主题是否贯穿始终
5. 伏笔回收：是否有未回收的伏笔或悬念

请以 JSON 格式输出：

{
  "overallArc": {
    "score": <1-100>,
    "structure": "<三幕/四幕/英雄之旅/其他>",
    "analysis": "<整体结构分析>",
    "suggestions": ["<建议>"]
  },
  "characterArcs": [
    {
      "name": "<角色名>",
      "arcType": "<成长/堕落/平坦/循环>",
      "completeness": <1-10>,
      "analysis": "<弧线分析>"
    }
  ],
  "pacingOverview": {
    "globalTensionCurve": [{"chapter": <章节号>, "avgTension": <1-10>}],
    "problematicSections": ["<问题区间描述>"],
    "suggestions": ["<建议>"]
  },
  "thematicConsistency": {
    "score": <1-100>,
    "mainThemes": ["<主题1>", "<主题2>"],
    "analysis": "<主题一致性分析>"
  },
  "unresolvedPlotThreads": [
    {
      "description": "<未回收的伏笔/悬念>",
      "introducedAt": "第X章",
      "severity": "<critical/warning/info>"
    }
  ],
  "executiveSummary": "<200字以内的全稿总评>"
}

[User]
## 故事信息
- 标题：{story_title}
- 类型：{story_genre}
- 基调：{story_tone}
- 总章节数：{total_chapters}
- 总字数：{total_word_count}

## 各章节分析摘要
{per_chapter_analysis_summaries}

## 角色列表
{character_cards_summary}
```

#### 3.5.4 Prompt 构建策略

`AnalysisPromptBuilder` 负责根据分析类型和范围动态构建 Prompt：

```java
@Component
public class AnalysisPromptBuilder {

    /**
     * 构建章节级 Beta Reader Prompt。
     * Token 预算分配：
     * - System prompt: ~800 tokens
     * - 故事信息 + 角色信息: ~500 tokens
     * - 前情摘要: ~1000 tokens（从 context_snapshots 获取）
     * - 章节正文: 剩余预算（通常 4000-8000 tokens）
     * - 预留输出空间: ~2000 tokens
     */
    public List<AiChatRequest.Message> buildChapterAnalysisPrompt(
            Story story,
            List<CharacterCard> characters,
            String chapterContent,
            String previousSummary) { ... }

    /**
     * 构建连续性检查 Prompt。
     * Token 预算分配：
     * - System prompt: ~1200 tokens
     * - Lorebook 条目: ~1500 tokens（按相关性排序截取）
     * - 知识图谱事实: ~1000 tokens
     * - 前文事实摘要: ~1000 tokens
     * - 章节正文: 剩余预算
     * - 预留输出空间: ~1500 tokens
     */
    public List<AiChatRequest.Message> buildContinuityCheckPrompt(
            Story story,
            String lorebookFacts,
            String graphFacts,
            String previousFacts,
            String chapterContent) { ... }

    /**
     * 构建全稿综合分析 Prompt。
     * 输入为各章节分析结果的摘要，而非原文。
     */
    public List<AiChatRequest.Message> buildFullManuscriptPrompt(
            Story story,
            List<CharacterCard> characters,
            List<BetaReaderReport> chapterReports) { ... }
}
```

---

## 4. 与现有代码的集成点

### 4.1 后端集成

| 现有文件 | 集成方式 | 说明 |
|----------|----------|------|
| `com.ainovel.app.manuscript.ManuscriptService` | 调用方 | 分析服务需要从 ManuscriptService 获取章节文本（`sectionsJson`） |
| `com.ainovel.app.story.StoryController` | 路由挂载 | 新增 `/v2/stories/{storyId}/analysis/**` 路由，由独立的 `AnalysisController` 处理 |
| `com.ainovel.app.story.model.Story` | 数据关联 | `beta_reader_reports`、`continuity_issues`、`analysis_jobs` 均通过 `story_id` 关联 |
| `com.ainovel.app.story.model.CharacterCard` | 数据读取 | 分析时需要读取角色卡信息作为上下文 |
| `com.ainovel.app.ai.AiService` | AI 调用 | 所有分析 Prompt 通过 `AiService.chat()` -> `AiGatewayGrpcClient.chatCompletions()` 发送 |
| `com.ainovel.app.security.SecurityConfig` | 权限配置 | 新增 `/v2/stories/*/analysis/**` 路径的认证规则 |
| 01-上下文记忆系统（Lorebook + KAG） | 事实来源 | 连续性检查依赖 Lorebook 条目和知识图谱中的结构化事实 |
| 02-风格画像系统 | 交叉引用 | Beta Reader 的对话分析可引用角色声音画像进行对比 |

### 4.2 前端集成

| 现有文件 | 集成方式 | 说明 |
|----------|----------|------|
| `frontend/src/pages/Workbench/Workbench.tsx` | 新增 Tab | 添加"质量分析"Tab，挂载 `AnalysisDashboard` 组件 |
| `frontend/src/pages/Workbench/tabs/ManuscriptWriter.tsx` | 快捷入口 | 在编辑器工具栏添加"分析本章"快捷按钮 |
| `frontend/src/components/editor/EditorToolbar.tsx` | 工具栏扩展 | 添加分析相关的工具栏按钮 |
| `frontend/src/components/ui/` | UI 组件复用 | 复用 Card、Badge、Progress、Tabs 等 shadcn/ui 组件 |

### 4.3 新增前端依赖

| 依赖 | 用途 |
|------|------|
| `recharts`（已有或新增） | 雷达图、折线图、面积图等数据可视化 |

---

## 5. 实施注意事项

### 5.1 Token 限制与分块策略

全稿分析面临的核心挑战是 Token 限制。一部 10 万字的小说约 50,000-70,000 tokens（中文），远超单次 API 调用的上下文窗口。

**分块策略**：

1. **章节级分析**：每次只分析一个章节（通常 3000-8000 字，约 2000-5000 tokens），加上系统提示和上下文信息，总计约 6000-10000 tokens，在大多数模型的窗口内。

2. **全稿分析采用 Map-Reduce 模式**：
   - Map 阶段：逐章分析，每章独立调用 AI，生成章节级报告
   - Reduce 阶段：将所有章节报告的摘要（而非原文）汇总，调用 AI 进行全稿综合分析
   - 这样 Reduce 阶段的输入量可控（每章摘要约 200-300 tokens，20 章约 4000-6000 tokens）

3. **连续性检查的事实窗口**：
   - 不将全部 Lorebook 条目注入，而是根据当前章节的关键词和语义相似度，检索最相关的条目（Token 预算 ~1500）
   - 知识图谱查询限定为当前章节出现的角色/地点的相关子图

### 5.2 成本管理

| 分析类型 | 预估 Token 消耗 | 说明 |
|----------|-----------------|------|
| 单章 Beta Reader | 8,000 - 12,000 | 输入 ~8000 + 输出 ~2000 |
| 单章连续性检查 | 6,000 - 10,000 | 输入 ~7000 + 输出 ~1500 |
| 全稿 Beta Reader（20章） | 180,000 - 280,000 | 20 次章节分析 + 1 次综合分析 |
| 全稿连续性检查（20章） | 140,000 - 220,000 | 20 次章节检查 + 交叉比对 |

**成本控制措施**：
- 通过 `EconomyService` 在触发分析前检查用户余额
- 全稿分析前给出预估消耗，需用户确认
- 支持中途取消（已完成的章节结果保留）
- `beta_reader_reports.token_cost` 记录实际消耗，用于账单和统计

### 5.3 误报处理

连续性检查不可避免会产生误报（false positives），特别是：
- 有意为之的不一致（如不可靠叙述者、角色说谎）
- 隐喻/比喻被当作字面描述
- 角色昵称/别名未被识别

**应对策略**：
- 提供 `false_positive` 状态，用户可标记误报
- 被标记为误报的模式会被记录，后续分析时作为排除条件注入 Prompt
- 严重程度分级（critical/warning/info），让用户优先关注高严重度问题
- AI 分析时要求输出置信度，低置信度的问题标记为 `info`

### 5.4 异步执行与进度反馈

复用现有的 Spring `@Async` + 状态轮询模式（与 `MaterialUploadJob` 一致）：

1. 用户触发分析 -> 立即返回 `AnalysisJob`（status: queued）
2. 后台异步执行，逐步更新 `progress` 和 `progressMessage`
3. 前端每 3 秒轮询 `/analysis/jobs/{jobId}` 获取进度
4. 完成后 `status` 变为 `completed`，`resultReference` 指向报告 ID
5. 前端检测到完成后自动加载报告

**进度更新粒度**（以全稿 Beta Reader 为例）：
- 0%: 开始分析
- 5%: 正在收集故事信息和角色数据
- 10-80%: 逐章分析（每章完成后更新，如"正在分析第3/20章..."）
- 85%: 正在进行跨章节综合分析
- 90%: 正在运行连续性检查
- 95%: 正在生成报告
- 100%: 分析完成

### 5.5 并发控制

- 同一故事同一时间只允许运行一个分析任务（通过 `analysis_jobs` 表的 status 字段检查）
- 触发新分析前检查是否有 `queued` 或 `processing` 状态的任务，如有则拒绝并返回现有任务 ID
- 任务超时机制：超过 30 分钟未完成的任务自动标记为 `failed`

---

## 6. 验收标准

### 6.1 功能验收

| 编号 | 验收项 | 验收条件 |
|------|--------|----------|
| AC-1 | 章节级 Beta Reader | 用户可对任意章节触发分析，3分钟内返回包含五维评分和详细建议的报告 |
| AC-2 | 全稿 Beta Reader | 用户可对全稿触发分析，系统逐章处理并实时更新进度，最终生成综合报告 |
| AC-3 | 连续性检查 | 系统能检测出角色属性矛盾、时间线错误、已死角色复活等至少5种类型的连续性问题 |
| AC-4 | 知识图谱集成 | 连续性检查能利用 Lorebook 条目和知识图谱中的事实进行校验 |
| AC-5 | 问题追踪 | 发现的问题可被标记为 acknowledged/resolved/false_positive，状态变更持久化 |
| AC-6 | 可视化报告 | 报告页面包含雷达图（五维评分）和张力曲线图，数据正确渲染 |
| AC-7 | 异步执行 | 分析任务异步执行，不阻塞用户操作，进度条实时更新 |
| AC-8 | 成本控制 | 触发全稿分析前显示预估 Token 消耗，分析完成后记录实际消耗 |
| AC-9 | 并发控制 | 同一故事不能同时运行多个分析任务 |
| AC-10 | 错误处理 | AI 调用失败时任务状态正确更新为 failed，用户可查看错误信息并重试 |

### 6.2 性能验收

| 指标 | 目标值 |
|------|--------|
| 单章分析响应时间 | < 60 秒（从触发到报告生成） |
| 全稿分析（20章）总时间 | < 15 分钟 |
| 进度轮询接口响应时间 | < 100ms |
| 报告详情加载时间 | < 500ms |
| 问题列表查询时间（含过滤） | < 200ms |

### 6.3 质量验收

| 指标 | 目标值 |
|------|--------|
| 连续性检查召回率（人工标注测试集） | > 70% |
| 连续性检查精确率 | > 60%（误报率 < 40%） |
| Beta Reader 评分与人类评审的相关性 | > 0.6（Pearson 相关系数） |
