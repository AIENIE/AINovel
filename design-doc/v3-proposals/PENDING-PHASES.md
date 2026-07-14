# AINovel v3 G2 — Pending Phases / 后续分期事项

决策日期：2026-07-12。除已落地的 G2 Step 1 外，以下条目均为**已明确计划、暂缓实施**的内容，不是被放弃的需求。

---

## 已实现：G2 Step 1 — Constrained Drafting（带约束起草）

**范围**（仅限此项）：
- Flyway `V2__slop_patterns.sql` 建立 `slop_patterns` 表并写入 38 条初始模式
- `PromptAssemblyService` 新增 `assembleWithCreativeConstraints()`：按类别配额采样 15 条负面约束，并轮换注入 2 项人味要素
- `ManuscriptService.generateForScene` 增加 `mode: FAST / CRAFTED` 参数（默认 FAST，保持向后兼容）
- API 暴露 mode 参数；前端加 精雕/快速 模式开关
- **不含**：节拍规划、多候选竞技场、AI 网关采样参数透传

实现边界：不新增模型调用，但动态 system 约束会影响 token、缓存和延迟；实际成本以运行时数据为准。

---

## 待续：G2 完整方案 A — 节拍规划 + 多候选竞技场

**触发条件**：Step 1 上线后 crafted 模式盲测"更想读下去"得票率 ≥ 55%（证明约束注入有效），即可推进。

**新增内容**：
- `PromptAssemblyService.assembleBeatPlan()`：生成 2-3 个差异化节拍方案（欲望/冲突/代价三要素 + 情绪曲线位置 + 结尾钩子类型），使用 Verbalized Sampling 范式
- `SlopQualityGate.scoreCandidate()`：抽出评分能力供竞技场独立调用
- 候选并行生成 N=2~3，快筛（LocalSlopHeuristics）+ 精评（composite = slop 风险反向 + 节拍达成度 + 钩子有效性）
- 新数据表：`creativity_profiles`、`candidate_runs`、`candidate_items`（`candidate_items` 是方案 C 的训练数据资产）
- `EconomyService` 计费接入（crafted 模式成本约为 fast ×2.5~×4）

**设计规格**：`design-doc/v3-proposals/02-生成端反AI味与创造力.md` §4

---

## 待续：AI 网关采样参数透传（min-p）

**影响**：解锁 temp 0.9-1.1 + min-p 0.05 采样，多样性提升 2-3 倍（Verbalized Sampling 2025 论文数据）。

**前置**：需与 ai-service 团队协调 gRPC 接口，增加 temperature / min_p 透传字段。

**对现有功能的影响**：纯增量，不改变现有 chat 语义；当前不设置则使用网关默认参数，行为不变。

**建议排期**：与完整方案 A 同期或稍早排入，避免方案 A 上线后再次改动 AI 网关。

---

## 待续：Manual 模式 — 用户介入节拍方案 / 候选选择

**前置**：完整方案 A（多候选竞技场）已上线。

**内容**：
- 节拍方案选择面板：展示 2-3 个候选节拍方案（摘要形式），用户可选择或让系统自动选
- 草稿候选选择面板：并排展示 N 个候选正文，用户可预览并选择或合并
- `creativity_profiles.beat_review_mode` 切换为 `manual`

---

## 待续：题材分库 — 按题材分类负面约束模式库

**前置**：统一库上线并积累一定使用数据。

**内容**：
- `slop_patterns` 表增加 `genre` 字段（都市 / 仙侠 / 武侠 / 女频 / 通用）
- 采样时优先匹配当前故事题材 + 通用库混合
- 外部研究（WebNovelBench 2026）确认：仙侠/武侠的 genre homogeneity 问题远比通用小说严重，题材特定模式清单是必要的

---

## 待续：长篇故事上下文检索增强（SCORE 方向）

**前置**：明确归入 v3 哪个目标统一设计（当前 G2 提案未显式覆盖此点；v2/01 上下文记忆系统是最近的设计）。

**内容**：扩展现有 Qdrant（material 模块已有）为 story context 向量库，生成场景时检索历史相关场景片段注入上下文，改善人物一致性与情节连贯性。

**参考**：SCORE（Story Coherence and Retrieval Enhancement，Stanford 2025）；DOC 框架。

---

## 长期储备：G2 方案 B — 多模型创意流水线 + 创意人格

**前置**：`AiModelPolicy` 解锁（按 `design-doc/v3-proposals/00-总体架构.md` §6.2 三步走）。

**内容**：阶段换模型（规划/起草/评审/修订用不同模型）+ `CreativePersonaInjector`（候选分配不同叙事者气质）。

---

## 长期储备：G2 方案 C — 偏好优化 / 风格 LoRA

**触发条件**：`candidate_items` 积累偏好对 ≥ 1 万，且方案 A/B 盲测提升进入平台期。

**内容**：用 `candidate_items`（chosen vs rejected 天然偏好对）+ `slop_quality_issues`（细粒度负例）+ 编辑历史 diff 做 DPO/GRPO 反 slop 偏好优化；题材 LoRA 族（快节奏爽文/细腻女频/文学向）。

---

## 关联文档

- 完整方案设计：`design-doc/v3-proposals/02-生成端反AI味与创造力.md`
- 研究与评估基础：`design-doc/v3-proposals/05-研究基础与参考.md`
- 评估体系与里程碑：`design-doc/v3-proposals/06-开发方式与评估体系.md`
